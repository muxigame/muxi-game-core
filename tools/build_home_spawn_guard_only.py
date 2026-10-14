"""Recompile owned guard sources in the registered Core against the verified CURRENT production ABI.
Other owners' in-progress auth/YSM changes are excluded. Every untouched current production entry is byte-identical.
"""
from pathlib import Path
import json,os,subprocess,zipfile,hashlib,re
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build/home-spawning-20261013';VERSION='1.12.3-homeguard.1'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 baseline=OUT/'production-core.jar';assert sha(baseline)=='46acc0ad4bd4b3cd0ff3ed812f649cfbbe6fc825820b3c9bca9267b8e0547d59'
 owned=json.loads((ROOT/'tests/home-spawning/own-source-files.json').read_text())['files'];files=[ROOT/p for p in owned if p.endswith('.java')];before={str(p.relative_to(ROOT)):sha(p) for p in files}
 classes=OUT/'guard-only-classes';classes.mkdir(exist_ok=True);cp=(OUT/'guard-classpath.txt').read_text();args=['--release','21','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes)]+list(map(str,files));af=OUT/'guard-only.args';af.write_text('\n'.join('"'+x.replace('\\','/')+'"' for x in args),encoding='utf-8')
 j=Path(os.environ['HOME_GUARD_JAVA_HOME']);subprocess.run([str(j/'bin/javac.exe'),'@'+str(af)],check=True)
 if before!={str(p.relative_to(ROOT)):sha(p) for p in files}:raise RuntimeError('Owned source changed during compilation')
 with zipfile.ZipFile(baseline) as z:old={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 entries=dict(old)
 for p in classes.rglob('*.class'):entries[p.relative_to(classes).as_posix()]=p.read_bytes()
 entries['META-INF/neoforge.mods.toml']=re.sub(rb'(?m)^version="[^"]+"',b'version="'+VERSION.encode()+b'"',old['META-INF/neoforge.mods.toml'],count=1)
 cfg=json.loads(old['muxi_game_core.mixins.json']);current=json.loads((ROOT/'src/main/resources/muxi_game_core.mixins.json').read_text());cfg['mixins'].extend(n for n in current['mixins'] if n.startswith('home.') and n not in cfg['mixins']);entries['muxi_game_core.mixins.json']=(json.dumps(cfg,indent=2)+'\n').encode()
 changed=sorted(n for n in old if old[n]!=entries[n]);assert set(changed)<= {'META-INF/neoforge.mods.toml','muxi_game_core.mixins.json','net/muxigame/core/feature/spawning/HomeSpawningFeature.class'}
 target=OUT/('muxi-game-core-'+VERSION+'.jar')
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
  for name,data in sorted(entries.items()):z.writestr(name,data)
 head=subprocess.check_output(['git','-c','safe.directory='+ROOT.as_posix(),'-C',str(ROOT),'rev-parse','HEAD'],text=True).strip()
 report={'sourceRoot':str(ROOT),'sourceHead':head,'ownedSourceHashes':before,'version':VERSION,'jar':str(target),'sha256':sha(target),'size':target.stat().st_size,'construction':'owned actual source incremental recompile against verified current production ABI','productionBaselineSha256':sha(baseline),'changedProductionEntries':changed,'addedEntries':sorted(set(entries)-set(old)),'missingProductionEntries':[],'unmodifiedProductionEntriesByteIdentical':True,'otherOwnerDirtyIncluded':False,'productionMutation':False,'nativeAcceptancePassed':False,'readyForRelease':False}
 (OUT/'guard-candidate.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({k:report[k] for k in ['jar','sha256','size','sourceHead','changedProductionEntries']}))
if __name__=='__main__':main()
