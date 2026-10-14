"""Pinned Sable 2.0.5 containers, distinct from MCA. Geometry and identities are preserved."""
from pathlib import Path
from collections import OrderedDict
import hashlib,math,os,re,struct
from . import nbt
from .transform import MAPPING,RESOURCE_FIELDS
JAR_SHA256='c8710f85bc780bbf523e6726ceaaf76dd0b8c61479db497b382c6bb34317e7ab'
HEADER=4096
REFERENCES={'dimension','Dimension','SpawnDimension','LodestoneDimension','target_dimension','origin_dimension','source_dimension','parent_dimension'}
TEXT={'Name','display_name','CustomName','custom_name','label','title','text','name'}
KEY_MAPS={'dimensions','claims','by_dimension','worlds'}

def sha(b):return hashlib.sha256(b).hexdigest()

def verify_jar(path):
    p=Path(path)
    if not p.is_file() or sha(p.read_bytes())!=JAR_SHA256:raise ValueError('Sable runtime does not match audited 2.0.5 jar SHA256')
    return {'path':str(p.resolve()),'sha256':JAR_SHA256,'profile':'sable-2.0.5-gzip'}

def rewrite(tag,path=(),changes=None):
    changes=[] if changes is None else changes
    field=path[-1] if path else ''
    if field in REFERENCES and tag.kind!=8:raise ValueError('Non-string Sable dimension binding: '+ '/'.join(path))
    if tag.kind==8:
        value=tag.value
        if field in RESOURCE_FIELDS or field in TEXT:return tag
        if value in MAPPING:
            if field not in REFERENCES:raise ValueError('Unrecognized Sable dimension binding: '+ '/'.join(path))
            changes.append('/'.join(path));return nbt.Tag(8,MAPPING[value])
        if any(old in value for old in MAPPING):raise ValueError('Unrecognized embedded Sable dimension reference: '+ '/'.join(path))
    elif tag.kind==7:
        raw=tag.raw if tag.raw is not None else tag.value[1]
        # Raw array representation includes the length; inspect only for opaque encoded old IDs.
        if isinstance(raw,bytes) and any(old.encode() in raw for old in MAPPING):raise ValueError('Opaque Sable bytes contain old dimension reference: '+ '/'.join(path))
    elif tag.kind==9:return nbt.Tag(9,(tag.value[0],[rewrite(t,path+(str(i),),changes) for i,t in enumerate(tag.value[1])]))
    elif tag.kind==10:
        out=OrderedDict()
        for key,value in tag.value.items():
            new=key
            if key in MAPPING:
                if field not in KEY_MAPS:raise ValueError('Unrecognized Sable dimension-key map: '+ '/'.join(path+(key,)))
                new=MAPPING[key];changes.append('/'.join(path+(key,)))
            if new in out:raise ValueError('Sable dimension key collision')
            out[new]=rewrite(value,path+(key,),changes)
        return nbt.Tag(10,out)
    elif field in REFERENCES:raise ValueError('Non-string Sable dimension binding: '+ '/'.join(path))
    return tag

def layout(path):
    path=Path(path)
    if 'sublevels' not in path.parts:raise ValueError('Sable container outside an owning sublevels folder')
    if path.suffix=='.slvlr':
        m=re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.slvlr',path.name);sector=128
    elif path.suffix=='.slvls':
        m=re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.(-?\d+)\.slvls',path.name);sector=4096
    else:raise ValueError('Unknown Sable container extension')
    if not m:raise ValueError('Unknown Sable container filename')
    # Actual getRegionStorageFile calls the no-index getExternalPath overload, also for slvls.
    rx,rz=m.groups()[:2];external=path.parent/f'r.{rx}.{rz}.r'
    return sector,external

def validate_structure(tag,holding):
    if tag.kind!=10:raise ValueError('Unknown Sable record root type')
    fields=tag.value
    if holding:
        if 'pointers' not in fields or fields['pointers'].kind!=11:raise ValueError('Unknown Sable holding-chunk structure: int-array pointers required')
        return
    required={'uuid':11,'pose':10,'world_bounds':10,'plot':10}
    for key,kind in required.items():
        if key not in fields or fields[key].kind!=kind:raise ValueError('Unknown Sable construct structure: '+key)
    if fields['uuid'].value[0]!=4:raise ValueError('Invalid Sable construct UUID')
    plot=fields['plot'].value
    for key,kind in {'plot_x':3,'plot_z':3,'log_size':3,'data_version':3,'biome':8,'chunks':10}.items():
        if key not in plot or plot[key].kind!=kind:raise ValueError('Unknown Sable plot structure: '+key)
    if plot['data_version'].value!=1:raise ValueError('Unknown Sable plot data_version')
    if 'loading_dependencies' in fields:
        deps=fields['loading_dependencies']
        if deps.kind!=9 or deps.value[0] not in (0,11) or any(t.kind!=11 or t.value[0]!=4 for t in deps.value[1]):raise ValueError('Unknown Sable dependency structure')

