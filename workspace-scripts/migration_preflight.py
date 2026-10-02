"""Read-only dimension migration inventory. No apply/copy/rename/RCON mode.

Inventory output is confined to this workspace. Live files can change while read;
results are planning evidence, never a consistent migration backup.
"""
from pathlib import Path
from collections import Counter
from datetime import datetime, timezone
import argparse, gzip, hashlib, json, re, struct
from chunk_sync import NBT

ROOT = Path(__file__).resolve().parent
DEFAULT_WORLD = Path(r'C:\Users\Administrator\WorkSpace\muxigame\bmc5server\world')
IDS = {'minecraft:overworld', 'muxi_game_core:overworld',
       'muxi_game_core:adventure', 'muxi_game_core:home',
       'minecraft:the_nether', 'minecraft:the_end'}
REMAP = {'minecraft:overworld':'muxi_game_core:home',
         'muxi_game_core:overworld':'minecraft:overworld'}

def read_nbt(path):
    body=path.read_bytes()
    if len(body)>16*1024*1024: raise ValueError('compressed file exceeds planning bound')
    if body[:2]==b'\x1f\x8b': body=gzip.decompress(body)
    return NBT(body).parse()

def refs(node, path=''):
    if isinstance(node, dict):
        for key,value in node.items():
            target=f'{path}.{key}' if path else key
            if key in IDS: yield {'path':target,'dimension':key,'kind':'compound_key'}
            elif any(key.startswith(x+'/') for x in IDS):
                yield {'path':target,'dimension':next(x for x in IDS if key.startswith(x+'/')),'kind':'composite_compound_key'}
            yield from refs(value,target)
    elif isinstance(node,list):
        for i,value in enumerate(node): yield from refs(value,f'{path}[{i}]')
    elif isinstance(node,str):
        if node in IDS: yield {'path':path,'dimension':node,'kind':'string'}
        elif any(node.startswith(x+'/') for x in IDS):
            yield {'path':path,'dimension':next(x for x in IDS if node.startswith(x+'/')),'kind':'composite_key_value'}

def inventory(root):
    result={'utc':datetime.now(timezone.utc).isoformat(),'world':str(root),
            'read_only':True,'consistent_backup':False,'mapping':REMAP,
            'dimensions':{},'nbt_files':[],'text_refs':[],'errors':[]}
    dims={'minecraft:overworld':root,'minecraft:the_nether':root/'DIM-1',
          'minecraft:the_end':root/'DIM1'}
    if (root/'dimensions').exists():
        for namespace in (root/'dimensions').iterdir():
            if not namespace.is_dir():continue
            for dimension in namespace.iterdir():
                if dimension.is_dir():dims[namespace.name+':'+dimension.name]=dimension
    for key,directory in dims.items():
        groups={}
        for kind in ['region','entities','poi','sublevels','data','chunk_activity_info','customnpcs','easy_npc']:
            p=directory/kind
            if not p.exists():continue
            files=[q for q in p.rglob('*') if q.is_file()]
            item={'files':len(files),'bytes':sum(q.stat().st_size for q in files),
                  'extensions':dict(Counter(q.suffix for q in files))}
            if kind in ('region','entities','poi'):
                chunks=0
                empty_files=0
                for f in files:
                    if f.suffix!='.mca':continue
                    try:
                        if f.stat().st_size==0:
                            empty_files+=1
                            continue
                        with f.open('rb') as handle:header=handle.read(4096)
                        if len(header)!=4096:raise ValueError('short Anvil location header')
                        chunks+=sum(x!=0 for x in struct.unpack('>1024I',header))
                    except Exception as error:result['errors'].append({'file':str(f.relative_to(root)),'error':type(error).__name__})
                item['occupied_anvil_slots']=chunks
                item['zero_byte_files']=empty_files
            if kind=='data':item['top_level_files']=[q.name for q in p.iterdir() if q.is_file()]
            if kind=='sublevels':item['non_header_files']=[q.name for q in files if q.stat().st_size>4096][:25]
            groups[kind]=item
        result['dimensions'][key]={'path':str(directory.relative_to(root)),'groups':groups}
    selected=[root/'level.dat']
    for directory in [root/'playerdata',root/'ftbteams',root/'ftbquests',root/'data',
                      *[p/'data' for k,p in dims.items() if p!=root]]:
        if directory.exists(): selected.extend(p for p in directory.rglob('*') if p.is_file() and p.suffix in ('.dat','.nbt','.snbt'))
    for p in dict.fromkeys(selected):
        if not p.exists():continue
        rel=p.relative_to(root).as_posix()
        if p.suffix=='.snbt':
            text=p.read_text(encoding='utf-8',errors='replace')
            hits={x:text.count(x) for x in IDS if x in text}
            if hits:result['text_refs'].append({'file':rel,'dimension_occurrences':hits})
            continue
        try:
            before=p.stat()
            node=read_nbt(p)
            hits=list(refs(node))
            after=p.stat()
            item={'file':rel,'bytes':before.st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),
                  'changed_during_read':before.st_mtime_ns!=after.st_mtime_ns,
                  'reference_count':len(hits),'references':hits[:200],
                  'references_by_dimension':dict(Counter(h['dimension'] for h in hits)),
                  'references_truncated':len(hits)>200}
            if rel=='level.dat':
                d=node.get('Data',{})
                item['world_metadata']={k:d.get(k) for k in ['DataVersion','SpawnX','SpawnY','SpawnZ','Time','DayTime','Difficulty']}
                item['seed']=d.get('WorldGenSettings',{}).get('seed')
                item['dimension_generators']=d.get('WorldGenSettings',{}).get('dimensions',{})
            if rel.startswith('playerdata/'):
                item['player_fields']={k:node.get(k) for k in ['Dimension','Pos','SpawnDimension','SpawnX','SpawnY','SpawnZ','SpawnForced','LastDeathLocation'] if k in node}
                item['root_keys']=list(node)
            if rel=='data/muxi_world_portals.dat':item['portal_data']=node
            if hits or rel in ('level.dat','data/muxi_world_portals.dat') or rel.startswith('playerdata/') or 'openpartiesandclaims' in rel:
                result['nbt_files'].append(item)
        except Exception as error:
            result['errors'].append({'file':rel,'error':type(error).__name__,'detail':str(error)[:120]})
    result['player_current_dimensions']=dict(Counter(p['player_fields'].get('Dimension','missing') for p in result['nbt_files'] if 'player_fields' in p))
    result['planned_rewrite_count']=sum(p['reference_count'] for p in result['nbt_files'])
    result['new_home_id_already_in_registry']='muxi_game_core:home' in next(p for p in result['nbt_files'] if p['file']=='level.dat')['dimension_generators']
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world',type=Path,default=DEFAULT_WORLD)
    parser.add_argument('--output',type=Path,default=ROOT/'world-role-inventory.json')
    args=parser.parse_args()
    destination=args.output.resolve()
    destination.relative_to(ROOT)
    data=inventory(args.world.resolve())
    destination.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'output':str(destination),'dimension_count':len(data['dimensions']),
                      'player_current_dimensions':data['player_current_dimensions'],
                      'matched_nbt_files':len(data['nbt_files']),'text_files':len(data['text_refs']),
                      'errors':len(data['errors'])},ensure_ascii=False))

if __name__=='__main__': main()
