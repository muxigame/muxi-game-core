"""Copy only final sibling build outputs into disposable server fixtures."""
import json
from pathlib import Path
import shutil

def install(lab: Path, root: Path, full: bool = False):
    modules = ['muxi-minigames', 'muxi-zombie-challenge', 'muxi-outbreak', 'muxi-terminal'] if full else ['muxi-minigames']
    artifacts = []
    for module in modules:
        sibling = root.parent / module
        metadata = json.loads((sibling / 'build/release.json').read_text(encoding='utf-8'))
        artifact = sibling / 'build/libs' / metadata['artifact']
        if not artifact.is_file():
            raise RuntimeError('Missing integrated fixture dependency ' + str(artifact))
        shutil.copy2(artifact, lab / 'mods' / artifact.name)
        artifacts.append(artifact)
    return artifacts
