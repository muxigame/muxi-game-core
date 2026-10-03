"""Isolated Java helper/wrapper checks; never launches Minecraft."""
import argparse,json,os,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--java-home',type=Path,required=True);p.add_argument('--dependencies',type=Path,required=True);a=p.parse_args()
with tempfile.TemporaryDirectory(prefix='cpu-',dir=ROOT/'build') as temp:
 root=Path(temp);classes=root/'classes';classes.mkdir()
 sources=[ROOT/'src/net/muxigame/shadernative'/n for n in ['FilesQA.java','JoinObservation.java','mixin/JoinReload.java']]+list((ROOT/'tests').glob('*.java'))
 result=subprocess.run([str(a.java_home/'bin/javac.exe'),'-proc:none','-encoding','UTF-8','-cp',str(a.dependencies),'-d',str(classes),*map(str,sources)],capture_output=True,text=True)
 rows=[dict(step='javac',exit=result.returncode,output=result.stdout+result.stderr)]
 if result.returncode:raise RuntimeError(result.stderr)
 cp=str(classes)+os.pathsep+str(ROOT/'build/classes')+os.pathsep+str(a.dependencies)
 jobs=[['JoinObservationTest'],['ReloadObservationTest'],['ShaderDiagnosticTest']]+[['ProfileTest',str(root/('profile-'+case)),case] for case in ['default','valid','wrong-run','wrong-player','unknown-field']]
 for job in jobs:
  result=subprocess.run([str(a.java_home/'bin/java.exe'),'-cp',cp,*job],capture_output=True,text=True);rows.append(dict(step=job[0]+(' '+job[-1] if job[0]=='ProfileTest' else ''),exit=result.returncode,output=result.stdout+result.stderr))
 (ROOT/'build/cpu-checks.json').write_text(json.dumps(dict(minecraftLaunched=False,checks=rows),indent=2),encoding='utf-8')
 for row in rows:print(row['step'],row['exit'],row['output'].strip())
 if any(row['exit'] for row in rows):raise SystemExit(1)
