"""Synthetic format tests only; these are not game reproduction results."""
import unittest
from transfer_trace_analyzer import analyze


def row(event, timestamp, side='client', transfer='one'):
    return dict(session='synthetic', transfer=transfer, side=side,
                event=event, mono_ms=timestamp)


class TraceTests(unittest.TestCase):
    def test_local_durations_and_independent_clocks(self):
        results = analyze([row('screen_open', 10), row('screen_close', 20010),
                           row('landing_chunk_receive', 30), row('section_ready', 19030),
                           row('position_send', 900000, 'server'),
                           row('teleport_ack', 900050, 'server')])
        by_side = {item['side']: item for item in results}
        self.assertEqual(by_side['client']['durations']['loading_screen_ms'], 20000)
        self.assertEqual(by_side['client']['durations']['chunk_to_section_ready_ms'], 19000)
        self.assertEqual(by_side['server']['durations']['position_ack_ms'], 50)
        self.assertNotIn('cross_clock_ms', by_side['server']['durations'])

    def test_missing_and_duplicate_events_are_not_success(self):
        result = analyze([row('screen_open', 1), row('screen_open', 2),
                          row('screen_close', 15)])[0]
        self.assertIn('loading_screen_ms', result['unavailable'])
        self.assertEqual(result['duplicate_events'], ['screen_open'])

    def test_reversed_time_is_unavailable(self):
        result = analyze([row('screen_open', 50), row('screen_close', 20)])[0]
        self.assertNotIn('loading_screen_ms', result['durations'])

    def test_transfers_cannot_mix(self):
        result = analyze([row('screen_open', 50), row('screen_close', 80, transfer='two')])
        self.assertEqual(len(result), 2)
        self.assertTrue(all('loading_screen_ms' in item['unavailable'] for item in result))


if __name__ == '__main__':
    unittest.main()
