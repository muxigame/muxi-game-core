"""Upload authorized task23 test inputs only into the newly created dedicated root."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT=Path(__file__).resolve().parent
remote='administrator@192.168.110.131:C:/Users/ranzh/Documents/Codex/schematic-task23-20261002/'
flags=['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=8']
files=[('schematic-client-overlay-task23-qa.zip','input/schematic-client-overlay-task23-qa.zip'),
       ('runtime-inputs-task23.zip','input/runtime-inputs-task23.zip'),
       ('runtime-build/build-receipt.json','input/qa-build-receipt.json'),
       ('runtime-build/muxi-schematic-qa-0.1.0-task23-test-only.jar','input/muxi-schematic-qa-0.1.0-task23-test-only.jar'),
       ('install_remote_131.py','install_remote_131.py'),('start_visible_131.py','start_visible_131.py'),('prepare_run_131.py','prepare_run_131.py'),
       ('start-visible-task23.ps1','start-visible-task23.ps1'),('ENTRY-COORDINATION-REQUEST.txt','ENTRY-COORDINATION-REQUEST.txt'),
       ('register-independent-entry.review.ps1','register-independent-entry.review.ps1'),('EXISTING-ENTRY-PARAMETERS.txt','EXISTING-ENTRY-PARAMETERS.txt')]
receipt=[]
for local,destination in files:
    p=ROOT/local
    result=subprocess.run(['scp',*flags,str(p),remote+destination],capture_output=True,timeout=45)
    if result.returncode:print(result.stderr.decode(errors='replace')[:1200]);raise SystemExit(result.returncode)
    receipt.append({'local':local,'destination':destination,'size':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
    print('Uploaded',destination,flush=True)
(ROOT/'131-upload-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
