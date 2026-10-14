"""Offline preparation by default. Minecraft launch is separately gated by a queue-owner slot receipt."""
from pathlib import Path
import argparse,hashlib,json,os,socket,subprocess,sys,time,tomllib,zipfile,shutil
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'build/world-role-migration-20261014';PREP=OUT/'full-native-preparation';BASE=ROOT/'build/home-spawning-20261013'
JAVA=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
RUNTIME=ROOT.parent/'bmc5server';PROJECT=ROOT.parent/'better-mc-remake'

def sha(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as source:
        for block in iter(lambda:source.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def save(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')

def record(path):return {'path':str(path.resolve()),'sha256':sha(path),'bytes':path.stat().st_size}

def descriptor(path):
    with zipfile.ZipFile(path) as archive:
        names=archive.namelist();name=next((n for n in ('META-INF/neoforge.mods.toml','META-INF/mods.toml') if n in names),None)
        data=tomllib.loads(archive.read(name).decode('utf-8-sig')) if name else {}
        return {'modIds':[m['modId'] for m in data.get('mods',[])],'metadata':name,'nestedJars':[{'entry':n,'sha256':hashlib.sha256(archive.read(n)).hexdigest(),'bytes':archive.getinfo(n).file_size} for n in names if n.endswith('.jar')]}

def checked(command,path):
    result=subprocess.run(command,capture_output=True);path.write_text((result.stdout+result.stderr).decode('utf-8',errors='replace'),encoding='utf-8')
    if result.returncode:raise RuntimeError('Offline preparation failed: '+str(path))

def deterministic_jar(path,files):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as archive:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,date_time=(2026,10,14,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,data)

def prepare():
    PREP.mkdir(parents=True,exist_ok=True);candidate=json.loads((OUT/'candidate.json').read_text());core=Path(candidate['jar']);assert sha(core)==candidate['sha256']
    for relative,digest in candidate['ownedSourceHashes'].items():assert sha(ROOT/relative)==digest,'Changed owned runtime source '+relative
    resolved=PREP/'registered-production-1.4.38-profile.json'
    production=json.loads(resolved.read_text(encoding='utf-8'))
    if not production.get('all350ExactSourcesResolved') or production.get('unresolved') or len(production.get('productInputs',[]))!=350:raise ValueError('Registered 1.4.38 full product combination is not equivalent; no partial preparation/launch permitted')
    product=[];inputs=[];replaced=[]
    for item in production['productInputs']:
        path=Path(item['path'])
        if sha(path)!=item['sha256'] or path.stat().st_size!=item['bytes']:raise ValueError('Registered production input missing/changed: '+item['name'])
        row={**record(path),'registeredFilename':item['name'],**descriptor(path)};product.append(row)
        if 'muxi_game_core' in row['modIds']:replaced.append(row)
        else:inputs.append(row)
    originals=[record(p) for p in sorted((RUNTIME/'mods').glob('*.jar'))]
    if len(replaced)!=1:raise ValueError('Production base must contain exactly one Core')
    old=Path(replaced[0]['path'])
    assert sha(old)=='46acc0ad4bd4b3cd0ff3ed812f649cfbbe6fc825820b3c9bca9267b8e0547d59'
    ids={}
    for item in inputs:
        for mod in item['modIds']:
            if mod in ids:raise ValueError('Duplicate top-level mod ID '+mod)
            ids[mod]=item['path']
    assert 'sable' in ids and sha(Path(ids['sable']))=='c8710f85bc780bbf523e6726ceaaf76dd0b8c61479db497b382c6bb34317e7ab'
    # Compile only; never invoke Minecraft/NeoForge main.
    cp=str(core)+';'+';'.join(item['path'] for item in inputs)+';'+(OUT/'classpath.txt').read_text()+';'+str(OUT/'sable-companion-pinned.jar')
    classes=PREP/'classes';args=['--release','21','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),str(ROOT/'tests/world-role-migration/FullWorldRolesLifecycleQA.java')]
    file=PREP/'compile.args';file.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8');checked([str(JAVA/'bin/javac.exe'),'-J-Xmx512m','@'+str(file)],PREP/'compile-result.txt')
    content={'META-INF/neoforge.mods.toml':b'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_world_roles_full_qa"\nversion="1.0"\ndisplayName="World roles full local profile QA ONLY"\n'}
    content.update({p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')});qa=PREP/'world-roles-full-lifecycle-QA-ONLY.jar';deterministic_jar(qa,content)
    # Existing role matrix is compiled independently; its fixed 16-mod execution does not occur.
    sys.path.insert(0,str(ROOT/'tests/world-role-migration'));import run_native_qa;run_native_qa.prepare()
    from world_roles.configs import rewrite
    policy=json.loads((OUT/'reviewed-external-config-policy.json').read_text());configs=[]
    for item in policy['files']:
        source=RUNTIME/'config'/item['path'];assert sha(source)==item['sourceSha256'];raw=source.read_bytes();mapped,count=rewrite(raw,item['path'],item['references'])
        for mode,data in [('old',raw),('new',mapped)]:
            target=PREP/'configs'/mode/item['path'];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);configs.append({'mode':mode,'relative':item['path'],**record(target)})
    library_records=[record(p) for p in sorted((RUNTIME/'libraries').rglob('*')) if p.is_file() and p.suffix.lower() in ('.jar','.txt')]
    helpers=[record(PROJECT/'scripts/local_mc_debug.py'),record(PROJECT/'scripts/local_mc_runtime.py')]
    sources=[record(ROOT/'tests/world-role-migration/FullWorldRolesLifecycleQA.java'),record(Path(__file__))]
    sources += [record(p) for p in sorted((PROJECT/'tests/local-mc-debug').rglob('*.java'))]
    sources += [record(resolved),record(Path(production['publishedManifest'])),record(Path(production['baselineInventory'])),record(Path(production['releaseArtifacts']))]
    sources += [record(p) for p in sorted((ROOT/'tools/world_roles').glob('*.py'))]+[record(ROOT/'tools/migrate_world_roles.py'),record(OUT/'verified-saveddata-policy.json'),record(OUT/'reviewed-external-config-policy.json'),record(JAVA/'bin/java.exe'),record(JAVA/'bin/javac.exe')]
    manifest={'schema':1,'profileName':'world-roles-1.4.38-registered-production-jars.1','preparedOnly':True,'minecraftStarted':False,'guiUsed':False,'productionAccessed':False,'backupCreated':False,'inputLocalJarCount':len(originals),'originalLocalInventory':originals,'sharedEffectiveMods':inputs,'registeredProductionJarCount':350,'registeredProductionInventory':product,'registeredProductJarEquivalentVerified':True,'registeredReleaseManifestSha256':production['registeredManifestSha256'],'replacedProductJars':replaced,'qaExtraInputsSeparate':True,'oldCore':record(old),'newCore':record(core),'lifecycleQA':record(qa),'matrixQA':record(OUT/'muxi-home-spawn-native-QA-ONLY.jar'),'configs':configs,'serverLibraries':library_records,'runtimeHelpers':helpers,'sourceFiles':sources,'javaVersion':record(JAVA/'release'),'slotRequired':True,'heapMiB':4096,'activeProcessors':2,'clients':0,'productionEquivalentVerified':False,'differences':['All 350 product JAR bytes match registered completed 1.4.38 inventory; live host/configuration is not re-read in this offline phase','Exactly one deliberate product delta in new-layout stages: Core baseline -> world-roles candidate; no framework/NPC/other product override; stage QA and generated agent are separate extras','Only eight public mod configurations are frozen old/new; other modules generate isolated defaults, no real world/player/private configuration copied'],'syntheticSeed':8324402536652151177,'stages':['seed-old','matrix-new','verify-new','restart-new','roundtrip-old'],'timingEstimate':{'offlinePreparationMinutes':[2,5],'fiveColdBootStagesMinutes':[40,90],'perBootTimeoutSeconds':900,'holdTimeoutSeconds':180,'shutdownTimeoutSeconds':120,'basis':'Estimate for registered 350-product-JAR set; full-profile startup has not been measured; 4GiB may be insufficient and must fail rather than reduce modules or silently raise memory'},'nativeChecksFrozen':['Dimension HOME/new primary/legacy registration and preserved Nether/End/adventure','Existing 275-case spawn/guard/source-import matrix on full profile','Home spawn metadata and actual End return target','Real Sable construct allocation/block/force ticket and UUID/user-data/plot/entity cold reload across old->new and offline apply->rollback->old','Explicit successful normal exits, results and mod hash match; connection/hold readiness alone is not acceptance'],'remainingManualOrExtendedChecks':['Actual Nether portal traversal and Aether/Twilight configuration consumer traversal','Valid bed/anchor, missing bed, first login/respawn with real network clients','Full native physics stability/pose/velocity and dependency relations beyond the minimal live construct','Selected client Xaero cache producer and display behavior; no client requested in this stage']}
    save(PREP/'manifest.json',manifest);save(PREP/'slot-receipt.template.json',{'task':'world-roles-full-native','slotGranted':False,'grantedBy':'queue owner after Create/music/YSM wheel','heapMiB':4096,'activeProcessors':2,'clients':0});return manifest

def verify():
    manifest=json.loads((PREP/'manifest.json').read_text());records=manifest['sharedEffectiveMods']+[manifest[k] for k in ('oldCore','newCore','lifecycleQA','matrixQA','javaVersion')]+manifest['configs']+manifest['serverLibraries']+manifest['runtimeHelpers']+manifest['sourceFiles']
    for item in records:
        if sha(Path(item['path']))!=item['sha256']:raise ValueError('Frozen input changed: '+item['path'])
    if manifest.get('registeredProductionJarCount')!=350 or manifest.get('registeredProductJarEquivalentVerified') is not True:raise ValueError('Unverified old 340 profile cannot run')
    for item in manifest['registeredProductionInventory']:
        if sha(Path(item['path']))!=item['sha256']:raise ValueError('Registered production base input changed')
    return manifest

def run_stage(stage,world,slot):
    manifest=verify();receipt=json.loads(slot.read_text())
    if receipt.get('task')!='world-roles-full-native' or receipt.get('slotGranted') is not True or receipt.get('heapMiB')!=4096 or receipt.get('clients')!=0 or receipt.get('activeProcessors')!=2:raise ValueError('Queue-owner slot receipt is not granted for this frozen zero-client budget')
    if stage!='seed-old' and world is None:raise ValueError('Explicit own synthetic stopped input world required')
    if world:
        root=world.resolve(strict=True)
        own_lab=root.parent.parent
        prepared=root.parent/'qa-own-input.json'
        if root.is_relative_to((PREP/'inputs').resolve()) and prepared.is_file():
            if json.loads(prepared.read_text()).get('createdBy')!='world-roles-full-native':raise ValueError('Prepared world owner not recognized')
        elif own_lab.parent!=(PROJECT/'build/local-mc-debug').resolve() or not own_lab.name.startswith('world-roles-full-') or root!=own_lab/'server/qa-world':raise ValueError('Only this task own isolated synthetic worlds accepted; production/other-owner saves refused')
        else:
            stopped=json.loads((own_lab/'run-result.json').read_text())
            if not stopped.get('normalExit') or stopped.get('exitCodes',{}).get('server')!=0:raise ValueError('Source own server has not exited normally')
        sys.path.insert(0,str(ROOT/'tools'));from world_roles.transaction import offline
        with offline(root):pass
    sys.path.insert(0,str(PROJECT/'scripts'));import local_mc_debug as debug
    original_popen=debug.subprocess.Popen;own_server=[None]
    def low(*args,**kwargs):
        kwargs['creationflags']=kwargs.get('creationflags',0)|getattr(subprocess,'BELOW_NORMAL_PRIORITY_CLASS',0)
        if own_server[0] is not None and kwargs.get('cwd') and Path(kwargs['cwd']).resolve()==own_server[0].resolve():
            props=own_server[0]/'server.properties';text=props.read_text();props.write_text(text+'\nlevel-seed=8324402536652151177\n',encoding='utf-8')
        return original_popen(*args,**kwargs)
    debug.subprocess.Popen=low;old_arguments=debug.rt.server_arguments;debug.rt.server_arguments=lambda *args,**kwargs:[('-XX:ActiveProcessorCount=2' if v.startswith('-XX:ActiveProcessorCount=') else v) for v in old_arguments(*args,**kwargs)]
    mode='new' if stage in ('matrix-new','verify-new','restart-new') else 'old';old_copy=debug.rt.copy_inputs
    def copied(lab,mods,data):
        old_copy(lab,mods,data)
        if Path(lab).name=='server':
            own_server[0]=Path(lab)
            for item in manifest['configs']:
                if item['mode']==mode:
                    target=Path(lab)/'config'/item['relative'];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(Path(item['path']).read_bytes())
    debug.rt.copy_inputs=copied;old_write=debug.rt.write_json
    def isolated(path,obj):
        if Path(path).name=='muxi-game-core.json':obj={'schema':1,'features':{'identity':{'enabled':False},'login':{'enabled':False},'terminalSso':{'enabled':False},'opSync':{'enabled':False}}}
        return old_write(path,obj)
    debug.rt.write_json=isolated
    with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
    lab=PROJECT/'build/local-mc-debug'/('world-roles-full-'+stage+'-'+time.strftime('%Y%m%d-%H%M%S'))
    qa=manifest['matrixQA'] if stage=='matrix-new' else manifest['lifecycleQA'];core=manifest['newCore'] if mode=='new' else manifest['oldCore']
    inputs=[item['path'] for item in manifest['sharedEffectiveMods']]+[core['path'],qa['path']]
    sys.argv=[str(PROJECT/'scripts/local_mc_debug.py'),'run','--project-root',str(PROJECT),'--instance-root',str(lab),'--server-runtime',str(RUNTIME),'--client-game',str(ROOT.parent/'_client_test/game'),'--java-home',str(JAVA),'--port',str(port),'--clients','0','--server-only','--mode','hold','--server-memory-mb','4096','--approved-heap-budget-mb','4096','--accept-eula','--hold-seconds','180','--boot-timeout','900','--shutdown-timeout','120','--unix-temp',str(ROOT.parent/'build/home-spawn-unix-temp')]
    if world:sys.argv+=['--world',str(world.resolve())]
    for path in inputs:sys.argv+=['--server-mod',path]
    properties=['qa.worldRoles.fullStage='+('verify-new' if mode=='new' else ('seed-old' if stage=='seed-old' else 'verify-old')),'http.proxyHost=127.0.0.1','http.proxyPort=9','https.proxyHost=127.0.0.1','https.proxyPort=9']
    if stage=='matrix-new':properties.append('qa.worldRoles.migratedFixture=true')
    for prop in properties:sys.argv+=['--jvm-property',prop]
    debug.main();file=lab/'server'/('home-spawn-native-result.json' if stage=='matrix-new' else 'world-roles-full-lifecycle-result.json');result=json.loads(file.read_text()) if file.exists() else None;life=json.loads((lab/'run-result.json').read_text());report={'stage':stage,'lab':str(lab),'result':result,'lifecycle':life,'productionMutation':False,'nativeAcceptancePassed':bool(result and result.get('success') and life.get('normalExit') and life.get('exitCodes',{}).get('server')==0)};save(PREP/(stage+'-result.json'),report)
    if not report['nativeAcceptancePassed']:raise RuntimeError('Full native stage did not pass; inspect own report and boot.log; no lower-mod fallback permitted')
    print(json.dumps({'stage':stage,'passed':True,'lab':str(lab)}))

def prepare_cases():
    manifest=verify();seed=json.loads((PREP/'seed-old-result.json').read_text())
    if not seed.get('nativeAcceptancePassed'):raise ValueError('Successful own old-layout seed stage required')
    source=Path(seed['lab']).resolve()/'server/qa-world'
    if not source.parent.parent.name.startswith('world-roles-full-seed-old-') or source.parent.parent.parent!=(PROJECT/'build/local-mc-debug').resolve():raise ValueError('Seed source does not belong to this task')
    sys.path.insert(0,str(ROOT/'tools'));from world_roles.transaction import offline,plan,apply,rollback,snapshot
    root=PREP/'inputs'
    if root.exists():raise ValueError('Prepared input root already exists; never replace or merge it')
    policy=json.loads((OUT/'verified-saveddata-policy.json').read_text());sable=Path(next(item['path'] for item in manifest['sharedEffectiveMods'] if 'sable' in item['modIds']))
    reports=[]
    with offline(source):
        for name in ('migrated','roundtrip'):
            server=root/name;server.mkdir(parents=True);world=server/'world';shutil.copytree(source,world)
            cfg=server/'config';cfg.mkdir();review={'schema':1,'files':[]}
            original=json.loads((OUT/'reviewed-external-config-policy.json').read_text())
            for item in original['files']:
                data=(PREP/'configs/old'/item['path']).read_bytes();target=cfg/item['path'];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);review['files'].append({**item,'sourceSha256':sha(target)})
            before=snapshot(world);draft=plan(world,hub='home',data_policy=policy,sable_jar=sable,config_root=cfg,config_policy=review);save(server/'migration-plan.json',draft)
            if draft['blockers']:raise ValueError('Own synthetic migration blocked; stop and audit, never remove blockers: '+str(draft['blockers']))
            action=apply(world,draft)
            if name=='roundtrip':action=rollback(world);assert snapshot(world)==before
            save(server/'qa-own-input.json',{'createdBy':'world-roles-full-native','sourceRun':seed['lab'],'sourceManifestSha256':sha(PREP/'manifest.json'),'worldRoleLayout':'new' if name=='migrated' else 'old-after-byte-exact-rollback'})
            reports.append({'case':name,'world':str(world),'result':action,'files':len(snapshot(world))})
    save(PREP/'input-preparation-result.json',{'success':True,'ownSyntheticWorldsOnly':True,'productionMutation':False,'minecraftStarted':False,'backupCreated':False,'cases':reports});print(json.dumps(reports))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('command',choices=['prepare','verify-inputs','prepare-cases','run-stage']);parser.add_argument('--stage',choices=['seed-old','matrix-new','verify-new','restart-new','roundtrip-old']);parser.add_argument('--world',type=Path);parser.add_argument('--slot-receipt',type=Path);args=parser.parse_args()
    if args.command=='prepare':sys.path.insert(0,str(ROOT/'tools'));manifest=prepare();print(json.dumps({'preparedOnly':True,'originalLocalJarCount':manifest['inputLocalJarCount'],'sharedEffectiveJarCount':len(manifest['sharedEffectiveMods']),'manifest':str(PREP/'manifest.json'),'sha256':sha(PREP/'manifest.json'),'minecraftStarted':False}));return
    if args.command=='verify-inputs':manifest=verify();print(json.dumps({'inputsMatch':True,'minecraftStarted':False,'manifestSha256':sha(PREP/'manifest.json')}));return
    if args.command=='prepare-cases':prepare_cases();return
    if not args.stage or not args.slot_receipt:parser.error('--stage and granted --slot-receipt required for run-stage; preparation never starts Minecraft')
    run_stage(args.stage,args.world,args.slot_receipt)
if __name__=='__main__':main()
