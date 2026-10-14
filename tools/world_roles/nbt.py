"""Bounded lossless NBT codec for offline migration; preserves untouched scalar/array bits."""
from dataclasses import dataclass
from collections import OrderedDict
import gzip, io, struct, zlib

@dataclass
class Tag:
    kind: int
    value: object
    raw: bytes | None = None

def mutf_read(b):
    units=[];i=0
    while i<len(b):
        x=b[i];i+=1
        if 0<x<128: units.append(x)
        elif x&224==192:
            if i>=len(b) or b[i]&192!=128: raise ValueError('Invalid modified UTF-8')
            units.append(((x&31)<<6)|(b[i]&63));i+=1
        elif x&240==224:
            if i+1>=len(b) or b[i]&192!=128 or b[i+1]&192!=128: raise ValueError('Invalid modified UTF-8')
            units.append(((x&15)<<12)|((b[i]&63)<<6)|(b[i+1]&63));i+=2
        else: raise ValueError('Unsupported/invalid modified UTF-8')
    return b''.join(struct.pack('>H',x) for x in units).decode('utf-16-be','surrogatepass')

def mutf_write(s):
    raw=s.encode('utf-16-be','surrogatepass');out=bytearray()
    for i in range(0,len(raw),2):
        x=int.from_bytes(raw[i:i+2],'big')
        if 0<x<128:out.append(x)
        elif x<2048:out.extend((192|(x>>6),128|(x&63)))
        else:out.extend((224|(x>>12),128|((x>>6)&63),128|(x&63)))
    if len(out)>65535:raise ValueError('NBT string exceeds unsigned-short length')
    return bytes(out)

def decode(raw,limit=64*1024*1024):
    if len(raw)>limit:raise ValueError('NBT expanded size limit')
    f=io.BytesIO(raw)
    def take(n):
        if n<0 or n>limit:raise ValueError('Invalid NBT length')
        b=f.read(n)
        if len(b)!=n:raise ValueError('Truncated NBT')
        return b
    def number(fmt):return struct.unpack(fmt,take(struct.calcsize(fmt)))[0]
    def string():return mutf_read(take(number('>H')))
    def payload(k,depth=0):
        if depth>512:raise ValueError('NBT nesting limit')
        if k in range(1,7):
            fmt={1:'>b',2:'>h',3:'>i',4:'>q',5:'>f',6:'>d'}[k];b=take(struct.calcsize(fmt));return Tag(k,struct.unpack(fmt,b)[0],b)
        if k in (7,11,12):
            count=number('>i');b=take(count*{7:1,11:4,12:8}[k]);return Tag(k,(count,b))
        if k==8:return Tag(k,string())
        if k==9:
            element=number('>B');count=number('>i')
            if count<0 or count>limit or (element==0 and count):raise ValueError('Invalid NBT list')
            return Tag(k,(element,[payload(element,depth+1) for _ in range(count)]))
        if k==10:
            fields=OrderedDict()
            while True:
                element=number('>B')
                if element==0:break
                key=string()
                if key in fields:raise ValueError('Duplicate NBT compound key')
                fields[key]=payload(element,depth+1)
            return Tag(k,fields)
        raise ValueError('Unknown NBT type '+str(k))
    kind=number('>B');name=string();tag=payload(kind)
    if f.tell()!=len(raw):raise ValueError('Unexpected trailing NBT bytes')
    return name,tag

def encode(name,tag):
    def string(s):b=mutf_write(s);return struct.pack('>H',len(b))+b
    def payload(t):
        k,v=t.kind,t.value
        if k in range(1,7):return t.raw if t.raw is not None else struct.pack({1:'>b',2:'>h',3:'>i',4:'>q',5:'>f',6:'>d'}[k],v)
        if k in (7,11,12):return struct.pack('>i',v[0])+v[1]
        if k==8:return string(v)
        if k==9:return bytes([v[0]])+struct.pack('>i',len(v[1]))+b''.join(payload(x) for x in v[1])
        if k==10:return b''.join(bytes([x.kind])+string(n)+payload(x) for n,x in v.items())+b'\0'
        raise ValueError('Unknown NBT type')
    return bytes([tag.kind])+string(name)+payload(tag)

def expand(data,compression,limit=64*1024*1024):
    if compression==3:
        if len(data)>limit:raise ValueError('NBT size limit')
        return data
    if compression not in (1,2):raise ValueError('Unsupported region compression '+str(compression))
    obj=zlib.decompressobj(31 if compression==1 else 15);raw=obj.decompress(data,limit+1)
    if len(raw)>limit or obj.unconsumed_tail:raise ValueError('NBT decompression limit')
    raw+=obj.flush()
    if len(raw)>limit or not obj.eof:raise ValueError('NBT decompression incomplete/oversized')
    if obj.unused_data:raise ValueError('Unexpected trailing compressed payload')
    return raw

def compress(raw,compression):
    if compression==1:return gzip.compress(raw,mtime=0)
    if compression==2:return zlib.compress(raw)
    if compression==3:return raw
    raise ValueError('Unsupported region compression')

def read_file(data):
    compression=1 if data.startswith(b'\x1f\x8b') else 3
    return compression,*decode(expand(data,compression))

def python_value(t):
    if t.kind==10:return {k:python_value(v) for k,v in t.value.items()}
    if t.kind==9:return [python_value(v) for v in t.value[1]]
    if t.kind in (7,11,12):return {'array_type':t.kind,'length':t.value[0]}
    return t.value
