from pathlib import Path
import subprocess,json,os,tempfile,shutil,hashlib,zipfile

root=Path(__file__).resolve().parent;task=root.parent;out=task/'native-map-full-review';out.mkdir(exist_ok=True)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def git(repo,*args,env=None):
 return subprocess.run(['git','-C',str(repo),*args],env=env,capture_output=True,check=True).stdout
selections={
 'muxi-terminal':['src/main/java/net/muxigame/terminal/client/map/NativeMapLauncher.java','src/main/java/net/muxigame/terminal/client/TerminalNativeMapBridge.java','src/main/java/net/muxigame/terminal/client/TerminalNativeBridge.java','src/main/resources/assets/muxi_terminal/html/terminal/native-map-app.js','src/main/resources/assets/muxi_terminal/html/terminal/index.html'],
 'muxi-game-core':[
 'src/main/java/net/muxigame/core/feature/waystones/WaystoneMapNetwork.java',
 *['src/main/java/net/muxigame/core/feature/waystones/network/'+n+'.java' for n in ['NetworkPermissionPolicy','NetworkFacilityConfig','NetworkPortalFacilities','NetworkTeleportGuard']],
 'src/main/java/net/muxigame/core/compat/mixin/waystones/NetworkPendingTeleportMixin.java',
 'src/main/java/net/muxigame/core/compat/CompatMixinPlugin.java',
 *['src/main/java/net/muxigame/core/client/waystones/'+n+'.java' for n in ['WaystoneMapClient','NativeStoneRenderer','StoneLabelLayout']],
 *['src/main/java/net/muxigame/core/compat/mixin/xaeroworldmap/'+n+'.java' for n in ['WaystoneWaypointMenuMixin','NetworkStoneRendererMixin','NetworkMapFrameMixin']],
 'src/main/resources/muxi_game_core.compat.maps.mixins.json',
 'src/main/resources/assets/muxi_game_core/lang/en_us.json','src/main/resources/assets/muxi_game_core/lang/zh_cn.json']}
records=[];tests=out/'tests';tests.mkdir(exist_ok=True)
for name,files in selections.items():
 repo=root/name;baseline=json.loads((root/(name+'-baseline.json')).read_text());git(repo,'add','--',*files)
 patch=out/(name+'-native-map-full.patch');patch.write_bytes(git(repo,'diff','--cached','--binary','--full-index',baseline['indexTree'],'--',*files))
 with tempfile.TemporaryDirectory(dir=root) as temp:
  env=dict(os.environ,GIT_INDEX_FILE=str(Path(temp)/'index'));git(repo,'read-tree',baseline['indexTree'],env=env)
  git(repo,'apply','--cached','--check',str(patch),env=env);git(repo,'apply','--cached',str(patch),env=env)
  for file in files:
   staged=git(repo,'show',':'+file)
   assert git(repo,'show',':'+file,env=env)==staged
   target=tests/name/file;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(staged)
 records.append({'repository':name,'baseHead':baseline['head'],'baseIndexTree':baseline['indexTree'],'selectedFiles':files,'patch':patch.name,'sha256':sha(patch),'cleanBaselineApplyCheck':True})
 # Only new selected files enter a patch. Other pre-existing SSO/friends/game/index changes are excluded.
 diff=git(repo,'diff','--cached','--name-only',baseline['indexTree']).decode().splitlines()
 assert set(diff)==set(files),diff

# The unmodified native dimension accessor is a compile fixture, never another source delta.
accessor='src/main/java/net/muxigame/core/compat/mixin/xaeroworldmap/GuiMapAccessor.java'
target=tests/'muxi-game-core'/accessor;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(root/'muxi-game-core'/accessor,target)
for name in ['test_native_entry.py','test_policy.py','test_ui_entry.cjs','NetworkPermissionPolicyTest.java','test_network_runtime.py','test_network_client.py','test_facility_config.py','test_native_sdk.py']:
 shutil.copyfile(root/name,tests/name)