def records(path):
    path=Path(path);sector,external=layout(path);size=path.stat().st_size
    with path.open('rb') as f:
        header=f.read(HEADER)
        if not header:return
        if len(header)!=HEADER:raise ValueError('Truncated Sable index header')
        used=set(range(HEADER//sector))
        for slot in range(1024):
            entry=int.from_bytes(header[slot*4:slot*4+4],'big')
            if not entry:continue
            offset,count=entry>>8,entry&255
            if offset<HEADER//sector or not count or offset+count>math.ceil(size/sector):raise ValueError('Invalid Sable sector span at slot '+str(slot))
            span=set(range(offset,offset+count))
            if used&span:raise ValueError('Overlapping Sable sector spans')
            used.update(span);f.seek(offset*sector);prefix=f.read(5)
            if len(prefix)!=5:raise ValueError('Truncated Sable record header')
            length,flag=struct.unpack('>IB',prefix)
            if flag not in (0,16):raise ValueError('Unknown Sable storage flag '+str(flag))
            if length<1 or length>count*sector-4:raise ValueError('Invalid Sable record length')
            ext=external/f'{slot}.slvl' if flag==16 else None
            if ext:
                if length!=1:raise ValueError('Sable record has both internal and external streams')
                if ext.is_symlink() or ext.is_junction():raise ValueError('Linked Sable external record unsupported')
                payload=ext.read_bytes()
            else:payload=f.read(length-1)
            if not payload.startswith(b'\x1f\x8b'):raise ValueError('Sable payload does not match audited gzip runtime')
            name,tag=nbt.decode(nbt.expand(payload,1))
            validate_structure(tag,path.suffix=='.slvlr')
            targets=[]
            if path.suffix=='.slvlr':
                pointers=tag.value['pointers'].value[1]
                rx,rz=re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.slvlr',path.name).groups()
                for (packed_pointer,) in struct.iter_unpack('>i',pointers):
                    storage=packed_pointer>>16;index=packed_pointer&65535
                    if storage<0 or index>=1024:raise ValueError('Invalid/unrecognized Sable construct pointer')
                    targets.append({'path':str((path.parent/f'r.{rx}.{rz}.{storage}.slvls').resolve()),'slot':index})
            changes=[];new=rewrite(tag,changes=changes);packed=nbt.compress(nbt.encode(name,new),1) if changes else payload
            if not ext and math.ceil((len(packed)+5)/sector)>255:raise ValueError('Sable internal-to-external conversion required; unsupported and blocked')
            yield {'slot':slot,'compression':1,'external':str(ext.resolve()) if ext else None,'originalPayloadSha256':sha(payload),'newPayloadSha256':sha(packed),'newPayloadLength':len(packed),'referenceCount':len(changes),'payload':packed,'sectorSize':sector,'targets':targets}

def inspect(path):
    changed=[];extra=0;externals=[];count=0;bindings=[]
    for record in records(path):
        count+=1;bindings.append({'path':str(Path(path).resolve()),'slot':record['slot'],'targets':record['targets'],'holding':Path(path).suffix=='.slvlr'})
        if record['external']:externals.append(record['external'])
        if record['referenceCount']:
            changed.append({k:v for k,v in record.items() if k!='payload'})
            extra+=record['newPayloadLength'] if record['external'] else math.ceil((record['newPayloadLength']+5)/record['sectorSize'])*record['sectorSize']
    return changed,extra,externals,count,bindings

def append(path,expected,appended=None):
    planned={r['slot']:r for r in expected};changed=[r for r in records(path) if r['referenceCount']]
    if set(planned)!=set(r['slot'] for r in changed):raise ValueError('Sable record plan became stale')
    with Path(path).open('r+b') as f:
        header=bytearray(f.read(HEADER))
        for record in changed:
            known=planned[record['slot']]
            if any(record[k]!=known[k] for k in ('originalPayloadSha256','newPayloadSha256','external','sectorSize')):raise ValueError('Sable data changed after plan')
            if record['external']:continue
            sector=record['sectorSize'];packed=record['payload'];f.seek(0,2);padding=(-f.tell())%sector
            if padding:f.write(b'\0'*padding)
            offset=f.tell()//sector;count=math.ceil((len(packed)+5)/sector)
            if offset>=1<<24:raise ValueError('Sable sector index overflow')
            body=struct.pack('>IB',len(packed)+1,0)+packed;f.write(body+b'\0'*(count*sector-len(body)))
            header[record['slot']*4:record['slot']*4+4]=((offset<<8)|count).to_bytes(4,'big')
        f.flush();os.fsync(f.fileno())
        if appended:appended()
        f.seek(0);f.write(header);f.flush();os.fsync(f.fileno())
    return changed
