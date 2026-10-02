from pathlib import Path
import importlib.util,os,shutil,json

root=Path(__file__).resolve().parent
workspace=Path('C:/Users/Administrator/WorkSpace/muxigame')
sdk=Path('C:/Program Files/Java/jdk-24')
pack=workspace/'better-mc-remake/pack/source/Better MC Remake [FORGE]/mods'
framework=root/'muxi-minigames/build/libs/muxi-minigames-0.1.1.jar'
framework.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(root.parent/'sso-integration/muxi-minigames/build/libs/muxi-minigames-0.1.1.jar',framework)
aggregate=root.parent.parent/'task-21/music-app/build/compiler-dependencies.jar'
for repo in ['muxi-game-core','muxi-terminal']:
 spec=importlib.util.spec_from_file_location('candidate_build',root/repo/'build.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 original=module.compile_java
 def compile_java(compiler,sources,output,classpath,argfile):
  extra=[aggregate,*sorted(pack.glob('*.jar')),framework]
  return original(compiler,sources,output,os.pathsep.join(map(str,extra))+os.pathsep+classpath,argfile)
 module.compile_java=compile_java
 if repo=='muxi-game-core':module.build(workspace/'bmc5server',sdk,False,workspace/'_client_test/game',pack)
 else:module.build(workspace/'bmc5server',sdk,workspace/'_client_test/game',pack)
