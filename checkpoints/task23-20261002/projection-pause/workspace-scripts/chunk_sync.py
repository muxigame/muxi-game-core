"""Read-only live inspection and offline-backup-only staging. Never installs files.

Python 3.8+, standard library only. The stage command writes solely below this
script's directory and requires a separate full-world backup as its input.
"""
import argparse
import gzip
import hashlib
import io
import json
import math
import os
from pathlib import Path
import struct
import uuid
import zlib

LIVE_WORLD = Path(r"C:\Users\Administrator\WorkSpace\muxigame\bmc5server\world")
WORKSPACE = Path(__file__).resolve().parent
TARGET_REL = Path("dimensions/muxi_game_core/adventure")
KINDS = ("region", "entities", "poi")
CX = range(140, 156)
CZ = range(-38, -26)
CHUNKS = {(x, z) for x in CX for z in CZ}
REGIONS = ((4, -2), (4, -1))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def within(path, root):
    try:
        path.absolute().resolve().relative_to(root.absolute().resolve())
        return True
    except ValueError:
        return False


def selected(rx, rz, slot):
    return (rx * 32 + slot % 32, rz * 32 + slot // 32) in CHUNKS


def coords(rx, rz, slot):
    return rx * 32 + slot % 32, rz * 32 + slot // 32


class Region:
    def __init__(self, path):
        self.path = Path(path)
        self.data = self.path.read_bytes() if self.path.exists() else None
        self.records = {}
        self.sidecars = {}
        if self.data is None:
            return
        if len(self.data) < 8192:
            raise ValueError("Short region: %s" % path)
        rx, rz = map(int, self.path.stem.split(".")[1:])
        spans = []
        for slot in range(1024):
            loc = struct.unpack_from(">I", self.data, slot * 4)[0]
            if loc == 0:
                continue
            offset, sectors = loc >> 8, loc & 255
            if offset < 2 or not sectors:
                raise ValueError("Invalid location: %s slot %d" % (path, slot))
            start = offset * 4096
            if start + 5 > len(self.data):
                raise ValueError("Record outside file: %s slot %d" % (path, slot))
            length = struct.unpack_from(">I", self.data, start)[0]
            if length < 1 or length + 4 > sectors * 4096 or start + length + 4 > len(self.data):
                raise ValueError("Invalid record length: %s slot %d" % (path, slot))
            spans.append((offset, offset + sectors))
            raw = self.data[start + 4:start + 4 + length]
            stamp = struct.unpack_from(">I", self.data, 4096 + slot * 4)[0]
            self.records[slot] = (raw, stamp)
            if raw[0] & 128:
                x, z = coords(rx, rz, slot)
                sidecar = self.path.parent / ("c.%d.%d.mcc" % (x, z))
                if len(raw) != 1:
                    raise ValueError("Invalid external marker: %s" % path)
                self.sidecars[slot] = (sidecar, sidecar.read_bytes())
        spans.sort()
        if any(b[0] < a[1] for a, b in zip(spans, spans[1:])):
            raise ValueError("Overlapping sectors: %s" % path)

    def payload(self, slot):
        raw = self.records[slot][0]
        body = self.sidecars[slot][1] if raw[0] & 128 else raw[1:]
        method = raw[0] & 127
        if method == 1:
            return gzip.decompress(body)
        if method == 2:
            return zlib.decompress(body)
        if method == 3:
            return body
        raise ValueError("Unsupported compression %d in %s" % (method, self.path))

    def fingerprint(self, slot):
        if slot not in self.records:
            return None
        raw, stamp = self.records[slot]
        external = self.sidecars[slot][1] if slot in self.sidecars else b""
        return sha(raw + external), stamp


def encode_region(records):
    header = bytearray(8192)
    body = bytearray()
    offset = 2
    for slot, (raw, stamp) in sorted(records.items()):
        record = struct.pack(">I", len(raw)) + raw
        sectors = (len(record) + 4095) // 4096
        if sectors > 255 or offset >= (1 << 24):
            raise ValueError("Record too large for internal Anvil storage")
        struct.pack_into(">I", header, slot * 4, offset * 256 + sectors)
        struct.pack_into(">I", header, 4096 + slot * 4, stamp)
        body.extend(record)
        body.extend(b"\0" * (sectors * 4096 - len(record)))
        offset += sectors
    return bytes(header + body)


class NBT:
    """Bounded parser used only for validation; never rewrites NBT."""
    def __init__(self, data):
        if len(data) > 64 * 1024 * 1024:
            raise ValueError("NBT exceeds validation limit")
        self.stream = io.BytesIO(data)

    def take(self, size):
        if size < 0:
            raise ValueError("Negative NBT length")
        value = self.stream.read(size)
        if len(value) != size:
            raise ValueError("Truncated NBT")
        return value

    def number(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.number(">H")).decode("utf-8", errors="replace")

    def count(self):
        count = self.number(">i")
        if not 0 <= count <= 4 * 1024 * 1024:
            raise ValueError("Invalid NBT collection size")
        return count

    def value(self, tag, depth=0):
        if depth > 100:
            raise ValueError("NBT nesting exceeds validation limit")
        numeric = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if tag in numeric:
            return self.number(numeric[tag])
        if tag == 7:
            return self.take(self.count())
        if tag == 8:
            return self.string()
        if tag == 9:
            subtype, count = self.number(">B"), self.count()
            return [self.value(subtype, depth + 1) for _ in range(count)]
        if tag == 10:
            result = {}
            while True:
                subtype = self.number(">B")
                if subtype == 0:
                    return result
                key = self.string()
                if key in result:
                    raise ValueError("Duplicate compound key")
                result[key] = self.value(subtype, depth + 1)
        if tag in (11, 12):
            fmt = ">i" if tag == 11 else ">q"
            return [self.number(fmt) for _ in range(self.count())]
        raise ValueError("Unknown NBT tag %d" % tag)

    def parse(self):
        tag = self.number(">B")
        self.string()
        result = self.value(tag)
        if self.stream.read(1):
            raise ValueError("Trailing NBT bytes")
        if not isinstance(result, dict):
            raise ValueError("Expected NBT root compound")
        return result


def entity_uuid(entity):
    value = entity.get("UUID")
    if isinstance(value, list) and len(value) == 4:
        bits = 0
        for word in value:
            bits = (bits << 32) | (word & 0xffffffff)
        return str(uuid.UUID(int=bits))
    if "UUIDMost" in entity and "UUIDLeast" in entity:
        bits = ((entity["UUIDMost"] & ((1 << 64) - 1)) << 64) | (entity["UUIDLeast"] & ((1 << 64) - 1))
        return str(uuid.UUID(int=bits))
    raise ValueError("Entity has no supported UUID")


def walk_entities(entity):
    yield entity
    for passenger in entity.get("Passengers", []):
        yield from walk_entities(passenger)


def dimension_strings(value, result):
    if isinstance(value, str) and value in ("minecraft:overworld", "muxi_game_core:adventure"):
        result[value] = result.get(value, 0) + 1
    elif isinstance(value, dict):
        for item in value.values():
            dimension_strings(item, result)
    elif isinstance(value, list):
        for item in value:
            dimension_strings(item, result)


def validate_slot(region, slot, kind, rx, rz, uuid_set=None, references=None):
    root = NBT(region.payload(slot)).parse()
    x, z = coords(rx, rz, slot)
    if kind == "region":
        if (root.get("xPos"), root.get("zPos")) != (x, z):
            raise ValueError("Chunk NBT coordinates disagree: %s slot %d" % (region.path, slot))
    elif kind == "entities":
        if root.get("Position") != [x, z]:
            raise ValueError("Entity storage coordinates disagree: %s slot %d" % (region.path, slot))
        for top in root.get("Entities", []):
            for entity in walk_entities(top):
                identity = entity_uuid(entity)
                if uuid_set is not None:
                    if identity in uuid_set:
                        raise ValueError("Duplicate entity UUID in one dimension: %s" % identity)
                    uuid_set.add(identity)
    if references is not None:
        dimension_strings(root, references)
    return root


def inspect(world, scan_outside=True):
    target = world / TARGET_REL
    report = {"source": "minecraft:overworld", "target": "muxi_game_core:adventure",
              "chunk_count": 192, "chunk_x": [140, 155], "chunk_z": [-38, -27],
              "block_x": [2240, 2495], "block_z": [-608, -417],
              "edge_expansion": {"west": 11, "east": 10, "north": 2, "south": 3},
              "files": [], "source_dimension_reference_strings": {},
              "entity_uuid_policy": "Preserve bytes and UUIDs; source entities remain in source dimension."}
    regions = {}
    source_uuids, target_selected_uuids, outside_uuids = set(), set(), set()
    tracked = {}

    def track(path, data):
        tracked[str(path)] = None if data is None else sha(data)

    for label, base in (("source", world), ("target", target)):
        for kind in KINDS:
            for rx, rz in REGIONS:
                path = base / kind / ("r.%d.%d.mca" % (rx, rz))
                region = Region(path)
                regions[label, kind, rx, rz] = region
                track(path, region.data)
                for sidepath, data in region.sidecars.values():
                    track(sidepath, data)
                slots = [s for s in range(1024) if selected(rx, rz, s)]
                occupied = [s for s in slots if s in region.records]
                if label == "source" and kind == "region" and len(occupied) != len(slots):
                    raise ValueError("Source terrain is missing selected chunks: %s" % path)
                for slot in occupied:
                    ids = source_uuids if label == "source" else target_selected_uuids
                    validate_slot(region, slot, kind, rx, rz, ids,
                                  report["source_dimension_reference_strings"] if label == "source" else None)
                report["files"].append({"side": label, "kind": kind, "region": [rx, rz],
                                        "selected_records": len(occupied),
                                        "outside_records": len(region.records) - len(occupied),
                                        "sha256": None if region.data is None else sha(region.data),
                                        "external_records": len(region.sidecars)})
        # Full 4096-byte zero header proves no allocated sublevel records in these regions.
        for rx, rz in REGIONS:
            path = base / "sublevels" / ("r.%d.%d.slvlr" % (rx, rz))
            data = path.read_bytes() if path.exists() else None
            track(path, data)
            if data is not None and (len(data) != 4096 or any(data)):
                raise ValueError("Nonempty Sable sublevel region needs a separate migration plan: %s" % path)
    if scan_outside:
        for path in sorted((target / "entities").glob("r.*.*.mca")):
            rx, rz = map(int, path.stem.split(".")[1:])
            region = regions.get(("target", "entities", rx, rz)) or Region(path)
            track(path, region.data)
            for sidepath, data in region.sidecars.values():
                track(sidepath, data)
            for slot in region.records:
                if selected(rx, rz, slot):
                    continue
                validate_slot(region, slot, "entities", rx, rz, outside_uuids)
        conflicts = source_uuids & outside_uuids
        if conflicts:
            raise ValueError("%d copied entity UUIDs already exist outside target replacement range" % len(conflicts))
    report["source_entity_count_with_passengers"] = len(source_uuids)
    report["target_replaced_entity_count_with_passengers"] = len(target_selected_uuids)
    report["target_outside_entity_count_with_passengers"] = len(outside_uuids)
    report["source_uuids_already_in_target_selected_range"] = len(source_uuids & target_selected_uuids)
    report["input_consistency"] = "Online inspection is advisory; staging requires stable offline backup inputs."
    return report, regions, tracked


def verify_inputs(tracked):
    for name, expected in tracked.items():
        path = Path(name)
        actual = sha(path.read_bytes()) if path.exists() else None
        if actual != expected:
            raise ValueError("Input changed during processing: %s" % path)


def stage(world, output):
    world, output = world.absolute().resolve(), output.absolute().resolve()
    if within(world, LIVE_WORLD):
        raise ValueError("Staging refuses the live world; use the full offline backup's world directory")
    if not (world / "level.dat").is_file():
        raise ValueError("Input must be a full world backup containing level.dat")
    if not within(output, WORKSPACE) or output == WORKSPACE or within(output, world) or within(world, output):
        raise ValueError("Output must be a new subdirectory of this workspace, separate from backup input")
    if output.exists():
        raise ValueError("Output already exists; choose a new staging directory")
    report, regions, tracked = inspect(world)
    verify_inputs(tracked)
    pending = []
    for kind in KINDS:
        for rx, rz in REGIONS:
            source = regions["source", kind, rx, rz]
            target = regions["target", kind, rx, rz]
            merged = dict(target.records)
            sidecars = dict(target.sidecars)
            delete_sidecars = []
            for slot in range(1024):
                if not selected(rx, rz, slot):
                    continue
                if slot in sidecars:
                    delete_sidecars.append(sidecars.pop(slot)[0].name)
                if slot in source.records:
                    raw, stamp = source.records[slot]
                    if slot in source.sidecars:
                        external = source.sidecars[slot][1]
                        embedded = bytes([raw[0] & 127]) + external
                        if (len(embedded) + 4 + 4095) // 4096 <= 255:
                            raw = embedded
                        else:
                            sidecars[slot] = source.sidecars[slot]
                            raw = bytes([raw[0]])
                            delete_sidecars = [n for n in delete_sidecars if n != source.sidecars[slot][0].name]
                    merged[slot] = raw, stamp
                else:
                    merged.pop(slot, None)
            relative = Path(kind) / target.path.name
            pending.append((relative, encode_region(merged), source, target, sidecars, delete_sidecars, rx, rz))
    output.mkdir(parents=True)
    manifest = {"status": "INCOMPLETE", "backup_world": str(world),
                "live_world": str(LIVE_WORLD), "target_relative": str(TARGET_REL),
                "report": report, "input_hashes": tracked, "replacement_files": [],
                "delete_selected_sidecars": [],
                "scope": "Only listed target files/sidecars. Do not copy source level.dat, playerdata, data, claims, balances, or configs.",
                "rollback": "While stopped, restore original target files from this exact offline full-world backup; remove files originally absent. Restart only after verification."}
    for relative, data, source, target, sidecars, deleted, rx, rz in pending:
        path = output / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        for slot, (oldpath, external) in sidecars.items():
            sidepath = path.parent / oldpath.name
            sidepath.write_bytes(external)
            # Unchanged external records are staged for verification, never installed.
            if selected(rx, rz, slot):
                original = target.path.parent / oldpath.name
                olddata = original.read_bytes() if original.exists() else None
                manifest["replacement_files"].append({"relative": str(relative.parent / oldpath.name),
                                                       "staged_sha256": sha(external),
                                                       "original_sha256": None if olddata is None else sha(olddata)})
        reread = Region(path)
        for slot in range(1024):
            desired = source if selected(rx, rz, slot) else target
            if selected(rx, rz, slot):
                if (slot in reread.records) != (slot in desired.records):
                    raise ValueError("Selected presence mismatch after staging")
                if slot in desired.records:
                    if reread.payload(slot) != desired.payload(slot) or reread.records[slot][1] != desired.records[slot][1]:
                        raise ValueError("Selected payload/timestamp mismatch after staging")
                    validate_slot(reread, slot, relative.parts[0], rx, rz)
            elif reread.fingerprint(slot) != desired.fingerprint(slot):
                raise ValueError("Outside record changed after staging")
        manifest["replacement_files"].append({"relative": str(relative), "staged_sha256": sha(data),
                                               "original_sha256": None if target.data is None else sha(target.data)})
        manifest["delete_selected_sidecars"].extend(str(relative.parent / name) for name in deleted)
    verify_inputs(tracked)
    manifest["status"] = "VERIFIED_STAGING_ONLY"
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    advisory = sub.add_parser("inspect", help="Read-only advisory inspection; never writes")
    advisory.add_argument("--world", type=Path, default=LIVE_WORLD)
    advisory.add_argument("--selected-only", action="store_true", help="Skip target-wide entity UUID scan")
    prepare = sub.add_parser("stage", help="Stage from a full offline backup only; never installs")
    prepare.add_argument("--backup-world", type=Path, required=True)
    prepare.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "inspect":
        report, _, _ = inspect(args.world, scan_outside=not args.selected_only)
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        manifest = stage(args.backup_world, args.output)
        print(json.dumps({"status": manifest["status"], "manifest": str(args.output / "manifest.json"),
                          "replacement_file_count": len(manifest["replacement_files"]),
                          "selected_sidecar_deletions": len(manifest["delete_selected_sidecars"])}, indent=2))


if __name__ == "__main__":
    main()
