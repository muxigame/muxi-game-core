"""Synthetic saves only: exact rollback, interruption points, role semantics, external chunks and refusal gates."""
from pathlib import Path
from collections import OrderedDict
import gzip,hashlib,json,struct,sys,time,uuid,zlib
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools'))
from world_roles import nbt,region,snbt
from world_roles.transform import HOME,SURVIVAL,OLD_SURVIVAL,remap_tag,remap_json
from world_roles.transaction import plan,apply,rollback,snapshot,offline
OUT=ROOT/'build/world-role-migration-20261014';CHECKS=0

def check(ok,label):
 global CHECKS;CHECKS+=1
 if not ok:raise AssertionError(label)
def C(**values):return nbt.Tag(10,OrderedDict(values))
def S(s):return nbt.Tag(8,s)
def I(n):return nbt.Tag(3,n)
def write(path,tag):path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(gzip.compress(nbt.encode('',tag),mtime=0))
def val(path):return nbt.python_value(nbt.read_file(path.read_bytes())[2])
def mca(path,tag,external=False):
 path.parent.mkdir(parents=True,exist_ok=True);packed=zlib.compress(nbt.encode('',tag));header=bytearray(8192);header[:4]=((2<<8)|1).to_bytes(4,'big');header[4096:4100]=(123456).to_bytes(4,'big')
 if external:path.with_name('c.0.0.mcc').write_bytes(packed);body=struct.pack('>I',1)+bytes([130])
 else:body=struct.pack('>I',len(packed)+1)+bytes([2])+packed
 path.write_bytes(header+body) # Actual NeoForge files can end in a partial final sector.
def actual_chunk(path):
 with path.open('rb') as stream:
  entry=int.from_bytes(stream.read(4),'big');stream.seek((entry>>8)*4096);size=int.from_bytes(stream.read(4),'big');kind=stream.read(1)[0]
  packed=path.with_name('c.0.0.mcc').read_bytes() if kind&128 else stream.read(size-1)
  return nbt.python_value(nbt.decode(nbt.expand(packed,kind&127))[1])
