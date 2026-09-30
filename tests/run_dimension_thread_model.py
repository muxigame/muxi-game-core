"""Compile/run the standalone scheduling model; does not launch Minecraft or change the core JAR."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import build

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    args = parser.parse_args()
    compiler, runtime = build.java_tools(args.java_home.resolve())
    output = ROOT / 'build/dimension-thread-model'
    output.mkdir(parents=True, exist_ok=True)
    result = output / 'result.json'
    result.unlink(missing_ok=True)
    build.compile_java(compiler, sorted((ROOT / 'experiments/dimension-threads/src').glob('*.java')),
                       output / 'classes', '', output / 'compile.args')
    subprocess.run([str(runtime), '-cp', str(output / 'classes'), 'DimensionThreadModelTest', str(result)],
                   check=True, timeout=60)
    report = json.loads(result.read_text(encoding='utf-8'))
    assert report['success'] and not report['minecraftIntegrated']
    print(result)

if __name__ == '__main__':
    main()
