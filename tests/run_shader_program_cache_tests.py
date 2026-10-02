"""CPU-only shader cache checks and optional real installed event-bus checks.

No Minecraft client, network, account files or production state is touched.
Output and owned disk fixtures stay in a fresh system temporary directory.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'src/main/java/net/muxigame/core/compat/shaders'
TESTS = ROOT / 'tests/shaders'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--javac', default=shutil.which('javac'))
    parser.add_argument('--java', default=shutil.which('java'))
    parser.add_argument('--dependencies', type=Path)
    parser.add_argument('--client-libraries', type=Path)
    parser.add_argument('--client-version-json', type=Path)
    args = parser.parse_args()
    if not args.javac or not args.java:
        parser.error('Java 21 javac/java paths are required')
    supplied = [args.dependencies, args.client_libraries, args.client_version_json]
    if any(supplied) and not all(supplied):
        parser.error('All three native-bus dependency/library options are required together')
    with tempfile.TemporaryDirectory(prefix='muxi-shader-program-tests-') as folder:
        output = Path(folder)
        classes = output / 'classes'
        classes.mkdir()
        sources = [SOURCE / (name + '.java') for name in [
            'ShaderBinaryStore', 'ShaderBinaryKey', 'ShaderBinaryLinker',
            'ShaderBinaryBootstrap']]
        subprocess.run([args.javac, '-encoding', 'UTF-8', '-d', str(classes),
                        *map(str, sources), str(TESTS / 'ShaderBinaryCorrectnessTest.java')], check=True)
        subprocess.run([args.java, '-Xmx512m', '-cp', str(classes),
                        'ShaderBinaryCorrectnessTest', str(output / 'owned-records')], check=True)
        if not all(supplied):
            print('Native event-bus checks skipped: dependency/library options not supplied')
            return
        subprocess.run([args.javac, '-encoding', 'UTF-8', '-cp', str(args.dependencies),
                        '-d', str(classes), str(SOURCE / 'ShaderBinaryBootstrap.java'),
                        str(SOURCE / 'VeilShaderEventDispatch.java'),
                        str(TESTS / 'VeilDispatchCorrectnessTest.java')], check=True)
        metadata = json.loads(args.client_version_json.read_text(encoding='utf-8'))
        library_root = args.client_libraries.resolve()
        libraries = []
        for item in metadata['libraries']:
            artifact = item.get('downloads', {}).get('artifact')
            if not artifact:
                continue
            library = (library_root / artifact['path']).resolve()
            if library_root not in library.parents:
                raise ValueError('Public library path escapes the supplied library root')
            if library.is_file():
                libraries.append(library)
        runtime = os.pathsep.join(map(str, [classes, *libraries, args.dependencies]))
        runtime_args = output / 'native-bus.args'
        runtime_args.write_text('"-cp"\n"' + runtime.replace('\\', '/') +
                                '"\n"VeilDispatchCorrectnessTest"\n', encoding='utf-8')
        subprocess.run([args.java, '@' + str(runtime_args)], check=True)


if __name__ == '__main__':
    main()
