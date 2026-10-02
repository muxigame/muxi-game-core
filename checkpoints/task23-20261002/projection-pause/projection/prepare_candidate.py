"""Assemble a client-only overlay; inspect artifacts without launching Minecraft."""
import gzip
import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parent
BASE_MANIFEST = ROOT.parent.parent / 'task-14/integration/reports/production-release/downloads/manifest.json'
CACHED_MODS = Path(r'C:\Users\Administrator\WorkSpace\muxigame\_client_test\game\mods')
sys.path.insert(0, str(ROOT.parent))
from chunk_sync import NBT

def digest(data, algorithm='sha256'):
    return hashlib.new(algorithm, data).hexdigest()

def save_json(name, value):
    (ROOT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

base_bytes = BASE_MANIFEST.read_bytes()
base = json.loads(base_bytes.decode('utf-8-sig'))
assert base['pack']['version'] == '1.4.26'
base_files = {f['path']: f for f in base['files']}
mods = []
for project in ('forgematica', 'mafglib'):
    release = json.loads((ROOT / f'{project}-versions.json').read_text())[0]
    assert release['game_versions'] == ['1.21.1'] and release['loaders'] == ['neoforge']
    assert release['environment'] == 'client_only'
    for file in release['files']:
        path = ROOT / ('client/mods' if file['primary'] else 'redistribution/sources') / file['filename']
        data = path.read_bytes()
        assert len(data) == file['size']
        assert all(digest(data, kind) == expected for kind, expected in file['hashes'].items())
    primary = next(f for f in release['files'] if f['primary'])
    with zipfile.ZipFile(ROOT / 'client/mods' / primary['filename']) as jar:
        metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        assert metadata['license'] == 'LGPLv3'
        dependencies = metadata.get('dependencies', {}).get(project, [])
        mods.append({'project': project, 'version': release['version_number'],
                     'mod_ids': [m['modId'] for m in metadata['mods']], 'dependencies': dependencies,
                     'loader_range': metadata['loaderVersion'], 'license': metadata['license'],
                     'native_access_transformer_present': 'META-INF/accesstransformer.cfg' in jar.namelist()})

library_path = ROOT / 'client/mods/mafglib-0.4.3+mc1.21.1.jar'
api_name = 'forgified-fabric-api-0.116.15+2.3.5+1.21.1.jar'
api_path = CACHED_MODS / api_name
assert digest(api_path.read_bytes(), 'sha1') == base_files['mods/' + api_name]['sha1']
embedded = []
with zipfile.ZipFile(library_path) as library, zipfile.ZipFile(api_path) as api:
    old = json.loads(library.read('META-INF/jarjar/metadata.json'))['jars']
    existing = json.loads(api.read('META-INF/jarjar/metadata.json'))['jars']
    for dependency in old:
        supplied = next(j for j in existing if j['identifier'] == dependency['identifier'])
        upstream_bytes = library.read(dependency['path'])
        supplied_bytes = api.read(supplied['path'])
        upstream_zip = zipfile.ZipFile(io.BytesIO(upstream_bytes))
        supplied_zip = zipfile.ZipFile(io.BytesIO(supplied_bytes))
        different_entries = sorted(name for name in set(upstream_zip.namelist()) | set(supplied_zip.namelist())
                                   if name not in upstream_zip.namelist() or name not in supplied_zip.namelist()
                                   or upstream_zip.read(name) != supplied_zip.read(name))
        embedded.append({'identifier': dependency['identifier'], 'requested': dependency['version'],
                         'existing': supplied['version'],
                         'binary_identical': upstream_bytes == supplied_bytes,
                         'different_entries': different_entries,
                         'all_class_entries_identical': not any(name.endswith('.class') for name in different_entries),
                         'range_check': 'Existing version satisfies published minimum (static); FML selection and ABI require runtime verification.'})
    assert embedded[0]['all_class_entries_identical']
    assert embedded[0]['different_entries'] == ['META-INF/neoforge.mods.toml']
    assert embedded[1]['existing']['artifactVersion'].startswith('4.3.1+')

# Config filename and keys are taken from the exact published source JARs.
with zipfile.ZipFile(next((ROOT / 'redistribution/sources').glob('forgematica*.jar'))) as jar:
    configs = jar.read('fi/dy/masa/litematica/config/Configs.java').decode()
    hotkeys = jar.read('fi/dy/masa/litematica/config/Hotkeys.java').decode()
    reference = jar.read('fi/dy/masa/litematica/Reference.java').decode()
    assert 'MOD_ID = "litematica"' in reference
    assert 'SCHEMATIC_VERSION = 7' in jar.read('fi/dy/masa/litematica/schematic/LitematicaSchematic.java').decode()
config = {'Generic': {'easyPlaceMode': False, 'easyPlaceHoldEnabled': False},
          'Hotkeys': {name: {'keys': ''} for name in ('easyPlaceUseKey', 'easyPlaceFirst', 'easyPlaceToggle', 'executeOperation')}}
for name in config['Generic']:
    assert '"' + name + '"' in configs
for name in config['Hotkeys']:
    assert '"' + name + '"' in hotkeys
assert 'new ConfigHotkey("executeOperation",                  "")' in hotkeys
(ROOT / 'client/config').mkdir(parents=True, exist_ok=True)
save_json('client/config/litematica.json', config)

# A 3 x 1 x 3 stone floor, with no entities, block entities or pending ticks.
def string(value):
    data = value.encode('utf-8')
    return struct.pack('>H', len(data)) + data
def payload(tag, value):
    if tag == 3:
        return struct.pack('>i', value)
    if tag == 4:
        return struct.pack('>q', value)
    if tag == 8:
        return string(value)
    if tag == 10:
        return b''.join(bytes([kind]) + string(name) + payload(kind, content)
                        for name, (kind, content) in value.items()) + b'\x00'
    if tag == 9:
        kind, items = value
        return bytes([kind]) + struct.pack('>i', len(items)) + b''.join(payload(kind, item) for item in items)
    if tag == 12:
        return struct.pack('>i', len(value)) + b''.join(struct.pack('>q', item) for item in value)
    raise ValueError(tag)
def vec(x, y, z):
    return {'x': (3, x), 'y': (3, y), 'z': (3, z)}
empty = (9, (10, []))
region = {'Position': (10, vec(0, 0, 0)), 'Size': (10, vec(3, 1, 3)),
          'BlockStatePalette': (9, (10, [{'Name': (8, 'minecraft:air')}, {'Name': (8, 'minecraft:stone')}])),
          'BlockStates': (12, [sum(1 << (2 * index) for index in range(9))]),
          'TileEntities': empty, 'Entities': empty, 'PendingBlockTicks': empty, 'PendingFluidTicks': empty}
root = {'Version': (3, 7), 'SubVersion': (3, 1), 'MinecraftDataVersion': (3, 3955),
        'Metadata': (10, {'Name': (8, 'Task23 projection check'), 'Author': (8, 'Task23'),
                         'Description': (8, 'Nine stone blocks; render and manual-building check only.'),
                         'RegionCount': (3, 1), 'TotalVolume': (3, 9), 'TotalBlocks': (3, 9),
                         'TimeCreated': (4, 0), 'TimeModified': (4, 0), 'EnclosingSize': (10, vec(3, 1, 3))}),
        'Regions': (10, {'TestFloor': (10, region)})}
raw = b'\x0a' + string('') + payload(10, root)
parsed = NBT(raw).parse()
assert parsed['Regions']['TestFloor']['Size'] == {'x': 3, 'y': 1, 'z': 3}
packed = parsed['Regions']['TestFloor']['BlockStates'][0]
assert [(packed >> (2 * i)) & 3 for i in range(9)] == [1] * 9
fixture = ROOT / 'client/schematics/task23-test-floor.litematic'
fixture.parent.mkdir(parents=True, exist_ok=True)
fixture.write_bytes(gzip.compress(raw, mtime=0))
assert NBT(gzip.decompress(fixture.read_bytes())).parse() == parsed

runtime_files = []
for file in sorted((ROOT / 'client').rglob('*')):
    if file.is_file():
        path = file.relative_to(ROOT / 'client').as_posix()
        assert path not in base_files, 'Refuse to silently replace formal file: ' + path
        data = file.read_bytes()
        runtime_files.append({'path': path, 'size': len(data), 'sha1': digest(data, 'sha1'),
                              'sha256': digest(data), 'policy': 'managed' if path.startswith('mods/') else 'seed'})
save_json('client-additions.json', {'schema': 1, 'type': 'local_candidate_delta_only',
                                  'base_pack': '1.4.26', 'side': 'client', 'files': runtime_files,
                                  'runtime_verified': False, 'promotable': False})
save_json('static-verification.json', {'base_pack': '1.4.26', 'base_manifest_sha256': digest(base_bytes),
                                     'minecraft': '1.21.1', 'neoforge': '21.1.250',
                                     'official_artifacts_verified': True, 'mods': mods,
                                     'embedded_dependencies': embedded, 'existing_api_hash_matches_formal': True,
                                     'existing_buffer_builder_at': json.loads((ROOT / 'existing-at-hits.json').read_text()),
                                     'config_schema_checked_against_exact_source': True,
                                     'fixture': {'version': 7, 'data_version': 3955, 'size': [3, 1, 3],
                                                 'palette_decoded': True, 'blocks': 9, 'entities': 0},
                                     'runtime_started': False, 'runtime_verified': False,
                                     'formal_or_task14_or_131_mutation': False})

# The distributable overlay includes exact sources and license copies alongside binaries.
for license_name in ('forgematica-LGPL-3.0.txt', 'mafglib-LGPL-3.0.txt', 'GPL-3.0.txt', 'Apache-2.0.txt'):
    assert (ROOT / 'redistribution/licenses' / license_name).stat().st_size > 1000
archive_path = ROOT / 'schematic-client-overlay-task23-qa.zip'
with zipfile.ZipFile(archive_path, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    for file in sorted((ROOT / 'client').rglob('*')):
        if file.is_file():
            archive.write(file, file.relative_to(ROOT / 'client').as_posix())
    for file in sorted((ROOT / 'redistribution').rglob('*')):
        if file.is_file():
            archive.write(file, 'third-party-notices/schematic/' + file.relative_to(ROOT / 'redistribution').as_posix())
    archive.write(ROOT / 'HANDOFF.txt', 'third-party-notices/schematic/HANDOFF.txt')
with zipfile.ZipFile(archive_path) as archive:
    assert archive.testzip() is None
save_json('overlay-receipt.json', {'file': archive_path.name, 'sha256': digest(archive_path.read_bytes()),
                                 'size': archive_path.stat().st_size, 'runtime_mods': 2,
                                 'runtime_verified': False, 'sources_and_licenses_included': True})
print('Prepared client overlay: 2 unchanged official mods, manual configuration, 9-block fixture, source/license notices.')
print('Static checks passed. No Minecraft launch; runtime validation remains pending with task14.')
print('ZIP SHA-256:', digest(archive_path.read_bytes()))
