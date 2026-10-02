from pathlib import Path
import subprocess,tempfile,json
root=Path(__file__).resolve().parent;out=root/'evidence';out.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(dir=out) as tmp:
    tmp=Path(tmp);sources=[root/'muxi-game-core/src/main/java/net/muxigame/core/feature/waystones/network/NetworkPermissionPolicy.java',root/'NetworkPermissionPolicyTest.java'];args=tmp/'args';args.write_text('--release\n21\n-encoding\nUTF-8\n-d\n"'+str(tmp/'classes').replace('\\','/')+'"\n'+'\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources),encoding='utf-8')
    subprocess.run(['C:/Program Files/Java/jdk-24/bin/javac.exe','@'+str(args)],check=True,capture_output=True)
    result=subprocess.run(['C:/Program Files/Java/jdk-24/bin/java.exe','-cp',str(tmp/'classes'),'NetworkPermissionPolicyTest'],check=True,capture_output=True)
    report=json.loads(result.stdout);(out/'policy-result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
