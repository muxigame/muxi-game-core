from pathlib import Path
import sys,json,time,traceback
import argparse
parser=argparse.ArgumentParser(description="QA-only real network + native callback input probe; never physical OS input")
parser.add_argument('--instance-root',type=Path,required=True)
parser.add_argument('--debug-project-root',type=Path,default=Path(__file__).resolve().parents[2]/'better-mc-remake')
parser.add_argument('--provenance',type=Path)
args=parser.parse_args();sys.path.insert(0,str(args.debug_project_root.resolve(strict=True)/'scripts'))
import local_mc_debug as debug,local_mc_runtime as rt
m=json.loads(args.provenance.read_text()) if args.provenance else {};root=args.instance_root.resolve(strict=True);evidence=[];receipts=[];seq=0
def wait(label,predicate,timeout=90):
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  result=predicate()
  if result:return result
  if (root/'run-result.json').exists():raise RuntimeError('Runner exited before '+label)
  time.sleep(.25)
 raise TimeoutError(label)
marker=wait('owned marker',lambda:rt.read_json(root/'local-mc-owner.json'),30);run=marker['runId']
assert marker['instanceRoot']==str(root) and marker['mode']=='hold' and marker['offlineLoopback'] is True and marker['roles']==['server','host'], 'One-client owned unified hold lab required'
PLAYER=marker['clientNames']['host']
def check(label,ok,detail=None):
 row={'check':label,'passed':bool(ok),'detail':detail};evidence.append(row);rt.write_json(root/'input-link-evidence.json',{'checks':evidence,'physicalOSInput':False});print('INPUT_LINK_ASSERT',label,bool(ok),flush=True)
 if not ok:raise AssertionError(label)
def command(role,body):
 result=debug.command(root,role,body,45);receipts.append(result);check('callback.'+str(len(receipts))+'.'+body['type'],result.get('ok') is True,result);return result
def native(key,mods=0,repeat=False):
 global seq
 seq+=1;rt.write_json(root/'coordinator/inputlink-command-host.json',{'runId':run,'id':seq,'key':key,'modifiers':mods,'repeat':repeat})
 result=wait('native result '+str(seq),lambda:rt.read_json(root/f'coordinator/inputlink-result-host-{seq}.json'),45);receipts.append(result);check('nativeCallback.'+str(seq),result.get('ok') is True,result);check('frameFinally.'+str(seq),result.get('frameCleared') is True);return result
def snapshot():return command('server',{'type':'game-snapshot','player':PLAYER,'game':'outbreak'})['snapshot']
def outbreak_state():
 s=snapshot();return next(row['state'] for row in s['games'] if row['id']=='outbreak')
