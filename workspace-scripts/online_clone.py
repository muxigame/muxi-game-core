"""Online block-only clone with exact bounds, reversible tickets, and disk backup.

No region writes. Runtime saves/copying are performed by the Minecraft commands.
Plan/backup/run are explicit separate commands. Python 3.8+, standard library.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import socket
import struct
import time

from chunk_sync import Region, NBT, sha, within, WORKSPACE, LIVE_WORLD

SERVER = LIVE_WORLD.parent
SOURCE = "minecraft:overworld"
TARGET = "muxi_game_core:adventure"
BOUNDS = (2251, 2485, -606, -420, -64, 319)
LIMIT = 32768


def tiles():
    xmin, xmax, zmin, zmax, ymin, ymax = BOUNDS
    for cx in range(xmin // 16, xmax // 16 + 1):
        for cz in range(zmin // 16, zmax // 16 + 1):
            x0, x1 = max(xmin, cx * 16), min(xmax, cx * 16 + 15)
            z0, z1 = max(zmin, cz * 16), min(zmax, cz * 16 + 15)
            for y0 in range(ymin, ymax + 1, 64):
                yield (cx, cz, x0, min(y0 + 63, ymax), z0, x1, y0, z1)


def tile_info(tile):
    cx, cz, x0, y1, z0, x1, y0, z1 = tile
    volume = (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1)
    command = "clone from %s %d %d %d %d %d %d to %s %d %d %d replace normal" % (
        SOURCE, x0, y0, z0, x1, y1, z1, TARGET, x0, y0, z0)
    return {"cx": cx, "cz": cz, "box": [x0, y0, z0, x1, y1, z1], "volume": volume, "command": command}


def plan():
    work = [tile_info(t) for t in tiles()]
    expected = 235 * 187 * 384
    assert sum(t["volume"] for t in work) == expected
    assert max(t["volume"] for t in work) <= 16384 < LIMIT
    assert len(work) == 1152
    return {"source": SOURCE, "target": TARGET, "bounds": list(BOUNDS),
            "block_count": expected, "chunks": 192, "commands": len(work),
            "max_batch_blocks": max(t["volume"] for t in work), "gamerule_limit": LIMIT,
            "entities_copied": False, "air_copied": True, "biomes_copied": False,
            "first_command": work[0]["command"], "last_command": work[-1]["command"]}


def properties():
    result = {}
    for line in (SERVER / "server.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            result[key] = value
    return result


class Rcon:
    def __init__(self):
        props = properties()
        self.port = int(props.get("rcon.port", 25575))
        self.password = props["rcon.password"]
        self.id = 10

    @staticmethod
    def packet(identity, kind, text):
        body = struct.pack("<ii", identity, kind) + text.encode("utf-8") + b"\0\0"
        return struct.pack("<i", len(body)) + body

    @staticmethod
    def read(stream):
        def exact(n):
            data = bytearray()
            while len(data) < n:
                part = stream.recv(n - len(data))
                if not part:
                    raise ConnectionError("RCON closed before complete reply")
                data.extend(part)
            return bytes(data)
        length = struct.unpack("<i", exact(4))[0]
        if not 10 <= length <= 16 * 1024 * 1024:
            raise ValueError("Invalid RCON reply size")
        data = exact(length)
        identity, kind = struct.unpack_from("<ii", data)
        return identity, kind, data[8:-2].decode("utf-8", errors="replace")

    def command(self, text):
        self.id += 2
        with socket.create_connection(("127.0.0.1", self.port), timeout=10) as stream:
            stream.settimeout(60)
            stream.sendall(self.packet(1, 3, self.password))
            identity, kind, _ = self.read(stream)
            if identity == -1:
                raise PermissionError("RCON authentication rejected")
            if kind != 2:
                identity, kind, _ = self.read(stream)
                if identity == -1 or kind != 2:
                    raise PermissionError("RCON authentication response invalid")
            stream.sendall(self.packet(self.id, 2, text))
            identity, _, first_body = self.read(stream)
            if identity != self.id:
                raise ValueError("Unexpected initial RCON reply identifier")
            # A second harmless command provides an ordered delimiter for fragmented replies.
            stream.sendall(self.packet(self.id + 1, 2, "list"))
            chunks = [first_body]
            while True:
                identity, _, body = self.read(stream)
                if identity == self.id + 1:
                    return "".join(chunks).strip()
                if identity != self.id:
                    raise ValueError("Unexpected RCON reply identifier")
                chunks.append(body)


def utc():
    return datetime.now(timezone.utc).isoformat()


def log(path, event):
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps({"utc": utc(), **event}, ensure_ascii=False) + "\n")
        handle.flush()


def performance(rcon):
    text = rcon.command("neoforge tps")
    match = re.search(r"Overall: ([\d.]+) TPS \(([\d.]+) ms/tick\)", text)
    if not match:
        raise ValueError("Cannot verify server TPS")
    return {"tps": float(match[1]), "mspt": float(match[2])}


def require_good_performance(rcon):
    perf = performance(rcon)
    if perf["tps"] < 19.0 or perf["mspt"] > 40.0:
        raise RuntimeError("Performance below safe threshold; stopped before next mutation: %s" % perf)
    return perf


def feedback_value(rcon):
    reply = rcon.command("gamerule sendCommandFeedback")
    match = re.search(r"currently set to: (true|false)", reply)
    if not match:
        raise ValueError("Cannot read command feedback setting")
    return match[1]


def suppress_feedback(rcon):
    original = feedback_value(rcon)
    if original == "true":
        reply = rcon.command("gamerule sendCommandFeedback false")
        if "false" not in reply:
            raise RuntimeError("Failed to suppress admin chat feedback")
    return original


def restore_feedback(rcon, original):
    if original == "true":
        rcon.command("gamerule sendCommandFeedback true")


def snapshot(destination):
    """Brief save-off/forced flush snapshot; save-on in finally before returning."""
    destination = destination.absolute().resolve()
    if not within(destination, WORKSPACE) or destination == WORKSPACE or destination.exists():
        raise ValueError("Backup must be a new workspace subdirectory")
    rcon = Rcon()
    require_good_performance(rcon)
    if "32768" not in rcon.command("gamerule commandModificationBlockLimit"):
        raise ValueError("Unexpected clone limit; refusing to change it")
    destination.mkdir(parents=True)
    journal = destination / "journal.jsonl"
    original_feedback = suppress_feedback(rcon)
    save_off = False
    saved_on = False
    manifest = {"status": "INCOMPLETE", "created_utc": utc(), "plan": plan(), "files": [],
                "backup_method": "save-off; forced save-all flush; stable hash verified copy; save-on",
                "recovery": "Do not overwrite live regions. Use these original chunks to restore only target block states/blockentities within the exact rectangle during coordinated recovery; preserve current outside blocks and entities.",
                "original_sendCommandFeedback": original_feedback}
    try:
        reply = rcon.command("save-off")
        log(journal, {"command": "save-off", "reply": reply})
        if "disabled" not in reply.lower() or "already" in reply.lower():
            raise RuntimeError("Could not establish ownership of temporary save-off state")
        save_off = True
        reply = rcon.command("save-all flush")
        log(journal, {"command": "save-all flush", "reply": reply})
        if "Saved the game" not in reply:
            raise RuntimeError("Forced flush not confirmed")
        pending = []
        for base in (LIVE_WORLD, LIVE_WORLD / "dimensions/muxi_game_core/adventure"):
            for kind in ("region", "poi", "entities"):
                for rz in (-2, -1):
                    path = base / kind / ("r.4.%d.mca" % rz)
                    if not path.exists():
                        manifest["files"].append({"relative": str(path.relative_to(LIVE_WORLD)), "sha256": None})
                        continue
                    region = Region(path)
                    pending.append((path, region.data))
                    for external, data in region.sidecars.values():
                        pending.append((external, data))
            for rz in (-2, -1):
                path = base / "sublevels" / ("r.4.%d.slvlr" % rz)
                if path.exists():
                    data = path.read_bytes()
                    if len(data) != 4096 or any(data):
                        raise ValueError("Sable sublevels allocated in selected regions; stop for a separate plan")
                    pending.append((path, data))
        for path, data in pending:
            relative = path.relative_to(LIVE_WORLD)
            dest = destination / "world" / relative
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
            if path.read_bytes() != data or dest.read_bytes() != data:
                raise RuntimeError("Snapshot input changed or copy mismatch: %s" % path)
            manifest["files"].append({"relative": str(relative), "bytes": len(data), "sha256": sha(data)})
        # Validate every selected source/target terrain NBT and coordinates, all 192 each.
        for base in (destination / "world", destination / "world/dimensions/muxi_game_core/adventure"):
            cached_regions = {rz: Region(base / "region" / ("r.4.%d.mca" % rz)) for rz in (-2, -1)}
            for cx in range(140, 156):
                for cz in range(-38, -26):
                    region = cached_regions[cz // 32]
                    slot = cx % 32 + (cz % 32) * 32
                    if slot not in region.records:
                        raise ValueError("Selected terrain missing from snapshot")
                    root = NBT(region.payload(slot)).parse()
                    if (root.get("xPos"), root.get("zPos")) != (cx, cz):
                        raise ValueError("Invalid snapshot chunk coordinates")
        manifest["status"] = "VERIFIED_PRE_CLONE_BACKUP"
    finally:
        try:
            if save_off:
                reply = rcon.command("save-on")
                log(journal, {"command": "save-on", "reply": reply})
                saved_on = "enabled" in reply.lower()
                if not saved_on:
                    raise RuntimeError("URGENT: automatic save re-enable not confirmed")
        finally:
            restore_feedback(rcon, original_feedback)
    manifest["automatic_saving_restored"] = saved_on
    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps({"status": manifest["status"], "files": len(manifest["files"]),
                      "backup": str(destination), "automatic_saving_restored": saved_on}), flush=True)


def query_ticket(rcon, dimension, x, z):
    reply = rcon.command("execute in %s run forceload query %d %d" % (dimension, x, z))
    if "is marked for force loading" in reply:
        return True
    if "is not marked for force loading" in reply:
        return False
    raise ValueError("Could not read chunk ticket state: %s" % reply)


def loaded(rcon, dimension, x, z):
    reply = rcon.command("execute in %s if loaded %d -64 %d" % (dimension, x, z))
    if reply == "Test passed":
        return True
    if reply == "Test failed":
        return False
    raise ValueError("Unexpected loaded test reply: %s" % reply)


def run(backup, chunk_limit):
    backup = backup.absolute().resolve()
    if not within(backup, WORKSPACE):
        raise ValueError("Backup must be in workspace")
    manifest = json.loads((backup / "manifest.json").read_text(encoding="utf-8"))
    if manifest["status"] != "VERIFIED_PRE_CLONE_BACKUP" or manifest["plan"] != plan():
        raise ValueError("Backup manifest not verified or scope changed")
    for file in manifest["files"]:
        if file["sha256"] is not None:
            if sha((backup / "world" / file["relative"]).read_bytes()) != file["sha256"]:
                raise ValueError("Backup integrity mismatch")
    journal = backup / "clone-journal.jsonl"
    completed = set()
    if journal.exists():
        for line in journal.read_text(encoding="utf-8").splitlines():
            event = json.loads(line)
            if event.get("event") == "clone_success":
                completed.add(event["command"])
            elif event.get("event") == "ambiguous_failure":
                raise RuntimeError("Prior ambiguous command failure requires review before resume")
    rcon = Rcon()
    if "32768" not in rcon.command("gamerule commandModificationBlockLimit"):
        raise ValueError("Clone limit changed; no gamerule changes made")
    require_good_performance(rcon)
    original_feedback = suppress_feedback(rcon)
    active_tickets = []
    chunks_done = 0
    try:
        work = [tile_info(t) for t in tiles()]
        for start in range(0, len(work), 6):
            batch = work[start:start + 6]
            if all(t["command"] in completed for t in batch):
                continue
            require_good_performance(rcon)
            x, _, z = batch[0]["box"][:3]
            active_tickets = []
            for dimension in (SOURCE, TARGET):
                if not query_ticket(rcon, dimension, x, z):
                    command = "execute in %s run forceload add %d %d" % (dimension, x, z)
                    reply = rcon.command(command)
                    if "Marked chunk" not in reply or "be force loaded" not in reply:
                        raise RuntimeError("Ticket add not confirmed: %s" % reply)
                    active_tickets.append((dimension, x, z))
                    log(journal, {"event": "ticket_add", "dimension": dimension, "x": x, "z": z, "reply": reply})
            deadline = time.monotonic() + 20
            while not all(loaded(rcon, dim, x, z) for dim in (SOURCE, TARGET)):
                if time.monotonic() > deadline:
                    raise RuntimeError("Single-chunk load did not finish in 20 seconds")
                time.sleep(0.25)
            for tile in batch:
                if tile["command"] in completed:
                    continue
                start_time = time.monotonic()
                log(journal, {"event": "clone_start", "command": tile["command"], "expected_blocks": tile["volume"]})
                try:
                    reply = rcon.command(tile["command"])
                except Exception as exc:
                    log(journal, {"event": "ambiguous_failure", "command": tile["command"], "error": str(exc)})
                    raise
                elapsed = time.monotonic() - start_time
                match = re.fullmatch(r"Successfully cloned (\d+) block\(s\)", reply)
                if not match or int(match[1]) != tile["volume"]:
                    log(journal, {"event": "ambiguous_failure", "command": tile["command"], "reply": reply})
                    raise RuntimeError("Clone reply/count not confirmed: %s" % reply)
                log(journal, {"event": "clone_success", "command": tile["command"], "blocks": int(match[1]), "elapsed_s": elapsed})
                completed.add(tile["command"])
                if elapsed > 2.0:
                    raise RuntimeError("Single batch took %.2fs; pause for review" % elapsed)
                time.sleep(0.5)
            for dimension, x, z in active_tickets[:]:
                reply = rcon.command("execute in %s run forceload remove %d %d" % (dimension, x, z))
                log(journal, {"event": "ticket_remove", "dimension": dimension, "x": x, "z": z, "reply": reply})
                if query_ticket(rcon, dimension, x, z):
                    raise RuntimeError("Ticket removal not confirmed")
                active_tickets.remove((dimension, x, z))
            chunks_done += 1
            perf = performance(rcon)
            log(journal, {"event": "chunk_complete", "cx": batch[0]["cx"], "cz": batch[0]["cz"], "completed_commands": len(completed), **perf})
            print(json.dumps({"completed_commands": len(completed), "total_commands": len(work), **perf}), flush=True)
            if chunk_limit and chunks_done >= chunk_limit:
                break
        log(journal, {"event": "run_complete", "completed_commands": len(completed), "total_commands": len(work)})
    finally:
        cleanup_errors = []
        for dimension, x, z in active_tickets:
            try:
                reply = rcon.command("execute in %s run forceload remove %d %d" % (dimension, x, z))
                log(journal, {"event": "ticket_cleanup", "dimension": dimension, "x": x, "z": z, "reply": reply})
                if query_ticket(rcon, dimension, x, z):
                    raise RuntimeError("Temporary chunk ticket cleanup not confirmed")
            except Exception as exc:
                cleanup_errors.append(str(exc))
        restore_feedback(rcon, original_feedback)
        if cleanup_errors:
            raise RuntimeError("URGENT: ticket cleanup errors: %s" % cleanup_errors)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("plan")
    snap = sub.add_parser("backup")
    snap.add_argument("--output", type=Path, required=True)
    execute = sub.add_parser("run")
    execute.add_argument("--backup", type=Path, required=True)
    execute.add_argument("--chunk-limit", type=int, default=0, help="Limit this invocation for pilot/review; 0 runs all remaining")
    args = parser.parse_args()
    if args.command == "plan":
        print(json.dumps(plan(), indent=2))
    elif args.command == "backup":
        snapshot(args.output)
    else:
        run(args.backup, args.chunk_limit)


if __name__ == "__main__":
    main()
