"""Three-owner offline intake contract. Real store/intake/wire; observations are explicit fixtures."""
import hashlib,json,sys
from pathlib import Path
from unittest.mock import patch

def exercise_intake_contract(store,request,claim,temp):
 root=Path(__file__).resolve().parents[2]
 sys.path.insert(0,str(root.parent/'muxi-ysm-compat/tools'))
 import production265_intake as intake
 from intake_bridge import QuarantinedIntakeBridge,IntakeBridgeRejected,IntakeRevisionChanged
 from app.ysm_assets import AssetError
 checks=0
 def check(ok,message):
  nonlocal checks;checks+=1
  if not ok:raise AssertionError(message)
 def denied(action,message):
  nonlocal checks;checks+=1
  try:action()
  except (ValueError,OSError,AssetError):return
  raise AssertionError(message)
 def upload(uid,data,key):
  lease,path=store.begin_upload(uid)
  try:path.write_bytes(data);return store.ingest(uid,'opaque.ysm',path,key,'private')['model']['id']
  finally:store.end_upload(uid,lease,path)
 data=b'Untrusted opaque intake fixture ONLY; no model format or native execution.'
 digest=hashlib.sha256(data).hexdigest();a=upload('10000',data,'intake-upload-a');b=upload('10001',data,'intake-upload-b')
 pending=[r for r in store.pending_validation() if r['sha256']==digest];check(len(pending)==1,'shared content queues only one pending source')
 revision=store.bridge_snapshot()['revision'];worker=QuarantinedIntakeBridge(store,intake,temp/'intake-worker',create=True)
 record=worker.enqueue_pending(digest,revision);check(record['status']=='quarantined_opaque' and not record['replayed'],'registered pending upload copied to isolated quarantine')
 replay=worker.enqueue_pending(digest,revision);check(replay['taskId']==record['taskId'] and replay['replayed'],'same revision/content is idempotent')
 restarted=QuarantinedIntakeBridge(store,intake,temp/'intake-worker');check(restarted.enqueue_pending(digest,revision)['taskId']==record['taskId'],'local worker restart retains binding')
 denied(lambda:worker.enqueue_pending('../bad',revision),'opaque hash/path injection rejected')
 denied(lambda:QuarantinedIntakeBridge(store,intake,store.root/'nested',create=True),'worker cannot live in website storage')
 denied(lambda:QuarantinedIntakeBridge(store,intake,temp/'yes_steve_model'/'intake',create=True),'worker cannot live in YSM scan root')
 with patch.object(intake,'MAX_STORE',len(data)-1):denied(lambda:worker.prepare_observation(digest),'observation input-copy byte budget enforced before preparing root')
 with patch.object(intake,'MAX_TASKS',0):denied(lambda:worker.prepare_observation(digest),'observation input-copy count budget enforced before preparing root')
 plan=worker.prepare_observation(digest);run_root=Path(plan['observationRoot']);check(not plan['executionAllowed'] and not plan['untrustedExecutionAllowed'] and not plan['networkIsolationEnforced'] and not plan['filesystemIsolationEnforced'],'preparation cannot claim launch/sandbox authority')
 check((run_root/'config/yes_steve_model/auth/intake.ysm').read_bytes()==data and plan['limits']==intake.LIMITS,'actual original preparation preserves opaque bytes and resource envelope')
 def observation(root,state='IMPORT_OBSERVED',code=0):
  marker=intake.read_json(root/'intake-job.json')
  return {'schema':'muxi-ysm-import-observation/v1','mode':'import-observation','taskId':marker['taskId'],'token':marker['token'],'jarSha256':marker['jarSha256'],'expectedInputSha256':marker['expectedInputSha256'],'expectedInputSize':marker['expectedInputSize'],'state':state,'observationExitCode':code,'reloadAccepted':state=='IMPORT_OBSERVED','formatValidated':False,'canPromoteVerified':False,'runtimeActivationAllowed':False,'privateDistributionSupported':False,'observation':{'nativeImportSuccess':state=='IMPORT_OBSERVED','catalogIds':['explicit-fixture-catalog-id'],'requiredAuthIds':['explicit-fixture-catalog-id']}}
 def write(root,result):(root/'intake-observation.json').write_text(json.dumps(result),encoding='utf-8')
 result=observation(run_root);write(run_root,result);assessment=worker.assess_observation(digest)
 check(assessment['nativeImportObserved'] and assessment['status']=='quarantined_pending_full_validation' and not assessment['canPromoteVerified'] and not assessment['active'],'successful fixture observation is still not validation/activation')
 denied(lambda:store.promote_verified(assessment),'intake assessment cannot be consumed as ValidationReceipt')
 for uid,model_id in [('10000',a),('10001',b)]:
  model=next(m for m in store.list_models(uid)['models'] if m['id']==model_id)
  check(model['validationStatus']=='pending' and model['syncStatus']=='blocked' and not model['canDownload'] and not model['canSelect'],'website remains pending/blocked for each owner')
 catalog=request('catalog',{'connection':claim,'revision':revision});check(catalog.status_code==200 and all(digest!=m['sha256'] for m in catalog.json()['models']),'real signed wire catalog excludes unknown input after success observation')
 check(request('blob',{'connection':claim,'revision':revision,'modelId':a}).status_code==404,'real signed wire cannot download observed/unverified input')
 snapshot_reply=request('snapshot',{});check(snapshot_reply.status_code==200,'signed snapshot is available for Core fallback contract');snapshot_path=root/'build/ysm-assets-131/intake-core-snapshot.json';snapshot_path.write_text(json.dumps(snapshot_reply.json()),encoding='utf-8')
 with store._db() as db:content_id=db.execute('SELECT public_id FROM ysm_contents WHERE sha256=?',(digest,)).fetchone()[0]
 check(request('staged',{'contentId':content_id,'sha256':digest,'revision':revision,'generation':'g_'+'1'*32,'status':'staged'}).status_code==409,'real signed staging refuses pending content')
 check(request('staged',{'contentId':content_id,'sha256':digest,'revision':revision,'generation':'g_'+'1'*32,'status':'active'}).status_code==400,'wire cannot turn an import observation into active receipt')
 for flag in ('formatValidated','canPromoteVerified','runtimeActivationAllowed','privateDistributionSupported'):
  fake=dict(result);fake[flag]=True;write(run_root,fake);denied(lambda:worker.assess_observation(digest),'forged approval flags rejected')
 write(run_root,dict(result,state='UNKNOWN',observationExitCode=99));denied(lambda:worker.assess_observation(digest),'unknown native observation result rejected')
 write(run_root,dict(result,expectedInputSha256='0'*64));denied(lambda:worker.assess_observation(digest),'wrong input observation rejected')
 newer=worker.prepare_observation(digest);current_root=Path(newer['observationRoot']);write(current_root,result);denied(lambda:worker.assess_observation(digest),'late old job token cannot bind to re-prepared current job')
 for state,code in [('RELOAD_NOT_ACCEPTED',20),('START_FAILED',21),('IMPORT_TIMEOUT',22),('ISOLATION_OR_CALLBACK_FAILED',23),('NATIVE_IMPORT_FAILED',24),('SERVER_STOPPED_BEFORE_OBSERVATION',25),('OBSERVATION_OUTPUT_LIMIT',26)]:
  write(current_root,observation(current_root,state,code));failed=worker.assess_observation(digest)
  check(not failed['nativeImportObserved'] and not failed['canPromoteVerified'] and failed['status']=='quarantined_pending_full_validation','each original failure state remains isolated and cannot assert corrupt/verified model')
 copied=worker.quarantine_root/record['taskId']/'input.bin';copied.write_bytes(b'x'*len(data));denied(lambda:worker.assess_observation(digest),'changed quarantine bytes rejected');copied.write_bytes(data)
 source=store.root/pending[0]['storageKey'];source.write_bytes(b'x'*len(data));denied(lambda:worker.assess_observation(digest),'changed registered website bytes rejected');source.write_bytes(data)
 lock=worker.root/'.worker.lock';lock.write_text('owned-test-lock',encoding='utf-8')
 try:denied(lambda:worker.enqueue_pending(digest,revision),'concurrent worker refuses occupied lock');check(lock.read_text(encoding='utf-8')=='owned-test-lock','busy worker never removes another lock')
 finally:lock.unlink()
 race_data=b'Explicit opaque race fixture, no native format.';race_sha=hashlib.sha256(race_data).hexdigest();race_id=upload('10000',race_data,'intake-race-upload');race_revision=store.bridge_snapshot()['revision'];original=intake.quarantine
 def race(*args,**kwargs):
  copied=original(*args,**kwargs);store.mutate('10000',race_id,'delete','intake-race-delete');return copied
 with patch.object(intake,'quarantine',side_effect=race):denied(lambda:worker.enqueue_pending(race_sha,race_revision),'real website deletion during copy fences intake binding')
 check(not (worker.ledger/(race_sha+'.json')).exists(),'revocation race never publishes a current worker binding')
 denied(lambda:worker.assess_observation(digest),'old worker revision rejected after website permission change')
 check(not any(m['canSelect'] for m in store.list_models('10000')['models']),'all actual website models remain unselectable')
 return {'passed':True,'checks':checks,'originalIntakeModule':str(Path(intake.__file__).resolve()),'originalIntakeSha256':hashlib.sha256(Path(intake.__file__).read_bytes()).hexdigest(),'observations':'synthetic result fixtures, not native executions','formatValidated':False,'runtimeActivated':False,'unknownInputNativeExecuted':False,'productionCredentialsReadOrCreated':False}
