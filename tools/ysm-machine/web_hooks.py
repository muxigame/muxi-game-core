"""Startup-injected YSM machine hooks. No key lookup from env, no key generation, no model activation."""
from __future__ import annotations
import hashlib,hmac,ipaddress,re,secrets,threading,time
from dataclasses import dataclass,field
from urllib.parse import urlsplit
from fastapi import HTTPException
from fastapi.concurrency import run_in_threadpool
from app.ysm_game_api import GameMachine,GameConnection

WINDOW_MS=30_000
PREFIX='muxi-ysm-machine-v1'
HEADERS=('Server','Key','Purpose','Audience','Time','Nonce','Signature')
ROUTES={'/api/internal/game/ysm/snapshot':'sync','/api/internal/game/ysm/staged':'sync','/api/internal/game/ysm/catalog':'player-read','/api/internal/game/ysm/blob':'player-read'}
def ident(value):
 if not isinstance(value,str) or not re.fullmatch(r'[A-Za-z0-9._:-]{1,128}',value):raise ValueError('invalid machine proof')
 return value
@dataclass(frozen=True)
class RegisteredCredential:
 server_id:str
 key_id:str
 secret:bytes=field(repr=False)
 purposes:frozenset[str]=field(default_factory=frozenset)
 audiences:frozenset[str]=field(default_factory=frozenset)
 def __post_init__(self):
  ident(self.server_id);ident(self.key_id)
  if not isinstance(self.secret,bytes) or len(self.secret)<32:raise ValueError('invalid registered credential')
  object.__setattr__(self,'purposes',frozenset(self.purposes));object.__setattr__(self,'audiences',frozenset(self.audiences))
  for value in self.purposes|self.audiences:ident(value)
class MemoryReplayStore:
 """Bounded volatile fixture store. Production supplies an explicit atomic replay store/policy."""
 def __init__(self):self.values={};self.lock=threading.Lock()
 def consume(self,identity,nonce,expires,now):
  with self.lock:
   self.values={k:v for k,v in self.values.items() if v>=now}
   key=(identity,nonce)
   if len(self.values)>=4096 or key in self.values:return False
   self.values[key]=expires;return True

def material(proof,method,path,body):
 for name in ('Server','Key','Purpose','Audience'):ident(proof[name])
 if not re.fullmatch(r'0|[1-9][0-9]{0,18}',proof['Time']) or not re.fullmatch(r'[a-f0-9]{32}',proof['Nonce']) or method!='POST' or not re.fullmatch(r'/[A-Za-z0-9_/-]{1,160}',path) or len(body)>16384:raise ValueError('invalid machine proof')
 return '\n'.join([PREFIX,proof['Server'],proof['Key'],proof['Purpose'],proof['Audience'],proof['Time'],proof['Nonce'],method,path,hashlib.sha256(body).hexdigest()]).encode()
def sign(credential,purpose,audience,method,path,body,now_ms=None,nonce=None):
 if purpose not in credential.purposes or audience not in credential.audiences:raise ValueError('unapproved machine use')
 p={'Server':credential.server_id,'Key':credential.key_id,'Purpose':purpose,'Audience':audience,'Time':str(int(time.time()*1000) if now_ms is None else now_ms),'Nonce':nonce or secrets.token_hex(16)}
 p['Signature']=hmac.new(credential.secret,material(p,method,path,body),'sha256').hexdigest()
 return {'X-Muxi-Ysm-'+key:value for key,value in p.items()}
