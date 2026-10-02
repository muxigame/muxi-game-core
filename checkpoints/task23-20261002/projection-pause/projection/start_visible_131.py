"""Session-2 entry for one task23 client; preserve every run and use normal game shutdown."""
from pathlib import Path
import ctypes
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import uuid

ROOT=Path(r'C:\Users\ranzh\Documents\Codex\schematic-task23-20261002')
JAVA=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1\bin\java.exe')
def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    if sys.stdout is None:sys.stdout=(ROOT/'receipts/python-entry.log').open('a',encoding='utf-8',buffering=1)
    if sys.stderr is None:sys.stderr=sys.stdout
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    session=ctypes.c_ulong()
    if not ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(),ctypes.byref(session)):raise SystemExit('No session ID')
    if os.environ.get('COMPUTERNAME','').upper()!='JBC_FCRL' or session.value!=2:raise SystemExit('Use coordinated 131 Session 2 entry; SSH Session 0 cannot run this client')
    active=ROOT/'receipts/active-run.json'
    if active.exists():
        previous=json.loads(active.read_text(encoding='utf-8'))
        if previous.get('processStillRunning',False):raise SystemExit('Own prior run still needs diagnosis/normal close; no new client launched')
    expected=json.loads((ROOT/'input/qa-build-receipt.json').read_text(encoding='utf-8'))
    qa=ROOT/'client/mods'/expected['jar']
    if digest(qa)!=expected['sha256']:raise SystemExit('Task23 QA JAR hash differs')
    prepared_file=ROOT/'receipts/prepared-run.json'
    if not prepared_file.exists():raise SystemExit('Prepare a new independent run before taking the focus slot')
    prepared=json.loads(prepared_file.read_text(encoding='utf-8'))
    lab=Path(prepared['lab']).resolve()
    if (ROOT/'runs').resolve() not in lab.parents or prepared.get('consumed') or prepared['qaHash']!=expected['sha256']:raise SystemExit('Prepared run is invalid or already consumed')
    if (lab/'boot.log').exists() or (lab/'saves').exists():raise SystemExit('Fresh unused prepared run required')
    if digest(lab/'mods'/expected['jar'])!=expected['sha256']:raise SystemExit('Prepared QA JAR hash mismatch')
    prepared['consumed']=True;write(prepared_file,prepared)
    provenance={'lab':str(lab),'startedUtc':datetime.datetime.utcnow().isoformat()+'Z','session':session.value,'computer':os.environ['COMPUTERNAME'],'user':os.environ.get('USERNAME',''),'testWorld':'schematic-task23-local-only','productionOrTask14Mutation':False,'qaHash':expected['sha256'],'processStillRunning':True,'globalKeyboardMouseAutomationUsed':False,'normalShutdownOnly':True}
    write(lab/'run-inputs.json',provenance)
    environment=dict(os.environ);environment.pop('MUXI_TERMINAL_GAME_CREDENTIAL',None)
    with (lab/'boot.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen([str(JAVA),'-Dmuxi.schematicQA.dir='+str(lab),'@'+str(lab/'launch.args')],cwd=lab,env=environment,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        provenance['pid']=process.pid;write(active,provenance);write(lab/'run-inputs.json',provenance)
        print(json.dumps({'started':True,'pid':process.pid,'lab':str(lab),'session':session.value}),flush=True)
        deadline=time.monotonic()+1500
        while process.poll() is None:
            if time.monotonic()>deadline:
                provenance['timedOut']=True;provenance['normalCloseRequired']=True;write(active,provenance);write(lab/'exit.json',provenance)
                print('QA deadline: evidence preserved, this one client needs normal close; no terminate/kill performed.',flush=True);return 124
            time.sleep(1)
    provenance['processStillRunning']=False;provenance['exitCode']=process.returncode;provenance['finishedUtc']=datetime.datetime.utcnow().isoformat()+'Z'
    result_file=lab/'runtime-result.json'
    result=json.loads(result_file.read_text(encoding='utf-8')) if result_file.exists() else {'completed':False,'error':'No in-game result'}
    provenance['driverCompleted']=result.get('completed',False);provenance['normalStopRequested']=result.get('normalStopRequested',False)
    provenance['cleanExit']=process.returncode==0 and provenance['driverCompleted'] and provenance['normalStopRequested']
    write(lab/'exit.json',provenance);write(active,provenance);write(ROOT/'receipts/latest-result.json',{'run':provenance,'gameResult':result})
    print(json.dumps(provenance,ensure_ascii=False),flush=True)
    return 0 if provenance['cleanExit'] else 1
if __name__=='__main__':raise SystemExit(main())