def server_count():return (rt.read_json(root/'coordinator/inputlink-server.json') or {}).get('outbreakInteractReceived',0)
result={'runId':run,'instanceRoot':str(root),'physicalOSInput':False,'nativeKeyboardCallback':True,'hardwareModifierStateInjected':False,'sourceHeads':m.get('sourceHeads',{}),'coreHead':m.get('coreHead'),'passed':False}
try:
 wait('real client connected',lambda:(rt.read_json(root/'coordinator/status-host.json') or {}).get('connected') and (rt.read_json(root/'coordinator/status-server.json') or {}).get('players')==1,240)
 s=command('server',{'type':'observe'});check('realNetworkConnection',s['status']['byName'][PLAYER]['network'])
 baseline=native(-1);check('ordinaryContextNotOutbreak',not baseline['contextClaimsOutbreak'])
 f=native(70);check('ordinaryFUsesTaczEntryOnce',f['gunLogicEntries']==1,f);check('ordinaryFNoOutbreak',f['outbreakInteractSent']==0)
 o=native(79);check('oldONoTaczEntry',o['gunLogicEntries']==0)
 command('server',{'type':'inventory-set','player':PLAYER,'slot':0,'item':'minecraft:diamond','count':3});command('server',{'type':'inventory-set','player':PLAYER,'slot':40,'item':'minecraft:emerald','count':7})
 tab=native(258);check('tabSendsOneSwapPacket',tab['swapPacketsSent']==1,tab)
 s=command('server',{'type':'observe'})['status']['byName'][PLAYER]['inventoryBySlot'];check('actualServerTabSwapsBothHands',s['0']['item']=='minecraft:emerald' and s['40']['item']=='minecraft:diamond',s)
 tabctrl=native(258,2);check('ctrlTabNoSwap',tabctrl['swapPacketsSent']==0,tabctrl);check('ctrlTabFrameAllowsListOnly',tabctrl['pressFrame']['key.playerlist']['allowed'] and not tabctrl['pressFrame']['key.swapOffhand']['allowed'],tabctrl['pressFrame'])
 altb=native(66,4);check('altBFrameRejectsBackpack',not altb['pressFrame']['key.sophisticatedbackpacks.open_backpack']['allowed'],altb['pressFrame']);check('altBAllowsNativeWheel',altb['pressFrame']['key.muxi_game_core.challenge_wheel']['allowed'])
 bareb=native(66);check('bareBRejectsNativeWheel',not bareb['pressFrame']['key.muxi_game_core.challenge_wheel']['allowed']);check('bareBPreservesBackpack',bareb['pressFrame']['key.sophisticatedbackpacks.open_backpack']['allowed'])
 command('server',{'type':'execute','command':'op '+PLAYER})
 command('host',{'type':'game-action','game':'outbreak','action':'createConfigured','value':'{"map":"lostschool","mode":"CAMPAIGN","difficulty":1}'})
 state=outbreak_state();result['createdState']=state
 def mapready():
  st=outbreak_state();rooms=st.get('rooms',[]);return rooms and rooms[0].get('mapReady')
 wait('real Outbreak map ready',mapready,180)
 check('waitingRoomDoesNotClaimF',not native(-1)['contextClaimsOutbreak'])
 command('host',{'type':'game-action','game':'outbreak','action':'start','value':''})
 def context():return native(-1)['contextClaimsOutbreak']
 wait('server context reaches native client',context,60);check('actualServerIssuedOutbreakContext',True)
 before=server_count();f=native(70,0,True);check('outbreakFOneOriginalPacketWithRepeat',f['outbreakInteractSent']==1,f);check('outbreakFSuppressesTaczSameEvent',f['gunLogicEntries']==0)
 wait('original action received by real server',lambda:server_count()==before+1,15);check('outbreakFServerReceivesExactlyOne',server_count()==before+1)
 for mods,label in [(1,'shift'),(2,'ctrl')]:
  before=server_count();f=native(70,mods);check('outbreak.'+label+'FOneRequest',f['outbreakInteractSent']==1 and f['gunLogicEntries']==0,f);wait('server receives '+label+'F',lambda:server_count()==before+1,15)
 altf=native(70,4);check('altFNeitherOutbreakNorTacz',altf['outbreakInteractSent']==0 and altf['gunLogicEntries']==0,altf)
 command('host',{'type':'game-action','game':'outbreak','action':'leave','value':''})
 wait('leave clears authoritative context',lambda:not native(-1)['contextClaimsOutbreak'],60);check('leaveContextCleared',True)
 f=native(70);check('leaveRestoresOrdinaryFPath',f['gunLogicEntries']==1 and f['outbreakInteractSent']==0,f)
 result['passed']=True
except BaseException as failure:result['error']=repr(failure);print('INPUT_LINK_FAILURE',traceback.format_exc(),flush=True)
finally:
 result['checks']=len(evidence);result['passedChecks']=sum(x['passed'] for x in evidence);result['receipts']=receipts;result['evidence']=evidence;rt.write_json(root/'input-link-result.json',result);rt.write_json(root/'stop-request.json',{'runId':run,'reason':'input linkage QA complete; normal own stop only'});print('INPUT_LINK_RESULT',json.dumps({k:v for k,v in result.items() if k not in ['receipts','evidence','createdState']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
