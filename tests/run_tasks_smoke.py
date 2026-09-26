"""Build a test-only mod and boot a disposable loopback-only server using existing local libraries.

Never installs anything in bmc5server or pack/source. No world copies, credentials, downloads or real players.
Logs/world/test JARs are retained under build/tasks-smoke-* for inspection.
"""
from __future__ import annotations
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import build as core_build


def run() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, default=ROOT.parent / 'bmc5server')
    parser.add_argument('--integrations', action='store_true', help='Exercise the installed TaCZ, Champions and Create mods in a disposable lab')
    parser.add_argument('--extended', action='store_true', help='Also exercise native maid/golem ownership, crops, flowers and mod foods')
    args = parser.parse_args()
    server = args.server.resolve()
    release = json.loads((ROOT / 'build/release.json').read_text(encoding='utf-8'))
    core = ROOT / 'build/libs' / release['artifact']
    lab = ROOT / 'build' / ('tasks-smoke-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
    lab.mkdir(parents=True, exist_ok=False)
    (lab / 'mods').mkdir(); (lab / 'config').mkdir()
    shutil.copy2(core, lab / 'mods' / core.name)
    dependencies=[]
    if args.integrations or args.extended:
        patterns=['tacz-neoforge*.jar','*champions*.jar','architectury-*.jar','*create-1.21*.jar']
        if args.extended: patterns+=['*touhoulittlemaid-*.jar','*modulargolems-*.jar','*l2library-*.jar','FarmersDelight*.jar','rightclickharvest*.jar','jamlib-*.jar']
        for pattern in patterns:
            found=list((server/'mods').glob(pattern))
            if len(found)!=1: raise SystemExit(f'Expected one existing integration dependency: {pattern}')
            dependencies.append(found[0]); shutil.copy2(found[0],lab/'mods'/found[0].name)
    (lab / 'config/muxi-game-core.json').write_text('{"schema":1,"features":{}}', encoding='utf-8')
    if not args.integrations and not args.extended:
        # This fixture specifically exercises the original statistics path; event paths run separately.
        config=json.loads((ROOT/'src/main/resources/muxi/daily-tasks-defaults.json').read_text(encoding='utf-8'))
        config['hardCount']=0
        config['pool']=[d for d in config['pool'] if d['kind'] in ('mined','custom','defeated')]
        for d in config['pool']:
            if d['kind']=='defeated': d['kind']='killed'  # Explicit legacy-stat regression fixture only.
        (lab/'config/muxi-daily-tasks.json').write_text(json.dumps(config,ensure_ascii=False),encoding='utf-8')
    (lab / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
    (lab / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=25586\nonline-mode=false\nlevel-name=qa-world\n'
        'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n'
        'view-distance=2\nsimulation-distance=2\nmax-players=1\n'
        'enable-rcon=false\nspawn-protection=0\nnetwork-compression-threshold=-1\n', encoding='utf-8')
    compiler, runtime = core_build.java_tools(None)
    java21 = ROOT.parent / 'perf-lab/java21/jdk-21.0.2/bin/java.exe'
    if java21.is_file(): runtime = java21
    jars = sorted((server / 'libraries').rglob('*.jar'))
    neo = server / 'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-server.jar'
    mapped = next(p for p in jars if p.name == 'server-1.21.1-20240808.144430-srg.jar')
    nested=lab/'compile-nested'; nested.mkdir()
    extra=[p for jar in dependencies for p in core_build.nested_jars(jar,nested)]
    cp = os.pathsep.join(str(p) for p in [core, neo, mapped, *jars, *dependencies, *extra])
    classes = lab / 'test-classes'
    sources='tests/extended-smoke/java' if args.extended else 'tests/integration-smoke/java' if args.integrations else 'tests/smoke/java'
    core_build.compile_java(compiler, sorted((ROOT / sources).rglob('*.java')), classes, cp, lab / 'compile.args')
    with zipfile.ZipFile(lab / 'mods/muxi-tasks-smoke-only.jar', 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/neoforge.mods.toml', 'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n'
                   '[[mods]]\nmodId="muxi_tasks_smoke"\nversion="1.0.0"\ndisplayName="Isolated task smoke tests"\n'
                   '[[dependencies.muxi_tasks_smoke]]\nmodId="muxi_game_core"\ntype="required"\nversionRange="[1.5.0,)"\nordering="AFTER"\nside="SERVER"\n')
        for p in classes.rglob('*.class'): z.write(p, p.relative_to(classes).as_posix())
        if args.extended:
            for registry in ['block','item']:
                z.writestr(f'data/minecraft/tags/{registry}/flowers.json',json.dumps({'replace':False,'values':['muxi_tasks_smoke:test_flower']}))
            z.writestr('data/muxi_tasks_smoke/loot_table/blocks/test_flower.json',json.dumps({
                'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'muxi_tasks_smoke:test_flower'}]}]}))
    launch = shlex.split((server / 'libraries/net/neoforged/neoforge/21.1.250/win_args.txt').read_text(encoding='utf-8'))
    libraries = (server / 'libraries').as_posix()
    launch = [s.replace('libraries/', libraries + '/').replace('-DlibraryDirectory=libraries', '-DlibraryDirectory=' + libraries) for s in launch]
    command = [str(runtime), '-Xms512M', '-Xmx2G', '-XX:ActiveProcessorCount=4', '-Dfile.encoding=UTF-8', *launch, '--nogui']
    with (lab / 'boot.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=lab, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
        try: code = process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
            raise SystemExit(f'Isolated smoke timed out; logs: {lab / "boot.log"}')
    result_file = lab / 'tasks-smoke-result.json'
    result = json.loads(result_file.read_text(encoding='utf-8')) if result_file.exists() else {'success': False, 'error': 'No test result'}
    result['exitCode'] = code; result['lab'] = str(lab)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if not result.get('success') or code:
        print('\n'.join((lab / 'boot.log').read_text(encoding='utf-8', errors='replace').splitlines()[-90:]))
        raise SystemExit(1)


if __name__ == '__main__': run()
