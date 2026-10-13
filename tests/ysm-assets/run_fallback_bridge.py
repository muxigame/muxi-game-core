"""Original production265 helper/transaction + Core adapter, fixtures and actual MC API compilation."""
import argparse,hashlib,importlib.util,json,os,subprocess,tempfile
from pathlib import Path
CORE=Path(__file__).resolve().parents[2];ROOT=CORE.parent;OUT=CORE/'build/ysm-assets-131'

def argfile(path,args):path.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in args),encoding='utf-8')
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--java-home',required=True,type=Path);parser.add_argument('--gson',required=True,type=Path);args=parser.parse_args()
 OUT.mkdir(parents=True,exist_ok=True);compat=ROOT/'muxi-ysm-compat';helper=list((compat/'src/production265-bridge/java').rglob('*.java'));assert len(helper)==2
 transaction_test=CORE/'tests/ysm-assets/YsmProductionFallbackTest.java';sources=list((CORE/'src/main/java/net/muxigame/core/feature/ysmassets').glob('*.java'))
 minigames=ROOT/'muxi-minigames/src/main/java/net/muxigame/minigames/TrustedAccounts.java';lang=ROOT/'bmc5server/libraries/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar';assert lang.is_file()
 with tempfile.TemporaryDirectory(prefix='fallback-bridge-',dir=OUT) as directory:
  directory=Path(directory);spec=importlib.util.spec_from_file_location('identity_fixture',CORE/'tests/run_terminal_sso_trust.py');fixture=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixture);generated=[]
  for name in ['com/mojang/authlib/GameProfile.java','net/minecraft/server/network/ServerGamePacketListenerImpl.java','net/minecraft/server/MinecraftServer.java','net/minecraft/server/level/ServerPlayer.java']:
   data=fixture.FILES[name]
   if name.endswith('ServerGamePacketListenerImpl.java'):data=data.replace('public boolean online=true;','public net.minecraft.server.level.ServerPlayer player;public boolean online=true;')
   if name.endswith('ServerPlayer.java'):data=data.replace('s.players.values.put(id,this);','connection.player=this;s.players.values.put(id,this);').replace('public java.util.UUID getUUID()', 'public net.minecraft.server.MinecraftServer getServer(){return server;}public java.util.UUID getUUID()')
   p=directory/'types'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(data,encoding='utf-8');generated.append(p)
  tick=directory/'types/net/neoforged/neoforge/event/tick/ServerTickEvent.java';tick.parent.mkdir(parents=True);tick.write_text('package net.neoforged.neoforge.event.tick; public class ServerTickEvent {public static class Post {}}');generated.append(tick)
  classes=directory/'classes';classes.mkdir();classpath=os.pathsep.join(map(str,[args.gson,lang]));compile_args=directory/'compile.args';argfile(compile_args,['--release','21','-encoding','UTF-8','-proc:none','-classpath',classpath,'-d',classes,*generated,*sources,minigames,*helper,transaction_test])
  subprocess.run([str(args.java_home/'bin/javac.exe'),'@'+str(compile_args)],check=True)
  snapshot=OUT/'intake-core-snapshot.json';assert snapshot.is_file(), 'Run the signed joint intake contract first'
  tested=subprocess.run([str(args.java_home/'bin/java.exe'),'-Xmx96m','-cp',str(classes)+os.pathsep+classpath,'net.muxigame.ysmcompat.production265.YsmProductionFallbackTest',str(snapshot)],check=True,capture_output=True,text=True)
  result=json.loads(tested.stdout)
 # Compile original helper and Core adapter against actual MC/NeoForge classes without fixture types.
 old=[line[1:-1] if line.startswith('"') and line.endswith('"') else line for line in (OUT/'auth-native-api.args').read_text(encoding='utf-8').splitlines() if line]
 native_classes=OUT/'intake-fallback-native-api-classes';native_classes.mkdir(exist_ok=True);old[old.index('-d')+1]=str(native_classes)
 own_new=CORE/'src/main/java/net/muxigame/core/feature/ysmassets/YsmProduction265Fallback.java'
 for p in [own_new,*helper]:
  if str(p).replace('\\','/') not in [a.replace('\\','/') for a in old]:old.append(str(p))
 native_args=OUT/'intake-fallback-native-api.args';argfile(native_args,old);subprocess.run([str(args.java_home/'bin/javac.exe'),'-J-Xmx384m','@'+str(native_args)],check=True)
 result.update(actualMinecraft1211NeoForgeApiCompiled=True,actualNativeCompileFixtureTypesUsed=False,productionCredentialsReadOrCreated=False,minecraftStarted=False,nativeExecuted=False,sourceHashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in [own_new,*helper,transaction_test]})
 (OUT/'fallback-bridge-result.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))
if __name__=='__main__':main()
