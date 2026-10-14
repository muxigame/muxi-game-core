"""Actual owned-source world-role candidate; untouched CURRENT production entries remain byte identical."""
from pathlib import Path
import os,sys,json,subprocess,zipfile,hashlib,re
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build/world-role-migration-20261014';OLD=ROOT/'build/home-spawning-20261013';VERSION='1.12.3-worldroles.1'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 baseline=OLD/'production-core.jar';assert sha(baseline)=='46acc0ad4bd4b3cd0ff3ed812f649cfbbe6fc825820b3c9bca9267b8e0547d59'
 roles=json.loads((ROOT/'tests/world-role-migration/owned-files.json').read_text())['files'];home=json.loads((ROOT/'tests/home-spawning/own-source-files.json').read_text())['files'];owned=list(dict.fromkeys([*roles,*home]));files=[ROOT/r for r in owned if r.endswith('.java')];before={str(f.relative_to(ROOT)):sha(f) for f in files}
 j=Path(os.environ['HOME_GUARD_JAVA_HOME']);classes=OUT/'classes';classes.mkdir(exist_ok=True);framework=ROOT.parent/'muxi-minigames/build/libs/muxi-minigames-0.2.1-equipment.1.jar';assert framework.is_file();cp=str(framework)+';'+(OLD/'guard-classpath.txt').read_text()+';'+ ';'.join(str(f) for f in (ROOT.parent/'bmc5server/mods').glob('*.jar'))
 (OUT/'classpath.txt').write_text(cp,encoding='utf-8')
 args=['--release','21','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),*map(str,files)];af=OUT/'compile.args';af.write_text('\n'.join('"'+x.replace('\\','/')+'"' for x in args),encoding='utf-8');run=subprocess.run([str(j/'bin/javac.exe'),'@'+str(af)],capture_output=True);(OUT/'compile-result.txt').write_bytes(run.stdout+run.stderr)
 if run.returncode:print((run.stdout+run.stderr).decode('utf-8',errors='replace'));raise RuntimeError('Owned-source compilation failed')
 assert before=={str(f.relative_to(ROOT)):sha(f) for f in files},'Source changed while compiling'
 with zipfile.ZipFile(baseline) as z:old={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 entries=dict(old)
 for f in classes.rglob('*.class'):entries[f.relative_to(classes).as_posix()]=f.read_bytes()
 entries['META-INF/neoforge.mods.toml']=re.sub(rb'(?m)^version="[^"]+"',b'version="'+VERSION.encode()+b'"',old['META-INF/neoforge.mods.toml'],count=1)
 cfg=json.loads(old['muxi_game_core.mixins.json']);current=json.loads((ROOT/'src/main/resources/muxi_game_core.mixins.json').read_text());extra={'WorldRoleLayoutGuardMixin','HomeWorldSpawnMixin','WorldRolePortalHubMixin','WorldRoleRespawnHubMixin'};cfg['mixins'].extend(n for n in current['mixins'] if (n.startswith('home.') or n in extra) and n not in cfg['mixins']);entries['muxi_game_core.mixins.json']=(json.dumps(cfg,indent=2)+'\n').encode()
 removed=['data/muxi_game_core/dimension/overworld.json'];entries.pop(removed[0],None);entries['data/muxi_game_core/dimension/homeworld.json']=(ROOT/'src/main/resources/data/muxi_game_core/dimension/homeworld.json').read_bytes()
 allowed={str(f.relative_to(ROOT/'src/main/java')).replace('\\','/').removesuffix('.java')+'.class' for f in files};allowed|={'META-INF/neoforge.mods.toml','muxi_game_core.mixins.json'}
 changed=sorted(n for n in old if n in entries and old[n]!=entries[n]);unexpected=[n for n in changed if n not in allowed and not any(n.startswith(x[:-6]+'$') for x in allowed if x.endswith('.class'))];assert not unexpected,unexpected
 jar=OUT/('muxi-game-core-'+VERSION+'.jar')
 with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
  for name,data in sorted(entries.items()):z.writestr(name,data)
 head=subprocess.check_output(['git','-c','safe.directory='+ROOT.as_posix(),'-C',str(ROOT),'rev-parse','HEAD'],text=True).strip();report={'sourceRoot':str(ROOT),'sourceHead':head,'version':VERSION,'jar':str(jar),'sha256':sha(jar),'size':jar.stat().st_size,'ownedSourceHashes':before,'construction':'actual owned sources incrementally compiled against verified current production ABI; other production bytes retained','productionBaselineSha256':sha(baseline),'changedProductionEntries':changed,'removedProductionEntries':removed,'addedEntries':sorted(set(entries)-set(old)),'unchangedProductionEntriesByteIdentical':True,'sourceOnlyOtherOwnerChangesIncluded':False,'targetIds':{'home':'muxi_game_core:homeworld','survival':'minecraft:overworld','adventure':'muxi_game_core:adventure'},'productionMigrationPerformed':False,'nativeAcceptancePassed':False,'readyForRelease':False,'blockers':['production portal rule and safe primary spawn unresolved','SavedData ownership audit and offline full-save plan required']};(OUT/'candidate.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({k:report[k] for k in ['jar','sha256','size','changedProductionEntries','removedProductionEntries']}))
if __name__=='__main__':main()
