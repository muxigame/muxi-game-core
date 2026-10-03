"""Compile/package this business QA mod only. Does not create or launch MC instances."""
import argparse,hashlib,json,os,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--dependencies',type=Path,action='append',required=True,help='MC/NeoForge/MixinExtras compile jar(s); may be repeated');p.add_argument('--mods-dir',type=Path,required=True,help='Read-only shader profile jars needed by typed Sodium hooks');p.add_argument('--qa-mode',choices=['matrix','first-spawn'],default='matrix');p.add_argument('--expected-player');a=p.parse_args()
 if a.qa_mode=='first-spawn' and (not a.expected_player or not __import__('re').fullmatch(r'[A-Za-z0-9_]{1,16}',a.expected_player)):p.error('--qa-mode first-spawn requires --expected-player ASCII name')
 profile={'mode':a.qa_mode}
 if a.expected_player:profile['expectedPlayer']=a.expected_player
 out=ROOT/'build';classes=out/'classes';classes.mkdir(parents=True,exist_ok=True)
 cp=[x.resolve(strict=True) for x in a.dependencies]+sorted(a.mods_dir.resolve(strict=True).glob('*.jar'))
 # Sodium distributes typed implementation classes in an embedded jar.
 nested=out/'nested';nested.mkdir(exist_ok=True)
 for jar in list(cp):
  if 'sodium' not in jar.name.lower():continue
  with zipfile.ZipFile(jar) as archive:
   for name in archive.namelist():
    if name.endswith('.jar'):
     data=archive.read(name);path=nested/(hashlib.sha256(data).hexdigest()+'.jar');path.write_bytes(data);cp.append(path)
 sources=sorted((ROOT/'src').rglob('*.java'));args=['-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*map(str,sources)]
 argfile=out/'javac.args';argfile.write_text('\n'.join('"'+x.replace('\\','/')+'"' for x in args),encoding='utf-8')
 result=subprocess.run([str(a.java_home/'bin/javac.exe'),'@'+str(argfile)],capture_output=True,text=True);(out/'compile.log').write_text(result.stdout+result.stderr,encoding='utf-8')
 if result.returncode: print(result.stderr);raise SystemExit(result.returncode)
 jar=out/'muxi-shader-native-qa.jar'
 with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('shader-native-profile.json',json.dumps(profile,sort_keys=True))
  for base in [classes,ROOT/'resources']:
   for f in sorted(base.rglob('*')):
    if f.is_file():z.write(f,f.relative_to(base).as_posix())
 report={'profile':profile,'jar':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'compileExitCode':result.returncode,'sources':{f.relative_to(ROOT).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in sources},'minecraftLaunched':False}
 (out/'build-result.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps({'jar':str(jar),'sha256':report['sha256'],'sourceCount':len(sources)}))
if __name__=='__main__':main()
