"""Prepare offline by default; --run additionally requires an explicit music-owner slot receipt."""
from pathlib import Path
import argparse, hashlib, json, os, socket, subprocess, sys, time, zipfile
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/home-spawning-20261013'
JAVA = Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
PYTHON = sys.executable

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p, obj): p.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding='utf-8')
def checked(command, evidence):
    run=subprocess.run(command, capture_output=True)
    output=(run.stdout+run.stderr).decode('utf-8', errors='replace')
    evidence.write_text(output, encoding='utf-8')
    if run.returncode: raise RuntimeError(f'Offline check failed: {evidence}')
    return output

def prepare():
    candidate=json.loads((OUT/'guard-candidate.json').read_text())
    jar=Path(candidate['jar'])
    assert sha(jar)==candidate['sha256']
    with zipfile.ZipFile(jar) as z: packed_home=[n for n in json.loads(z.read('muxi_game_core.mixins.json'))['mixins'] if n.startswith('home.')]
    source_home=[n for n in json.loads((ROOT/'src/main/resources/muxi_game_core.mixins.json').read_text())['mixins'] if n.startswith('home.')]
    assert packed_home==source_home, 'Owned home mixin registration changed after candidate build'
    for relative, expected in candidate['ownedSourceHashes'].items():
        assert sha(ROOT/relative)==expected, f'Candidate stale for source {relative}'
    cp=str(jar)+';'+(OUT/'guard-classpath.txt').read_text()+';'+str(OUT/'production-customnpcs-stock4.jar')
    args=['--release','21','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(OUT/'native-qa-classes'),str(ROOT/'tests/home-spawning/native/HomeSpawnNativeQA.java')]
    af=OUT/'native-qa-compile.args'
    af.write_text('\n'.join('"'+x.replace('\\','/')+'"' for x in args), encoding='utf-8')
    checked([str(JAVA/'bin/javac.exe'),'@'+str(af)], OUT/'native-qa-compile-result.txt')
    qa=OUT/'muxi-home-spawn-native-QA-ONLY.jar'
    with zipfile.ZipFile(qa, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/neoforge.mods.toml', 'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_home_spawn_qa"\nversion="1.0"\ndisplayName="Home spawning isolated QA ONLY"\n')
        for f in sorted((OUT/'native-qa-classes').rglob('*.class')): z.write(f, f.relative_to(OUT/'native-qa-classes').as_posix())
    checked([str(JAVA/'bin/java.exe'),'-Xmx512m','-cp',str(OUT/'target-audit-classes')+';'+cp,'MixinTargetAudit',str(jar),str(OUT/'audit-target-jars.txt')],OUT/'target-audit-result.txt')
    mods=ROOT.parent/'bmc5server/mods'
    names=['waystones-neoforge-1.21.1-21.1.42.jar','balm-neoforge-1.21.1-21.0.65.jar','simplenicknames-1.21.1-neoforge-0.8.0.jar','tacz-neoforge-1.21.1-1.1.8-hotfix-r6.jar','iceandfire-2.0.jar','uranus-2.4.1-1.21.1-neoforge.jar','jupiter-2.3.7-1.21.1-neoforge.jar','architectury-13.0.11-neoforge.jar','alexsmobs-1.22.17.jar','citadel-1.21.1-2.7.6.jar','easy_npc-neoforge-1.21.1-7.12.1.jar','easy_npc_config_ui-neoforge-1.21.1-7.12.1.jar']
    inputs=[jar,qa,OUT/'production-customnpcs-stock4.jar',ROOT.parent/'muxi-minigames/build/libs/muxi-minigames-0.2.1-equipment.1.jar',*[mods/n for n in names]]
    for f in inputs: assert f.is_file(), f'Missing pinned runtime input: {f}'
    proof={'preparedOnly':True,'minecraftStarted':False,'candidateSha256':sha(jar),'qaSourceSha256':sha(ROOT/'tests/home-spawning/native/HomeSpawnNativeQA.java'),'inputs':[{'path':str(f),'sha256':sha(f)} for f in inputs],'heapMiB':4096,'activeProcessors':2,'clients':0,'productionMutation':False,'resourceSlotConfirmed':False,'nativeAcceptancePassed':False}
    write(OUT/'native-preparation.json',proof)
    return inputs,proof

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--run',action='store_true')
    ap.add_argument('--music-slot-confirmation',help='Exact confirmation received from the CURRENT music owner; omission prohibits MC startup')
    args=ap.parse_args()
    inputs,proof=prepare()
    print(json.dumps({'prepared':True,'candidateSha256':proof['candidateSha256'],'manifest':str(OUT/'native-preparation.json'),'minecraftStarted':False}),flush=True)
    if not args.run: return
    if not args.music_slot_confirmation: raise ValueError('Current music-owner resource confirmation is required before MC startup')
    sys.path.insert(0,str(ROOT.parent/'better-mc-remake/scripts'))
    import local_mc_debug as debug
    # Adjust only this coordinator process, never edit the shared runtime helper.
    original_args=debug.rt.server_arguments
    debug.rt.server_arguments=lambda *a,**k:[('-XX:ActiveProcessorCount=2' if v.startswith('-XX:ActiveProcessorCount=') else v) for v in original_args(*a,**k)]
    original_write=debug.rt.write_json
    def private_write(path,obj):
        if Path(path).name=='muxi-game-core.json' and isinstance(obj,dict) and 'features' in obj:
            obj={**obj,'features':{**obj['features'],'terminalSso':{'enabled':False},'opSync':{'enabled':False}}}
        return original_write(path,obj)
    debug.rt.write_json=private_write
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
    project=ROOT.parent/'better-mc-remake'
    lab=project/'build/local-mc-debug'/('home-spawn-native-'+time.strftime('%Y%m%d-%H%M%S'))
    proof.update({'resourceSlotConfirmed':True,'musicSlotReceipt':args.music_slot_confirmation,'instanceRoot':str(lab),'port':port})
    write(OUT/'native-launch-receipt.json',proof)
    sys.argv=[str(project/'scripts/local_mc_debug.py'),'run','--project-root',str(project),'--instance-root',str(lab),'--server-runtime',str(ROOT.parent/'bmc5server'),'--client-game',str(ROOT.parent/'_client_test/game'),'--java-home',str(JAVA),'--port',str(port),'--clients','0','--server-only','--mode','hold','--server-memory-mb','4096','--approved-heap-budget-mb','4096','--accept-eula','--hold-seconds','60','--boot-timeout','90','--shutdown-timeout','30','--unix-temp',str(ROOT.parent/'build/home-spawn-unix-temp')]
    for f in inputs: sys.argv.extend(['--server-mod',str(f)])
    for prop in ['http.proxyHost=127.0.0.1','http.proxyPort=9','https.proxyHost=127.0.0.1','https.proxyPort=9']:sys.argv.extend(['--jvm-property',prop])
    debug.main()
    result=lab/'server/home-spawn-native-result.json'
    lifecycle=lab/'run-result.json'
    report={'instanceRoot':str(lab),'candidateSha256':proof['candidateSha256'],'qaResult':json.loads(result.read_text()) if result.exists() else None,'lifecycle':json.loads(lifecycle.read_text()) if lifecycle.exists() else None,'productionMutation':False}
    owner=json.loads((lab/'local-mc-owner.json').read_text())
    report['minecraftStarted']=bool(owner.get('processes'))
    report['qaChecksPassed']=len(report['qaResult'].get('checks',[])) if report['qaResult'] else 0
    write(OUT/'native-acceptance.json',report)
    if not report['qaResult'] or not report['qaResult'].get('success'): raise RuntimeError('Native fixture has not passed; inspect isolated lab boot.log and result')
    if not report['lifecycle'] or not report['lifecycle'].get('normalExit') or report['lifecycle'].get('exitCodes',{}).get('server')!=0: raise RuntimeError('Owned server has not exited normally')

if __name__=='__main__': main()
