"""Lightweight JVM check against the installed CSL/Authlib jars; never starts Minecraft."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from build import compile_java, java_tools

p = argparse.ArgumentParser()
p.add_argument('--client-game', type=Path, required=True)
p.add_argument('--csl', type=Path, required=True)
p.add_argument('--neoforge-universal', type=Path, required=True)
a = p.parse_args()
compiler, runtime = java_tools(None)
out = ROOT / 'build' / 'skin-profile-smoke'
out.mkdir(parents=True, exist_ok=True)
# Authlib 6 is the runtime used by Minecraft 1.21.1; don't load older bundled versions.
libs = a.client_game / 'libraries'
jars = [a.csl, libs / 'com/mojang/authlib/6.0.54/authlib-6.0.54.jar',
        libs / 'com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar',
        libs / 'com/google/code/gson/gson/2.10.1/gson-2.10.1.jar']
jars += [x for x in libs.rglob('*.jar') if not any(y in x.as_posix() for y in
         ('/com/mojang/authlib/', '/com/google/guava/guava/', '/com/google/code/gson/gson/'))]
cp = os.pathsep.join(str(x.resolve()) for x in jars)
with zipfile.ZipFile(a.neoforge_universal) as z:
    entry = next(n for n in z.namelist() if n.startswith('META-INF/jarjar/mixinextras-') and n.endswith('.jar'))
    extras = out / Path(entry).name
    extras.write_bytes(z.read(entry))
cp += os.pathsep + str(extras.resolve())
sources = [ROOT / 'src/main/java/net/muxigame/core/compat/skins/SkinProfiles.java',
           ROOT / 'src/main/java/net/muxigame/core/compat/mixin/customskinloader/SkinManagerProfileMixin.java',
           ROOT / 'src/main/java/net/muxigame/core/feature/identity/IdentityRules.java',
           ROOT / 'tests/skins-smoke/SkinProfileSmoke.java']
compile_java(compiler, sources, out / 'classes', cp, out / 'compile.args')
subprocess.run([str(runtime), '-cp', str(out / 'classes') + os.pathsep + cp, 'SkinProfileSmoke'], check=True, timeout=30)
