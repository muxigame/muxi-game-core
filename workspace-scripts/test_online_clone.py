import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import online_clone as c


class FakeRcon:
    def __init__(self, clone_reply=None):
        self.calls = []
        self.tickets = set()
        self.clone_reply = clone_reply

    def command(self, command):
        self.calls.append(command)
        if command == "gamerule commandModificationBlockLimit":
            return "Gamerule commandModificationBlockLimit is currently set to: 32768"
        if command == "gamerule sendCommandFeedback":
            return "Gamerule sendCommandFeedback is currently set to: true"
        if command.startswith("gamerule sendCommandFeedback "):
            return command.rsplit(" ", 1)[-1]
        if command == "neoforge tps":
            return "Overall: 20.000 TPS (10.000 ms/tick)"
        if "if loaded" in command:
            return "Test passed"
        if "forceload query" in command:
            dimension = command.split()[2]
            return "Chunk is%s marked for force loading" % ("" if dimension in self.tickets else " not")
        if "forceload add" in command:
            self.tickets.add(command.split()[2])
            return "Marked chunk [140, -38] to be force loaded"
        if "forceload remove" in command:
            self.tickets.remove(command.split()[2])
            return "Unmarked chunk"
        if command.startswith("clone "):
            if self.clone_reply:
                return self.clone_reply
            return "Successfully cloned 4480 block(s)"
        raise AssertionError(command)


class SafetyTests(unittest.TestCase):
    def test_exact_rectangle_without_overlap_and_full_height(self):
        work = [c.tile_info(t) for t in c.tiles()]
        columns = {}
        for tile in work:
            x0, y0, z0, x1, y1, z1 = tile["box"]
            self.assertLessEqual(tile["volume"], 16384)
            self.assertIn(" replace normal", tile["command"])
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    columns.setdefault((x, z), []).append((y0, y1))
        self.assertEqual(set(columns), {(x, z) for x in range(2251, 2486) for z in range(-606, -419)})
        self.assertTrue(all(sorted(spans) == [(-64, -1), (0, 63), (64, 127), (128, 191), (192, 255), (256, 319)] for spans in columns.values()))
        self.assertEqual(sum(t["volume"] for t in work), 16874880)

    def run_fixture(self, rcon):
        with tempfile.TemporaryDirectory(dir=c.WORKSPACE) as tmp:
            directory = Path(tmp)
            (directory / "manifest.json").write_text(json.dumps({"status": "VERIFIED_PRE_CLONE_BACKUP", "plan": c.plan(), "files": []}))
            with patch.object(c, "Rcon", return_value=rcon), patch.object(c.time, "sleep"):
                c.run(directory, 1)
            return (directory / "clone-journal.jsonl").read_text()

    def test_pilot_only_first_chunk_and_cleans_tickets(self):
        rcon = FakeRcon()
        journal = self.run_fixture(rcon)
        self.assertEqual(sum(cmd.startswith("clone ") for cmd in rcon.calls), 6)
        self.assertEqual(rcon.tickets, set())
        self.assertEqual(rcon.calls[-1], "gamerule sendCommandFeedback true")
        self.assertEqual(journal.count('"event": "clone_success"'), 6)

    def test_wrong_reply_stops_and_cleans_tickets(self):
        rcon = FakeRcon("Unknown or incomplete command")
        with self.assertRaisesRegex(RuntimeError, "Clone reply/count"):
            self.run_fixture(rcon)
        self.assertEqual(sum(cmd.startswith("clone ") for cmd in rcon.calls), 1)
        self.assertEqual(rcon.tickets, set())
        self.assertEqual(rcon.calls[-1], "gamerule sendCommandFeedback true")

    def test_preserves_preexisting_ticket(self):
        rcon = FakeRcon()
        rcon.tickets.add(c.TARGET)
        self.run_fixture(rcon)
        self.assertEqual(rcon.tickets, {c.TARGET})
        self.assertFalse(any(c.TARGET in cmd and "forceload remove" in cmd for cmd in rcon.calls))


if __name__ == "__main__":
    unittest.main()
