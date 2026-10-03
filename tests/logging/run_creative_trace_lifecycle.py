"""Offline lifecycle test with actual installed NeoForge/Log4j dependencies; never launches Minecraft.
The tiny LogBegone fixture permits constructing a real production Lease and intentionally
fails production pins. This does not establish native dedicated-server activation or savings.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, default=ROOT.parent / 'bmc5server')
    parser.add_argument('--java-home', type=Path)
    args = parser.parse_args()
    java = str(args.java_home / 'bin/java.exe') if args.java_home else shutil.which('java')
    javac = str(args.java_home / 'bin/javac.exe') if args.java_home else shutil.which('javac')
    if not java or not javac:
        parser.error('JDK 21+ required: set PATH or --java-home')
    jars = sorted((args.server / 'libraries').rglob('*.jar'))
    if not jars:
        parser.error('installed server libraries not found')
    # Patched server classes precede vanilla Minecraft classes.
    jars.sort(key=lambda p: (0 if p.name.startswith('neoforge-') and p.name.endswith('-server.jar') else 1, str(p)))
    cp = os.pathsep.join(str(p) for p in jars)
    base = ROOT / 'src/main/java/net/muxigame/core/compat/logging'
    sources = [base / (n + '.java') for n in ('CreativeTraceGate', 'CreativeTraceGateClientEvents', 'CreativeTraceGateServerEvents')]
    with tempfile.TemporaryDirectory(prefix='muxi-creative-lifecycle-') as folder:
        temp = Path(folder)
        fixture = temp / 'mod/azure/logbegone/CommonMod.java'
        fixture.parent.mkdir(parents=True)
        fixture.write_text(r'package mod.azure.logbegone; public final class CommonMod { public static final org.apache.logging.log4j.core.Filter FILTER = new org.apache.logging.log4j.core.filter.AbstractFilter() {}; public static final com.google.gson.JsonObject CONFIG = com.google.gson.JsonParser.parseString("{\"logbegone\":{\"phrases\":[],\"regex\":[]}}").getAsJsonObject(); }', encoding='utf-8')
        output = temp / 'classes'
        output.mkdir()
        arguments = ['--release', '21', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(output), *map(str, sources), str(fixture), str(Path(__file__).with_name('CreativeTraceLifecycleTest.java'))]
        argfile = temp / 'javac.args'
        argfile.write_text('\n'.join('"' + a.replace('\\', '/') + '"' for a in arguments), encoding='utf-8')
        subprocess.run([javac, '@' + str(argfile)], check=True)
        for name in ('CreativeTraceGate', 'CreativeTraceGateServerEvents'):
            compiled = output / ('net/muxigame/core/compat/logging/' + name + '.class')
            assert b'neoforge/client/' not in compiled.read_bytes(), name + ': client type dependency'
        subprocess.run([java, '-cp', str(output) + os.pathsep + cp, 'CreativeTraceLifecycleTest'], cwd=temp, check=True)
        print('PASS: actual dependencies compiled; helper/dedicated subscriber have no client event constant-pool references')

if __name__ == '__main__':
    main()
