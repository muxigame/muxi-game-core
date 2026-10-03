from pathlib import Path
import argparse,ctypes,datetime,hashlib,importlib.util,json,os,shutil,subprocess,time,uuid,zipfile
from run_icons_native import allowed
HERE=Path(__file__).resolve().parent
OUT=HERE/'transfer-lab'
GAME=Path(r'C:\Users\ranzh\workspace\dev\muxigame\_client_test\game')
SYNC=Path(r'C:\Users\ranzh\Documents\Codex\2026-10-02\task\dev-sync-20261002')
JDK=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
DEPS=Path(r'C:\Users\ranzh\Documents\Codex\2026-10-02\task-10\album-build\dependencies.jar')
spec=importlib.util.spec_from_file_location('transferbuild',SYNC/'muxi-terminal/build.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
spec=importlib.util.spec_from_file_location('fmlqa',SYNC/'muxi-terminal/tests/album-runtime/qa_fml_config.py');fml=importlib.util.module_from_spec(spec);spec.loader.exec_module(fml)
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('--shaders',choices=['on','off'],required=True);p.add_argument('--candidate',action='store_true');p.add_argument('--fix',action='store_true');p.add_argument('--final-validation',action='store_true');p.add_argument('--control',action='store_true');p.add_argument('--extended',action='store_true');p.add_argument('--probe-v2',action='store_true');p.add_argument('--stage-profile',action='store_true');p.add_argument('--stage-cache',action='store_true');p.add_argument('--program-binary',action='store_true');p.add_argument('--program-control',action='store_true');p.add_argument('--distance-controls',action='store_true');p.add_argument('--join-only',action='store_true');p.add_argument('--regex-cache',action='store_true');p.add_argument('--regex-control',action='store_true');p.add_argument('--regex-paired',action='store_true');p.add_argument('--material-cache',action='store_true');p.add_argument('--material-paired',action='store_true');p.add_argument('--home-fix',action='store_true');p.add_argument('--program-paired',action='store_true');p.add_argument('--binary-template',type=Path);p.add_argument('--samples',type=int,default=8);p.add_argument('--prepare-only',action='store_true');p.add_argument('--world-template',type=Path);a=p.parse_args()
 session=ctypes.c_ulong();ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(),ctypes.byref(session))
 if os.environ.get('COMPUTERNAME')!='JBC_FCRL' or session.value!=2:raise SystemExit('131 Session2 required')
 prepared=json.loads((OUT/'release-pack-prepare.json').read_text(encoding='utf-8'))
 if prepared['version']!='1.4.27' or prepared['missing']:raise SystemExit('Released manifest incomplete')
 mode='fix' if a.fix else 'candidate' if a.candidate else 'baseline'
 if a.control:mode+='-control'
 if a.extended:mode+='-extended'
 if a.final_validation:mode+='-final-validation'
 if a.stage_profile:mode+='-stageprofile'
 if a.stage_cache:mode+='-stagecache'
 if a.program_binary:mode+='-programbinary'
 if a.program_control:mode+='-programcontrol'
 if a.distance_controls:mode+='-distancecontrols'
 if a.join_only:mode+='-join'
 if a.regex_cache:mode+='-regex'
 if a.regex_control:mode+='-regexctrl'
 if a.regex_paired:mode+='-paired'
 if a.material_cache or a.material_paired:mode+='-mat'
 if a.material_paired:mode+='-paired'
 if a.home_fix:mode+='-homefix'
 if a.program_paired:mode+='-paired'
 folder_mode=mode.replace('programbinary-regex-mat-paired','pb-rm').replace('homefix','hf').replace('programbinary-regexctrl-paired','pb-rp').replace('extended-programbinary','xpbin').replace('programcontrol','pctrl').replace('programbinary','pbin').replace('distancecontrols','dist')
 lab=OUT/('native-'+a.shaders+'-'+folder_mode+'-'+datetime.datetime.utcnow().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]);lab.mkdir()
 shutil.copytree(OUT/'release-pack',lab,dirs_exist_ok=True)
 if a.world_template:
  world=a.world_template.resolve()
  if OUT.resolve() not in world.parents or world.name!='transfer-private':raise SystemExit('Only own private transfer world templates are allowed')
  shutil.copytree(world,lab/'saves/transfer-private')
 # Only private test copies are replaced; installed and shared package files remain untouched.
 for p in list((lab/'mods').glob('*.jar')):
  if p.name.startswith(('muxi-game-core-','muxi-terminal-','muxi-minigames-')):p.unlink()
 for name in ['dev-core-baseline.jar','dev-terminal-baseline.jar','dev-probe-baseline.jar']:shutil.copy2(OUT/'modules'/name,lab/'mods'/name)
 if a.fix:shutil.copy2(OUT/'modules/dev-core-fix.jar',lab/'mods/dev-core-baseline.jar')
 if a.final_validation:shutil.copy2(OUT/'modules/dev-core-final.jar',lab/'mods/dev-core-baseline.jar')
 if a.stage_profile or a.stage_cache or a.program_binary or a.program_control:
  shutil.copy2(OUT/'modules/dev-core-final.jar',lab/'mods/dev-core-baseline.jar')
  shutil.copy2(OUT/'modules/stage-lab.jar',lab/'mods/stage-lab.jar')
 if a.program_binary or a.program_control:
  shutil.copy2(OUT/'modules/program-lab.jar',lab/'mods/program-lab.jar')
 if a.regex_cache or a.regex_control:shutil.copy2(OUT/'modules/regex-lab.jar',lab/'mods/regex-lab.jar')
 if a.material_cache or a.material_paired:shutil.copy2(OUT/'modules/material-lab.jar',lab/'mods/material-lab.jar')
 if a.home_fix:shutil.copy2(OUT/'modules/dev-core-home.jar',lab/'mods/dev-core-baseline.jar')
 if a.binary_template:
  source=a.binary_template.resolve()
  assert OUT.resolve() in source.parents and source.name=='.muxi-program-cache-v1','Only own private binary cache templates allowed'
  shutil.copytree(source,lab/'.muxi-program-cache-v1')
 if a.probe_v2:shutil.copy2(OUT/'modules/dev-probe-v2.jar',lab/'mods/dev-probe-baseline.jar')
 framework=next((SYNC/'muxi-minigames/build/libs').glob('muxi-minigames-*.jar'))
 if a.final_validation or a.stage_profile or a.stage_cache or a.program_binary or a.program_control:
  control=Path(json.loads((OUT/'latest-on-baseline-control.json').read_text())['lab']);framework=next((control/'mods').glob('muxi-minigames-*.jar'))
 shutil.copy2(framework,lab/'mods'/framework.name)
 shutil.copytree(GAME/'mods/mcef-libraries',lab/'mods/mcef-libraries',dirs_exist_ok=True)
 shutil.copytree(GAME/'versions/BatterMC5Remake/BatterMC5Remake-natives',lab/'natives')
 fml.configure_file(lab/'config/fml.toml')
 fml.patch_properties(lab/'config/iris.properties',{'enableShaders':str(a.shaders=='on').lower(),'shaderPack':'Better MC - Low'})
 (lab/'config/mcef').mkdir(exist_ok=True);(lab/'config/mcef/mcef.properties').write_text('skip-download=true\nuse-cache=false\n',encoding='utf-8')
 (lab/'config/muxi-game-core.json').write_text(json.dumps({'schema':1,'features':{'identity':{'enabled':False},'login':{'enabled':False}}}),encoding='utf-8')
 options=(lab/'options.txt').read_text(encoding='utf-8')
 options_map={}
 for line in options.splitlines():
  if ':' in line:key,value=line.split(':',1);options_map[key]=value
 options_map.update({'maxFps':'60','enableVsync':'false','fullscreen':'false','guiScale':'2','pauseOnLostFocus':'false','onboardAccessibility':'false','soundCategory_master':'0.0'})
 (lab/'options.txt').write_text('\n'.join(k+':'+v for k,v in options_map.items())+'\n',encoding='utf-8')
 qa_name='JoinNativeQA.java' if a.join_only else 'TransferNativeQA.java'
 qa=lab/'qa-source';qa.mkdir();shutil.copy2(HERE/qa_name,qa/qa_name)
 for name in ['HardwareWmiTimeoutQAMixin.java','OfflineMcefMixin.java']:shutil.copy2(SYNC/'muxi-terminal/tests/music-runtime/java/net/muxigame/terminal/qa/mixin'/name,qa/name)
 shutil.copy2(SYNC/'muxi-terminal/tests/client-smoke/java/net/muxigame/terminal/smoke/mixin/HiddenWindowMixin.java',qa/'HiddenWindowMixin.java')
 cp=os.pathsep.join(map(str,[DEPS,OUT/'modules/dev-core-baseline.jar',OUT/'modules/dev-probe-baseline.jar']))
 b.compile_java(JDK/'bin/javac.exe',list(qa.glob('*.java')),lab/'qa-classes',cp,lab/'qa.args')
 with zipfile.ZipFile(lab/'mods/muxi-transfer-qa-only.jar','w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_transfer_qa"\nversion="1.0.0"\ndisplayName="Private transfer timing QA"\n[[mixins]]\nconfig="transfer_qa.mixins.json"\n[[mixins]]\nconfig="transfer_hidden.mixins.json"\n')
  z.writestr('transfer_qa.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.qa.mixin','compatibilityLevel':'JAVA_21','client':['HardwareWmiTimeoutQAMixin','OfflineMcefMixin'],'injectors':{'defaultRequire':1}}))
  z.writestr('transfer_hidden.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.smoke.mixin','compatibilityLevel':'JAVA_21','client':['HiddenWindowMixin'],'injectors':{'defaultRequire':1}}))
  for file in (lab/'qa-classes').rglob('*.class'):z.write(file,file.relative_to(lab/'qa-classes').as_posix())
 meta=json.loads((lab/'versions/BatterMC5Remake/BatterMC5Remake.json').read_text(encoding='utf-8'))
 libs=list(dict.fromkeys([GAME/'libraries'/x['downloads']['artifact']['path'] for x in meta['libraries'] if allowed(x.get('rules')) and x.get('downloads',{}).get('artifact')]+[GAME/'versions/BatterMC5Remake/BatterMC5Remake.jar']))
 if any(not p.is_file() for p in libs):raise SystemExit('Missing installed client library')
 subs={'auth_player_name':'TransferQA131','auth_uuid':'694054dfd9ea4e1f8c5b6cc2a14de27e',
       'auth_access_token':'0','version_name':'BatterMC5Remake','game_directory':str(lab),
       'assets_root':str(GAME/'assets'),'assets_index_name':meta['assetIndex']['id'],
       'clientid':'','auth_xuid':'','user_type':'legacy','version_type':'release',
       'resolution_width':'1280','resolution_height':'720','natives_directory':str(lab/'natives'),
       'launcher_name':'transfer-private-qa','launcher_version':'1',
       'classpath':os.pathsep.join(map(str,libs)),'library_directory':str(GAME/'libraries'),
       'classpath_separator':os.pathsep}
 def expand(items):
  out=[]
  for item in items:
   if isinstance(item,dict):
    if not allowed(item.get('rules')):continue
    values=item['value'] if isinstance(item['value'],list) else [item['value']]
   else:values=[item]
   for value in values:
    for key,replacement in subs.items():value=value.replace('${'+key+'}',replacement)
    if '${' in value:raise ValueError('Unresolved launch template '+value)
    out.append(value)
  return out
 args=['-Xms1G','-Xmx10G','-XX:ActiveProcessorCount=4','-Dfile.encoding=UTF-8','-Dmuxi.transferProbe=true','-Dmuxi.transferProbe.skipExtraDimensionReload='+str(a.candidate).lower(),'-Dqa.transfer.shaders='+str(a.shaders=='on').lower(),'-Dqa.transfer.samples='+str(a.samples),'-Dhttp.proxyHost=127.0.0.1','-Dhttp.proxyPort=9','-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort=9',*expand(meta['arguments']['jvm']),meta['mainClass'],*expand(meta['arguments']['game'])]
 args[0:0]=['-Dqa.transfer.extended='+str(a.extended).lower(),'-Dqa.transfer.finalValidation='+str(a.final_validation).lower(),'-Dqa.transfer.serverMovement=true','-Dqa.transfer.expectFix='+str(a.fix and a.shaders=='on').lower()]
 args.insert(0,'-Dqa.transfer.distanceControls='+str(a.distance_controls).lower())
 if a.program_paired:args.insert(0,'-Dqa.transfer.programPaired=true')
 if a.regex_cache or a.regex_control:args.insert(0,'-Dmuxi.logRegexCache='+str(a.regex_cache).lower())
 if a.regex_paired:args.insert(0,'-Dqa.transfer.regexPaired=true')
 if a.material_cache or a.material_paired:args[0:0]=['-Dmuxi.materialMapCache='+str(a.material_cache).lower(),'-Dqa.transfer.materialPaired='+str(a.material_paired).lower(),'-Dqa.materialMap.verifyOnce=true']
 if a.stage_profile or a.stage_cache:args.insert(0,'-Dmuxi.shaderStageCache='+str(a.stage_cache).lower())
 if a.program_binary or a.program_control:
  # Use content hashes, not absolute lab paths. Full processed GLSL/macros/options are keyed at runtime.
  base_inputs={p.relative_to(OUT/'release-pack').as_posix():digest(p) for folder in ['shaderpacks/Better MC - Low','resourcepacks'] for p in (OUT/'release-pack'/folder).rglob('*') if p.is_file()}
  base_inputs.update({p.name:digest(p) for p in (lab/'mods').glob('*.jar') if p.name!='muxi-transfer-qa-only.jar'})
  fingerprint=hashlib.sha256(json.dumps(base_inputs,sort_keys=True).encode()).hexdigest()
  (lab/'program-input-content-hashes.json').write_text(json.dumps(base_inputs,indent=2),encoding='utf-8')
  args[0:0]=['-Dmuxi.programBinaryCache='+str(a.program_binary).lower(),'-Dmuxi.shaderStageCache=false','-Dmuxi.binaryCache.inputFingerprint='+fingerprint]
 (lab/'launch.args').write_text('\n'.join('"'+x.replace('\\','/').replace('"','\\"')+'"' for x in args),encoding='utf-8')
 receipt={'owner':'task5-transfer-performance','lab':str(lab),'packVersion':'1.4.27','allManifestFilesVerified':8998,'worldTemplate':str(a.world_template) if a.world_template else None,'shaders':a.shaders,'candidate':a.candidate,'samples':a.samples,'renderDistance':options_map['renderDistance'],'simulationDistance':options_map['simulationDistance'],'maxFps':60,'javaHeapGiB':10,'session':session.value,'physicalInput':False,'globalCacheModified':False,'devModules':json.loads((OUT/'modules/source-receipt.json').read_text(encoding='utf-8')),'frameworkJarSha256':digest(framework)}
 receipt.update({'fix':a.fix,'probeV2':a.probe_v2,'actualCoreJarSha256':digest(lab/'mods/dev-core-baseline.jar'),'actualProbeJarSha256':digest(lab/'mods/dev-probe-baseline.jar')})
 receipt.update({'extendedQA':a.extended,'effectiveProcessors':4,'qaSourceSha256':digest(qa/qa_name),'joinOnly':a.join_only,'qaJarSha256':digest(lab/'mods/muxi-transfer-qa-only.jar')})
 if a.stage_profile or a.stage_cache:receipt.update({'shaderStageCache':a.stage_cache,'shaderStageProfile':True,'stageLabJarSha256':digest(lab/'mods/stage-lab.jar')})
 if a.program_binary or a.program_control:receipt.update({'programBinaryCache':a.program_binary,'programLabJarSha256':digest(lab/'mods/program-lab.jar'),'binaryInputFingerprint':fingerprint,'binaryTemplate':str(a.binary_template) if a.binary_template else None,'shaderStageCache':False,'distanceControls':a.distance_controls,'cachePhaseToggleAtSample':12 if a.distance_controls or a.program_paired else None,'programPaired':a.program_paired})
 if a.regex_cache or a.regex_control:receipt.update({'logRegexCache':a.regex_cache,'logRegexPaired':a.regex_paired,'regexToggleAtSample':6 if a.regex_paired else None,'regexLabJarSha256':digest(lab/'mods/regex-lab.jar')})
 if a.material_cache or a.material_paired:receipt.update({'materialMapCache':a.material_cache,'materialMapPaired':a.material_paired,'materialLabJarSha256':digest(lab/'mods/material-lab.jar'),'nativeMaterialMapVerification':True})
 if a.home_fix:receipt.update({'homeInitFix':True,'homeInitCoreSourceReceipt':str(OUT/'modules/core-home-receipt.json')})
 (lab/'inputs.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');(OUT/('latest-'+a.shaders+'-'+mode+'.json')).write_text(json.dumps({'lab':str(lab)}),encoding='utf-8')
 if a.prepare_only:print(json.dumps({'prepared':str(lab)}));return
 env=dict(os.environ);env.pop('MUXI_TERMINAL_GAME_CREDENTIAL',None)
 with (lab/'boot.log').open('w',encoding='utf-8') as log:
  proc=subprocess.Popen([str(JDK/'bin/java.exe'),'@'+str(lab/'launch.args')],cwd=lab,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
  (lab/'pid.json').write_text(json.dumps({'pid':proc.pid}),encoding='utf-8');print(json.dumps({'launched':True,'pid':proc.pid,'lab':str(lab)}),flush=True)
  deadline=time.monotonic()+1500
  while proc.poll() is None:
   if time.monotonic()>deadline-60:(lab/'request-normal-close.json').write_text('{}')
   if time.monotonic()>deadline:raise SystemExit('Deadline; no force kill; normal close requested')
   time.sleep(1)
 result=json.loads((lab/'transfer-qa-result.json').read_text(encoding='utf-8')) if (lab/'transfer-qa-result.json').exists() else {'success':False,'error':'No QA result'}
 result.update({'exitCode':proc.returncode,'lab':str(lab)})
 (lab/'run-summary.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
 print(json.dumps({'success':result.get('success'),'exitCode':proc.returncode,'samples':len(result.get('samples',[])),'lab':str(lab),'error':result.get('error')}),flush=True)
 if not result.get('success'):raise SystemExit(1)
if __name__=='__main__':main()
