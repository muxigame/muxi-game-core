"""Real scoped MAC + actual Web wire -> actual Core/Auth hooks. MC/issuer are explicit fixtures."""
import argparse,hashlib,importlib.util,json,os,subprocess,sys,tempfile,time,urllib.request
from pathlib import Path
CORE=Path(__file__).resolve().parents[2];ROOT=CORE.parent;OUT=CORE/'build/ysm-assets-131';OUT.mkdir(parents=True,exist_ok=True)
checks=0

def check(value,message):
 global checks;checks+=1
 if not value:raise AssertionError(message)
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--java-home',type=Path,required=True);parser.add_argument('--gson',type=Path,required=True);parser.add_argument('--with-intake-contract',action='store_true');args=parser.parse_args()
 sys.path.insert(0,str(ROOT/'better-mc-remake/server'));sys.path.insert(0,str(CORE/'tools/ysm-machine'))
 from fastapi import FastAPI,HTTPException
 from fastapi.testclient import TestClient
 from app.ysm_assets import YsmAssetStore,ValidationReceipt
 from app.ysm_game_api import ysm_game_router
 from web_hooks import RegisteredCredential,HmacMachineAuthorizer,MemoryReplayStore,CoreConnectionResolver,install_identity_hooks,sign
 with tempfile.TemporaryDirectory(prefix='joint-wire-',dir=OUT) as temp:
  temp=Path(temp);spec=importlib.util.spec_from_file_location('sso_fixture',CORE/'tests/run_terminal_sso_trust.py');fixture=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixture);fixture.FILES['net/neoforged/neoforge/event/tick/ServerTickEvent.java']='package net.neoforged.neoforge.event.tick; public class ServerTickEvent {public static class Post {}}'
  sources=[]
  for name,data in fixture.FILES.items():
   p=temp/'types'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(data,encoding='utf-8');sources.append(p)
  sources+=list((CORE/'src/main/java/net/muxigame/core/feature/ysmassets').glob('*.java'))+[ROOT/'muxi-minigames/src/main/java/net/muxigame/minigames/TrustedAccounts.java',CORE/'tests/terminal-sso-trust/TerminalTrustHarness.java',CORE/'tests/ysm-assets/CoreWireAuthFixture.java']
  sources += [CORE/'src/main/java/net/muxigame/core'/n for n in ['config/CoreConfig.java','feature/identity/IdentityRules.java','feature/login/TerminalSsoIdentity.java','feature/login/TerminalPassportServer.java']]
  classes=temp/'classes';classes.mkdir();arguments=['--release','21','-encoding','UTF-8','-proc:none','-cp',str(args.gson),'-d',str(classes),*map(str,sources)];argfile=temp/'compile.args';argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in arguments),encoding='utf-8');subprocess.run([str(args.java_home/'bin/javac.exe'),'@'+str(argfile)],check=True)
  log=(OUT/'joint-core-fixture.log').open('w',encoding='utf-8');process=subprocess.Popen([str(args.java_home/'bin/java.exe'),'-Xmx192m','-cp',str(classes)+os.pathsep+str(args.gson),'net.muxigame.core.feature.login.CoreWireAuthFixture'],stdout=subprocess.PIPE,stderr=log,text=True,encoding='utf-8')
  base=None
  try:
   ready=json.loads(process.stdout.readline());check(ready['syntheticIssuerAndMcListeners'] and not ready['productionActivated'],'isolated fixture flags');base='http://127.0.0.1:'+str(ready['port']);sid=ready['serverId']
   def control(action,uid='10000'):
    req=urllib.request.Request(base+'/control',data=json.dumps({'action':action,'uid':uid}).encode(),headers={'Content-Type':'application/json','X-Core-Wire-Test':'isolated-wire-control-only'})
    with urllib.request.urlopen(req,timeout=4) as response:return json.load(response)
   ca=control('state')['claim'];cb=control('state','10001')['claim'];check(ca['uid']=='10000' and cb['uid']=='10001' and ca['connectionId']!=cb['connectionId'],'two actual hook bindings')
   web_secret=b'fixture-web-resolver-key-not-production-32bytes';core_secret=b'fixture-core-game-key-not-production-32bytes'
   webkey=RegisteredCredential('fixture-web-131','fixture-web-key',web_secret,frozenset({'connection-resolve'}),frozenset({'ysm-core:'+sid}))
   corekey=RegisteredCredential(sid,'fixture-core-key',core_secret,frozenset({'sync','player-read'}),frozenset({'ysm-web'}))
   limited=RegisteredCredential(sid,'fixture-sync-key',core_secret,frozenset({'sync'}),frozenset({'ysm-web'}));registered={corekey.key_id:corekey,limited.key_id:limited}
   authority=HmacMachineAuthorizer(registered.get,MemoryReplayStore());resolver=CoreConnectionResolver(base+'/internal/ysm/connection/resolve',webkey,sid,isolated_loopback=True)
   store=YsmAssetStore(temp/'store',validation_profile='joint-fixture-only');data=b'Joint wire opaque fixture; NOT a valid YSM model.'
   lease,path=store.begin_upload('10000')
   try:path.write_bytes(data);asset=store.ingest('10000','joint.ysm',path,'joint-upload-key','private')['model']['id']
   finally:store.end_upload('10000',lease,path)
   digest=hashlib.sha256(data).hexdigest();store.promote_verified(ValidationReceipt(digest,len(data),'joint-fixture-only','joint-validation-fixture-1',True));revision=store.bridge_snapshot()['revision'];pub=store.bridge_snapshot()['models'][0]['contentId']
   app=FastAPI();check(install_identity_hooks(app,authority,resolver),'real startup hook registration')
   async def machine(request,purpose):
    hook=getattr(app.state,'ysm_machine_authorizer',None)
    if hook is None:raise HTTPException(503,'ysm_game_authority_not_configured')
    return await hook(request,purpose)
   async def connection(machine,claim):
    hook=getattr(app.state,'ysm_connection_resolver',None)
    if hook is None:raise HTTPException(503,'ysm_game_authority_not_configured')
    return await hook(machine,claim)
   app.include_router(ysm_game_router(store,machine,connection))
   with TestClient(app) as client:
    prefix='/api/internal/game/ysm'
    def request(route,payload,key=corekey,headers=None,now=None,nonce=None,sign_body=None):
     body=json.dumps(payload,separators=(',',':')).encode();purpose='sync' if route in ('snapshot','staged') else 'player-read';path=prefix+'/'+route;h=sign(key,purpose,'ysm-web','POST',path,body if sign_body is None else sign_body,now_ms=now,nonce=nonce);h['Content-Type']='application/json'
     if headers:h.update(headers)
     return client.post(path,content=body,headers=h)
    def catalog(claim):return request('catalog',{'connection':claim,'revision':revision})
    def blob(claim,model=asset):return request('blob',{'connection':claim,'revision':revision,'modelId':model})
    response=request('snapshot',{});check(response.status_code==200 and response.json()['active'] is False,'signed sync snapshot remains inactive')
    check(catalog(ca).status_code==200,'owner current UID catalog');response=catalog(cb);check(response.status_code==200 and response.json()['models']==[],'other UID private catalog hidden')
    check(blob(ca).status_code==200 and blob(ca).content==data,'owner read with actual Core lookup before/after IO')
    check(blob(cb).status_code==404,'other UID private handle denied');check(blob(cb,pub).status_code==404,'private public-id denied');check(blob(cb,digest).status_code==404,'private hash cannot grant download')
    forged=dict(cb,uid='10000');check(catalog(forged).status_code==403,'valid MAC with forged UID denied by actual Core');check(catalog(cb).status_code==200,'forgery cannot retire valid binding')
    check(client.post(prefix+'/snapshot',json={},headers={'Authorization':'Bearer fixture-user-not-machine'}).status_code==403,'player bearer not machine authority')
    check(request('snapshot',{},headers={'Cookie':'fixture=user'}).status_code==403,'cookie cannot mix with machine auth')
    signer=RegisteredCredential(sid,limited.key_id,core_secret,frozenset({'sync','player-read'}),frozenset({'ysm-web'}));check(request('catalog',{'connection':ca,'revision':revision},key=signer).status_code==403,'registered sync-only key cannot read player assets')
    check(request('snapshot',{},sign_body=b'{"different":true}').status_code==403,'body tampering denied');check(request('snapshot',{},now=int(time.time()*1000)-60001).status_code==403,'expired proof denied')
    nonce='a'*32;check(request('snapshot',{},nonce=nonce).status_code==200,'fresh nonce accepted');check(request('snapshot',{},nonce=nonce).status_code==403,'nonce replay denied')
    check(request('snapshot',{},headers={'X-Muxi-Ysm-Audience':'ysm-core:'+sid}).status_code==403,'wrong audience denied')
    old=ca;control('revoke');check(catalog(old).status_code==409 and blob(old).status_code==409,'actual explicit Auth revoke blocks old wire reads')
    ca=control('authorize')['claim'];check(ca['bindingGeneration']!=old['bindingGeneration'],'same listener reauthorization rotates generation');check(catalog(old).status_code==409 and catalog(ca).status_code==200,'only current authorization generation accepted')
    before=ca;control('join-source');ca=control('state')['claim'];control('expire');fresh=control('state')['claim'];check(fresh is not None and fresh['bindingGeneration']!=ca['bindingGeneration'],'SSO expiry preserves independent join identity under fresh claim');check(catalog(ca).status_code==409 and catalog(fresh).status_code==200,'old terminal epoch fenced while join remains verified')
    cb_old=cb;cb=control('reconnect','10001')['claim'];check(cb['connectionId']!=cb_old['connectionId'] and catalog(cb_old).status_code==409 and catalog(cb).status_code==200,'replacement listener denies old connection')
    control('logout','10001');check(catalog(cb).status_code==409,'actual disconnect immediately retires claim')
    check(all(not x['canSelect'] for x in store.list_models('10000')['models']),'no runtime activation ACK')
    if args.with_intake_contract:
     from test_intake_bridge import exercise_intake_contract
     intake_receipt=exercise_intake_contract(store,request,fresh,temp)
     (OUT/'intake-wire-result.json').write_text(json.dumps(intake_receipt,indent=2),encoding='utf-8')
    disabled=HmacMachineAuthorizer();check(not install_identity_hooks(app,disabled,resolver),'missing provider not installed');check(client.post(prefix+'/snapshot',json={}).status_code==503,'unconfigured startup fails closed')
    check(core_secret.decode() not in repr(corekey),'credential repr redacted')
   control('stop');code=process.wait(timeout=8);check(code==0,'owned fixture normal exit');tail=process.stdout.read();check('OWNED_FIXTURE_NORMAL_EXIT' in tail,'fixture stop receipt')
   receipt={'passed':True,'checks':checks,'actualCoreAuthSourceHooks':True,'actualScopedMacValidation':True,'actualWebRouter':True,'actualLoopbackCoreResolver':True,'issuerAndMinecraftListeners':'explicit isolated fixtures','testUids':['10000','10001'],'productionCredentialsReadOrCreated':False,'modelFormatValidated':False,'runtimeActivated':False,'minecraftStarted':False,'ownedFixtureExitCode':code,'webHead':subprocess.check_output(['git','-c','safe.directory='+str(ROOT/'better-mc-remake'),'-C',str(ROOT/'better-mc-remake'),'rev-parse','HEAD'],text=True).strip()};
   if args.with_intake_contract:receipt['intakeContract']=intake_receipt
   (OUT/'joint-wire-result.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps(receipt))
  finally:
   if process.poll() is None and base:
    try:control('stop');process.wait(timeout=8)
    except Exception:pass
   log.close()
   if process.poll() is None:raise RuntimeError('Owned isolated Core fixture did not stop normally; PID='+str(process.pid))
if __name__=='__main__':main()