evidence=out/'evidence';evidence.mkdir(exist_ok=True);reports=[]
shutil.copyfile(task/'native-map-131-qa/access-preflight.json',evidence/'native-131-access-preflight.json')
for p in sorted((root/'evidence').glob('*-result.json')):
 report=json.loads(p.read_text('utf-8'));assert report['success'];reports.append({'file':p.name,'checks':report['checks'],'scope':report.get('scope','')});shutil.copyfile(p,evidence/p.name)
assert len(reports)==7
checks=sum(r['checks'] for r in reports);assert checks==191,checks
releases=[]
for name in selections:
 release=json.loads((root/name/'build/release.json').read_text());jar=root/name/'build/libs'/release['artifact'];assert sha(jar)==release['sha256']
 review=out/(name+'-native-map-full-review.jar');shutil.copyfile(jar,review);releases.append(dict(release,repository=name,reviewFile=review.name,privateCandidateOnly=True))

frozen={'stable':'9a3aba2103bc93687f572eb0123562f0e0577c7bdf2fca3bcd6786d19aed3fad','friends':'5a5519c18ba95e526f5f8afc02f6af0e753fbde164ef9282b65089cdfcdbf2a0','preliminaryMap':'5ca16c9d7d8a19b9b2427d65b781006f4576e656520b9c0e995d7490a4c91603'}
for key,file in [('stable','task6-current-integration-review.zip'),('friends','task6-friends-increment-review.zip'),('preliminaryMap','task6-native-map-entry-review.zip')]:assert sha(task/file)==frozen[key]
manifest={'purpose':'complete isolated native Xaero transmission-network implementation for parent integration and native QA; no deployment','revision':7,'patches':records,'privateReviewBuilds':releases,'tests':reports,'executedBehaviorChecks':checks-23,'installedSdkStaticChecks':23,'totalLightChecks':checks,'realMinecraftOrMCEFTests':False,'liveMixinApplicationTested':False,'developerNativeQARequired':True,'developerNativeQAComplete':False,'releaseReady':False,'acceptanceOwner':'task6 developer, not task14','nativeQAInteractiveBlocker':'Authorized JBC-1 SSH verified and own 131 QA kit prepared; task14 one-time coordinated interactive start of fixed task6 entry pending','featureWiringComplete':True,'serverWorldRegistryAdapterWired':True,'crossDimensionGuardWired':True,'nativeMarkerRenderingWired':True,'actualServerFacilityProvisioned':False,'MKeyChanged':False,'embeddedMapImplemented':False,'playerGateGrantsAuthority':False,'originalWaystonesRulesPreserved':True,'sharedSourcesModified':False,'frozenPublicationModified':False,'oldStableArtifacts':frozen,'closeXFixIncluded':False,'cameraFixIncluded':False,'remainingValidation':'Task6 developer-owned native client/server integration QA: actual Mixin load, GL/UI clarity/dense labels, valid registered gate, native XP/activation/cooldown/events and real arrival. Own 131 files prepared through verified JBC-1 SSH; no native QA or 008 heavy MC started; no live game occupied.'}
(out/'MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(out/'teleport-network.example.json').write_text(json.dumps({'version':1,'associationRadius':4,'associationVertical':3,'entranceRadius':4,'entranceVertical':3,'facilities':[]},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for name in ['CURRENT-NATIVE-MAP-REQUIREMENTS.txt','INTERFACE-TESTS-current.json','interface-preparation-result.json','installed-mods.json']:
 shutil.copyfile(task/'teleport-network-investigation'/name,out/name)
shutil.copyfile(root/'FULL-REVIEW-README.txt',out/'README.txt')
shutil.copyfile(root/'build_candidate.py',out/'build_candidate.py')
archive=task/'task6-native-map-full-review.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
 for file in sorted(out.rglob('*')):
  if file.is_file() and '__pycache__' not in file.parts:z.write(file,file.relative_to(out).as_posix())
print(json.dumps({'success':True,'archive':archive.name,'sha256':sha(archive),'size':archive.stat().st_size,'files':len(zipfile.ZipFile(archive).namelist()),'selectedFiles':sum(len(r['selectedFiles']) for r in records),'patches':records,'privateBuilds':releases,'totalLightChecks':checks,'realMinecraftTests':False},ensure_ascii=False))
