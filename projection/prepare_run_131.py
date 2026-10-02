"""Prepare a fresh task23 run from Session 0 without launching a desktop client."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import shutil
import uuid

ROOT=Path(r'C:\Users\ranzh\Documents\Codex\schematic-task23-20261002')
if os.environ.get('COMPUTERNAME','').upper()!='JBC_FCRL':raise SystemExit('131 only')
receipt=json.loads((ROOT/'input/qa-build-receipt.json').read_text(encoding='utf-8'))
source=ROOT/'client/mods'/receipt['jar']
if hashlib.sha256(source.read_bytes()).hexdigest()!=receipt['sha256']:raise SystemExit('QA driver hash mismatch')
runs=ROOT/'runs';runs.mkdir(exist_ok=True)
lab=runs/('projection-'+datetime.datetime.utcnow().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8])
shutil.copytree(ROOT/'client',lab)
text=(lab/'launch.args').read_text(encoding='utf-8').replace(str(ROOT/'client').replace('\\','/'),str(lab).replace('\\','/'))
(lab/'launch.args').write_text(text,encoding='utf-8')
data={'lab':str(lab),'qaHash':receipt['sha256'],'prepared':True,'consumed':False,'runtimeStarted':False,'preparedUtc':datetime.datetime.utcnow().isoformat()+'Z'}
(ROOT/'receipts/prepared-run.json').write_text(json.dumps(data,indent=2),encoding='utf-8')
print(json.dumps(data))
