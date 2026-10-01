"""Build a test-only mod and boot a disposable loopback-only server using existing local libraries.

Never installs anything in bmc5server or pack/source. No world copies, credentials, downloads or real players.
Logs/world/test JARs are retained under build/tasks-smoke-* for inspection.
"""
from __future__ import annotations
import argparse
from datetime import datetime
import json
import hashlib
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import build as core_build


def run() -> None:
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, default=ROOT.parent / 'bmc5server')
    parser.add_argument('--integrations', action='store_true', help='Exercise the installed TaCZ, Champions and Create mods in a disposable lab')
    parser.add_argument('--extended', action='store_true', help='Also exercise native maid/golem ownership, crops, flowers and mod foods')
    parser.add_argument('--challenge', action='store_true', help='Exercise challenge rooms, arena, inventory recovery and rewards')
    parser.add_argument('--dimensions', action='store_true', help='Exercise home and survival on a disposable server')
    parser.add_argument('--travel', action='store_true', help='Exercise nonblocking movement and bounded pregeneration')
    parser.add_argument('--worldgen-audit', action='store_true', help='Compare home and survival worldgen registries in a disposable full-pack server')
    parser.add_argument('--seed',type=int,default=12345,help='Seed for isolated worldgen A/B tests')
    parser.add_argument('--serial-worldgen',action='store_true',help='Diagnostic only: single C2ME worker for order-dependence comparison')
    parser.add_argument('--without-c2me',action='store_true',help='Omit C2ME only from the disposable full-pack lab, preserving the source server')
    parser.add_argument('--vanilla-bg-threads',type=int,help='Diagnostic vanilla background pool limit (1..255), only with --without-c2me')
    parser.add_argument('--threaded',action='store_true',help='Enable the experimental native dimension runtime only in this lab')
    parser.add_argument('--java-home', type=Path, help='Use an existing local JDK 21+')
    parser.add_argument('--full-pack', action='store_true', help='Use locally installed server mods, with fresh offline configs, for dimension compatibility tests')
    parser.add_argument('--portals', action='store_true', help='Exercise built gates and adventure restrictions with the real Twilight Forest mod')
    args = parser.parse_args()
    if args.challenge: args.integrations=True
    if args.worldgen_audit: args.dimensions=True; args.full_pack=True
    if args.travel:
        args.dimensions=True
        if not args.threaded: parser.error('--travel requires --threaded')
    if args.full_pack and not args.dimensions: parser.error('--full-pack requires --dimensions')
    if args.without_c2me and not args.full_pack: parser.error('--without-c2me requires --full-pack or --worldgen-audit')
    if args.without_c2me and args.serial_worldgen: parser.error('--serial-worldgen configures C2ME and cannot be combined with --without-c2me')
    if args.threaded and args.full_pack and not args.without_c2me: parser.error('--threaded --full-pack requires --without-c2me')
    if args.vanilla_bg_threads is not None and (not args.without_c2me or not 1<=args.vanilla_bg_threads<=255):
        parser.error('--vanilla-bg-threads requires --without-c2me and a value between 1 and 255')
    server = args.server.resolve()
    release = json.loads((ROOT / 'build/release.json').read_text(encoding='utf-8'))
    core = ROOT / 'build/libs' / release['artifact']
    lab = ROOT / 'build' / ('tasks-smoke-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    lab.mkdir(parents=True, exist_ok=False)
    (lab / 'mods').mkdir(); (lab / 'config').mkdir()
    shutil.copy2(core, lab / 'mods' / core.name)
    omitted=[]
    if args.full_pack:
        for mod in (server/'mods').glob('*.jar'):
            if mod.name.startswith(('muxi-game-core-', 'muxi-identity-')): continue
            if args.without_c2me and mod.name.startswith('c2me-'):
                omitted.append(mod.name); continue
            shutil.copy2(mod,lab/'mods'/mod.name)
        if args.without_c2me and not omitted: raise SystemExit('No source C2ME jar found; cannot establish the requested removal baseline')
    dependencies=[]
    # Waystones is a required Core 1.12+ dependency in every minimal server fixture.
    if not args.full_pack:
        for pattern in ('waystones-neoforge*.jar', 'balm-neoforge*.jar'):
            matches=list((server/'mods').glob(pattern))
            if len(matches)!=1: raise SystemExit('Ambiguous required test dependency '+pattern)
            dependencies.append(matches[0]);shutil.copy2(matches[0],lab/'mods'/matches[0].name)
    if args.portals:
        twilight=next((server/'mods').glob('twilightforest-*.jar'))
        dependencies.append(twilight);shutil.copy2(twilight,lab/'mods'/twilight.name)
    if args.integrations or args.extended:
        patterns=['tacz-neoforge*.jar','*champions*.jar','architectury-*.jar','*create-1.21*.jar']
        if args.extended: patterns+=['*touhoulittlemaid-*.jar','FarmersDelight*.jar','rightclickharvest*.jar','jamlib-*.jar']
        if args.extended or args.challenge: patterns+=['*modulargolems-*.jar','*l2library-*.jar']
        for pattern in patterns:
            found=list((server/'mods').glob(pattern))
            if len(found)!=1: raise SystemExit(f'Expected one existing integration dependency: {pattern}')
            dependencies.append(found[0]); shutil.copy2(found[0],lab/'mods'/found[0].name)
    (lab / 'config/muxi-game-core.json').write_text('{"schema":1,"features":{}}', encoding='utf-8')
    (lab / 'config/neoforge-server.toml').write_text('advertiseDedicatedServerToLan = false\n', encoding='utf-8')
    (lab / 'config/fml.toml').write_text('versionCheck = false\n', encoding='utf-8')
    if args.travel:
        (lab/'config/spark').mkdir()
        (lab/'config/spark/config.json').write_text('{"backgroundProfiler":false}\n')
    if args.full_pack:
        # Public loader compatibility overrides (e.g. Sable/ScalableLux), never core credentials.
        shutil.copy2(server/'config/fml.toml',lab/'config/fml.toml')
        (lab/'config/webdisplays_common.toml').write_text('[mini_server]\nminiserv_port = 0\n',encoding='utf-8')
    if args.worldgen_audit:
        # Explicit public gameplay allowlist: never copy login/identity or other private config.
        for name in ('mowziesmobs-common.toml','alexsmobs-common.toml','goblintraders-entities.toml','terrablender.toml','sereneseasons/seasons.toml'):
            source=server/'config'/name
            if source.is_file():
                target=lab/'config'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
        # Public worldgen/spawn data and pack scripts, shared by both worlds in this lab.
        for name in ('iceandfire','alexsmobs','mowziesmobs','biomeswevegone','paxi/datapacks'):
            if (server/'config'/name).is_dir():shutil.copytree(server/'config'/name,lab/'config'/name,dirs_exist_ok=True)
        for name in ('server_scripts','startup_scripts','data'):
            if (server/'kubejs'/name).is_dir():shutil.copytree(server/'kubejs'/name,lab/'kubejs'/name,dirs_exist_ok=True)
        for name in ('blueprint-common.toml','sparsestructures.json5','structureessentials.json','repurposed_structures-common.toml','irons_spellbooks-server.toml','environmental-common.toml','crittersandcompanions-common.toml'):
            if (server/'config'/name).is_file():shutil.copy2(server/'config'/name,lab/'config'/name)
        if args.serial_worldgen:shutil.copy2(ROOT/'compatibility/survival-worldgen/config/c2me.toml',lab/'config/c2me.toml')
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
    if args.worldgen_audit:
        (lab/'server.properties').write_text(f'server-ip=127.0.0.1\nonline-mode=false\nlevel-name=qa-world\nlevel-seed={args.seed}\nlevel-type=minecraft:normal\nview-distance=2\nsimulation-distance=2\nenable-rcon=false\nenable-query=false\nmax-tick-time=-1\n',encoding='utf-8')
    compiler, runtime = core_build.java_tools(args.java_home)
    java21 = ROOT.parent / 'perf-lab/java21/jdk-21.0.2/bin/java.exe'
    if java21.is_file(): runtime = java21
    jars = sorted((server / 'libraries').rglob('*.jar'))
    neo = server / 'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-server.jar'
    mapped = next(p for p in jars if p.name == 'server-1.21.1-20240808.144430-srg.jar')
    nested=lab/'compile-nested'; nested.mkdir()
    extra=[p for jar in dependencies for p in core_build.nested_jars(jar,nested)]
    cp = os.pathsep.join(str(p) for p in [core, neo, mapped, *jars, *dependencies, *extra])
    classes = lab / 'test-classes'
    sources='tests/portals-smoke/java' if args.portals else 'tests/dimensions-smoke/java' if args.dimensions else 'tests/challenge-smoke/java' if args.challenge else 'tests/extended-smoke/java' if args.extended else 'tests/integration-smoke/java' if args.integrations else 'tests/smoke/java'
    if args.worldgen_audit: sources='tests/worldgen-audit/java'
    if args.travel: sources='tests/travel-smoke/java'
    core_build.compile_java(compiler, sorted((ROOT / sources).rglob('*.java')) + sorted((ROOT / 'tests/smoke-common/java').rglob('*.java')), classes, cp, lab / 'compile.args')
    with zipfile.ZipFile(lab / 'mods/muxi-tasks-smoke-only.jar', 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/neoforge.mods.toml', 'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n'
                   '[[mixins]]\nconfig="muxi_smoke_only.mixins.json"\n'
                   '[[mods]]\nmodId="muxi_tasks_smoke"\nversion="1.0.0"\ndisplayName="Isolated task smoke tests"\n'
                   '[[dependencies.muxi_tasks_smoke]]\nmodId="muxi_game_core"\ntype="required"\nversionRange="[1.5.0,)"\nordering="AFTER"\nside="SERVER"\n')
        z.writestr('muxi_smoke_only.mixins.json', json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.core.taskssmoke.mixin','compatibilityLevel':'JAVA_21','mixins':['DisableNetworkMixin']}))
        for p in classes.rglob('*.class'): z.write(p, p.relative_to(classes).as_posix())
        if args.extended:
            for registry in ['block','item']:
                z.writestr(f'data/minecraft/tags/{registry}/flowers.json',json.dumps({'replace':False,'values':['muxi_tasks_smoke:test_flower']}))
            z.writestr('data/muxi_tasks_smoke/loot_table/blocks/test_flower.json',json.dumps({
                'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'muxi_tasks_smoke:test_flower'}]}]}))
    launch = shlex.split((server / 'libraries/net/neoforged/neoforge/21.1.250/win_args.txt').read_text(encoding='utf-8'))
    libraries = (server / 'libraries').as_posix()
    launch = [s.replace('libraries/', libraries + '/').replace('-DlibraryDirectory=libraries', '-DlibraryDirectory=' + libraries) for s in launch]
    command = [str(runtime), '-Xms512M', '-Xmx6G' if args.full_pack else '-Xmx2G', '-XX:ActiveProcessorCount=4', '-Dfile.encoding=UTF-8', *launch, '--nogui']
    if args.vanilla_bg_threads is not None: command.insert(1,f'-Dmax.bg.threads={args.vanilla_bg_threads}')
    if args.threaded: command.insert(1,'-Dmuxi.dimensionThreads=true')
    with (lab / 'boot.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=lab, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
        stopped_background=False
        try:
            timeout=900 if args.worldgen_audit else 360 if args.full_pack else 180
            deadline=time.monotonic()+timeout
            stopped_at=None
            while process.poll() is None:
                if args.full_pack and (lab/'dimensions-server-stopped').exists():
                    stopped_at=stopped_at or time.monotonic()
                    if time.monotonic()-stopped_at>15:
                        # A few pack mods leave non-daemon threads alive after Minecraft's shutdown event.
                        process.terminate();process.wait(timeout=15);stopped_background=True;break
                if time.monotonic()>deadline: raise subprocess.TimeoutExpired(process.args,timeout)
                time.sleep(0.5)
            code=process.returncode
        except subprocess.TimeoutExpired:
            process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
            raise SystemExit(f'Isolated smoke timed out; logs: {lab / "boot.log"}')
    result_file = lab / 'tasks-smoke-result.json'
    result = json.loads(result_file.read_text(encoding='utf-8')) if result_file.exists() else {'success': False, 'error': 'No test result'}
    result['exitCode'] = code; result['lab'] = str(lab)
    result['terminatedBackgroundThreadsAfterServerStopped']=stopped_background
    result['environment']={'coreArtifact':core.name,'coreSha256':hashlib.sha256((lab/'mods'/core.name).read_bytes()).hexdigest(),
        'omittedMods':omitted,'serialC2meProfile':args.serial_worldgen,'vanillaBackgroundThreads':args.vanilla_bg_threads,'dimensionThreads':args.threaded}
    result_file.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if not result.get('success') or (code and not stopped_background):
        print('\n'.join((lab / 'boot.log').read_text(encoding='utf-8', errors='replace').splitlines()[-90:]))
        raise SystemExit(1)


if __name__ == '__main__': run()
