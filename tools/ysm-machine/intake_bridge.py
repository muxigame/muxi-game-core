"""Trusted LOCAL pending-upload worker; opaque intake only, never an HTTP or native executor.

Source hashes/paths come only from YsmAssetStore.pending_validation(). The caller
injects the original production265_intake module; no source copy or implicit key.
"""
from __future__ import annotations
from contextlib import contextmanager
import hashlib,json,os,re,secrets,tempfile
from pathlib import Path
from app.ysm_assets import YsmAssetStore

SHA=re.compile(r'[a-f0-9]{64}')
FLAGS={'formatValidated':False,'canPromoteVerified':False,'runtimeActivationAllowed':False,
       'privateDistributionSupported':False,'active':False,'selectEnabled':False}

class IntakeBridgeRejected(ValueError):pass
class IntakeRevisionChanged(IntakeBridgeRejected):pass

class QuarantinedIntakeBridge:
 """Explicit local worker root, outside website storage and any live model scan tree."""
 def __init__(self,store,intake,worker_root,*,create=False):
  if not isinstance(store,YsmAssetStore):raise TypeError('A trusted local website store is required')
  self.store,self.intake=store,intake
  self.root=Path(os.path.abspath(worker_root));intake.reject_model_scan_destination(self.root)
  if self.root==store.root or store.root in self.root.parents or self.root in store.root.parents:
   raise IntakeBridgeRejected('Worker and website storage must be separate')
  if create:
   intake.plain_path(self.root.parent,directory=True);self.root.mkdir()
   (self.root/'ledger').mkdir();(self.root/'observations').mkdir()
   intake.initialize_store(self.root/'quarantine')
   intake.write_new_json(self.root/'worker.json',{'schema':'muxi-ysm-intake-bridge/v1'})
  intake.plain_path(self.root,directory=True)
  if intake.read_json(self.root/'worker.json')!={'schema':'muxi-ysm-intake-bridge/v1'}:raise IntakeBridgeRejected('Unknown worker root')
  self.ledger=intake.plain_path(self.root/'ledger',directory=True)
  intake.plain_path(self.root/'observations',directory=True)
  self.quarantine_root=intake.plain_path(self.root/'quarantine',directory=True)
 @contextmanager
 def _lock(self):
  self.intake.plain_path(self.root,directory=True)
  path=self.root/'.worker.lock';fd=os.open(path,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
  try:yield
  finally:os.close(fd);path.unlink()
 def _revision(self,expected):
  if type(expected) is not int or expected<0:raise IntakeBridgeRejected('Invalid trusted revision')
  if self.store.bridge_snapshot()['revision']!=expected:raise IntakeRevisionChanged('Website permission revision changed')
 def _pending(self,digest):
  if not isinstance(digest,str) or not SHA.fullmatch(digest):raise IntakeBridgeRejected('Invalid content identity')
  rows=[r for r in self.store.pending_validation() if r.get('sha256')==digest]
  if len(rows)!=1:raise IntakeBridgeRejected('Content is no longer pending validation')
  row=rows[0]
  if set(row)!={'sha256','size','storageKey'} or type(row['size']) is not int or not 1<=row['size']<=self.intake.MAX_INPUT or row['storageKey']!='quarantine/'+digest+'.bin':raise IntakeBridgeRejected('Invalid local pending metadata')
  return row
 def _source(self,row):
  source=self.intake.plain_path(self.store.root/row['storageKey'])
  if source.parent!=self.store.quarantine or source.stat().st_size!=row['size']:raise IntakeBridgeRejected('Pending source differs from registered storage')
  return source
 def _intact(self,path,digest,size):
  path=self.intake.plain_path(path);h=hashlib.sha256();count=0
  with path.open('rb') as incoming:
   before=os.fstat(incoming.fileno())
   while chunk:=incoming.read(min(self.intake.CHUNK,size-count+1)):
    count+=len(chunk)
    if count>size:raise IntakeBridgeRejected('Intake bytes grew')
    h.update(chunk)
   after=os.fstat(incoming.fileno())
  if count!=size or h.hexdigest()!=digest or (before.st_size,before.st_mtime_ns,before.st_ino)!=(after.st_size,after.st_mtime_ns,after.st_ino):raise IntakeBridgeRejected('Intake integrity changed')
 def _write(self,path,value):
  fd,name=tempfile.mkstemp(prefix='.job-',dir=self.ledger);temporary=Path(name)
  try:
   with os.fdopen(fd,'w',encoding='utf-8',newline='\n') as outgoing:
    json.dump(value,outgoing);outgoing.flush();os.fsync(outgoing.fileno())
   os.replace(temporary,path)
  finally:temporary.unlink(missing_ok=True)
 def _load(self,digest):
  if not isinstance(digest,str) or not SHA.fullmatch(digest):raise IntakeBridgeRejected('Invalid content identity')
  record=self.intake.read_json(self.ledger/(digest+'.json'))
  if record.get('schema')!='muxi-ysm-intake-binding/v1' or record.get('sha256')!=digest or not isinstance(record.get('taskId'),str) or not self.intake.TASK.fullmatch(record['taskId']) or any(record.get(k) is not v for k,v in FLAGS.items()):raise IntakeBridgeRejected('Invalid local intake binding')
  if type(record.get('size')) is not int or not 1<=record['size']<=self.intake.MAX_INPUT:raise IntakeBridgeRejected('Invalid local input size')
  row=self._pending(digest)
  if row['size']!=record['size']:raise IntakeBridgeRejected('Pending content changed')
  self._revision(record.get('revision'))
  copied=self.quarantine_root/record['taskId']/'input.bin';self._intact(copied,digest,row['size'])
  self._intact(self._source(row),digest,row['size'])
  return record
 def enqueue_pending(self,digest,expected_revision):
  """Internal worker selects registered pending content; no caller path, UID or receipt is trusted."""
  with self._lock():
   self._revision(expected_revision);row=self._pending(digest);source=self._source(row)
   ledger=self.ledger/(digest+'.json')
   if ledger.exists():
    old=self.intake.read_json(ledger)
    if old.get('revision')==expected_revision:
     record=self._load(digest);return dict(record,replayed=True)
   intake=self.intake.quarantine(source,self.quarantine_root,row['sha256'],row['size'])
   self._revision(expected_revision)
   if self._pending(digest)!=row:raise IntakeBridgeRejected('Pending metadata changed while copying')
   if intake.get('status')!='quarantined_opaque' or intake.get('sha256')!=digest or intake.get('size')!=row['size'] or not self.intake.TASK.fullmatch(intake.get('taskId','')) or any(intake.get(k) is not False for k in ('formatValidated','canPromoteVerified','runtimeActivationAllowed')):raise IntakeBridgeRejected('Intake did not produce a closed quarantine record')
   record={'schema':'muxi-ysm-intake-binding/v1','taskId':intake['taskId'],'sha256':digest,
           'size':row['size'],'revision':expected_revision,'status':'quarantined_opaque',
           'observationReceived':False,'nativeImportObserved':False,'observationRoot':None,**FLAGS}
   self._write(ledger,record);return dict(record,replayed=False)
 def _observation_capacity(self,size):
  count,total=0,0
  for folder in (self.root/'observations').iterdir():
   if not re.fullmatch(r'o_[a-f0-9]{32}',folder.name):raise IntakeBridgeRejected('Unexpected observation root')
   self.intake.plain_path(folder,directory=True);count+=1
   source=folder/'config/yes_steve_model/auth/intake.ysm'
   if source.exists():
    self.intake.plain_path(source);stored=source.stat().st_size
    if stored>self.intake.MAX_INPUT:raise IntakeBridgeRejected('Oversized observation input')
    total+=stored
  if count>=self.intake.MAX_TASKS or total+size>self.intake.MAX_STORE:raise IntakeBridgeRejected('Observation copy capacity reached')
 def prepare_observation(self,digest):
  """Prepare a new isolated COPY only. No process, native importer or automatic launch exists here."""
  with self._lock():
   record=self._load(digest);self._observation_capacity(record['size']);relative='observations/o_'+secrets.token_hex(16);run_root=self.root/relative
   marker=self.intake.prepare_observation(self.quarantine_root,record['taskId'],run_root)
   self._revision(record['revision']);self._pending(digest)
   if marker.get('taskId')!=record['taskId'] or marker.get('expectedInputSha256')!=digest or any(marker.get(k) is not False for k in ('formatValidated','canPromoteVerified','runtimeActivationAllowed')):raise IntakeBridgeRejected('Observation preparation changed intake identity')
   record.update(observationRoot=relative,observationReceived=False,nativeImportObserved=False,status='quarantined_opaque')
   self._write(self.ledger/(digest+'.json'),record)
   return {'taskId':record['taskId'],'observationRoot':str(run_root),'limits':dict(marker['limits']),
           'executionAllowed':False,'untrustedExecutionAllowed':False,'networkIsolationEnforced':False,
           'filesystemIsolationEnforced':False,'diskHardLimitEnforced':False,'gpuMemoryLimitEnforced':False,**FLAGS}
 def assess_observation(self,digest):
  with self._lock():
   record=self._load(digest);relative=record.get('observationRoot')
   if not isinstance(relative,str) or not re.fullmatch(r'observations/o_[a-f0-9]{32}',relative):raise IntakeBridgeRejected('No current prepared observation')
   run_root=self.intake.plain_path(self.root/relative,directory=True)
   assessment=self.intake.assess_observation(self.quarantine_root,record['taskId'],run_root)
   self._revision(record['revision']);self._pending(digest)
   if assessment.get('taskId')!=record['taskId'] or assessment.get('sha256')!=digest or assessment.get('size')!=record['size'] or assessment.get('status')!='quarantined_pending_full_validation' or any(assessment.get(k) is not False for k in ('formatValidated','canPromoteVerified','runtimeActivationAllowed')):raise IntakeBridgeRejected('Observation cannot assert full validation')
   record.update(status=assessment['status'],observationReceived=True,nativeImportObserved=assessment['nativeImportObserved'],observationState=assessment['observationState'])
   self._write(self.ledger/(digest+'.json'),record)
   # This record is not a ValidationReceipt. Website verification, sync and selection remain untouched.
   return dict(record)
