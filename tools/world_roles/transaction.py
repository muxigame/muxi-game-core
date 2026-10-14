"""Offline, same-volume world-role transaction. Planning is read-only; apply is explicit and locked."""
from pathlib import Path
from contextlib import contextmanager
from collections import OrderedDict
import base64, hashlib, json, os, struct, time
from . import nbt, region, snbt, toml, sable, configs
from .generators import PACK,preserve_generators
from .transform import remap_tag, HOME, OLD_SURVIVAL, SURVIVAL, remap_tag, remap_json, player_defaults, level_roles
JOURNAL='.muxi-world-roles'
LOCAL_VANILLA={'raids.dat','chunks.dat'}

def sha_file(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
def b64(b):return base64.b64encode(b).decode('ascii')
def unb64(b):return base64.b64decode(b,validate=True)
def bounded(root,relative):
    p=(root/relative).resolve()
    if not p.is_relative_to(root) or p==root:raise ValueError('Path outside explicit world boundary')
    return p
def atomic(path,data,staged=None):
    path.parent.mkdir(parents=True,exist_ok=True);temp=path.with_name(path.name+'.muxi-partial')
    with temp.open('xb') as f:f.write(data);f.flush();os.fsync(f.fileno())
    if staged:staged()
    os.replace(temp,path)

def receipt(path,data):
    payload=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode('utf-8');temp=path.with_name(path.name+'.muxi-partial')
    if temp.exists():
        if temp.is_symlink() or temp.is_junction():raise ValueError('Linked journal staging file')
        owner=data.get('world',data.get('root'))
        if path.exists():
            old=json.loads(path.read_text(encoding='utf-8'))
            if not owner or old.get('world',old.get('root'))!=owner:raise ValueError('Unknown journal staging owner')
        elif not payload.startswith(temp.read_bytes()):raise ValueError('Unknown initial journal staging content')
        temp.unlink() # Only the reserved staging file of this verified journal, never a user payload.
    atomic(path,payload)

def safe_files(root):
    for path in root.rglob('*'):
        if path.is_symlink() or (hasattr(path,'is_junction') and path.is_junction()):raise ValueError('Symlink/junction migration is unsupported')
        if JOURNAL in path.relative_to(root).parts:continue
        if path.is_file() and path.name!='session.lock':
            if path.stat().st_nlink>1:raise ValueError('Hardlinked world payload unsupported')
            yield path

def snapshot(root):return {str(p.relative_to(root)):sha_file(p) for p in sorted(safe_files(root))}

def config_snapshot(root):
    result={}
    for p in sorted(root.rglob('*')):
        if p.is_symlink() or p.is_junction() or (p.is_file() and p.stat().st_nlink>1):raise ValueError('Linked external config entry unsupported')
        if p.is_file():result[p.relative_to(root).as_posix()]=sha_file(p)
    return result

def config_boundary(root,j):
    if not j.get('configRoot'):return None
    p=Path(j['configRoot'])
    if p.is_symlink() or p.is_junction() or p.resolve()!=(root.parent/'config').resolve():raise ValueError('Config journal escaped server sibling boundary')
    return p.resolve(strict=True)

def op_path(root,j,op):return bounded(config_boundary(root,j) if op['kind']=='config' else root,op['path'])
@contextmanager
def offline(root):
    lock=root/'session.lock'
    if not lock.is_file():raise ValueError('Existing session.lock required; no production lock file is fabricated')
    with lock.open('r+b') as f:
        try:
            if os.name=='nt':
                import msvcrt;f.seek(0);msvcrt.locking(f.fileno(),msvcrt.LK_NBLCK,1)
            else:
                import fcntl;fcntl.lockf(f.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
        except OSError as e:raise ValueError('World is open/locked; normal shutdown required') from e
        try:yield
        finally:
            if os.name=='nt':f.seek(0);msvcrt.locking(f.fileno(),msvcrt.LK_UNLCK,1)
            else:fcntl.lockf(f.fileno(),fcntl.LOCK_UN)

def migrated(root):
    f=root/JOURNAL/'journal.json'
    if not f.exists():return False
    j=json.loads(f.read_text(encoding='utf-8'))
    ready=j.get('phase')=='committed' and j.get('mapping')=={'minecraft:overworld':HOME,OLD_SURVIVAL:SURVIVAL}
    if ready:
        _,_,t=nbt.read_file((root/'level.dat').read_bytes());dims=t.value['Data'].value['WorldGenSettings'].value['dimensions'].value
        if HOME not in dims or SURVIVAL not in dims or OLD_SURVIVAL in dims:raise ValueError('Committed receipt disagrees with actual dimension layout')
        if (root.parent/'config').exists() and not j.get('configRoot'):raise ValueError('Committed world-only layout lacks external config transaction coverage')
        expected_pack='datapacks/'+PACK+'/'
        pack_ops=[op for op in j.get('operations',[]) if op.get('path','').replace('\\','/').startswith(expected_pack)]
        if len(pack_ops)!=3:raise ValueError('Committed migration lacks audited generator preservation artifacts')
        for op in pack_ops:
            file=bounded(root,op['path'])
            if not file.is_file() or sha_file(file)!=op['afterSha256']:raise ValueError('Retained generator datapack changed/missing; no automatic repeated apply')
        for op in j.get('operations',[]):
            if op['kind']=='config':
                p=op_path(root,j,op)
                if not p.is_file() or sha_file(p)!=op['afterSha256']:raise ValueError('Migrated config changed; repeated apply never overwrites it')
    return ready

def plan(root,main_spawn=None,hub=None,data_policy=None,sable_jar=None,config_root=None,config_policy=None):
    root=root.resolve(strict=True)
    if migrated(root):return {'schema':2,'world':str(root),'alreadyMigrated':True,'operations':[],'blockers':[]}
    policy=dict(data_policy or {});blockers=[];ops=[];moves=[];extra=0;changes=0
    config_path=None;config_before=None;sable_profile=None
    expected_config=root.parent/'config'
    blockers.extend(configs.unscoped_reference_roots(root.parent))
    if config_root is not None:
        candidate=Path(config_root)
        if candidate.is_symlink() or candidate.is_junction() or candidate.resolve()!=expected_config.resolve():raise ValueError('Config root must be the explicit server sibling config directory')
        config_path=candidate.resolve(strict=True)
        if config_policy is None:blockers.append('Explicit reviewed external config policy is required')
        else:
            if hub not in (None,'home'):blockers.append('External return-config profile preserves home hub; another hub requires a separately reviewed config policy')
            config_ops,config_blocks,count=configs.prepared_ops(config_path,config_policy);blockers.extend(config_blocks);changes+=count
            for op in config_ops:op['before']=b64(op['before']);op['after']=b64(op['after']);ops.append(op);extra+=len(unb64(op['after']))
        config_before=config_snapshot(config_path)
    elif expected_config.exists():blockers.append('Server config directory exists: explicit config root and reviewed reference policy required; world-only cutover refused')
    if hub is None:hub='home' # Preserve the current Core home-entry/return rule.
    if hub not in ('home','survival'):blockers.append('Invalid portal hub')
    target=root/'dimensions/muxi_game_core/homeworld'
    if target.exists() and any(p.is_file() for p in target.rglob('*')):blockers.append('Target homeworld already contains data; never merged/overwritten automatically')
    for p in safe_files(root):
        if p.name.endswith('.muxi-partial'):blockers.append(str(p.relative_to(root))+': unidentified staging file; recover existing transaction first')
    source=root/'dimensions/muxi_game_core/overworld'
    if not source.is_dir():blockers.append('Original survival world directory missing')
    current=root/'level.dat';c,name,tag=nbt.read_file(current.read_bytes())
    if main_spawn is None:main_spawn=tuple(tag.value['Data'].value[k].value for k in ['SpawnX','SpawnY','SpawnZ']) # DerivedLevelData shared this point with original survival.
    try:new_level,roles=level_roles(tag,main_spawn,hub or 'survival')
    except (ValueError,KeyError) as e:blockers.append(str(e));new_level=roles=None
    if new_level is not None:
        pack_dir=root/'datapacks'/PACK
        if pack_dir.exists() and any(p.is_file() for p in pack_dir.rglob('*')):blockers.append('Existing generator preservation datapack is a conflict; never overwritten automatically')
        try:
            for relative,after in preserve_generators(new_level).items():
                ops.append({'kind':'file','path':relative,'before':None,'beforeSha256':None,'after':b64(after),'afterSha256':hashlib.sha256(after).hexdigest(),'state':'planned'});extra+=len(after)
        except (ValueError,KeyError,TypeError) as e:blockers.append('Generator preservation: '+str(e))
    storage_files=[p for p in safe_files(root) if p.suffix.lower() in ('.slvlr','.slvls','.slvl')]
    if storage_files:
        if sable_jar is None:blockers.append('Sable storage present: exact audited runtime jar required')
        else:
            try:sable_profile=sable.verify_jar(sable_jar)
            except (ValueError,OSError) as e:blockers.append(str(e))
    sable_external_owners={};sable_bindings=[]
    for path in sorted(safe_files(root)):
        rel=str(path.relative_to(root));suffix=path.suffix.lower()
        try:
            if suffix=='.mca':
                records,added=region.inspect(path)
                if records:
                    ops.append({'kind':'region','path':rel,'beforeSha256':sha_file(path),'beforeLength':path.stat().st_size,'header':b64(path.open('rb').read(8192)),'records':records,'state':'planned'});extra+=added;changes+=sum(r['referenceCount'] for r in records)
                continue
            if suffix in ('.slvlr','.slvls'):
                if sable_profile is None:continue
                records,added,externals,count,bindings=sable.inspect(path);sable_bindings.extend(bindings)
                for external in externals:
                    if external in sable_external_owners:raise ValueError('Ambiguous shared Sable external record: '+external)
                    sable_external_owners[external]=rel
                if records:
                    ops.append({'kind':'sable','path':rel,'beforeSha256':sha_file(path),'beforeLength':path.stat().st_size,'headerLength':4096,'header':b64(path.open('rb').read(4096)),'records':records,'state':'planned'});extra+=added;changes+=sum(r['referenceCount'] for r in records)
                continue
            if suffix=='.slvl':continue # validated by its unique owning Sable container
            if suffix=='.mcc':continue # handled by its owning external MCA record
            if suffix not in ('.dat','.dat_old','.nbt','.json','.snbt','.toml'):continue
            before=path.read_bytes();new=None;refs=[]
            if path==current and new_level is not None:new=nbt.compress(nbt.encode(name,new_level),c);refs=['world generators and roles']
            elif path==root/'level.dat_old':continue
            elif suffix in ('.dat','.dat_old','.nbt'):
                compression,n,t=nbt.read_file(before);changed=remap_tag(t,changes=refs)
                if 'playerdata' in path.relative_to(root).parts:
                    changed=player_defaults(changed)
                    if nbt.encode(n,changed)!=nbt.encode(n,t) and not refs:refs=['implicit player/bed dimension']
                if refs:new=nbt.compress(nbt.encode(n,changed),compression)
            elif suffix=='.json':
                obj=json.loads(before.decode('utf-8-sig'));changed=remap_json(obj,changes=refs)
                if refs:new=(json.dumps(changed,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
            elif suffix=='.snbt':
                changed,refs=snbt.rewrite(before.decode('utf-8-sig'))
                if refs:new=changed.encode('utf-8')
            elif suffix=='.toml':
                changed,refs=toml.rewrite(before.decode('utf-8-sig'))
                if refs:new=changed.encode('utf-8')
            if new is not None and new!=before:
                ops.append({'kind':'file','path':rel,'beforeSha256':hashlib.sha256(before).hexdigest(),'before':b64(before),'after':b64(new),'afterSha256':hashlib.sha256(new).hexdigest(),'state':'planned'});changes+=len(refs);extra+=len(new)
        except (ValueError,KeyError,UnicodeError,OSError,IndexError,struct.error) as e:blockers.append(rel+': '+str(e))
    if sable_profile is not None:
        construct_slots={(binding['path'],binding['slot']) for binding in sable_bindings if not binding['holding']}
        for binding in sable_bindings:
            for target in binding['targets']:
                if (target['path'],target['slot']) not in construct_slots:blockers.append(str(Path(binding['path']).relative_to(root))+f": holding slot {binding['slot']} references missing/unrecognized Sable construct {target['path']} slot {target['slot']}")
        for path in storage_files:
            if path.suffix.lower()=='.slvl' and str(path.resolve()) not in sable_external_owners:blockers.append(str(path.relative_to(root))+': orphan/unidentified Sable external payload')
    if roles is not None:
        path='data/muxi_world_roles.dat'
        if (root/path).exists():blockers.append('World-role state already exists without a committed transaction')
        after=nbt.compress(nbt.encode('',roles),1);ops.append({'kind':'file','path':path,'before':None,'beforeSha256':None,'after':b64(after),'afterSha256':hashlib.sha256(after).hexdigest(),'state':'planned'});extra+=len(after)
    # Existing fallback metadata must not revive old dimensions against new terrain after a damaged level.dat.
    if (root/'level.dat_old').exists() and new_level is not None:
        before=(root/'level.dat_old').read_bytes();after=nbt.compress(nbt.encode(name,new_level),c);ops.append({'kind':'file','path':'level.dat_old','before':b64(before),'beforeSha256':hashlib.sha256(before).hexdigest(),'after':b64(after),'afterSha256':hashlib.sha256(after).hexdigest(),'state':'planned'});extra+=len(after)
    for directory in ['region','entities','poi','sublevels']:
        if (root/directory).exists():moves.append({'source':directory,'target':'dimensions/muxi_game_core/homeworld/'+directory,'state':'planned'})
        if (source/directory).exists():moves.append({'source':'dimensions/muxi_game_core/overworld/'+directory,'target':directory,'state':'planned'})
    # Underlay's actual persistence handler names files by dimension inside GLOBAL world/data.
    for before,after in [('data/underlays.dat','data/underlays_muxi_game_core_homeworld.dat'),('data/underlays_muxi_game_core_overworld.dat','data/underlays.dat')]:
        if (root/before).exists():moves.append({'source':before,'target':after,'state':'planned'})
    if (root/'data/underlays_muxi_game_core_homeworld.dat').exists():blockers.append('Existing homeworld underlay file is a conflict')
    names=set(p.name for p in (root/'data').glob('*.dat'))|set(p.name for p in (source/'data').glob('*.dat'))
    for name in sorted(names):
        if name.startswith('underlays'):continue # handled by the audited global filename mapping above
        if name in LOCAL_VANILLA:scope='dimension'
        elif name.startswith(('map_','magicmap_')) or name in {'idcounts.dat','scoreboard.dat','muxi_world_portals.dat','random_sequences.dat','waystones.dat','waystones_NameGenerator.dat'}:scope='global'
        else:scope=policy.get(name)
        if scope not in ('dimension','global'):
            blockers.append('SavedData ownership not audited: '+name);continue
        if scope=='global' and (source/'data'/name).exists():
            if not (root/'data'/name).exists():moves.append({'source':'dimensions/muxi_game_core/overworld/data/'+name,'target':'data/'+name,'state':'planned'})
            elif sha_file(root/'data'/name)!=sha_file(source/'data'/name):blockers.append('Distinct global SavedData copies need explicit conflict resolution: '+name)
        if scope=='dimension':
            if (root/'data'/name).exists():moves.append({'source':'data/'+name,'target':'dimensions/muxi_game_core/homeworld/data/'+name,'state':'planned'})
            if (source/'data'/name).exists():moves.append({'source':'dimensions/muxi_game_core/overworld/data/'+name,'target':'data/'+name,'state':'planned'})
    # Old Core exploration timer becomes the native primary-world timer. Same native load/save schema.
    legacy_timer='muxi_goblin_trader.dat';native_timer='goblintraders_goblin_trader_spawner.dat'
    if (source/'data'/legacy_timer).exists():
        if policy.get(legacy_timer)!='dimension' or policy.get(native_timer)!='dimension':blockers.append('Trader timer ownership and primary filename conversion require audited dimension policies')
        elif (source/'data'/native_timer).exists():blockers.append('Both original survival trader timer variants exist; explicit conflict resolution required')
        else:moves.append({'source':'data/'+legacy_timer,'target':'data/'+native_timer,'state':'planned'})
    for move in moves:bounded(root,move['source']);bounded(root,move['target'])
    original=snapshot(root)
    journal_size=len(json.dumps({'operations':ops,'moves':moves}).encode('utf-8'))
    if journal_size>32*1024*1024:blockers.append('Recovery metadata exceeds 32 MiB review limit; explicit audit needed')
    return {'schema':2,'world':str(root),'mapping':{'minecraft:overworld':HOME,OLD_SURVIVAL:SURVIVAL},'alreadyMigrated':False,'operations':ops,'moves':moves,'beforeSnapshot':original,'referenceChanges':changes,'additionalPayloadBytesUpperBound':extra,'journalMetadataBytes':journal_size,'blockers':sorted(set(blockers)),'sableProfile':sable_profile,'configRoot':str(config_path) if config_path else None,'configPolicy':config_policy,'configBeforeSnapshot':config_before,'resolvedMainSpawn':list(main_spawn),'portalHub':hub,'dataPolicy':policy}

def apply(root,prepared,fail_after=None,fail_at=None):
    root=root.resolve(strict=True)
    with offline(root):
        if migrated(root):return {'alreadyMigrated':True}
        if prepared.get('blockers'):raise ValueError('Plan has unresolved blockers')
        if prepared['world']!=str(root) or snapshot(root)!=prepared['beforeSnapshot']:raise ValueError('World changed after planning; replan offline')
        config_path=config_boundary(root,prepared)
        if config_path and config_snapshot(config_path)!=prepared['configBeforeSnapshot']:raise ValueError('Server config changed after plan')
        if prepared.get('sableProfile'):sable.verify_jar(prepared['sableProfile']['path'])
        fresh=plan(root,prepared['resolvedMainSpawn'],prepared['portalHub'],prepared['dataPolicy'],prepared['sableProfile']['path'] if prepared.get('sableProfile') else None,config_path,prepared.get('configPolicy'))
        if fresh.get('blockers') or fresh!=prepared:raise ValueError('Prepared plan differs from current audited plan; edited blockers/operations cannot bypass validation')
        def checkpoint(label):
            if label==fail_at:raise RuntimeError('Injected interruption at '+label)
        journal_path=root/JOURNAL/'journal.json'
        if journal_path.exists():
            previous=json.loads(journal_path.read_text(encoding='utf-8'))
            if previous.get('phase')!='rolled_back':raise ValueError('Existing incomplete transaction: rollback before replanning')
            history=journal_path.parent/'history';history.mkdir(exist_ok=True);os.rename(journal_path,history/(str(time.time_ns())+'.json'))
        j={**prepared,'phase':'applying','external':[]};receipt(journal_path,j);number=0
        def done():
            nonlocal number
            number+=1;receipt(journal_path,j)
            if fail_after is not None and number==fail_after:raise RuntimeError('Injected synthetic interruption')
        for op in j['operations']:
            op['state']='pending';receipt(journal_path,j);path=op_path(root,j,op);checkpoint('op:'+op['path']+':intent')
            if op['kind'] in ('file','config'):atomic(path,unb64(op['after']),lambda:checkpoint('op:'+op['path']+':staged'))
            else:
                records=sable.append(path,op['records'],lambda:checkpoint('op:'+op['path']+':appended')) if op['kind']=='sable' else region.append(path,op['records'])
                for record in records:
                    if not record['external']:continue
                    ext=Path(record['external']).resolve();relative=str(ext.relative_to(root));old=JOURNAL+'/external/'+hashlib.sha256(relative.encode()).hexdigest()+ext.suffix;saved=bounded(root,old);saved.parent.mkdir(parents=True,exist_ok=True)
                    item={'source':relative,'original':old,'afterSha256':record['newPayloadSha256'],'beforeSha256':record['originalPayloadSha256'],'state':'pending'};j['external'].append(item);receipt(journal_path,j);checkpoint('external:'+relative+':intent');os.rename(ext,saved);checkpoint('external:'+relative+':renamed');atomic(ext,record['payload'],lambda:checkpoint('external:'+relative+':staged'));checkpoint('external:'+relative+':written');item['state']='done';receipt(journal_path,j)
            checkpoint('op:'+op['path']+':written');op['state']='done';done()
        for move in j['moves']:
            source=bounded(root,move['source']);target=bounded(root,move['target'])
            if target.exists():raise ValueError('Destination collision; transaction remains recoverable: '+move['target'])
            target.parent.mkdir(parents=True,exist_ok=True);move['state']='pending';receipt(journal_path,j);checkpoint('move:'+move['source']+':intent');os.rename(source,target);checkpoint('move:'+move['source']+':renamed');move['state']='done';done()
        j['phase']='committed';j['afterSnapshot']=snapshot(root);j['configAfterSnapshot']=config_snapshot(config_path) if config_path else None;receipt(journal_path,j)
        return {'committed':True,'referenceChanges':j['referenceChanges'],'fullWorldCopied':False,'oldWorldDataDeleted':False}

def rollback(root,fail_at=None):
    root=root.resolve(strict=True)
    with offline(root):
        path=root/JOURNAL/'journal.json';j=json.loads(path.read_text(encoding='utf-8'))
        if j['world']!=str(root):raise ValueError('Journal belongs to another world')
        if j['phase']=='rolled_back':return {'alreadyRolledBack':True}
        if j['phase'] not in ('committed','applying','rolling_back'):raise ValueError('Unknown transaction phase')
        if j['phase']=='committed' and snapshot(root)!=j['afterSnapshot']:raise ValueError('Save changed after cutover; refusing to lose post-migration gameplay')
        config_path=config_boundary(root,j)
        if j['phase']=='committed' and config_path and config_snapshot(config_path)!=j['configAfterSnapshot']:raise ValueError('Server config changed after cutover; rollback refused')
        j['phase']='rolling_back';receipt(path,j)
        def checkpoint(label):
            if label==fail_at:raise RuntimeError('Injected rollback interruption at '+label)
        def clean_partial(p,expected):
            temp=p.with_name(p.name+'.muxi-partial')
            if not temp.exists():return
            if temp.is_symlink() or temp.is_junction():raise ValueError('Linked staged payload')
            raw=temp.read_bytes()
            if not expected.startswith(raw):raise ValueError('Unrecognized staged payload; never deleted: '+str(temp))
            temp.unlink()
        for move in reversed(j['moves']):
            if move['state']=='planned' or move.get('rollbackState')=='done':continue
            source=bounded(root,move['source']);target=bounded(root,move['target'])
            move['rollbackState']='pending';receipt(path,j);checkpoint('move:'+move['source']+':intent')
            if not source.exists() and target.exists():source.parent.mkdir(parents=True,exist_ok=True);os.rename(target,source)
            elif not source.exists() or target.exists():raise ValueError('Move rollback collision')
            checkpoint('move:'+move['source']+':renamed');move['rollbackState']='done';receipt(path,j)
        for item in reversed(j['external']):
            if item.get('rollbackState')=='done':continue
            source=bounded(root,item['source']);original=bounded(root,item['original'])
            # The replacement bytes can be reconstructed from the still-original region record only before replacement.
            temp=source.with_name(source.name+'.muxi-partial')
            if temp.exists():
                if temp.is_symlink() or temp.is_junction():raise ValueError('Linked external staging file')
                raw=temp.read_bytes()
                if sha_file(temp)!=item['afterSha256']:
                    if not original.exists():raise ValueError('Unknown external partial without its original')
                    compression=next(rec['compression'] for op in j['operations'] if op['kind'] in ('region','sable') for rec in op['records'] if rec['external'] and Path(rec['external']).resolve()==source)
                    tagname,tag=nbt.decode(nbt.expand(original.read_bytes(),compression))
                    changed=sable.rewrite(tag) if source.suffix=='.slvl' else remap_tag(tag)
                    after=nbt.compress(nbt.encode(tagname,changed),compression)
                    if not after.startswith(raw):raise ValueError('Unrecognized external staged bytes')
                temp.unlink()
            item['rollbackState']='pending';receipt(path,j)
            if original.exists():
                if source.exists():
                    if sha_file(source)!=item['afterSha256']:raise ValueError('External chunk changed after migration')
                    source.unlink() # Verified new replacement only; original file is moved back.
                checkpoint('external:'+item['source']+':removed');os.rename(original,source)
            elif not source.exists() or sha_file(source)!=item.get('beforeSha256'):raise ValueError('External original missing or changed')
            checkpoint('external:'+item['source']+':restored');item['rollbackState']='done';receipt(path,j)
        for op in reversed(j['operations']):
            if op['state']=='planned' or op.get('rollbackState')=='done':continue
            p=op_path(root,j,op);op['rollbackState']='pending';receipt(path,j)
            if op['kind'] in ('file','config'):
                temp=p.with_name(p.name+'.muxi-partial')
                if temp.exists():
                    raw=temp.read_bytes();after=unb64(op['after']);before=unb64(op['before']) if op['before'] is not None else None
                    if not after.startswith(raw) and (before is None or not before.startswith(raw)):raise ValueError('Unknown staged metadata content')
                    if temp.is_symlink() or temp.is_junction():raise ValueError('Linked metadata staging file')
                    temp.unlink()
                if op['before'] is None:
                    if p.exists():
                        if sha_file(p)!=op['afterSha256']:raise ValueError('New migration metadata changed')
                        p.unlink()
                else:
                    if not p.is_file() or sha_file(p) not in (op['beforeSha256'],op['afterSha256']):raise ValueError('Metadata changed outside transaction: '+op['path'])
                    atomic(p,unb64(op['before']),lambda:checkpoint('op:'+op['path']+':staged'))
            else:
                with p.open('r+b') as f:
                    original_header=unb64(op['header']);header_length=op.get('headerLength',8192);f.seek(header_length);original_body=f.read(op['beforeLength']-header_length)
                    if hashlib.sha256(original_header+original_body).hexdigest()!=op['beforeSha256']:raise ValueError('Original storage sectors changed outside transaction')
                    f.seek(0);f.write(original_header);f.truncate(op['beforeLength']);f.flush();os.fsync(f.fileno())
            checkpoint('op:'+op['path']+':restored');op['rollbackState']='done';receipt(path,j)
        if snapshot(root)!=j['beforeSnapshot']:raise ValueError('Rollback validation differs from original save')
        if config_path and config_snapshot(config_path)!=j['configBeforeSnapshot']:raise ValueError('Rollback config validation differs from original')
        j['phase']='rolled_back';receipt(path,j);return {'rolledBack':True,'byteIdenticalOriginalSave':True,'byteIdenticalOriginalConfigs':bool(config_path)}
