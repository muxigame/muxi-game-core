"""Actual Web store exports -> actual Core/TrustedAccounts -> two explicit runtime fixtures."""
import argparse,hashlib,importlib.util,json,os,subprocess,sys,tempfile
from pathlib import Path
CORE=Path(__file__).resolve().parents[2];ROOT=CORE.parent;OUT=CORE/'build/ysm-assets-131';OUT.mkdir(parents=True,exist_ok=True)
def export(store,uid,name,data,key):
 lease,path=store.begin_upload(uid)
 try:path.write_bytes(data);return store.ingest(uid,name,path,key,'private')
 finally:store.end_upload(uid,lease,path)
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--java-home',type=Path,required=True);parser.add_argument('--gson',type=Path,required=True);parser.add_argument('--with-appearance-a',action='store_true');args=parser.parse_args()
 sys.path.insert(0,str(ROOT/'better-mc-remake/server'));from app.ysm_assets import YsmAssetStore,ValidationReceipt
 with tempfile.TemporaryDirectory(prefix='two-accounts-',dir=OUT) as tmp:
  tmp=Path(tmp);store=YsmAssetStore(tmp/'web-store',validation_profile='explicit-protocol-fixture');cases=[];data=b'Core/Web opaque fixture ONLY; not a valid YSM model\x00';sha=hashlib.sha256(data).hexdigest()
  def save(name):cases.append({'name':name,'snapshot':store.bridge_snapshot()})
  first=export(store,10001,'sample-a.ysm',data,'upload-a-key');a=first['model']['id'];save('pendingA')
  store.promote_verified(ValidationReceipt(sha,len(data),'explicit-protocol-fixture','fixture-receipt-1',True));save('privateA')
  duplicate=export(store,10001,'sample-a.ysm',data,'upload-a-key');assert duplicate['model']['id']==a and duplicate['replayed'];save('duplicateA')
  second=export(store,10002,'sample-b.ysm',data,'upload-b-key');b=second['model']['id'];save('sharedPrivate')
  content=store.bridge_snapshot()['models'][0]['contentId'];assert len(list(store.blobs.glob('*.bin')))==1
  store.mutate(10001,a,'visibility','public-a-key','public');save('publicA');store.mutate(10001,a,'visibility','private-a-key','private');save('privateAgain')
  store.mutate(10001,a,'delete','delete-a-key');save('deletedA');store.mutate(10002,b,'delete','delete-b-key');save('deletedBoth');store.mutate(10001,a,'restore','restore-a-key');save('restoredA')
  assert not store.list_models(10001)['models'][0]['canSelect'];assert all(not r['snapshot'].get('active',False) for r in cases)
  payload={'assetA':a,'assetB':b,'contentId':content,'sha256':sha,'syntheticOpaqueBytes':True,'formatVerifiedByRealYsm':False,'cases':cases};snapshot=OUT/'web-contract-snapshots.json';snapshot.write_text(json.dumps(payload,indent=2),encoding='utf-8')
  # Reuse only the four established deterministic MC identity stubs; product sources remain canonical.
  spec=importlib.util.spec_from_file_location('identity_fixture',CORE/'tests/run_terminal_sso_trust.py');fixture=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixture)
  generated=[]
  for name in ['com/mojang/authlib/GameProfile.java','net/minecraft/server/network/ServerGamePacketListenerImpl.java','net/minecraft/server/MinecraftServer.java','net/minecraft/server/level/ServerPlayer.java']:
   target=tmp/'identity-fixture'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(fixture.FILES[name],encoding='utf-8');generated.append(target)
  tick=tmp/'identity-fixture/net/neoforged/neoforge/event/tick/ServerTickEvent.java';tick.parent.mkdir(parents=True,exist_ok=True);tick.write_text('package net.neoforged.neoforge.event.tick; public class ServerTickEvent {public static class Post {}}',encoding='utf-8');generated.append(tick)
  sources=generated+list((CORE/'src/main/java/net/muxigame/core/feature/ysmassets').glob('*.java'))+[ROOT/'muxi-minigames/src/main/java/net/muxigame/minigames/TrustedAccounts.java',CORE/'tests/ysm-assets/YsmAssetProtocolTest.java'];sources+=([CORE/'tests/ysm-assets/YsmAppearanceATest.java'] if args.with_appearance_a else []);classes=tmp/'classes';classes.mkdir();arguments=['--release','21','-encoding','UTF-8','-proc:none','-cp',str(args.gson),'-d',str(classes),*map(str,sources)];argfile=tmp/'compile.args';argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in arguments),encoding='utf-8')
  subprocess.run([str(args.java_home/'bin/javac.exe'),'@'+str(argfile)],check=True)
  result=subprocess.run([str(args.java_home/'bin/java.exe'),'-cp',str(classes)+os.pathsep+str(args.gson),'net.muxigame.core.feature.ysmassets.YsmAssetProtocolTest',str(snapshot)],check=False,capture_output=True,text=True,encoding='utf-8');
  if result.returncode:
   print(result.stdout);print(result.stderr,file=sys.stderr);raise SystemExit(result.returncode)
  receipt=json.loads(result.stdout.strip())
  if args.with_appearance_a:
   appearance=subprocess.run([str(args.java_home/'bin/java.exe'),'-Xmx96m','-cp',str(classes)+os.pathsep+str(args.gson),'net.muxigame.core.feature.ysmassets.YsmAppearanceATest',str(snapshot)],capture_output=True,text=True,encoding='utf-8')
   if appearance.returncode:
    print(appearance.stdout);print(appearance.stderr,file=sys.stderr);raise SystemExit(appearance.returncode)
   policy=json.loads(appearance.stdout);policy['webHead']=subprocess.check_output(['git','-C',str(ROOT/'better-mc-remake'),'rev-parse','HEAD'],text=True).strip();policy['snapshotSha256']=hashlib.sha256(snapshot.read_bytes()).hexdigest();policy['coordinatorSha256']=hashlib.sha256((CORE/'src/main/java/net/muxigame/core/feature/ysmassets/YsmAssetCoordinator.java').read_bytes()).hexdigest();(OUT/'appearance-a-result.json').write_text(json.dumps(policy,indent=2),encoding='utf-8');receipt['appearanceA']=policy
  receipt['webHead']=subprocess.check_output(['git','-c','safe.directory='+str(ROOT/'better-mc-remake'),'-C',str(ROOT/'better-mc-remake'),'rev-parse','HEAD'],text=True).strip();receipt['testedWebStoreSha256']=hashlib.sha256((ROOT/'better-mc-remake/server/app/ysm_assets.py').read_bytes()).hexdigest();receipt['snapshotSha256']=hashlib.sha256(snapshot.read_bytes()).hexdigest();receipt['allWebAssetsRuntimeInactive']=True;(OUT/'protocol-result.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps(receipt))
if __name__=='__main__':main()
