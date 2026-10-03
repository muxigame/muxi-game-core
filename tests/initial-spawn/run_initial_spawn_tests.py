"""Compile owned production changes against installed 1.21.1/NeoForge libraries; run focused CPU-only tests.
No Minecraft launch. Search tests exercise the production state machine with controlled futures;
world/provider mixin transformation and native configuration lifecycle require separate real TCP QA.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java-home', type=Path, required=True)
    p.add_argument('--core-jar', type=Path, required=True, help='read-only previous Core jar for unchanged classes')
    p.add_argument('--server', type=Path, default=ROOT.parent/'bmc5server')
    a = p.parse_args()
    jars = list((a.server/'libraries').rglob('*.jar'))
    jars.sort(key=lambda x: (0 if x.name.startswith('neoforge-') and x.name.endswith('-server.jar') else 1, str(x)))
    framework = sorted((ROOT.parent/'muxi-minigames/build/libs').glob('*0.2.1*.jar'))
    if len(framework) != 1: raise SystemExit('Expected one installed minigames 0.2.1 jar')
    jars += [a.core_jar, *framework]
    base = ROOT/'src/main/java/net/muxigame/core'
    sources = sorted((base/'feature/dimensions/initialspawn').glob('*.java'))
    sources += [base/'feature/dimensions/DimensionsFeature.java', base/'mixin/InitialPlayerSpawnMixin.java',base/'mixin/InitialSpawnChunkInvoker.java']
    tests = sorted(Path(__file__).parent.glob('*Test.java'))
    # Ensure the old existing-player smoke entry remains compilable after removal of the synchronous API.
    tests.append(ROOT/'tests/dimensions-smoke/java/net/muxigame/core/taskssmoke/DimensionsSmoke.java')
    cp = os.pathsep.join(str(x.resolve()) for x in jars)
    with tempfile.TemporaryDirectory(prefix='muxi-initial-spawn-tests-') as folder:
        temp=Path(folder);classes=temp/'classes';classes.mkdir()
        args=['--release','21','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),*map(str,sources+tests)]
        argfile=temp/'javac.args'
        argfile.write_text('\n'.join('"'+x.replace('\\','/')+'"' for x in args),encoding='utf-8')
        subprocess.run([str(a.java_home/'bin/javac.exe'),'@'+str(argfile)],check=True)
        for test in ('InitialSpawnSearchTest','InitialSpawnPredictionTest'):
            subprocess.run([str(a.java_home/'bin/java.exe'),'-cp',str(classes)+os.pathsep+cp,test],cwd=temp,check=True)
    config=json.loads((ROOT/'src/main/resources/muxi_game_core.mixins.json').read_text())
    assert 'InitialSpawnChunkInvoker' in config['mixins']
    dims=(base/'feature/dimensions/DimensionsFeature.java').read_text(encoding='utf-8')
    assert 'findInitialSurvivalLanding' not in dims
    for source in sources:
        text=source.read_text(encoding='utf-8')
        assert '.getChunkFuture(' not in text, f'blocking public future API: {source}'
    print(f'PASS: {len(sources)} owned production sources and legacy smoke compiled; no main-thread public getChunkFuture fallback')

if __name__=='__main__':main()
