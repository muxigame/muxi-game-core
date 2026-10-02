from pathlib import Path
import subprocess, json, hashlib
root=Path(__file__).resolve().parent
out=root/'runtime-evidence-retry';out.mkdir(exist_ok=True)
remote='administrator@192.168.110.131:C:/Users/ranzh/Documents/Codex/schematic-task23-20261002/runs/projection-20261002-053523-f39cfd49/'
flags=['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=8']
receipt=[]
for pattern in ('*.png','runtime-*.json','run-inputs.json','exit.json','logs/latest.log','boot.log'):
 r=subprocess.run(['scp',*flags,remote+pattern,str(out)],capture_output=True,timeout=30)
 receipt.append({'pattern':pattern,'returncode':r.returncode,'stderr':r.stderr.decode(errors='replace')[:600]})
for p in sorted(out.iterdir()):
 if p.is_file():receipt.append({'file':p.name,'size':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
(root/'retry-evidence-download.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
print(json.dumps(receipt))
