from pathlib import Path
import subprocess, hashlib, json
root=Path(__file__).resolve().parent
remote='administrator@192.168.110.131:C:/Users/ranzh/Documents/Codex/schematic-task23-20261002/'
flags=['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=8']
files=[('normal-close-agent/task23-normal-close-agent.jar','input/task23-normal-close-agent.jar'),('normal-close-agent/Task23Attach.class','input/Task23Attach.class'),('runtime-build/build-receipt.json','input/qa-build-receipt.json'),('runtime-build/muxi-schematic-qa-0.1.0-task23-test-only.jar','input/muxi-schematic-qa-0.1.0-task23-test-only.jar')]
receipts=[]
for source,dest in files:
 p=root/source
 result=subprocess.run(['scp',*flags,str(p),remote+dest],capture_output=True,timeout=30)
 if result.returncode:print(result.stderr.decode(errors='replace'));raise SystemExit(result.returncode)
 receipts.append({'source':source,'destination':dest,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
(root/'131-retry-inputs-upload.json').write_text(json.dumps(receipts,indent=2),encoding='utf-8')
print(json.dumps(receipts))
