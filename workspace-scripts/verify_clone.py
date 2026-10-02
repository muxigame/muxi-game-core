"""Compare saved source and target block states inside the exact clone rectangle.

Reads snapshots only. Includes air and canonical blockentity NBT comparison.
Natural ticking can change either world after a successful command; differences
are reported, never automatically overwritten or hidden.
"""
import argparse
import json
from pathlib import Path

from chunk_sync import Region, NBT
from online_clone import BOUNDS, SOURCE, TARGET


AIR = '{"Name":"minecraft:air"}'


def canonical(value):
    if isinstance(value, bytes):
        return {"byte_array_hex": value.hex()}
    if isinstance(value, dict):
        return {k: canonical(v) for k, v in value.items()}
    if isinstance(value, list):
        return [canonical(v) for v in value]
    return value


def signature(value):
    return json.dumps(canonical(value), sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def states(section):
    if section is None or "block_states" not in section:
        return [AIR] * 4096
    container = section["block_states"]
    palette = [signature(state) for state in container["palette"]]
    if len(palette) == 1:
        return palette * 4096
    bits = max(4, (len(palette) - 1).bit_length())
    per_long, mask = 64 // bits, (1 << bits) - 1
    data = container["data"]
    expected_longs = (4096 + per_long - 1) // per_long
    if len(data) != expected_longs:
        raise ValueError("Unexpected packed block state length")
    result = []
    for index in range(4096):
        number = (data[index // per_long] >> ((index % per_long) * bits)) & mask
        if number >= len(palette):
            raise ValueError("Block state palette index out of bounds")
        result.append(palette[number])
    return result


def root_at(cache, base, cx, cz):
    pair = cx // 32, cz // 32
    if pair not in cache:
        cache[pair] = Region(base / "region" / ("r.%d.%d.mca" % pair))
    region = cache[pair]
    slot = cx % 32 + cz % 32 * 32
    return NBT(region.payload(slot)).parse()


def compare(world, chunk_limit=0):
    xmin, xmax, zmin, zmax, ymin, ymax = BOUNDS
    caches = ({}, {})
    bases = (world, world / "dimensions/muxi_game_core/adventure")
    report = {"source": SOURCE, "target": TARGET, "snapshot_world": str(world),
              "blocks_checked": 0, "state_mismatches": 0, "mismatch_examples": [],
              "mismatch_transitions": {},
              "blockentities_checked": 0, "blockentity_mismatches": 0,
              "blockentity_mismatch_examples": [], "chunks_checked": 0}
    for cx in range(xmin // 16, xmax // 16 + 1):
        for cz in range(zmin // 16, zmax // 16 + 1):
            roots = [root_at(cache, base, cx, cz) for cache, base in zip(caches, bases)]
            sections = [{s["Y"]: s for s in root.get("sections", [])} for root in roots]
            xs = range(max(xmin, cx * 16) - cx * 16, min(xmax, cx * 16 + 15) - cx * 16 + 1)
            zs = range(max(zmin, cz * 16) - cz * 16, min(zmax, cz * 16 + 15) - cz * 16 + 1)
            for sy in range(ymin // 16, ymax // 16 + 1):
                src, dst = [states(s.get(sy)) for s in sections]
                for y in range(16):
                    for z in zs:
                        for x in xs:
                            index = y * 256 + z * 16 + x
                            report["blocks_checked"] += 1
                            if src[index] != dst[index]:
                                report["state_mismatches"] += 1
                                transition = src[index] + " -> " + dst[index]
                                report["mismatch_transitions"][transition] = report["mismatch_transitions"].get(transition, 0) + 1
                                if len(report["mismatch_examples"]) < 20:
                                    report["mismatch_examples"].append({"pos": [cx * 16 + x, sy * 16 + y, cz * 16 + z],
                                                                        "source": src[index], "target": dst[index]})
            entity_maps = []
            for root in roots:
                entries = {}
                for entity in root.get("block_entities", []):
                    x, y, z = entity["x"], entity["y"], entity["z"]
                    if xmin <= x <= xmax and zmin <= z <= zmax and ymin <= y <= ymax:
                        entries[(x, y, z)] = signature(entity)
                entity_maps.append(entries)
            for position in entity_maps[0].keys() | entity_maps[1].keys():
                report["blockentities_checked"] += 1
                if entity_maps[0].get(position) != entity_maps[1].get(position):
                    report["blockentity_mismatches"] += 1
                    if len(report["blockentity_mismatch_examples"]) < 20:
                        left = json.loads(entity_maps[0].get(position, "{}"))
                        right = json.loads(entity_maps[1].get(position, "{}"))
                        report["blockentity_mismatch_examples"].append({"pos": position,
                            "source_present": position in entity_maps[0], "target_present": position in entity_maps[1],
                            "differing_keys": sorted(k for k in left.keys() | right.keys() if left.get(k) != right.get(k)),
                            "source_type": left.get("id"), "target_type": right.get("id")})
            report["chunks_checked"] += 1
            if chunk_limit and report["chunks_checked"] >= chunk_limit:
                return report
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot-world", type=Path, required=True)
    parser.add_argument("--chunk-limit", type=int, default=0)
    args = parser.parse_args()
    print(json.dumps(compare(args.snapshot_world, args.chunk_limit), indent=2))
