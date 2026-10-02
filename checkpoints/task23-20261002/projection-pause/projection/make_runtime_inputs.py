"""Package only independently owned test inputs and formal product binaries."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT=Path(__file__).resolve().parent
TASK14=ROOT.parent.parent/'task-14'
formal=json.loads((TASK14/'integration/reports/production-release/downloads/manifest.json').read_text(encoding='utf-8-sig'))
expected=json.loads((TASK14/'unified-review/unified-app-qa-v2/desktop/expected-pack.json').read_text(encoding='utf-8'))
foreign={f['path'] for f in expected['files']}
mods=[f for f in formal['files'] if f['path'].startswith('mods/') and f['path'].endswith('.jar') and len(Path(f['path']).parts)==2]
own=[f for f in mods if f['path'] not in foreign]
assert len(own)==5
build=ROOT/'runtime-inputs'
build.mkdir(exist_ok=True)
(build/'formal-mods.json').write_text(json.dumps({'sourcePackVersion':'1.4.26','files':mods},ensure_ascii=False,indent=2),encoding='utf-8')
(build/'optional-selection.json').write_text(json.dumps({'enabled_optional':[],'reference':'Same released default used by task14 full QA; FirstPerson, AutoThirdPerson and C2ME not enabled.'},indent=2),encoding='utf-8')
with zipfile.ZipFile(ROOT/'runtime-inputs-task23.zip','w',zipfile.ZIP_DEFLATED) as z:
    for f in own:
        p=Path(r'C:\Users\Administrator\WorkSpace\muxigame\bmc5server')/f['path']
        assert hashlib.sha1(p.read_bytes()).hexdigest()==f['sha1']
        z.write(p,'formal-own-mods/'+p.name)
    for p in build.glob('*.json'):
        z.write(p,p.name)
    for p in (ROOT/'runtime-src').rglob('*'):
        if p.is_file():z.write(p,p.relative_to(ROOT).as_posix())
print('Prepared formal five-product-mod input bundle; no frozen QA artifact reused.')