class HmacMachineAuthorizer:
 def __init__(self,credential_provider=None,replay_store=None,audience='ysm-web',clock_ms=lambda:int(time.time()*1000)):
  self.provider=credential_provider;self.replay=replay_store;self.audience=ident(audience);self.clock=clock_ms
 @property
 def configured(self):return self.provider is not None and self.replay is not None
 async def __call__(self,request,purpose):
  if not self.configured:raise HTTPException(503,'ysm_game_authority_not_configured')
  try:raw_path=request.scope.get('raw_path',request.url.path.encode()).decode('ascii','strict')
  except (UnicodeError,AttributeError):raise HTTPException(403,'invalid_machine_authority') from None
  if request.method!='POST' or request.url.query or ROUTES.get(raw_path)!=purpose or 'authorization' in request.headers or 'cookie' in request.headers:raise HTTPException(403,'invalid_machine_authority')
  body=bytearray()
  async for block in request.stream():
   if len(body)+len(block)>16384:raise HTTPException(413,'wire_request_too_large')
   body.extend(block)
  # Preserve this bounded body for the already-existing game wire's request.stream parser.
  request._body=bytes(body)
  try:
   p={}
   for name in HEADERS:
    values=request.headers.getlist('X-Muxi-Ysm-'+name)
    if len(values)!=1:raise ValueError()
    p[name]=values[0]
   return await run_in_threadpool(self.verify,p,purpose,request.method,raw_path,bytes(body))
  except (ValueError,KeyError,TypeError,UnicodeError):raise HTTPException(403,'invalid_machine_authority') from None
 def verify(self,p,purpose,method,path,body):
  key=self.provider(p['Key']);message=material(p,method,path,body);now=self.clock();stamp=int(p['Time'])
  if (not isinstance(key,RegisteredCredential) or key.server_id!=p['Server'] or key.key_id!=p['Key'] or purpose!=p['Purpose'] or purpose not in key.purposes or self.audience!=p['Audience'] or self.audience not in key.audiences or not re.fullmatch('[a-f0-9]{64}',p['Signature']) or not now-WINDOW_MS<=stamp<=now+WINDOW_MS):raise ValueError()
  if not hmac.compare_digest(hmac.new(key.secret,message,'sha256').hexdigest(),p['Signature']):raise ValueError()
  if not self.replay.consume((self.audience,key.server_id,key.key_id),p['Nonce'],stamp+2*WINDOW_MS,now):raise ValueError()
  return GameMachine(key.server_id)

class CoreConnectionResolver:
 def __init__(self,endpoint,credential,core_server_id,*,verify=True,isolated_loopback=False):
  uri=urlsplit(endpoint)
  if uri.username or uri.password or uri.query or uri.fragment or uri.path!='/internal/ysm/connection/resolve':raise ValueError('invalid Core resolver endpoint')
  if uri.scheme!='https':
   if not isolated_loopback or uri.scheme!='http' or not ipaddress.ip_address(uri.hostname).is_loopback:raise ValueError('Core resolver requires approved HTTPS transport')
  if verify is False:raise ValueError('TLS verification cannot be disabled')
  ident(core_server_id);self.endpoint=endpoint;self.credential=credential;self.server_id=core_server_id;self.verify=verify
  if 'connection-resolve' not in credential.purposes or 'ysm-core:'+core_server_id not in credential.audiences:raise ValueError('unapproved Core resolver credential')
 async def __call__(self,machine,claim):
  if machine.server_id!=self.server_id or claim.get('serverId')!=self.server_id:raise HTTPException(403,'wrong_game_server')
  import json,httpx
  body=json.dumps(claim,separators=(',',':')).encode();headers=sign(self.credential,'connection-resolve','ysm-core:'+self.server_id,'POST','/internal/ysm/connection/resolve',body);headers['Content-Type']='application/json'
  try:
   async with httpx.AsyncClient(verify=self.verify,timeout=3,follow_redirects=False,trust_env=False) as client:
    async with client.stream('POST',self.endpoint,content=body,headers=headers) as response:
     if response.status_code!=200:
      code=response.status_code if response.status_code in (400,403,409,503) else 503
      raise HTTPException(code,'game_connection_changed' if code==409 else 'game_connection_lookup_unavailable')
     raw=bytearray()
     async for block in response.aiter_bytes(chunk_size=4097):
      if len(raw)+len(block)>4096:raise HTTPException(503,'invalid_core_connection_response')
      raw.extend(block)
  except httpx.HTTPError:raise HTTPException(503,'game_connection_lookup_unavailable') from None
  try:
   actual=json.loads(raw)
   if set(actual)!=set(claim)|{'live'} or actual['live'] is not True or any(actual[k]!=claim[k] for k in claim):raise ValueError()
   return GameConnection(actual['serverId'],actual['connectionId'],actual['bindingGeneration'],actual['uid'],True)
  except (ValueError,KeyError,TypeError):raise HTTPException(409,'game_connection_changed') from None

def install_identity_hooks(app,authorizer,resolver):
 """Explicit trusted startup only. Does not activate files, YSM selection, schedules or runtime ACK."""
 if not isinstance(authorizer,HmacMachineAuthorizer) or not authorizer.configured:
  app.state.ysm_machine_authorizer=None;app.state.ysm_connection_resolver=None;return False
 if not isinstance(resolver,CoreConnectionResolver):raise ValueError('approved live Core resolver required')
 app.state.ysm_machine_authorizer=authorizer;app.state.ysm_connection_resolver=resolver;return True
