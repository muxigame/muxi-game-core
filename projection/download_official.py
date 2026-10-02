"""Fetch exact Modrinth release artifacts and validate published digests."""
import hashlib
import json
from pathlib import Path
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent
records = []
for project in ("forgematica", "mafglib"):
    version = json.loads((ROOT / f"{project}-versions.json").read_text())[0]
    for item in version["files"]:
        url = item["url"]
        if urllib.parse.urlsplit(url).hostname != "cdn.modrinth.com":
            raise ValueError("Unexpected download host")
        target = ROOT / ("client/mods" if item["primary"] else "redistribution/sources") / item["filename"]
        target.parent.mkdir(parents=True, exist_ok=True)
        request = urllib.request.Request(url, headers={"User-Agent": "Muxi-Task23-Client-Schematic-Install/1.0"})
        with urllib.request.urlopen(request, timeout=45) as response:
            data = response.read()
        if len(data) != item["size"]:
            raise ValueError("Size mismatch: " + item["filename"])
        for kind, expected in item["hashes"].items():
            if hashlib.new(kind, data).hexdigest() != expected:
                raise ValueError("Digest mismatch: " + item["filename"])
        target.write_bytes(data)
        records.append({"project": project, "version": version["version_number"], "version_id": version["id"],
                        "path": target.relative_to(ROOT).as_posix(), "url": url, "size": len(data),
                        "sha256": hashlib.sha256(data).hexdigest(), "published_hashes": item["hashes"],
                        "published_hashes_verified": True, "runtime_mod": item["primary"]})
        print(item["filename"], len(data), "published SHA-1/SHA-512 verified")
(ROOT / "download-receipt.json").write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