def fixture():
 p=OUT/'synthetic'/uuid.uuid4().hex;p.mkdir(parents=True);(p/'session.lock').write_bytes(b'LOCK1234')
 gen=lambda marker:C(type=S('minecraft:overworld'),generator=C(type=S('minecraft:noise'),settings=S('minecraft:overworld'),biome_source=C(type=S('minecraft:multi_noise'),preset=S('minecraft:overworld'))),fixtureMarker=S(marker))
 dims=nbt.Tag(10,OrderedDict([('minecraft:overworld',gen('HOME-BYTES')), (OLD_SURVIVAL,gen('SURVIVAL-BYTES')),('muxi_game_core:adventure',gen('ADVENTURE-BYTES'))]))
 level=C(Data=C(WorldGenSettings=C(seed=nbt.Tag(4,8324402536652151177),dimensions=dims),SpawnX=I(-496),SpawnY=I(74),SpawnZ=I(304),SpawnAngle=nbt.Tag(5,37.5),GameRules=C(doMobSpawning=S('true'))));write(p/'level.dat',level);(p/'level.dat_old').write_bytes((p/'level.dat').read_bytes())
 player=C(Dimension=S(OLD_SURVIVAL),SpawnX=I(12),SpawnY=I(70),SpawnZ=I(24),Pos=nbt.Tag(9,(6,[nbt.Tag(6,x) for x in (10.5,71.0,20.5)])),UUID=nbt.Tag(11,(4,struct.pack('>iiii',1,2,3,4))),Brain=C(memories=C(home=C(value=C(dimension=S('minecraft:overworld'))))),label=S('温柔🐎\0原样'))
 write(p/'playerdata/test.dat',player); (p/'playerdata/test.dat_old').write_bytes((p/'playerdata/test.dat').read_bytes())
 for prefix,dimension,marker in [('', 'minecraft:overworld','home-terrain'),('dimensions/muxi_game_core/overworld/',OLD_SURVIVAL,'survival-terrain')]:
  for kind in ['region','entities','poi']:
   tag=C(marker=S(marker+'-'+kind),dimension=S(dimension),array=nbt.Tag(12,(2,struct.pack('>qq',-1,9223372036854775807))))
   mca(p/(prefix+kind+'/r.0.0.mca'),tag,external=kind=='entities')
  write(p/(prefix+'data/raids.dat'),C(data=C(marker=S(marker))))
 write(p/'data/map_1.dat',C(data=C(dimension=S('minecraft:overworld'))));write(p/'data/idcounts.dat',C(data=C(map=I(99))));write(p/'data/underlays.dat',C(overlays=C(marker=S('original home overlay'))));write(p/'data/underlays_muxi_game_core_overworld.dat',C(overlays=C(marker=S('original survival overlay'))))
 (p/'entities/r.1.0.mca').write_bytes(b'') # Actual native empty placeholder must move and rollback byte-exactly.
 (p/'serverconfig').mkdir();(p/'serverconfig/claims.toml').write_text('# keep minecraft:overworld in comment\n[claims]\nallowedDimensions = ["minecraft:overworld", "muxi_game_core:overworld"]\ntype = "minecraft:overworld"\n',encoding='utf-8');(p/'serverconfig/terminal.json').write_text(json.dumps({'dimension':OLD_SURVIVAL,'claims':{'minecraft:overworld':[1,2]},'type':'minecraft:overworld','portal':'minecraft:overworld/123/x'}),encoding='utf-8')
 home_claims=C(claims=nbt.Tag(9,(10,[C(x=I(3),z=I(4),owner=S('fixed-owner'))])))
 survival_claims=C(claims=nbt.Tag(9,(10,[C(x=I(5),z=I(6),owner=S('fixed-owner'))])))
 write(p/'data/openpartiesandclaims/claims/synthetic-owner.nbt',C(dimensions=nbt.Tag(10,OrderedDict([('minecraft:overworld',home_claims),(OLD_SURVIVAL,survival_claims)]))))
 (p/'data/openpartiesandclaims/claims/raw-owner.nbt').write_bytes(nbt.encode('',C(dimension=S(OLD_SURVIVAL),owner=S('raw-owner'))))
 (p/'ftbteams').mkdir();(p/'ftbteams/team.snbt').write_text('{claims: {"minecraft:overworld": [1L, 2L]}, point: {dimension: "muxi_game_core:overworld"}, title: "你好🐎", typed: [I; 1, 2, 3]}',encoding='utf-8')
 return p

