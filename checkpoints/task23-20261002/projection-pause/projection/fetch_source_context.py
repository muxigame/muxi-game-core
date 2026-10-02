"""Preserve official upstream build context and license notices for redistribution."""
import hashlib
import json
from pathlib import Path
import urllib.parse
import urllib.request
import zipfile
import io

ROOT = Path(__file__).resolve().parent
records = []
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Muxi-Task23-Client-Schematic-Install/1.0"})
    with urllib.request.urlopen(req, timeout=45) as response:
        return response.read()

for project, repo in (("forgematica", "litematica-neoforge"), ("mafglib", "malilib-neoforge")):
    version = json.loads((ROOT / f"{project}-versions.json").read_text())[0]
    query = urllib.parse.urlencode({"sha": "neoforge/1.21.1", "until": version["date_published"], "per_page": 1})
    url = f"https://api.github.com/repos/CagayakeGirls/{repo}/commits?{query}"
    metadata = json.loads(get(url))
    if not metadata:
        raise ValueError("No official source context before release")
    commit = metadata[0]["sha"]
    url = f"https://codeload.github.com/CagayakeGirls/{repo}/zip/{commit}"
    data = get(url)
    dest = ROOT / "redistribution" / "source-context"
    dest.mkdir(parents=True, exist_ok=True)
    archive = dest / f"{project}-{commit}.zip"
    archive.write_bytes(data)
    z = zipfile.ZipFile(io.BytesIO(data))
    notices = ROOT / "redistribution" / "licenses"
    notices.mkdir(parents=True, exist_ok=True)
    for name in z.namelist():
        if name.endswith("/LICENSE.txt") and len(name.split("/")) == 2:
            (notices / f"{project}-LGPL-3.0.txt").write_bytes(z.read(name))
        if name.endswith("/gradle.properties") and len(name.split("/")) == 2:
            (dest / f"{project}-gradle.properties").write_bytes(z.read(name))
    records.append({"project":project, "release":version["version_number"], "context_commit":commit,
                    "source_context_url":url, "sha256":hashlib.sha256(data).hexdigest(),
                    "note":"Latest official 1.21.1 branch commit before release; contextual build files, not a claim of reproducible binary provenance. Exact published source JAR is retained separately."})
    print(project, commit, "official source context and license saved")
license_dest = ROOT / "redistribution" / "licenses" / "GPL-3.0.txt"
license_dest.write_bytes(get("https://www.gnu.org/licenses/gpl-3.0.txt"))
(ROOT / "source-context-receipt.json").write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")
