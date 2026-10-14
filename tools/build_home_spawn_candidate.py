"""Build actual registered Core sources into a separate candidate; never overwrite another owner's libs."""
from pathlib import Path
import sys,os,json,hashlib,zipfile,subprocess,tempfile,importlib.util
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/home-spawning-20261013'
VERSION='1.12.3-homeguard.1'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 spec=importlib.util.spec_from_file_location('core_local_builder',ROOT/'build.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
 server=ROOT.parent/'bmc5server';game=ROOT.parent/'_client_test/game';deps=json.loads((ROOT/'dependencies.json').read_text());meta=json.loads((ROOT/'mod.json').read_text());meta['version']=VERSION
 java=Path(os.environ['HOME_GUARD_JAVA_HOME']);compiler,runtime=b.java_tools(java)
 sources=sorted((ROOT/'src/main/java').rglob('*.java'));inputs=sources+sorted(p for p in (ROOT/'src/main/resources').rglob('*') if p.is_file());before={str(p.relative_to(ROOT)):digest(p) for p in inputs}
 jars=list((server/'libraries').rglob('*.jar'));nd=server/'libraries/net/neoforged/neoforge/21.1.250';neo=nd/'neoforge-21.1.250-universal.jar';ns=nd/'neoforge-21.1.250-server.jar';mapped=next(p for p in jars if p.name=='server-1.21.1-20240808.144430-srg.jar')
 nick=server/'mods'/deps['simpleNicknames']['filename'];assert hashlib.sha512(nick.read_bytes()).hexdigest()==deps['simpleNicknames']['sha512']
 compat=[]
 for name in deps['compileOnly']:
  choices=[game/'mods'/name,server/'mods'/name,ROOT.parent/'better-mc-remake/pack/source/Better MC Remake [FORGE]/mods'/name,ROOT/'build/adventure-protection/compile-mods'/name,ROOT/'build/teleport-network-20261012-020620/compile-only'/name];p=next((p for p in choices if p.is_file()),None)
  if p is None:raise RuntimeError('Compile-only dependency absent: '+name)
  compat.append(p)
 fm=ROOT.parent/'muxi-minigames';fv=json.loads((fm/'mod.json').read_text())['version'];framework=fm/'build/libs'/('muxi-minigames-'+fv+'.jar');assert framework.exists()
 OUT.mkdir(exist_ok=True)
 with tempfile.TemporaryDirectory(dir=OUT,prefix='whole-source-') as tmp:
  tmp=Path(tmp);nested=tmp/'nested';nested.mkdir();classes=tmp/'classes'
  client=[game/deps['client']['neoforgePatched'],game/deps['client']['minecraft']];lwjgl=[p for p in (game/'libraries/org/lwjgl').rglob('*.jar') if 'natives-' not in p.name]
  libs=client+lwjgl+[ns,mapped]+[p for p in jars if '/net/minecraft/' not in p.as_posix() and p!=ns]+b.nested_jars(neo,nested)+[nick,framework]+compat+[j for p in compat for j in b.nested_jars(p,nested)]
  cp=os.pathsep.join(map(str,libs));b.compile_java(compiler,sources,classes,cp,tmp/'compile.args')
  testclasses=tmp/'self-test-classes';b.compile_java(compiler,sorted((ROOT/'tests/java').rglob('*.java')),testclasses,str(classes)+os.pathsep+cp,tmp/'tests.args')
  subprocess.run([str(runtime),'-cp',str(testclasses)+os.pathsep+str(ROOT/'src/main/resources')+os.pathsep+str(classes)+os.pathsep+cp,'net.muxigame.core.CoreSelfTest'],check=True)
  if before!={str(p.relative_to(ROOT)):digest(p) for p in inputs}:raise RuntimeError('Concurrent source changes during build; no candidate packaged')
  entries={'META-INF/LICENSE':(ROOT/'LICENSE').read_bytes()}
  for base in [classes,ROOT/'src/main/resources']:
   for p in base.rglob('*'):
    if p.is_file():
     data=p.read_bytes()
     if p.suffix=='.toml':data=data.replace(b'${mod_version}',VERSION.encode())
     entries[str(p.relative_to(base)).replace('\\','/')]=data
  live=OUT/'production-core.jar'
  with zipfile.ZipFile(live) as z:approved={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
  excluded=sorted(n for n in entries if n not in approved and not n.startswith(('net/muxigame/core/feature/spawning/','net/muxigame/core/mixin/home/')))
  entries={n:v for n,v in entries.items() if n in approved or n.startswith(('net/muxigame/core/feature/spawning/','net/muxigame/core/mixin/home/'))}
  # Packaging uses the current production-approved metadata, never the concurrent Paster/YSM registration.
  import re
  entries['META-INF/neoforge.mods.toml']=re.sub(rb'(?m)^version="[^"]+"',b'version="'+VERSION.encode()+b'"',approved['META-INF/neoforge.mods.toml'],count=1)
  cfg=json.loads(approved['muxi_game_core.mixins.json']);current=json.loads((ROOT/'src/main/resources/muxi_game_core.mixins.json').read_text());cfg['mixins'].extend(n for n in current['mixins'] if n.startswith('home.') and n not in cfg['mixins']);entries['muxi_game_core.mixins.json']=(json.dumps(cfg,indent=2)+'\n').encode()
  outside=[n for n in set(approved)&set(entries) if approved[n]!=entries[n] and not n.startswith(('net/muxigame/core/feature/spawning/','net/muxigame/core/mixin/home/')) and n not in ('META-INF/neoforge.mods.toml','muxi_game_core.mixins.json')]
  if outside:raise RuntimeError('Unrelated product code differs from production: '+str(outside))
  target=OUT/('muxi-game-core-'+VERSION+'.jar')
  with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
   for name,data in sorted(entries.items()):z.writestr(name,data)
  live=OUT/'production-core.jar'
  with zipfile.ZipFile(live) as z:
   old={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
  removed=sorted(set(old)-set(entries));missing=[n for n in removed if n!='net/muxigame/core/feature/spawning/HomeSpawningFeature$1.class'];changed=sorted(n for n in set(old)&set(entries) if old[n]!=entries[n]);extra=sorted(set(entries)-set(old))
  head=subprocess.check_output(['git','-c','safe.directory='+ROOT.as_posix(),'-C',str(ROOT),'rev-parse','HEAD'],text=True).strip()
  report={'sourceRoot':str(ROOT),'sourceHead':head,'version':VERSION,'jar':str(target),'sha256':digest(target),'size':target.stat().st_size,'sourceHashes':before,'buildInputs':{str(p):digest(p) for p in libs if p.is_file()},'productionBaselineSha256':digest(live),'missingProductionEntries':missing,'ownedRemovedCompilerEntries':removed,'changedProductionEntries':changed,'addedEntries':extra,'includesOtherOwnersPreexistingDirty':False,'excludedConcurrentAddedEntries':excluded,'packagingMetadataBaseline':'current production 46acc0ad, only candidate version and owned home mixins differ','productionMutation':False,'readyForRelease':False}
  (OUT/'candidate.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({k:report[k] for k in ['jar','sha256','size','sourceHead','missingProductionEntries','readyForRelease']}))
  if missing:raise RuntimeError('Candidate loses production entries; excluded from release')
if __name__=='__main__':main()
