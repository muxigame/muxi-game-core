"""Compile/run actual Sable container readers without starting Minecraft or any server."""
from pathlib import Path
import json,subprocess,sys
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'build/world-role-migration-20261014';JAVA=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1\bin');SABLE=ROOT.parent/'bmc5server/mods/sable-neoforge-1.21.1-2.0.5.jar'
def argument_file(name,args):
    p=OUT/name;p.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in args),encoding='utf-8');return p

def checked(command,evidence):
    p=subprocess.run(command,capture_output=True);output=(p.stdout+p.stderr).decode('utf-8',errors='replace');evidence.write_text(output,encoding='utf-8')
    if p.returncode:raise RuntimeError('Standalone Sable codec check failed: '+str(evidence))
    return output

def prepare():
    cp=(OUT/'classpath.txt').read_text()+';'+str(SABLE)+';'+str(OUT/'sable-companion-pinned.jar')
    args=['--release','21','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(OUT/'sable-codec-classes'),str(ROOT/'tests/world-role-migration/SableStorageCodecQA.java')]
    file=argument_file('sable-codec-compile.args',args);checked([str(JAVA/'javac.exe'),'@'+str(file)],OUT/'sable-codec-compile-result.txt');return cp

def run(mode,world,label):
    cp=(OUT/'classpath.txt').read_text()+';'+str(SABLE)+';'+str(OUT/'sable-companion-pinned.jar')+';'+str(OUT/'sable-codec-classes')
    file=argument_file('sable-codec-'+label+'.args',['-Xmx256m','-XX:ActiveProcessorCount=1','-cp',cp,'SableStorageCodecQA',mode,str(world)])
    return checked([str(JAVA/'java.exe'),'@'+str(file)],OUT/('sable-codec-'+label+'-result.txt'))
if __name__=='__main__':prepare();print('Standalone Sable codec QA compiled; no Minecraft started')
