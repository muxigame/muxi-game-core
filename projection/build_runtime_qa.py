"""Compile the private observation/fixture mod offline on 008; never run Minecraft here."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parent
TASK14=ROOT.parent.parent/'task-14/unified-review/unified-app-qa-v2/desktop'
source=ROOT/'runtime-src/net/muxigame/schematicqa/mixin'
source.mkdir(parents=True,exist_ok=True)
for name in ('HardwareWmiTimeoutQAMixin','OfflineMcefMixin'):
    text=(TASK14/('java/net/muxigame/terminal/qa/mixin/'+name+'.java')).read_text(encoding='utf-8')
    text=text.replace('package net.muxigame.terminal.qa.mixin;','package net.muxigame.schematicqa.mixin;')
    (source/(name+'.java')).write_text(text,encoding='utf-8')
client=Path(r'C:\Users\Administrator\WorkSpace\muxigame\_client_test\game')
server=Path(r'C:\Users\Administrator\WorkSpace\muxigame\bmc5server')
patched=client/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar'
mapped=client/'libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar'
libs=[patched,mapped]+sorted(p for p in server.rglob('*.jar') if '/libraries/' in p.as_posix() and '/net/minecraft/' not in p.as_posix() and not p.name.endswith('-server.jar'))
libs+=sorted((client/'libraries').rglob('*.jar'))+list((ROOT/'client/mods').glob('*.jar'))+list((client/'mods').glob('*mcef*.jar'))
libs=list(dict.fromkeys(libs))
build=ROOT/'runtime-build';classes=build/'classes';classes.mkdir(parents=True,exist_ok=True)
args=['-J-Xmx512m','--release','21','-encoding','UTF-8','-proc:none','-classpath',os.pathsep.join(map(str,libs)),'-d',str(classes),*map(str,(ROOT/'runtime-src').rglob('*.java'))]
# JVM -J options are passed before the argfile rather than inside it.
args.remove('-J-Xmx512m')
argfile=build/'compile.args';argfile.write_text('\n'.join('"'+x.replace('\\','/').replace('"','\\"')+'"' for x in args),encoding='utf-8')
result=subprocess.run([r'C:\Program Files\Java\jdk-24\bin\javac.exe','-J-Xmx512m','@'+str(argfile)],capture_output=True,timeout=60)
(build/'compile.log').write_bytes(result.stdout+b'\n'+result.stderr)
if result.returncode:
    print(result.stderr.decode('utf-8',errors='replace')[:12000]);raise SystemExit(result.returncode)
jar=build/'muxi-schematic-qa-0.1.0-task23-test-only.jar'
definition={'required':True,'minVersion':'0.8','package':'net.muxigame.schematicqa.mixin','compatibilityLevel':'JAVA_21','client':['HardwareWmiTimeoutQAMixin','OfflineMcefMixin','RenderEvidenceMixin'],'injectors':{'defaultRequire':1}}
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_schematic_qa"\nversion="0.1.0-task23-test-only"\ndisplayName="Task23 isolated schematic QA — remove before release"\n[[mixins]]\nconfig="schematic-task23-qa.mixins.json"\n')
    z.writestr('schematic-task23-qa.mixins.json',json.dumps(definition))
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
receipt={'jar':jar.name,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'sourceFiles':len(list((ROOT/'runtime-src').rglob('*.java'))),'classFiles':len(list(classes.rglob('*.class'))),'javaRelease':21,'minecraftLaunchedOn008':False,'productionArtifact':False,'scope':'Task23 independent client only; actual rendering observation and local fixture, no global input or other client control.'}
(build/'build-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps(receipt))
