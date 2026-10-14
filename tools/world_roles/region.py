"""MCA inspection and append-only patches. Old chunk sectors remain intact for offline rollback."""
import hashlib, math, re, struct
from pathlib import Path
from .nbt import decode, encode, expand, compress
from .transform import remap_tag

SECTOR=4096

def sha(data):return hashlib.sha256(data).hexdigest()
def chunks(path):
    match=re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca',path.name)
    if not match:raise ValueError('Unexpected MCA filename')
    rx,rz=map(int,match.groups())
    with path.open('rb') as f:
        header=f.read(8192)
        if not header:return # NeoForge can create an empty region placeholder.
        if len(header)!=8192:raise ValueError('Truncated MCA header')
        used=set((0,1))
        for slot in range(1024):
            entry=int.from_bytes(header[slot*4:slot*4+4],'big');offset,count=entry>>8,entry&255
            if not entry:continue
            if offset<2 or not count or offset+count>math.ceil(path.stat().st_size/SECTOR):raise ValueError('Invalid MCA sector range')
            sectors=set(range(offset,offset+count))
            if sectors&used:raise ValueError('Overlapping MCA chunks')
            used.update(sectors);f.seek(offset*SECTOR);length=struct.unpack('>I',f.read(4))[0];kind=f.read(1)[0]
            if not length or length>count*SECTOR-4:raise ValueError('Invalid MCA payload length')
            external=kind&128;compression=kind&127
            ext=path.with_name(f'c.{rx*32+slot%32}.{rz*32+slot//32}.mcc') if external else None
            if external and length!=1:raise ValueError('Invalid external MCA record')
            payload=ext.read_bytes() if ext else f.read(length-1)
            name,tag=decode(expand(payload,compression));changes=[];new=remap_tag(tag,changes=changes)
            if changes:
                packed=compress(encode(name,new),compression)
                if not ext and math.ceil((len(packed)+5)/SECTOR)>255:raise ValueError('Patched MCA chunk needs external conversion; explicit support required')
                yield {'slot':slot,'originalPayloadSha256':sha(payload),'compression':compression,'external':str(ext) if ext else None,'newPayloadSha256':sha(packed),'newPayloadLength':len(packed),'referenceCount':len(changes),'payload':packed}

def inspect(path):
    records=[];extra=0
    for record in chunks(path):
        extra+=record['newPayloadLength'] if record['external'] else math.ceil((record['newPayloadLength']+5)/SECTOR)*SECTOR
        records.append({k:v for k,v in record.items() if k!='payload'})
    return records,extra

def append(path,expected):
    planned={r['slot']:r for r in expected};records=list(chunks(path))
    if set(planned)!=set(r['slot'] for r in records):raise ValueError('MCA plan became stale')
    with path.open('r+b') as f:
        header=bytearray(f.read(8192))
        for record in records:
            known=planned[record['slot']]
            if any(record[k]!=known[k] for k in ['originalPayloadSha256','newPayloadSha256','compression','external']):raise ValueError('MCA chunk changed after planning')
            if record['external']:continue
            packed=record['payload'];f.seek(0,2);padding=(-f.tell())%SECTOR
            if padding:f.write(b'\0'*padding)
            offset=f.tell()//SECTOR;count=math.ceil((len(packed)+5)/SECTOR)
            if offset>=1<<24:raise ValueError('MCA sector offset overflow')
            body=struct.pack('>I',len(packed)+1)+bytes([record['compression']])+packed
            f.write(body+b'\0'*(count*SECTOR-len(body)));header[record['slot']*4:record['slot']*4+4]=((offset<<8)|count).to_bytes(4,'big')
        f.flush();__import__('os').fsync(f.fileno());f.seek(0);f.write(header);f.flush();__import__('os').fsync(f.fileno())
    return records
