"""One journal for actual Sable-codec synthetic constructs and reviewed external config fields."""
from pathlib import Path
import json,hashlib,os,struct,sys,uuid
from run_synthetic import ROOT,OUT,fixture,C,S,I
import run_sable_codec
sys.path.insert(0,str(ROOT/'tools'))
from world_roles import nbt,sable
from world_roles.transaction import plan,apply,rollback,snapshot,config_snapshot
from world_roles.transform import HOME,SURVIVAL,OLD_SURVIVAL
SABLE=ROOT.parent/'bmc5server/mods/sable-neoforge-1.21.1-2.0.5.jar';CHECKS=0;TEMPLATE={}

def check(value,label):
    global CHECKS
    CHECKS+=1
    if not value:raise AssertionError(label)

def sha(data):return hashlib.sha256(data).hexdigest()

def container(path,tag,sector=4096,flag=0,slot=3):
    path.parent.mkdir(parents=True,exist_ok=True);payload=nbt.compress(nbt.encode('',tag),1);body=struct.pack('>IB',len(payload)+1,flag)+payload;count=(len(body)+sector-1)//sector
    header=bytearray(4096);header[slot*4:slot*4+4]=(((4096//sector)<<8)|count).to_bytes(4,'big');path.write_bytes(header+body+b'\0'*(count*sector-len(body)))

def original_tag(path,slot):
    data=path.read_bytes();sector=128 if path.suffix=='.slvlr' else 4096;entry=int.from_bytes(data[slot*4:slot*4+4],'big');start=(entry>>8)*sector;size,flag=struct.unpack('>IB',data[start:start+5]);check(flag==0,'fixture internal record')
    return nbt.decode(nbt.expand(data[start+5:start+4+size],1))[1]

def case():
    server=OUT/'synthetic-integrated'/uuid.uuid4().hex;server.mkdir(parents=True);source=fixture();world=server/'world';os.rename(source,world)
    for relative,data in TEMPLATE.items():p=world/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    config=server/'config';config.mkdir()
    files=[('aether-server.toml',b'# minecraft:overworld in comment remains\n[Modpack]\n"Sets portal return dimension" = "minecraft:overworld"\n',[(['Modpack','Sets portal return dimension'],SURVIVAL)]),('twilightforest-common.toml',b'["Portal Settings"]\noriginDimension="minecraft:overworld"\n',[(['Portal Settings','originDimension'],SURVIVAL)]),('dimensions.toml',b'allowed=["minecraft:overworld", "muxi_game_core:overworld"]\n',[(['allowed'],SURVIVAL),(['allowed'],OLD_SURVIVAL)]),('yigd.json',json.dumps({'graveConfig':{'minimumGraveYLevel':[{'key':SURVIVAL,'value':15},{'key':OLD_SURVIVAL,'value':-64}]},'untouchedToken':'FAKE SYNTHETIC SECRET'}).encode(),[(['graveConfig','minimumGraveYLevel','0','key'],SURVIVAL),(['graveConfig','minimumGraveYLevel','1','key'],OLD_SURVIVAL)])]
    policy={'schema':1,'files':[]}
    for relative,data,rules in files:
        (config/relative).write_bytes(data);policy['files'].append({'path':relative,'sourceSha256':sha(data),'references':[{'path':path,'source':source} for path,source in rules]})
    # Existing Nether/End constructs are valid native plot data but have no roles to remap.
    source=world/'sublevels/r.0.0.0.slvls'
    for directory,dimension in [('DIM-1','minecraft:the_nether'),('DIM1','minecraft:the_end')]:
        tag=original_tag(source,3);tag.value['user_data'].value['dimension']=S(dimension);container(world/directory/'sublevels/r.5.2.0.slvls',tag)
    return world,config,policy

def draft(world,config,policy):return plan(world,sable_jar=SABLE,config_root=config,config_policy=policy)

def interrupted(action,label):
    try:action()
    except RuntimeError:return
    raise AssertionError('Missing injected fault: '+label)

def run():
    global TEMPLATE
    run_sable_codec.prepare();gold=fixture();run_sable_codec.run('create',gold,'integrated-template');check('"checks":24' in run_sable_codec.run('verify-original',gold,'integrated-original'),'actual Sable original serializer/containers pass')
    TEMPLATE={p.relative_to(gold).as_posix():p.read_bytes() for p in gold.rglob('*') if p.is_file() and 'sublevels' in p.relative_to(gold).parts}
    world,config,policy=case();before=snapshot(world);cfgbefore=config_snapshot(config);p=draft(world,config,policy);check(not p['blockers'],'integrated scope planned without blockers')
    check(any('explicit config root' in b for b in plan(world,sable_jar=SABLE)['blockers']),'world-only plan refuses existing external config scope')
    check(apply(world,p)['committed'],'one journal commits config/constructs/worlds together');check('"checks":24' in run_sable_codec.run('verify-migrated',world,'integrated-migrated'),'actual Sable readers reconstruct identities/plot/pose/bounds/pointers/voxel data after migration')
    check('muxi_game_core:homeworld' in (config/'aether-server.toml').read_text(),'Aether original home return follows HOME');check('muxi_game_core:homeworld' in (config/'twilightforest-common.toml').read_text(),'Twilight original home return follows HOME')
    check('# minecraft:overworld in comment remains' in (config/'aether-server.toml').read_text(),'config comments retained');check('allowed=["muxi_game_core:homeworld", "minecraft:overworld"]' in (config/'dimensions.toml').read_text(),'config simultaneous role mapping never cascades')
    graves=json.loads((config/'yigd.json').read_text());check(graves['graveConfig']['minimumGraveYLevel']==[{'key':HOME,'value':15},{'key':SURVIVAL,'value':-64}],'grave fields keep physical world values');check(graves['untouchedToken']=='FAKE SYNTHETIC SECRET','unrelated config values retained')
    for directory in ('DIM-1','DIM1'):
        key=str(Path(directory)/'sublevels/r.5.2.0.slvls');check(snapshot(world)[key]==before[key],'existing other-dimension construct bytes not modified')
    check(apply(world,p)['alreadyMigrated'],'repeat committed integrated apply inert');check(rollback(world)['byteIdenticalOriginalConfigs'],'integrated rollback checks configs');check(snapshot(world)==before and config_snapshot(config)==cfgbefore,'original world and config bytes exactly restored');check(rollback(world)['alreadyRolledBack'],'repeat rollback inert')
    p=draft(world,config,policy);apply(world,p);check(rollback(world)['byteIdenticalOriginalSave'],'retry after rollback with config and Sable allowed')
    forward=[];backward=[]
    for op in p['operations']:
        if op['kind']=='config':forward += ['op:'+op['path']+':'+edge for edge in ('intent','staged','written')]
        if op['kind']=='sable':forward += ['op:'+op['path']+':'+edge for edge in ('intent','appended','written')]
        if op['kind'] in ('config','sable'):backward.append('op:'+op['path']+':restored')
        if op['kind']=='config':backward.append('op:'+op['path']+':staged')
        if op['kind'] in ('region','sable'):
            for record in op['records']:
                if not record['external']:continue
                relative=str(Path(record['external']).relative_to(world));forward += ['external:'+relative+':'+edge for edge in ('intent','renamed','staged','written')];backward += ['external:'+relative+':'+edge for edge in ('removed','restored')]
    for move in p['moves']:
        forward += ['move:'+move['source']+':'+edge for edge in ('intent','renamed')];backward += ['move:'+move['source']+':'+edge for edge in ('intent','renamed')]
    for index,label in enumerate(forward):
        (OUT/'sable-config-progress.json').write_text(json.dumps({'phase':'forward','index':index,'fault':label,'checks':CHECKS}),encoding='utf-8')
        world,config,policy=case();original=snapshot(world);originalcfg=config_snapshot(config);p=draft(world,config,policy)
        interrupted(lambda:apply(world,p,fail_at=label),label);check(rollback(world)['byteIdenticalOriginalSave'],'forward interruption recovery '+label);check(snapshot(world)==original and config_snapshot(config)==originalcfg,'forward world/config byte identity '+label)
    for label in backward:
        (OUT/'sable-config-progress.json').write_text(json.dumps({'phase':'rollback','fault':label,'checks':CHECKS}),encoding='utf-8')
        world,config,policy=case();original=snapshot(world);originalcfg=config_snapshot(config);p=draft(world,config,policy);apply(world,p)
        interrupted(lambda:rollback(world,fail_at=label),label);check(rollback(world)['byteIdenticalOriginalSave'],'rollback interruption resumes '+label);check(snapshot(world)==original and config_snapshot(config)==originalcfg,'rollback world/config byte identity '+label)
    world,config,policy=case();p=draft(world,config,policy);(config/'unrelated.txt').write_text('new config')
    try:apply(world,p);raise AssertionError('stale config accepted')
    except ValueError:check(True,'config snapshot changes invalidate plan before writes')
    world,config,policy=case();p=draft(world,config,policy);apply(world,p);(config/'aether-server.toml').write_text('operator changed config')
    for action in (lambda:apply(world,p),lambda:rollback(world)):
        try:action();raise AssertionError('new config overwritten')
        except ValueError:check(True,'postcutover config progress protected')
    for relative,data in [('unknown.toml',b'unknown="minecraft:overworld"'),('unknown.json',b'{"minecraft:overworld":{}}'),('unknown.yaml',b'world: minecraft:overworld')]:
        world,config,policy=case();(config/relative).write_bytes(data);check(bool(draft(world,config,policy)['blockers']),'unreviewed config/format refused '+relative)
    for marker,modify in [('unknown-field',lambda t:t.value['user_data'].value.update({'unknown_binding':S(SURVIVAL)})),('opaque',lambda t:t.value['user_data'].value.update({'opaque':nbt.Tag(7,(len(SURVIVAL),SURVIVAL.encode()))})),('unknown-version',lambda t:t.value['plot'].value.update({'data_version':I(99)})),('unknown-root',lambda t:t.value.pop('uuid'))]:
        world,config,policy=case();path=world/'sublevels/r.0.0.0.slvls';tag=original_tag(path,3);modify(tag);container(path,tag);check(any('Sable' in b for b in draft(world,config,policy)['blockers']),'unknown Sable structure refused '+marker)
    world,config,policy=case();path=world/'sublevels/r.0.0.slvlr';data=bytearray(path.read_bytes());entry=int.from_bytes(data[:4],'big');data[(entry>>8)*128+4]=32;path.write_bytes(data);check(any('Unknown Sable storage flag' in b for b in draft(world,config,policy)['blockers']),'unknown storage flags refused')
    world,config,policy=case();(world/'sublevels/r.9.9.r').mkdir();(world/'sublevels/r.9.9.r/0.slvl').write_bytes(b'unknown');check(any('orphan/unidentified' in b for b in draft(world,config,policy)['blockers']),'orphan external file refused')
    world,config,policy=case();tag=original_tag(world/'sublevels/r.0.0.0.slvls',3);external=world/'sublevels/r.0.0.r/7.slvl';external.write_bytes(nbt.compress(nbt.encode('',tag),1));header=bytearray(4096);header[28:32]=((1<<8)|1).to_bytes(4,'big');stub=header+struct.pack('>IB',1,16)+b'\0'*(4096-5)
    for filename in ('r.0.0.1.slvls','r.0.0.2.slvls'):(world/'sublevels'/filename).write_bytes(stub)
    check(any('Ambiguous shared' in b for b in draft(world,config,policy)['blockers']),'multiple containers sharing one external path refused')
    world,config,policy=case();path=world/'sublevels/r.0.0.0.slvls';data=bytearray(path.read_bytes());data[12:16]=b'\0'*4;path.write_bytes(data);check(any('references missing/unrecognized' in b for b in draft(world,config,policy)['blockers']),'dangling holding pointer refused')
    world,config,policy=case();wrong=world/'wrong-sable.jar';wrong.write_bytes(b'not audited');check(any('does not match audited' in b for b in plan(world,sable_jar=wrong,config_root=config,config_policy=policy)['blockers']),'wrong runtime version refused')
    world,config,policy=case();(config/'unknown.toml').write_text('unknown="minecraft:overworld"');p=draft(world,config,policy);check(bool(p['blockers']),'unknown binding genuinely blocks plan');p['blockers']=[]
    try:apply(world,p);raise AssertionError('edited blockers bypassed recognition')
    except ValueError:check(True,'apply independently rejects removed blockers')
    world,config,policy=case();p=draft(world,config,policy);p['moves']=[]
    try:apply(world,p);raise AssertionError('edited moves accepted')
    except ValueError:check(True,'apply independently rejects changed move plan')
    for directory in ('defaultconfigs','configureddefaults','kubejs'):
        world,config,policy=case();other=world.parent/directory;other.mkdir();(other/'unknown.txt').write_text('minecraft:overworld');check(any(directory+'/unknown.txt' in b for b in draft(world,config,policy)['blockers']),'unscoped active root explicitly blocks migration '+directory)
    report={'success':True,'checks':CHECKS,'nativeSerializerChecksBefore':24,'nativeSerializerChecksAfter':24,'forwardFaultPoints':len(forward),'rollbackFaultPoints':len(backward),'fixturesOnly':True,'productionMutation':False,'minecraftStarted':False,'backupCreated':False,'scope':'known Sable 2.0.5 gzip containers and reviewed external config fields in one journal; full game/physics not claimed'}
    (OUT/'sable-config-integrated-result.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
if __name__=='__main__':
    try:run()
    except Exception:
        import traceback
        (OUT/'sable-config-integrated-failure.txt').write_text(traceback.format_exc(),encoding='utf-8')
        raise
