"""Only explicitly reviewed config fields; unknown references or syntax block migration."""
from pathlib import Path
import copy,hashlib,json,math,tomllib
from .transform import MAPPING

def equal(a,b):
    if type(a)!=type(b):return False
    if isinstance(a,dict):return a.keys()==b.keys() and all(equal(a[k],b[k]) for k in a)
    if isinstance(a,list):return len(a)==len(b) and all(equal(x,y) for x,y in zip(a,b))
    if isinstance(a,float) and math.isnan(a):return math.isnan(b)
    return a==b

def read_at(obj,path):
    for part in path:obj=obj[int(part)] if isinstance(obj,list) else obj[part]
    return obj

def set_at(obj,path,value):
    parent=read_at(obj,path[:-1]);key=int(path[-1]) if isinstance(parent,list) else path[-1];parent[key]=value

def mapped_value(value):
    if isinstance(value,str):return MAPPING.get(value,value)
    if isinstance(value,list):return [mapped_value(v) for v in value]
    raise ValueError('Reviewed dimension field must contain strings or string arrays')

def collect(obj,path=()):
    if isinstance(obj,str):
        if obj in MAPPING:yield path
        elif any(old in obj for old in MAPPING):yield path+('<embedded-reference>',)
    elif isinstance(obj,list):
        for i,v in enumerate(obj):yield from collect(v,path+(str(i),))
    elif isinstance(obj,dict):
        for k,v in obj.items():
            if k in MAPPING:yield path+('<dimension-key>',k)
            yield from collect(v,path+(k,))

def string_tokens(text):
    i=0
    while i<len(text):
        if text[i]=='#':
            end=text.find('\n',i);i=len(text) if end<0 else end+1;continue
        if text[i] not in "\"'":i+=1;continue
        start=i;q=text[i];delimiter=q*3 if text[i:i+3]==q*3 else q;i+=len(delimiter)
        while i<len(text):
            if text[i:i+len(delimiter)]==delimiter:i+=len(delimiter);break
            if q=='"' and text[i]=='\\':i+=2
            else:i+=1
        else:raise ValueError('Unclosed config string')
        yield start,i,delimiter,tomllib.loads('v='+text[start:i])['v']

def differences(a,b,path=()):
    if equal(a,b):return []
    if isinstance(a,dict) and isinstance(b,dict) and a.keys()==b.keys():return [p for k in a for p in differences(a[k],b[k],path+(k,))]
    if isinstance(a,list) and isinstance(b,list) and len(a)==len(b):return [p for i,(x,y) in enumerate(zip(a,b)) for p in differences(x,y,path+(str(i),))]
    return [path]