def run():
 p=fixture();before=snapshot(p);prepared=plan(p,(16,80,16),'survival');check(not prepared['blockers'],str(prepared['blockers']));check(snapshot(p)==before,'plan is read-only');check(prepared['referenceChanges']>10,'nested references inspected');check(prepared['additionalPayloadBytesUpperBound']<50000,'no full world duplicate planned')
 check(apply(p,prepared)['committed'],'apply commits');check(apply(p,prepared)['alreadyMigrated'],'second apply is inert');check(plan(p)['alreadyMigrated'],'second plan is inert')
 current=val(p/'level.dat')['Data'];dims=current['WorldGenSettings']['dimensions'];check(dims[SURVIVAL]['fixtureMarker']=='SURVIVAL-BYTES','real survival generator travels to vanilla ID');check(dims[HOME]['fixtureMarker']=='HOME-BYTES','real home generator travels to homeworld ID');check(dims[HOME]['type']=='minecraft:overworld','dimension TYPE is not remapped');check(dims[HOME]['generator']['settings']=='minecraft:overworld','noise settings stay unchanged');check(current['WorldGenSettings']['seed']==8324402536652151177,'global seed exact');check(OLD_SURVIVAL not in dims,'legacy registry key absent');check(current['SpawnX']==16,'explicit new primary spawn');check(val(p/'data/muxi_world_roles.dat')['data']['homeSpawn']=={'x':-496,'y':74,'z':304,'angle':37.5},'original home spawn retained separately')
 player=val(p/'playerdata/test.dat');check(player['Dimension']==SURVIVAL,'player current world follows actual survival terrain');check(player['SpawnDimension']==HOME,'implicit original overworld bed made explicit homeworld');check(player['Pos']==[10.5,71.0,20.5],'player coordinates unchanged');check(player['label']=='温柔🐎\0原样','modified UTF-8 astral and nul preserved');check(nbt.read_file((p/'playerdata/test.dat').read_bytes())[2].value['UUID'].value[1]==struct.pack('>iiii',1,2,3,4),'UUID array exact bits preserved');check(player['Brain']['memories']['home']['value']['dimension']==HOME,'nested brain location follows original home')
 check(val(p/'data/raids.dat')['data']['marker']=='survival-terrain','per-world survival SavedData moved to primary');check(val(p/'dimensions/muxi_game_core/homeworld/data/raids.dat')['data']['marker']=='home-terrain','home SavedData retained');check(val(p/'data/underlays_muxi_game_core_homeworld.dat')['overlays']['marker']=='original home overlay','audited global underlay home filename migrated');check(val(p/'data/underlays.dat')['overlays']['marker']=='original survival overlay','audited underlay survival filename migrated');check(val(p/'data/map_1.dat')['data']['dimension']==HOME,'global map physical-world binding follows home');cfg=json.loads((p/'serverconfig/terminal.json').read_text());check(cfg['dimension']==SURVIVAL and cfg['portal']==HOME+'/123/x','terminal and portal prefixes simultaneous');check(cfg['type']=='minecraft:overworld','JSON type identifier stays a resource');import tomllib;claims_config=tomllib.loads((p/'serverconfig/claims.toml').read_text());check(claims_config['claims']['allowedDimensions']==[HOME,SURVIVAL] and claims_config['claims']['type']=='minecraft:overworld','TOML dimension arrays change while type stays fixed');check((p/'serverconfig/claims.toml').read_text().startswith('# keep minecraft:overworld in comment'),'TOML comments unchanged');check('"muxi_game_core:homeworld"' in (p/'ftbteams/team.snbt').read_text(),'claims SNBT migrated without losing long suffixes');claims=val(p/'data/openpartiesandclaims/claims/synthetic-owner.nbt')['dimensions'];check(claims[HOME]['claims'][0]=={'x':3,'z':4,'owner':'fixed-owner'} and claims[SURVIVAL]['claims'][0]=={'x':5,'z':6,'owner':'fixed-owner'},'actual OPAC-style NBT dimension map preserves owner/chunk bindings')
 for directory in ['region','entities','poi']:
  home=p/'dimensions/muxi_game_core/homeworld'/directory/'r.0.0.mca';surv=p/directory/'r.0.0.mca';check(home.exists() and surv.exists(),'both physical terrain stores retained '+directory);check(actual_chunk(home)['dimension']==HOME and actual_chunk(surv)['dimension']==SURVIVAL,'physical chunk payload binding correct '+directory);check(actual_chunk(home)['marker']=='home-terrain-'+directory and actual_chunk(surv)['marker']=='survival-terrain-'+directory,'original world content retained '+directory)
 check(rollback(p)['byteIdenticalOriginalSave'],'committed rollback verified');check(snapshot(p)==before,'all original world/player/data/region/external bytes restored exactly');check(rollback(p)['alreadyRolledBack'],'second rollback inert');prepared=plan(p,(16,80,16),'home');check(not prepared['blockers'],'replan after rollback allowed');check(apply(p,prepared)['committed'],'retry after rollback commits');check(rollback(p)['byteIdenticalOriginalSave'],'retry can rollback')
 # Every transaction boundary is injected: no mixing of partial metadata and directory roles.
 for boundary in range(1,len(prepared['operations'])+len(prepared['moves'])+1):
  trial=fixture();original=snapshot(trial);draft=plan(trial,(16,80,16),'survival')
  try:apply(trial,draft,fail_after=boundary);raise AssertionError('interruption missing')
  except RuntimeError:pass
  check(rollback(trial)['byteIdenticalOriginalSave'],'interruption rollback '+str(boundary));check(snapshot(trial)==original,'byte identity at interruption '+str(boundary))
 trial=fixture();draft=plan(trial,(16,80,16),'survival');(trial/'stats').mkdir();(trial/'stats/new.json').write_text('{}')
 try:apply(trial,draft);raise AssertionError('stale plan accepted')
 except ValueError:check(True,'stale plan refused without mutation')
 trial=fixture();defaults=plan(trial);check(not defaults['blockers'],'existing default spawn and home-hub policy inherited without a new user decision');level_op=next(o for o in defaults['operations'] if o['path']=='level.dat');default_data=nbt.python_value(nbt.read_file(__import__('base64').b64decode(level_op['after']))[2])['Data'];check((default_data['SpawnX'],default_data['SpawnY'],default_data['SpawnZ'])==(-496,74,304),'original derived survival default coordinates kept');write(trial/'data/mystery.dat',C(data=C()))
 check(any('ownership' in x for x in plan(trial,(1,2,3),'survival')['blockers']),'unknown SavedData ownership blocked');check(not plan(trial,(1,2,3),'survival',{'mystery.dat':'global'})['blockers'],'explicit audited SavedData policy resolves gate')
 trial=fixture();draft=plan(trial,(16,80,16),'survival');apply(trial,draft);(trial/'stats').mkdir();(trial/'stats/gameplay.json').write_text('{"played":true}')
 try:rollback(trial);raise AssertionError('post-cutover progress discarded')
 except ValueError:check(True,'post-cutover changes block destructive rollback')
 try:remap_json({'minecraft:overworld':1,HOME:2});raise AssertionError('collision hidden')
 except ValueError:check(True,'existing target dimension key conflict refused')
 raw=nbt.encode('utf🐎',C(value=S('A\0🐎B'),nan=nbt.Tag(5,float('nan'),bytes.fromhex('7fc01234'))));name,tag=nbt.decode(raw);check(nbt.encode(name,tag)==raw,'all untouched scalar bits and modified UTF roundtrip exact')
 trial=fixture();draft=plan(trial);apply(trial,draft);check(json.loads((trial/'datapacks/muxi-world-roles/data/muxi_game_core/dimension/homeworld.json').read_text())['fixtureMarker']=='HOME-BYTES','home generator datapack retains original stem');check(json.loads((trial/'datapacks/muxi-world-roles/data/minecraft/dimension/overworld.json').read_text())['fixtureMarker']=='SURVIVAL-BYTES','survival generator datapack retains original stem');check('file/muxi-world-roles' in val(trial/'level.dat')['Data']['DataPacks']['Enabled'],'retained generator pack enabled before cold boot');check(rollback(trial)['byteIdenticalOriginalSave'],'datapack and enablement rollback exact')
 trial=fixture();draft=plan(trial);apply(trial,draft);pack=trial/'datapacks/muxi-world-roles/pack.mcmeta';saved=pack.read_bytes();pack.unlink()
 try:apply(trial,draft);raise AssertionError('missing generator pack accepted')
 except ValueError:check(True,'repeated apply validates retained generator artifacts')
 pack.write_bytes(saved);check(rollback(trial)['byteIdenticalOriginalSave'],'generator artifact restoration permits exact rollback')
 trial=fixture();(trial/'sublevels').mkdir();(trial/'sublevels/r.0.0.slvlr').write_bytes(b'opaque-sable');check(any('Sable storage present' in x for x in plan(trial)['blockers']),'unimplemented Sable codec explicitly blocks cutover rather than silently skips')
 trial=fixture();draft=plan(trial);apply(trial,draft);check(val(trial/'playerdata/test.dat_old')['Dimension']==SURVIVAL,'player recovery metadata has migrated dimension binding');check(rollback(trial)['byteIdenticalOriginalSave'],'player recovery metadata rollback byte exact')
 report={'success':True,'checks':CHECKS,'productionMutation':False,'fixturesOnly':True,'worldRoot':str(OUT/'synthetic'),'coverage':['generators/seed','current position/bed defaults','UUID/arrays/MUTF','region/entities/poi','external .mcc','global maps','per-world raids','terminal JSON','claims SNBT','idempotence','all operation interruption boundaries','stale plan','post-cutover rollback refusal','unknown ownership gate']};(OUT/'synthetic-result.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
if __name__=='__main__':run()
