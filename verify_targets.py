"""Validate exact pinned bytecode target/callback signatures without launching the game."""
import json
import os
import pathlib
import subprocess
from build import compile_sources

ROOT = pathlib.Path(__file__).resolve().parent
CLIENT = pathlib.Path(r'C:\Users\Administrator\WorkSpace\muxigame\_client_test\game')
JDK = pathlib.Path(r'C:\Program Files\Java\jdk-24\bin')

if __name__ == '__main__':
    libs = CLIENT / 'libraries'
    asm = [libs / f'org/ow2/asm/{name}/9.10.1/{name}-9.10.1.jar' for name in ['asm', 'asm-tree']]
    out = ROOT / 'build/target-verifier'
    compile_sources(JDK / 'javac.exe', [ROOT / 'tools/ValidateTargets.java'], out,
                    os.pathsep.join(map(str, asm)), ROOT / 'build/verify.args')
    originals = [libs / 'net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar',
                 libs / 'net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar',
                 CLIENT / 'mods/iris-neoforge-1.8.14-beta.1+mc1.21.1.jar',
                 CLIENT / 'mods/EuphoriaPatcher-1.10.0-r5.9-neoforge.jar',
                 ROOT.parent / 'transfer-sodium-nested.jar']
    result = subprocess.run([str(JDK / 'java.exe'), '-Xms16m', '-Xmx64m', '-cp',
                             os.pathsep.join(map(str, [out] + asm)), 'ValidateTargets',
                             str(ROOT / 'build/classes'), *map(str, originals)],
                            capture_output=True, text=True)
    if result.returncode:
        print(result.stderr)
        raise RuntimeError('Target signature verification failed')
    receipt = json.loads(result.stdout)
    (ROOT / 'build/target-signature-receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(receipt))
