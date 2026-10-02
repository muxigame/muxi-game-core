"""Offline compiler/package builder. Does not start Minecraft or mutate dependencies."""
import argparse
import hashlib
import json
import os
import pathlib
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent


def compile_sources(javac, sources, output, cp, args_file):
    output.mkdir(parents=True, exist_ok=True)
    args = ['--release', '21', '-encoding', 'UTF-8', '-proc:none', '-classpath', cp,
            '-d', str(output), *map(str, sources)]
    args_file.write_text('\n'.join('"' + item.replace('\\', '/').replace('"', '\\"') + '"'
                                  for item in args), encoding='utf-8')
    subprocess.run([str(javac), '@' + str(args_file)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=pathlib.Path, default=pathlib.Path(r'C:\Users\Administrator\WorkSpace\muxigame\bmc5server'))
    parser.add_argument('--client', type=pathlib.Path, default=pathlib.Path(r'C:\Users\Administrator\WorkSpace\muxigame\_client_test\game'))
    parser.add_argument('--jdk', type=pathlib.Path, default=pathlib.Path(r'C:\Program Files\Java\jdk-24'))
    args = parser.parse_args()
    build = ROOT / 'build'
    build.mkdir(exist_ok=True)
    cl = args.client / 'libraries'
    sl = args.server / 'libraries'
    patched = cl / 'net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar'
    mapped = cl / 'net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar'
    dependencies = [patched, mapped] + sorted(p for p in sl.rglob('*.jar')
                    if '/net/minecraft/' not in p.as_posix() and not p.name.endswith('-server.jar'))
    if not patched.is_file() or not mapped.is_file():
        raise ValueError('Installed patched NeoForge 21.1.250 client dependencies are required')
    cp = os.pathsep.join(str(p) for p in dependencies)
    suffix = '.exe' if os.name == 'nt' else ''
    javac, java = args.jdk / ('bin/javac' + suffix), args.jdk / ('bin/java' + suffix)
    classes, test_classes = build / 'classes', build / 'test-classes'
    compile_sources(javac, sorted((ROOT / 'src/main/java').rglob('*.java')), classes, cp, build / 'main.args')
    compile_sources(javac, sorted((ROOT / 'tests').glob('*.java')), test_classes, str(classes), build / 'test.args')
    result = subprocess.run([str(java), '-Xmx128m', '-cp', str(test_classes) + os.pathsep + str(classes),
                             'net.muxigame.transferprobe.ProbeWindowTest'], capture_output=True, text=True, check=True)
    (build / 'unit-test.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    jar = build / 'muxi-transfer-probe-0.1.0-task23-diag.jar'
    with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as archive:
        for base in [classes, ROOT / 'src/main/resources']:
            for file in sorted(base.rglob('*')):
                if file.is_file():
                    info = zipfile.ZipInfo(file.relative_to(base).as_posix(), (2026, 10, 2, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    archive.writestr(info, file.read_bytes())
    receipt = {'jar': str(jar), 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
               'sourceCount': len(list((ROOT / 'src/main/java').rglob('*.java'))),
               'unitTest': result.stdout.strip(), 'minecraftStarted': False,
               'runtimeMixinApplicationVerified': False,
               'performanceGainMeasuredInMinecraft': False}
    (build / 'build-receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(receipt))


if __name__ == '__main__':
    main()
