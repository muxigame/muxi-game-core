"""Install Game Core into a STOPPED server. Offline; no client or account-service writes."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import tempfile
from urllib.parse import urlsplit
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent
CONFIG_NAME = 'config/muxi-game-core.json'
LEGACY_NAME = 'config/muxi-identity-bridge.json'


def validate_config(data: dict) -> None:
    """Match runtime validation without exposing private input values."""
    try:
        if not isinstance(data, dict) or data.get('schema', 1) != 1: raise ValueError()
        identity = data.get('features', {}).get('identity', {})
        enabled = identity.get('enabled', False)
        if type(enabled) is not bool: raise ValueError()
        if not enabled: return
        endpoint, key = identity['endpoint'], identity['serverKey']
        uri = urlsplit(endpoint)
        secure = uri.scheme == 'https'
        local = uri.scheme == 'http' and uri.hostname in ('127.0.0.1', 'localhost')
        refresh = identity.get('refreshSeconds', 60)
        if ((not secure and not local) or not uri.hostname or not endpoint.endswith('/')
                or uri.username is not None or uri.password is not None or uri.query or uri.fragment
                or not isinstance(key, str) or not 32 <= len(key) <= 512
                or any(ord(c) < 33 or ord(c) > 126 for c in key)
                or type(refresh) is not int or not 10 <= refresh <= 600): raise ValueError()
    except (ValueError, TypeError, AttributeError, KeyError):
        raise ValueError('Invalid private Game Core configuration (values redacted)') from None


def migrated_config(legacy: dict) -> dict:
    result = {'schema': 1, 'features': {'identity': {'enabled': True, **legacy}}}
    validate_config(result)
    return result


def atomic_write(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix='.game-core-', delete=False) as output:
        output.write(content); stage = Path(output.name)
    try:
        stage.chmod(0o600)
        os.replace(stage, path)
    finally: stage.unlink(missing_ok=True)


def install(server: Path, dry_run: bool = False, repo: Path = ROOT) -> dict:
    server = server.resolve()
    if not (server / 'mods').is_dir() or not (server / 'server.properties').is_file():
        raise ValueError('Expected an installed, stopped Minecraft server directory')
    metadata = json.loads((repo / 'build/release.json').read_text(encoding='utf-8'))
    filename = metadata['artifact']
    if Path(filename).name != filename or not filename.startswith('muxi-game-core-') or not filename.endswith('.jar'):
        raise ValueError('Invalid build artifact name')
    built = repo / 'build/libs' / filename
    binary = built.read_bytes()
    if hashlib.sha256(binary).hexdigest() != metadata['sha256'] or len(binary) != metadata['size']:
        raise ValueError('Build artifact checksum/size does not match release.json')
    with zipfile.ZipFile(built) as jar:
        if 'modId="muxi_game_core"' not in jar.read('META-INF/neoforge.mods.toml').decode():
            raise ValueError('Artifact is not muxi Game Core')
    existing = server / CONFIG_NAME; old = server / LEGACY_NAME
    if existing.exists():
        config_bytes = existing.read_bytes()
        try: config = json.loads(config_bytes.decode('utf-8-sig'))
        except (UnicodeError, ValueError): raise ValueError('Existing Game Core config is unreadable (values redacted)') from None
    elif old.exists():
        try: legacy = json.loads(old.read_text(encoding='utf-8-sig'))
        except (UnicodeError, ValueError): raise ValueError('Legacy identity config is unreadable (values redacted)') from None
        config = migrated_config(legacy)
        config_bytes = (json.dumps(config, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    else:
        config_bytes = (repo / 'config-examples/muxi-game-core.json').read_bytes()
        config = json.loads(config_bytes)
    validate_config(config)
    enabled = config.get('features', {}).get('identity', {}).get('enabled', False)
    if enabled:
        dep = json.loads((repo / 'dependencies.json').read_text())['simpleNicknames']
        nick = server / 'mods' / dep['filename']
        if not nick.is_file() or hashlib.sha512(nick.read_bytes()).hexdigest() != dep['sha512']:
            raise ValueError('Identity enabled but pinned Simple Nicknames is missing/modified; no files changed')
    operations: dict[Path, bytes | None] = {server / 'mods' / filename: binary,
        existing: config_bytes, server / 'start-muxi.ps1': (repo / 'start-muxi.ps1').read_bytes()}
    for pattern in ('muxi-identity-*.jar', 'muxi-game-core-*.jar'):
        for file in (server / 'mods').glob(pattern):
            if file.name != filename: operations[file] = None
    if old.exists(): operations[old] = None
    operations = {p: data for p, data in operations.items() if (p.read_bytes() if p.exists() else None) != data}
    report = {'version': metadata['version'], 'artifact': filename, 'identityEnabled': enabled,
              'changed': [p.relative_to(server).as_posix() for p in operations], 'dryRun': dry_run}
    if dry_run or not operations: return report
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S') + '-' + uuid.uuid4().hex[:8]
    backup = server / '.muxi-game-core-backups' / stamp
    backup.mkdir(parents=True, mode=0o700)
    originals = {path: path.read_bytes() if path.exists() else None for path in operations}
    manifest = []
    for path, content in originals.items():
        relative = path.relative_to(server)
        if content is not None: atomic_write(backup / relative, content)
        manifest.append({'path': relative.as_posix(), 'existed': content is not None})
    (backup / 'restore-index.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    touched = []
    try:
        for path, content in operations.items():
            touched.append(path)
            if content is None: path.unlink(missing_ok=True)
            else: atomic_write(path, content)
    except Exception:
        for path in reversed(touched):
            if originals[path] is None: path.unlink(missing_ok=True)
            else: atomic_write(path, originals[path])
        raise RuntimeError('Installation failed; original files restored. Private values were not logged.') from None
    report['backup'] = str(backup)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, default=ROOT.parent / 'bmc5server')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try: print(json.dumps(install(args.server, args.dry_run), ensure_ascii=False, indent=2))
    except (OSError, ValueError, RuntimeError) as error: raise SystemExit(str(error)) from None


if __name__ == '__main__': main()
