"""Package saved commits and sanitized pause evidence; no push/build/test/process control."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import re
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'task23-checkpoint-delivery'
OUT.mkdir(exist_ok=True)
repos=[('portal-exact-core','checkpoint/task23-portal-exact-20261002'),('transfer-probe','diagnosis/transfer-latency'),('task23-checkpoint','checkpoint/task23-projection-pause-20261002')]
def git(repo,*args):
 p=subprocess.run(['git','-C',str(ROOT/repo),*args],capture_output=True,check=True)
 return p.stdout.decode('utf-8',errors='replace').strip()
entries=[]
for repo,branch in repos:
 commit=git(repo,'rev-parse',branch)
 bundle=OUT/(repo+'.bundle')
 git(repo,'bundle','create',str(bundle),branch)
 verification=git(repo,'bundle','verify',str(bundle))
 entries.append({'repo':str(ROOT/repo),'sourceName':repo,'branch':branch,'commit':commit,'remoteNames':git(repo,'remote').splitlines(),'visibility':'local-only; no remote configured; no publication performed','workingTree':git(repo,'status','--porcelain'),'bundle':bundle.name,'bundleBytes':bundle.stat().st_size,'bundleSha256':hashlib.sha256(bundle.read_bytes()).hexdigest(),'bundleVerified':True})

raw=ROOT/'schematic-client-candidate/runtime-evidence-retry'
archive=OUT/'projection-paused-evidence-sanitized.zip'
excluded_lines=0
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
 for file in sorted(raw.iterdir()):
  if not file.is_file() or file.suffix not in ('.log','.json','.txt'):continue
  text=file.read_text(encoding='utf-8-sig',errors='replace')
  if file.suffix=='.json':
   # Only our own launcher identities/status were selected; omit any sensitive key if present.
   data=json.loads(text)
   def clean(value):
    if isinstance(value,dict):return {key:clean(item) for key,item in value.items() if not re.search(r'(?i)password|token|secret|credential|authorization|cookie|private.?key',key)}
    if isinstance(value,list):return [clean(item) for item in value]
    return value
   text=json.dumps(clean(data),indent=2)+'\n'
  else:
   lines=[]
   for line in text.splitlines():
    if re.search(r'(?i)password|passwd|token|secret|credential|authorization|cookie|private.?key|(?:AKIA|ASIA)[A-Z0-9]{16}|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}',line):
     lines.append('[REDACTED: possible credential/token line]');excluded_lines+=1
    else:lines.append(line)
   text='\n'.join(lines)+'\n'
  z.writestr(file.name,text)
 z.write(ROOT/'task23-checkpoint/PAUSED-CHECKPOINT.json','PAUSED-CHECKPOINT.json')

manifest={
 'savedUtc':datetime.now(timezone.utc).isoformat(),'repositories':entries,'pushPerformed':False,'solePushOwner':'Parent-appointed publishing owner; do not push main/dev/tags or force',
 'pausedTest':{'pid':4204,'exitCode':0,'finishedUtc':'2026-10-02T05:55:57.777219Z','driverCompleted':False,'runtimeVerified':False,'slotReleasedAccordingToLauncherReceipt':True},
 'evidenceArchive':{'file':archive.name,'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'credentialLinesRedacted':excluded_lines,'rawOriginalsPreserved':str(raw)},
 'excludedPreservedLocally':['Generated jars/classes and official dependency binaries/source archives','Original raw logs/screenshots and task QA receipts','Production/player/backup world data: not included in Git or delivery archive'],
 'resume':'Await authorization to continue on131. Source checkpoint is not a release or test pass. Do not repeat kill, rebuild, test, launch or deploy during pause.',
}
(OUT/'delivery-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
final=ROOT/'task23-source-checkpoint-20261002.zip'
with zipfile.ZipFile(final,'w',zipfile.ZIP_DEFLATED) as z:
 for file in sorted(OUT.iterdir()):
  if file.is_file():z.write(file,file.name)
receipt={'deliveryZip':str(final),'bytes':final.stat().st_size,'sha256':hashlib.sha256(final.read_bytes()).hexdigest(),'repositories':[{k:v for k,v in item.items() if k in ('sourceName','branch','commit','remoteNames','visibility','workingTree','bundleVerified')} for item in entries],'pushPerformed':False,'sanitizedEvidenceBytes':archive.stat().st_size}
(ROOT/'task23-source-checkpoint-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(receipt,ensure_ascii=False))
