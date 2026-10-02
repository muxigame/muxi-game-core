"""Compile only isolated portal classes against the installed Core 1.12.2.

Never builds/deploys unrelated source changes, writes into production, or starts
the production server. Candidate JAR preserves every other installed entry.
"""
from pathlib import Path
import hashlib
import json
import os
import shutil
import zipfile
import build

ROOT = Path(__file__).resolve().parent
SERVER = Path(r"C:\Users\Administrator\WorkSpace\muxigame\bmc5server")
PRODUCTION = SERVER / "mods/muxi-game-core-1.12.2.jar"
EXPECTED = "56cdb297d0a0de3f6bc745fcc94914bb90dd4f06e078b2c15fd40b010eb9f59d"
JAVA_HOME = Path(r"C:\Program Files\Java\jdk-24")


def main():
    if hashlib.sha256(PRODUCTION.read_bytes()).hexdigest() != EXPECTED:
        raise RuntimeError("Production baseline changed; stop and rebase the isolated patch")
    output = ROOT / "build"
    output.mkdir(exist_ok=True)
    nested = output / "nested"
    nested.mkdir(exist_ok=True)
    cache = output / "library-cache"
    for original in sorted((SERVER / "libraries").rglob("*.jar")):
        target = cache / original.relative_to(SERVER / "libraries")
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            shutil.copyfile(original, target)
    production = ROOT / "baseline-production.jar"
    if hashlib.sha256(production.read_bytes()).hexdigest() != EXPECTED:
        raise RuntimeError("Isolated production baseline copy mismatch")
    minigames = output / "muxi-minigames-0.1.1.jar"
    shutil.copyfile(SERVER / "mods/muxi-minigames-0.1.1.jar", minigames)
    libraries = sorted(cache.rglob("*.jar"))
    neo_dir = cache / "net/neoforged/neoforge/21.1.250"
    neo_server = neo_dir / "neoforge-21.1.250-server.jar"
    neo = neo_dir / "neoforge-21.1.250-universal.jar"
    mapped = next(p for p in libraries if p.name == "server-1.21.1-20240808.144430-srg.jar")
    extra = build.nested_jars(neo, nested)
    jars = [production, minigames, neo_server, mapped] + [p for p in libraries if "/net/minecraft/" not in p.as_posix() and p != neo_server] + extra
    compiler, _ = build.java_tools(JAVA_HOME)
    classes = output / "portal-classes"
    sources = sorted((ROOT / "src/main/java").rglob("*.java"))
    build.compile_java(compiler, sources, classes, os.pathsep.join(map(str, jars)), output / "portal.args")
    libdir = output / "libs"
    libdir.mkdir(exist_ok=True)
    candidate = libdir / "muxi-game-core-1.12.2-portal-exact-candidate.jar"
    replacements = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob("*.class")}
    differences = []
    with zipfile.ZipFile(production) as original, zipfile.ZipFile(candidate, "w", zipfile.ZIP_DEFLATED) as archive:
        for entry in original.infolist():
            original_data = original.read(entry.filename)
            data = replacements.pop(entry.filename, original_data)
            archive.writestr(entry, data)
            if data != original_data:
                differences.append(entry.filename)
        for name, data in replacements.items():
            archive.writestr(name, data)
            differences.append(name)
    report = {"baseline_jar": str(PRODUCTION), "baseline_sha256": EXPECTED,
              "candidate": str(candidate), "candidate_sha256": hashlib.sha256(candidate.read_bytes()).hexdigest(),
              "changed_entries": differences, "compiler": str(compiler), "deployed": False}
    (output / "patch-build.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    (output / "release.json").write_text(json.dumps({"artifact": candidate.name}), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
