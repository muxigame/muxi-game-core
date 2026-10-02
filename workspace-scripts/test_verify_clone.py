import unittest

from verify_clone import AIR, signature, states


class PaletteTests(unittest.TestCase):
    def test_single_palette_and_missing_section(self):
        state = {"Name": "minecraft:stone"}
        self.assertEqual(states({"block_states": {"palette": [state]}}), [signature(state)] * 4096)
        self.assertEqual(states(None), [AIR] * 4096)

    def test_padded_five_bit_longs_with_signed_values(self):
        palette = [{"Name": "test:block_%d" % i} for i in range(19)]
        indexes = [i % 19 for i in range(4096)]
        longs = []
        for start in range(0, 4096, 12):
            value = sum(n << (offset * 5) for offset, n in enumerate(indexes[start:start + 12]))
            longs.append(value if value < (1 << 63) else value - (1 << 64))
        actual = states({"block_states": {"palette": palette, "data": longs}})
        self.assertEqual(actual, [signature(palette[i]) for i in indexes])

    def test_invalid_packed_size_fails(self):
        with self.assertRaisesRegex(ValueError, "packed block state length"):
            states({"block_states": {"palette": [{"Name": "a"}, {"Name": "b"}], "data": [0]}})


if __name__ == "__main__":
    unittest.main()
