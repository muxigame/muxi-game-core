import unittest
from analyze_logs import analyze, extract


def row(event, time, side='client', transfer='1', session='client-process'):
    return {'event': event, 'mono_ms': time, 'side': side, 'transfer': transfer,
            'session': session, 'target': 'muxi_game_core:adventure', 'teleport_id': 7}


class Tests(unittest.TestCase):
    def test_pipeline_and_extra_reload_are_separate_spans(self):
        data = [row('respawn_receive', 0), row('iris_pipeline_begin', 1), row('iris_pipeline_return', 4001),
                row('respawn_return', 4010), row('iris_reload_begin', 4020),
                row('iris_pipeline_begin', 4100), row('iris_pipeline_return', 15000),
                row('iris_reload_return', 15020), row('screen_close', 15500)]
        result = analyze(data)[0]
        self.assertEqual(result['iris_pipeline']['completed_ms'], [4000, 10900])
        self.assertEqual(result['iris_reload']['completed_ms'], [11000])
        self.assertEqual(result['durations']['total_client_transfer_ms'], 15500)

    def test_missing_return_remains_incomplete(self):
        result = analyze([row('iris_reload_begin', 1)])[0]
        self.assertEqual(result['iris_reload']['incomplete'], 1)
        self.assertNotIn('total_client_transfer_ms', result['durations'])

    def test_independent_server_clock(self):
        data = [row('request', 900000, 'server', session='server-process'),
                row('position_send', 900100, 'server', session='server-process'),
                row('respawn_receive', 1), row('screen_close', 11)]
        result = {r['side']: r for r in analyze(data)}
        self.assertEqual(result['server']['durations']['position_dispatch_ms'], 100)
        self.assertEqual(result['client']['durations']['total_client_transfer_ms'], 10)

    def test_extract_ignores_non_probe_log_lines(self):
        self.assertEqual(extract(['ordinary log', 'log MUXI_TRANSFER_TRACE {"event":"x"}']), [{'event': 'x'}])


if __name__ == '__main__':
    unittest.main()
