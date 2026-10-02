"""Low-memory original-bytecode replay. Runs stubs, never launches Minecraft or GL."""
import hashlib
import json
import os
import pathlib
import subprocess
import zipfile
from build import compile_sources

ROOT = pathlib.Path(__file__).resolve().parent
CLIENT = pathlib.Path(r'C:\Users\Administrator\WorkSpace\muxigame\_client_test\game')
JDK = pathlib.Path(r'C:\Program Files\Java\jdk-24\bin')
JAR = CLIENT / 'mods/EuphoriaPatcher-1.10.0-r5.9-neoforge.jar'
EXPECTED = 'd728a26bd67b70dfc510fbb08def619ec015c998'


if __name__ == '__main__':
    if hashlib.sha1(JAR.read_bytes()).hexdigest() != EXPECTED:
        raise ValueError('Euphoria original differs from formal 1.4.26 manifest')
    out = ROOT / 'build/replay'
    original = out / 'original'
    member = 'com/euphoriapatches/euphoria_patcher/neoforge/mixin/ReloadShadersOnDimensionChangeMixin.class'
    with zipfile.ZipFile(JAR) as archive:
        file = original / member
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(archive.read(member))
    mixin = CLIENT / 'libraries/net/fabricmc/sponge-mixin/0.15.2+mixin.0.8.7/sponge-mixin-0.15.2+mixin.0.8.7.jar'
    classes = out / 'classes'
    cp = os.pathsep.join(map(str, [ROOT / 'build/classes', mixin]))
    compile_sources(JDK / 'javac.exe', sorted((ROOT / 'replay').rglob('*.java')), classes, cp, out / 'replay.args')
    runtime_cp = os.pathsep.join(map(str, [classes, original, ROOT / 'build/classes', mixin]))
    results = []
    for mode in ['baseline', 'guarded']:
        result = subprocess.run([str(JDK / 'java.exe'), '-Xms16m', '-Xmx64m', '-cp', runtime_cp,
                                 'net.muxigame.transferprobe.ReplayOriginalHandler', mode],
                                capture_output=True, text=True, check=True)
        results.append(json.loads(result.stdout))
    receipt = {'originalSha1': EXPECTED, 'runs': results,
               'claim': 'Six extra reload requests eliminated by the policy in original-handler replay; actual shader and transfer durations remain unmeasured.'}
    (ROOT / 'build/original-replay-receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(receipt))
