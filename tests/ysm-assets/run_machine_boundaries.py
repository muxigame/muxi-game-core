"""Offline machine key/HTTP boundaries. Fixed fixtures only, no MC or production credentials."""
import argparse,asyncio,hashlib,json,subprocess,sys,tempfile
from pathlib import Path
from unittest.mock import patch
CORE=Path(__file__).resolve().parents[2];OUT=CORE/'build/ysm-assets-131'
checks=0

def check(value,message):
 global checks;checks+=1
 if not value:raise AssertionError(message)

async def python_checks():
 import httpx
 from fastapi import HTTPException
 from starlette.requests import Request
 from app.ysm_game_api import GameMachine
 from web_hooks import RegisteredCredential,CoreConnectionResolver,HmacMachineAuthorizer,MemoryReplayStore,sign
 credential=RegisteredCredential('fixture-web','registered-key',b'fixed-machine-boundary-fixture-not-production',frozenset({'connection-resolve'}),frozenset({'ysm-core:fixture'}))
 resolver=CoreConnectionResolver('https://fixture.invalid/internal/ysm/connection/resolve',credential,'fixture')
 claim={'serverId':'fixture','connectionId':'fixture-connection','bindingGeneration':'fixture-generation','uid':'10000'}
 actual=dict(claim,live=True);real_client=httpx.AsyncClient
 class Stream(httpx.AsyncByteStream):
  def __init__(self,blocks):self.blocks=blocks;self.read=0;self.closed=False
  async def __aiter__(self):
   for block in self.blocks:
    self.read+=1
    if isinstance(block,Exception):raise block
    yield block
  async def aclose(self):self.closed=True
 async def call(blocks,code=200):
  stream=Stream(blocks)
  def handler(request):return httpx.Response(code,stream=stream)
  with patch('httpx.AsyncClient',side_effect=lambda **kwargs:real_client(transport=httpx.MockTransport(handler),**kwargs)):
   try:return await resolver(GameMachine('fixture'),claim),stream
   except HTTPException as error:return error,stream
 payload=json.dumps(actual).encode()
 result,stream=await call([payload]);check(not isinstance(result,HTTPException) and result.uid=='10000','matching current claim accepted');check(stream.closed,'successful response closed')
 result,stream=await call([payload+b' '*(4096-len(payload))]);check(not isinstance(result,HTTPException),'4096-byte boundary accepted')
 result,stream=await call([payload+b' '*(4097-len(payload))]);check(isinstance(result,HTTPException) and result.status_code==503 and stream.closed,'4097-byte boundary rejected and closed')
 result,stream=await call([b'x'*3000,b'y'*1200,AssertionError('must stop before consuming unbounded response')]);check(isinstance(result,HTTPException) and result.status_code==503,'oversized response rejected');check(stream.read==2 and stream.closed,'oversized response stops early and closes')
 result,stream=await call([json.dumps(dict(actual,uid='10001')).encode()]);check(isinstance(result,HTTPException) and result.status_code==409,'mismatched authoritative UID rejected')
 result,stream=await call([b'invalid']);check(isinstance(result,HTTPException) and result.status_code==409,'malformed Core response rejected')
 result,stream=await call([AssertionError('must not read rejected response body')],409);check(isinstance(result,HTTPException) and result.status_code==409 and stream.read==0 and stream.closed,'error response is closed without body read')
 result,stream=await call([AssertionError('must not read redirected response body')],302);check(isinstance(result,HTTPException) and result.status_code==503 and stream.read==0 and stream.closed,'redirect is closed without body read')
 key=RegisteredCredential('fixture','web-key',credential.secret,frozenset({'sync'}),frozenset({'ysm-web'}));authorizer=HmacMachineAuthorizer(lambda _:key,MemoryReplayStore())
 def request(raw_path,headers):
  return Request({'type':'http','method':'POST','path':'/api/internal/game/ysm/snapshot','raw_path':raw_path,'query_string':b'','headers':[(k.lower().encode(),v.encode()) for k,v in headers],'scheme':'https','server':('fixture',443),'client':('127.0.0.1',1)})
 try:await authorizer(request(b'/api/internal/game/ysm/\xff',[]),'sync')
 except HTTPException as error:check(error.status_code==403,'non-ASCII raw path fails as denied request')
 else:raise AssertionError('non-ASCII path was accepted')
 disabled=HmacMachineAuthorizer()
 try:await disabled(request(b'/api/internal/game/ysm/snapshot',[]),'sync')
 except HTTPException as error:check(error.status_code==503,'unconfigured authorizer remains disabled')
 else:raise AssertionError('unconfigured authorizer was accepted')

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--java-home',required=True,type=Path);args=parser.parse_args();OUT.mkdir(parents=True,exist_ok=True)
 sys.path.insert(0,str(CORE.parent/'better-mc-remake/server'));sys.path.insert(0,str(CORE/'tools/ysm-machine'))
 sources=[CORE/'src/main/java/net/muxigame/core/feature/ysmassets/YsmMachineAuthority.java',CORE/'tests/ysm-assets/YsmMachineAuthorityTest.java']
 with tempfile.TemporaryDirectory(prefix='machine-boundaries-',dir=OUT) as directory:
  subprocess.run([str(args.java_home/'bin/javac.exe'),'--release','21','-encoding','UTF-8','-d',directory,*map(str,sources)],check=True)
  java=subprocess.run([str(args.java_home/'bin/java.exe'),'-Xmx96m','-cp',directory,'net.muxigame.core.feature.ysmassets.YsmMachineAuthorityTest'],check=True,capture_output=True,text=True)
  java_receipt=json.loads(java.stdout)
 asyncio.run(python_checks())
 receipt={'passed':True,'javaChecks':java_receipt['checks'],'pythonChecks':checks,'fixedFixtureCredentialsOnly':True,'productionCredentialsReadOrCreated':False,'minecraftStarted':False,'runtimeActivated':False,'sourceHashes':{str(p.relative_to(CORE)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources+[CORE/'tools/ysm-machine/web_hooks.py']}}
 (OUT/'machine-boundaries-result.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps(receipt))
if __name__=='__main__':main()
