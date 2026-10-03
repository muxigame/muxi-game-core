"""Build a QA-only native input observer for an existing local_mc_debug hold lab.

No downloads, runtime launch, source mutation or operating-system input injection.
"""
from pathlib import Path
import argparse,json,os,subprocess,sys,zipfile

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ['debug-project-root','server-runtime','client-game','java-home','output','core','framework','tacz']:
        parser.add_argument('--'+key,type=Path,required=True)
    args=parser.parse_args()
    sys.path.insert(0,str(args.debug_project_root.resolve(strict=True)/'scripts'))
    import local_mc_runtime as rt
    server,game,jdk=[p.resolve(strict=True) for p in [args.server_runtime,args.client_game,args.java_home]]
    deps=[p.resolve(strict=True) for p in [args.core,args.framework,args.tacz]]
    metadata=rt.client_metadata(game,'BatterMC5Remake');libs=rt.client_libraries(game,'BatterMC5Remake',metadata)
    cp=rt.javac_classpath(server,game,'21.1.250',libs)+os.pathsep+os.pathsep.join(map(str,deps))
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=False);classes=output/'classes';classes.mkdir()
    sources=list((Path(__file__).resolve().parent/'input-link-qa').glob('*.java'))
    argfile=output/'javac.args';rt.argfile(argfile,['--release','21','-encoding','UTF-8','-proc:none','-classpath',cp,'-d',classes,*sources])
    with (output/'compile.log').open('wb') as log:
        result=subprocess.run([str(jdk/'bin/javac.exe'),'@'+str(argfile)],stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:raise RuntimeError('QA compilation failed: '+str(output/'compile.log'))
    config={'required':True,'minVersion':'0.8','package':'net.muxigame.inputlinkqa.mixin','compatibilityLevel':'JAVA_21','mixins':['ConnectionProbeMixin','GameRequestProbeMixin'],'client':['TaczProbeMixin','KeyboardProbeMixin'],'injectors':{'defaultRequire':1}}
    toml='modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="input_link_qa"\nversion="0.0.1"\ndisplayName="Input linkage QA ONLY"\n[[mixins]]\nconfig="input-link-qa.mixins.json"\n'
    jar=output/'input-link-QA-ONLY.jar'
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/neoforge.mods.toml',toml);z.writestr('input-link-qa.mixins.json',json.dumps(config))
        for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
    print(json.dumps({'artifact':str(jar),'sha256':rt.sha256(jar),'physicalOSInput':False}))

if __name__=='__main__':main()
