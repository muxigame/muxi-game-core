"""Compile a QA-only host containing the exact production protection module; never deploy this jar."""
import argparse,importlib.util,json,os,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);a=p.parse_args()
 spec=importlib.util.spec_from_file_location('core_build',ROOT/'build.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
 out=ROOT/'build/adventure-protection';out.mkdir(parents=True,exist_ok=True)
 jars=sorted((a.server/'libraries').rglob('*.jar'));mapped=[j for j in jars if j.name.endswith('-srg.jar')]
 patched=[j for j in jars if j.name=='neoforge-21.1.250-server.jar']
 cp=os.pathsep.join(map(str,patched+mapped+[j for j in jars if j not in patched+mapped]))
 paths=['src/main/java/net/muxigame/core/feature/dimensions/WorldDimensions.java','src/main/java/net/muxigame/core/feature/rules/AdventureEnvironmentProtection.java','src/main/java/net/muxigame/core/mixin/AdventureFirePlacementMixin.java','src/main/java/net/muxigame/core/mixin/AdventureFireTickMixin.java','tests/adventure-environment/AdventureEnvironmentSmoke.java']
 compiler,_=b.java_tools(a.java_home);classes=out/'qa-classes';b.compile_java(compiler,[ROOT/x for x in paths],classes,cp,out/'qa-javac.args')
 target=out/'adventure-environment-qa.jar'
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
  for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
  z.write(ROOT/'src/main/resources/muxi_game_core.adventure_protection.mixins.json','muxi_game_core.adventure_protection.mixins.json')
  z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_game_core"\nversion="0.0.0-qa"\ndisplayName="Adventure protection QA ONLY"\n[[mixins]]\nconfig="muxi_game_core.adventure_protection.mixins.json"\n')
  for name in ['adventure','overworld']:
   z.writestr('data/muxi_game_core/dimension/'+name+'.json',json.dumps({'type':'minecraft:overworld','generator':{'type':'minecraft:flat','settings':{'biome':'minecraft:plains','layers':[{'height':1,'block':'minecraft:bedrock'}],'features':False,'lakes':False,'structure_overrides':[]}}}))
 print(target)
if __name__=='__main__':main()
