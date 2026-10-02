from pathlib import Path
import subprocess,json,os,tempfile,shutil,hashlib,zipfile
root=Path(__file__).resolve().parent;task=root.parent;out=task/'native-map-entry-review';out.mkdir(exist_ok=True)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def git(repo,*args,env=None):return subprocess.run(['git','-C',str(repo),*args],env=env,capture_output=True,check=True).stdout
selections={
 'muxi-terminal':['src/main/java/net/muxigame/terminal/client/map/NativeMapLauncher.java','src/main/java/net/muxigame/terminal/client/TerminalNativeMapBridge.java','src/main/java/net/muxigame/terminal/client/TerminalNativeBridge.java','src/main/resources/assets/muxi_terminal/html/terminal/native-map-app.js','src/main/resources/assets/muxi_terminal/html/terminal/index.html'],
 'muxi-game-core':['src/main/java/net/muxigame/core/feature/waystones/network/NetworkPermissionPolicy.java']}
records=[]
for name,files in selections.items():
 repo=root/name;baseline=json.loads((root/(name+'-baseline.json')).read_text());git(repo,'add','--',*files)
 patch=out/(name+'-native-map-entry.patch');patch.write_bytes(git(repo,'diff','--cached','--binary','--full-index',baseline['indexTree'],'--',*files))
 with tempfile.TemporaryDirectory(dir=root) as temp:
  env=dict(os.environ,GIT_INDEX_FILE=str(Path(temp)/'index'));git(repo,'read-tree',baseline['indexTree'],env=env);git(repo,'apply','--cached','--check',str(patch),env=env);git(repo,'apply','--cached',str(patch),env=env)
  for file in files:assert git(repo,'show',':'+file,env=env)==git(repo,'show',':'+file)
 records.append({'repository':name,'baseHead':baseline['head'],'baseIndexTree':baseline['indexTree'],'selectedFiles':files,'patch':patch.name,'sha256':sha(patch),'cleanBaselineApplyCheck':True})
evidence=out/'evidence';evidence.mkdir(exist_ok=True)
for p in (root/'evidence').glob('*-result.json'):shutil.copyfile(p,evidence/p.name)
tests=out/'tests';tests.mkdir(exist_ok=True)
for name in ['test_native_entry.py','test_policy.py','test_ui_entry.cjs','NetworkPermissionPolicyTest.java']:shutil.copyfile(root/name,tests/name)
investigation=task/'teleport-network-investigation'
for name in ['CURRENT-NATIVE-MAP-REQUIREMENTS.txt','INTERFACE-TESTS-current.json','interface-preparation-result.json','installed-mods.json']:shutil.copyfile(investigation/name,out/name)
release=json.loads((root/'muxi-terminal/build/release.json').read_text());assert sha(root/'muxi-terminal/build/libs/muxi-terminal-0.2.2.jar')==release['sha256']
manifest={'purpose':'native Xaero entry and isolated policy review, not full feature or deployment','patches':records,'terminalCompile':release,'terminalJarOmittedFromDelivery':True,'realMinecraftOrMCEFTests':False,'MKeyChanged':False,'embeddedMapImplemented':False,'serverWorldRegistryAdapterWired':False,'crossDimensionGuardActive':False,'markerRenderingWired':False,'sharedSourcesModified':False,'frozenPublicationModified':False,'priorStableZipSha256':sha(task/'task6-current-integration-review.zip'),'priorFriendsZipSha256':sha(task/'task6-friends-increment-review.zip')}
assert manifest['priorStableZipSha256']=='9a3aba2103bc93687f572eb0123562f0e0577c7bdf2fca3bcd6786d19aed3fad';assert manifest['priorFriendsZipSha256']=='5a5519c18ba95e526f5f8afc02f6af0e753fbde164ef9282b65089cdfcdbf2a0'
(out/'MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'success':True,'patches':2,'selectedFiles':6,'cleanBaselineApplyChecks':True,'fullTerminalCompile':True,'priorArtifactsUnchanged':True,'fullFeatureImplemented':False}))
