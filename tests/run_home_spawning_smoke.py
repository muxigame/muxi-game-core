"""Run native spawn paths on a fresh, socket-free NeoForge server. Never installs or reloads production."""
from __future__ import annotations
import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import build as core_build
from integrated_fixture import install as install_integrated_fixture


def run():
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--server', type=Path, default=ROOT.parent/'bmc5server')
    parser.add_argument('--full-pack', action='store_true')
    parser.add_argument('--isolate-collective-update-checker', action='store_true',
                        help='Diagnostic only: bypass the unrelated Collective update checker; does not fix runtime sockets')
    args = parser.parse_args()
    if args.isolate_collective_update_checker and not args.full_pack:
        parser.error('--isolate-collective-update-checker requires --full-pack')
    server = args.server.resolve()
    release = json.loads((ROOT/'build/release.json').read_text(encoding='utf-8'))
    core = ROOT/'build/libs'/release['artifact']
    lab = ROOT/'build'/('home-spawning-smoke-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    (lab/'mods').mkdir(parents=True)
    (lab/'config').mkdir()
    shutil.copy2(core, lab/'mods'/core.name)
    compiler, runtime = core_build.java_tools(args.java_home)
    print('Isolated lab: '+str(lab), flush=True)
    if args.full_pack:
        # Real HttpClient startup, without mods, network requests or lifecycle bypasses.
        # Detect a broken JDK local selector before copying/booting the entire pack.
        with (lab/'http-runtime-preflight.log').open('w', encoding='utf-8') as log:
            probe = subprocess.run([str(runtime), str(ROOT/'tests/socket-runtime-probe/SocketRuntimeProbe.java'),
                                    'http', str(Path(tempfile.gettempdir()).resolve())],
                                   stdout=log, stderr=subprocess.STDOUT, timeout=30)
        output = (lab/'http-runtime-preflight.log').read_text(encoding='utf-8', errors='replace')
        if probe.returncode or 'success=true' not in output:
            result = {'success':False, 'passed':[], 'fullPack':True, 'serverStarted':False,
                      'error':'JDK HttpClient local selector initialization failed; see http-runtime-preflight.log',
                      'failureStage':'http-runtime-preflight', 'probeExitCode':probe.returncode,
                      'coreSha256':hashlib.sha256(core.read_bytes()).hexdigest(), 'lab':str(lab),
                      'excludedUnrelatedLifecycle':None, 'excludedCollectiveUpdateChecker':False}
            (lab/'home-spawning-result.json').write_text(json.dumps(result,indent=2), encoding='utf-8')
            print(json.dumps(result,indent=2))
            raise SystemExit(1)
    dependencies = []
    if args.full_pack:
        dependencies = [p for p in (server/'mods').glob('*.jar')
                        if not p.name.startswith(('muxi-game-core-', 'muxi-identity-', 'muxi-terminal-', 'muxi-outbreak-', 'muxi-minigames-', 'muxi-zombie-challenge-'))]
    else:
        for pattern in ('waystones-neoforge*.jar', 'balm-neoforge*.jar', 'goblintraders*.jar', 'framework-neoforge*.jar'):
            found = list((server/'mods').glob(pattern))
            if len(found) != 1: raise SystemExit('Expected one local dependency: '+pattern)
            dependencies.extend(found)
    for path in dependencies: shutil.copy2(path, lab/'mods'/path.name)
    dependencies.extend(install_integrated_fixture(lab, ROOT, args.full_pack))
    # Fresh configs and world; never copy account configuration, world or player data.
    (lab/'config/muxi-game-core.json').write_text('{"schema":1,"features":{}}', encoding='utf-8')
    (lab/'config/neoforge-server.toml').write_text('advertiseDedicatedServerToLan = false\n')
    (lab/'config/fml.toml').write_text('versionCheck = false\n')
    if args.full_pack:
        shutil.copy2(server/'config/fml.toml', lab/'config/fml.toml')
        (lab/'config/webdisplays_common.toml').write_text('[mini_server]\nminiserv_port = 0\n')
        # Include actual public KubeJS scripts to check interaction with this fix.
        for name in ('server_scripts', 'startup_scripts', 'data'):
            if (server/'kubejs'/name).is_dir():
                shutil.copytree(server/'kubejs'/name, lab/'kubejs'/name)
    (lab/'eula.txt').write_text('eula=true\n')
    (lab/'server.properties').write_text(
        'server-ip=127.0.0.1\nonline-mode=false\nlevel-name=qa-world\nlevel-seed=12345\n'
        'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n'
        'view-distance=2\nsimulation-distance=2\nenable-rcon=false\nenable-query=false\nspawn-protection=0\nmax-tick-time=-1\n')
    jars = sorted((server/'libraries').rglob('*.jar'))
    neo = server/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-server.jar'
    mapped = next(p for p in jars if p.name == 'server-1.21.1-20240808.144430-srg.jar')
    nested = lab/'compile-nested'; nested.mkdir()
    extra = [p for jar in dependencies for p in core_build.nested_jars(jar, nested)]
    cp = os.pathsep.join(str(p) for p in [core, neo, mapped, *jars, *dependencies, *extra])
    classes = lab/'test-classes'
    sources = sorted((ROOT/'tests/home-spawning-smoke/java').rglob('*.java'))
    sources += sorted((ROOT/'tests/smoke-common/java').rglob('*.java'))
    core_build.compile_java(compiler, sources, classes, cp, lab/'compile.args')
    fixture_mixins = ['DisableNetworkMixin']
    if not args.full_pack: fixture_mixins.append('DisableTaskBridgeMixin')
    if args.isolate_collective_update_checker: fixture_mixins.append('DisableCollectiveUpdateCheckMixin')
    with zipfile.ZipFile(lab/'mods/muxi-home-spawning-smoke-only.jar', 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('META-INF/neoforge.mods.toml', 'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n'
            '[[mixins]]\nconfig="muxi_smoke_only.mixins.json"\n[[mods]]\nmodId="muxi_tasks_smoke"\nversion="1.0.0"\ndisplayName="Isolated home spawning tests"\n'
            '[[dependencies.muxi_tasks_smoke]]\nmodId="muxi_game_core"\ntype="required"\nversionRange="[1.12.0,)"\nordering="AFTER"\nside="SERVER"\n')
        archive.writestr('muxi_smoke_only.mixins.json', json.dumps({'required':True, 'minVersion':'0.8',
            'package':'net.muxigame.core.taskssmoke.mixin', 'compatibilityLevel':'JAVA_21', 'mixins':fixture_mixins}))
        for p in classes.rglob('*.class'): archive.write(p, p.relative_to(classes).as_posix())
    launch = shlex.split((server/'libraries/net/neoforged/neoforge/21.1.250/win_args.txt').read_text(encoding='utf-8'))
    libraries = (server/'libraries').as_posix()
    launch = [s.replace('libraries/', libraries+'/').replace('-DlibraryDirectory=libraries', '-DlibraryDirectory='+libraries) for s in launch]
    command = [str(runtime), '-Xms512M', '-Xmx6G' if args.full_pack else '-Xmx2G', '-XX:ActiveProcessorCount=4',
               '-Dfile.encoding=UTF-8', *launch, '--nogui']
    terminated = False
    isolated_env = {k:v for k,v in os.environ.items() if not k.startswith('MUXI_TASK_POINTS_')}
    with (lab/'boot.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=lab, env=isolated_env, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic()+(600 if args.full_pack else 240)
        stopped = None
        try:
            while process.poll() is None:
                if (lab/'home-spawning-server-stopped').exists():
                    stopped = stopped or time.monotonic()
                    if time.monotonic()-stopped > 15:
                        process.terminate(); process.wait(timeout=15); terminated=True; break
                if time.monotonic() > deadline: raise subprocess.TimeoutExpired(command, deadline)
                time.sleep(0.5)
        finally:
            if process.poll() is None:
                process.terminate()
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
    result_path = lab/'home-spawning-result.json'
    result = json.loads(result_path.read_text(encoding='utf-8')) if result_path.exists() else {'success':False, 'error':'No test result'}
    result.update(exitCode=process.returncode, lab=str(lab), fullPack=args.full_pack,
                  coreSha256=hashlib.sha256(core.read_bytes()).hexdigest(), terminatedBackgroundThreadsAfterServerStopped=terminated,
                  excludedUnrelatedLifecycle=None if args.full_pack else 'DailyTasksFeature.onStarted (account points HTTP initialization)')
    result['excludedCollectiveUpdateChecker'] = args.isolate_collective_update_checker
    result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if not result.get('success') or (process.returncode and not terminated):
        print('\n'.join((lab/'boot.log').read_text(encoding='utf-8', errors='replace').splitlines()[-65:]))
        raise SystemExit(1)


if __name__ == '__main__': run()
