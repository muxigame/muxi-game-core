"""Offline local build: Python 3.11+, JDK 21+, an installed NeoForge 21.1.250 server.

Only this repository's classes/resources enter the jar. No Gradle, network,
Minecraft launch, game mutation or third-party binary redistribution is needed.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent


def java_tools(home: Path | None) -> tuple[Path, Path]:
    homes = [home] if home else []
    if not home:
        if os.getenv('JAVA_HOME'): homes.append(Path(os.environ['JAVA_HOME']))
        for base in (Path('C:/Program Files/Java'), Path('C:/Program Files/Eclipse Adoptium'), Path('C:/Program Files/Microsoft')):
            if base.exists(): homes.extend(sorted(base.iterdir(), reverse=True))
        if shutil.which('javac'): homes.append(Path(shutil.which('javac')).resolve().parent.parent)
    suffix = '.exe' if os.name == 'nt' else ''
    for candidate in homes:
        compiler, runtime = candidate / f'bin/javac{suffix}', candidate / f'bin/java{suffix}'
        if not compiler.is_file() or not runtime.is_file(): continue
        try:
            result = subprocess.run([str(compiler), '-version'], capture_output=True, text=True, timeout=10, check=True)
            version = re.search(r'javac (\d+)', result.stdout + result.stderr)
            if version and int(version[1]) >= 21: return compiler, runtime
        except (OSError, subprocess.SubprocessError): continue
    raise ValueError('JDK 21+ not found; use --java-home to select a JDK.')


def compile_java(compiler: Path, sources: list[Path], output: Path, classpath: str, argfile: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    arguments = ['--release', '21', '-encoding', 'UTF-8', '-proc:none', '-classpath', classpath, '-d', str(output), *map(str, sources)]
    argfile.write_text('\n'.join('"' + arg.replace('\\', '/').replace('"', '\\"') + '"' for arg in arguments), encoding='utf-8')
    subprocess.run([str(compiler), '@' + str(argfile)], check=True)


def nested_jars(jar: Path, into: Path) -> list[Path]:
    """Jar-in-jar libraries (MixinExtras inside NeoForge, PinIn inside JECh) are needed to compile, never to ship."""
    found = []
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                target = into / f'{jar.stem}--{Path(name).name}'
                target.write_bytes(archive.read(name)); found.append(target)
    return found


def build(server: Path, java_home: Path | None = None, run_tests: bool = False,
          client_game: Path | None = None, pack_mods: Path | None = None) -> Path:
    meta = json.loads((ROOT / 'mod.json').read_text(encoding='utf-8'))
    deps = json.loads((ROOT / 'dependencies.json').read_text(encoding='utf-8'))
    dep = deps['simpleNicknames']
    nickname = server / 'mods' / dep['filename']
    if not nickname.is_file() or hashlib.sha512(nickname.read_bytes()).hexdigest() != dep['sha512']:
        raise ValueError('Pinned Simple Nicknames jar missing or modified. Use the existing server mods directory.')
    all_jars = sorted((server / 'libraries').rglob('*.jar'))
    mapped = [p for p in all_jars if p.name == 'server-1.21.1-20240808.144430-srg.jar']
    neo_dir = server / f"libraries/net/neoforged/neoforge/{meta['neoforge']}"
    neo = neo_dir / f"neoforge-{meta['neoforge']}-universal.jar"
    neo_server = neo_dir / f"neoforge-{meta['neoforge']}-server.jar"
    if len(mapped) != 1 or not neo.exists() or not neo_server.exists():
        raise ValueError('Installed Minecraft 1.21.1 / NeoForge 21.1.250 server libraries are required.')
    # 客户端兼容部分要对着客户端类和目标模组编译；这些只参与编译，一个字节都不进产物。
    client = deps['client']
    client_game = client_game or ROOT.parent / '_client_test' / 'game'
    pack_mods = pack_mods or ROOT.parent / 'better-mc-remake' / 'pack' / 'source' / 'Better MC Remake [FORGE]' / 'mods'
    client_jars = [client_game / client['neoforgePatched'], client_game / client['minecraft']]
    compile_only = [pack_mods / name for name in deps['compileOnly']]
    missing = [str(p) for p in client_jars + compile_only if not p.is_file()]
    if missing:
        raise ValueError('Client / compile-only jars missing (use --client-game / --pack-mods): ' + ', '.join(missing))
    compiler, runtime = java_tools(java_home)
    output = ROOT / 'build'; (output / 'libs').mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output, prefix='compile-') as temp:
        temp = Path(temp); classes = temp / 'classes'
        nested = temp / 'nested'; nested.mkdir()
        # NeoForge 打过补丁的类放在原版前面，否则 Entity.hasData 这类补丁方法编译时看不见。
        libraries = (client_jars + [neo_server] + mapped
                     + [p for p in all_jars if '/net/minecraft/' not in p.as_posix() and p != neo_server]
                     + nested_jars(neo, nested) + [nickname] + compile_only
                     + [j for jar in compile_only for j in nested_jars(jar, nested)])
        framework = ROOT.parent / "muxi-minigames/build/libs/muxi-minigames-0.1.2.jar"
        if not framework.is_file(): raise ValueError("Build muxi-minigames first")
        libraries.append(framework)
        classpath = os.pathsep.join(str(p.resolve()) for p in libraries)
        compile_java(compiler, sorted((ROOT / 'src/main/java').rglob('*.java')), classes, classpath, temp / 'main.args')
        if run_tests:
            test_cp = str(classes) + os.pathsep + classpath
            test_classes = temp / 'test-classes'
            compile_java(compiler, sorted((ROOT / 'tests/java').rglob('*.java')), test_classes, test_cp, temp / 'test.args')
            result = subprocess.run([str(runtime), '-cp', str(test_classes) + os.pathsep + str(ROOT / 'src/main/resources') + os.pathsep + test_cp, 'net.muxigame.core.CoreSelfTest'])
            if result.returncode:
                raise ValueError(f'Core self-tests failed (exit {result.returncode}); see the assertion above.')
        target = output / 'libs' / f"muxi-game-core-{meta['version']}.jar"
        entries = {'META-INF/LICENSE': (ROOT / 'LICENSE').read_bytes()}
        for base in (classes, ROOT / 'src/main/resources'):
            for file in base.rglob('*'):
                if file.is_file():
                    data = file.read_bytes()
                    if file.suffix == '.toml': data = data.replace(b'${mod_version}', meta['version'].encode('ascii'))
                    entries[file.relative_to(base).as_posix()] = data
        staged = temp / target.name
        with zipfile.ZipFile(staged, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in sorted(entries.items()):
                info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, data)
        os.replace(staged, target)
        release = {'id': meta['id'], 'name': meta['name'], 'version': meta['version'],
                   'artifact': target.name, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                   'size': target.stat().st_size, 'minecraft': meta['minecraft'], 'neoforge': meta['neoforge'], 'java': 21}
        (output / 'release.json').write_text(json.dumps(release, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(release))
        return target


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, default=ROOT.parent / 'bmc5server')
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--test', action='store_true')
    parser.add_argument('--client-game', type=Path, help='a game dir with the 1.21.1 client libraries (default ../_client_test/game)')
    parser.add_argument('--pack-mods', type=Path, help='the pack mods dir holding the compat target mods')
    args = parser.parse_args()
    try: build(args.server.resolve(), args.java_home, args.test, args.client_game, args.pack_mods)
    except (OSError, ValueError, subprocess.SubprocessError) as error: raise SystemExit(str(error)) from None


if __name__ == '__main__': main()