def rewrite(raw,filename,rules):
    if len(raw)>1024*1024:raise ValueError('Config exceeds reviewed 1 MiB limit')
    text=raw.decode('utf-8-sig');suffix=Path(filename).suffix.lower()
    if suffix=='.json':parse=json.loads
    elif suffix=='.toml':parse=tomllib.loads
    else:raise ValueError('Unknown external config format')
    before=parse(text);expected=copy.deepcopy(before);paths=[]
    for rule in rules:
        path=tuple(rule['path']);value=read_at(before,path)
        if rule.get('source') not in MAPPING:raise ValueError('Unreviewed source dimension')
        if value!=rule['source'] and not (isinstance(value,list) and rule['source'] in value):raise ValueError('Reviewed field no longer contains expected source: '+ '/'.join(path))
        set_at(expected,path,mapped_value(value));paths.append(path)
    for path in collect(before):
        if not any(path[:len(selected)]==selected for selected in paths):raise ValueError('Unreviewed external config binding: '+ '/'.join(path))
        if path and path[-1]=='<embedded-reference>' or '<dimension-key>' in path:raise ValueError('Embedded/key-encoded config references are not supported: '+ '/'.join(path))
    if suffix=='.json':after=(json.dumps(expected,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    else:
        edits=[]
        for start,end,delimiter,value in string_tokens(text):
            if value not in MAPPING:continue
            replacement=delimiter+MAPPING[value]+delimiter
            candidate=parse(text[:start]+replacement+text[end:]);changed=differences(before,candidate)
            if changed and all(any(path[:len(selected)]==selected for selected in paths) for path in changed):edits.append((start,end,replacement))
        for start,end,value in reversed(edits):text=text[:start]+value+text[end:]
        if not equal(parse(text),expected):raise ValueError('Config lexical rewrite disagrees with reviewed structured fields')
        after=text.encode('utf-8')
    if not equal(parse(after.decode('utf-8')),expected):raise ValueError('Config output semantic validation failed')
    return after,len(differences(before,expected))

def prepared_ops(root,policy):
    if policy.get('schema')!=1:raise ValueError('Unknown external config policy schema')
    ops=[];blockers=[];references=0;reviewed={}
    for file in policy.get('files',[]):
        relative=file['path'];p=(root/relative).resolve()
        if p==root or not p.is_relative_to(root) or Path(relative).is_absolute():raise ValueError('External config policy escapes explicit config root')
        if relative in reviewed:raise ValueError('Duplicate external config policy file')
        reviewed[relative]=file
        try:
            raw=p.read_bytes();digest=hashlib.sha256(raw).hexdigest()
            if digest!=file['sourceSha256']:raise ValueError('Config changed since reference audit; refresh policy')
            after,count=rewrite(raw,relative,file['references']);references+=count
            if raw!=after:ops.append({'kind':'config','path':relative,'before':raw,'after':after,'beforeSha256':digest,'afterSha256':hashlib.sha256(after).hexdigest(),'state':'planned'})
        except (OSError,ValueError,KeyError,TypeError,UnicodeError) as e:blockers.append('config/'+relative+': '+str(e))
    # Only files containing old markers need parsing; uncertain formats are never silently ignored.
    for p in sorted(root.rglob('*')):
        if p.is_symlink() or p.is_junction():raise ValueError('Linked external config path unsupported')
        if not p.is_file():continue
        relative=p.relative_to(root).as_posix()
        if p.name.endswith('.muxi-partial'):blockers.append('config/'+relative+': unidentified staged file; recover transaction first');continue
        if relative in reviewed:continue
        if p.stat().st_size>1024*1024:
            tail=b'';found=False
            with p.open('rb') as source:
                for chunk in iter(lambda:source.read(65536),b''):
                    data=tail+chunk
                    if any(old.encode() in data for old in MAPPING):found=True;break
                    tail=data[-128:]
            if found:blockers.append('config/'+relative+': reference-bearing config exceeds reviewed size limit')
            continue
        raw=p.read_bytes()
        if not any(old.encode() in raw for old in MAPPING):continue
        try:
            _,count=rewrite(raw,relative,[])
            if count:raise ValueError('Unreviewed external references')
        except (ValueError,KeyError,TypeError,UnicodeError) as e:blockers.append('config/'+relative+': '+str(e))
    return ops,blockers,references


def unscoped_reference_roots(server):
    blockers=[]
    for directory in ('defaultconfigs','configureddefaults','kubejs'):
        root=server/directory
        if not root.exists():continue
        if root.is_symlink() or root.is_junction():raise ValueError('Linked active configuration/script root unsupported')
        for p in sorted(root.rglob('*')):
            if p.is_symlink() or p.is_junction():raise ValueError('Linked active configuration/script entry unsupported')
            if not p.is_file():continue
            tail=b'';found=False
            with p.open('rb') as source:
                for chunk in iter(lambda:source.read(65536),b''):
                    data=tail+chunk
                    if any(old.encode() in data for old in MAPPING):found=True;break
                    tail=data[-128:]
            if found:blockers.append(p.relative_to(server).as_posix()+': old dimension marker outside reviewed config transaction; explicit producer/schema audit required (comments/resources are not guessed)')
    return blockers
