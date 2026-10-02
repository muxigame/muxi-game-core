"""Extract probe events from standard logs; measure local spans without mixing clocks."""
import argparse
import collections
import json
import pathlib

PAIR = {
    'client': [
        ('respawn_handler_ms', 'respawn_receive', 'respawn_return'),
        ('world_switch_ms', 'set_level_begin', 'set_level_return'),
        ('engine_switch_ms', 'engines_begin', 'engines_return'),
        ('position_after_respawn_ms', 'respawn_receive', 'position_receive'),
        ('position_handler_ms', 'position_receive', 'position_return_after_ack'),
        ('chunk_after_respawn_ms', 'respawn_receive', 'landing_chunk_receive'),
        ('chunk_to_section_ms', 'landing_chunk_receive', 'section_ready'),
        ('loading_screen_lifecycle_ms', 'screen_open', 'screen_close'),
        ('rendered_screen_phase_ms', 'screen_first_frame', 'screen_close'),
        ('total_client_transfer_ms', 'respawn_receive', 'screen_close'),
    ],
    'server': [
        ('engine_transfer_ms', 'request', 'server_transfer_return'),
        ('position_dispatch_ms', 'request', 'position_send'),
        ('landing_chunk_dispatch_ms', 'position_send', 'landing_chunk_send'),
        ('teleport_ack_ms', 'position_send', 'teleport_ack'),
    ],
}


def extract(lines):
    records = []
    for line in lines:
        marker = 'MUXI_TRANSFER_TRACE '
        if marker not in line:
            continue
        records.append(json.loads(line.split(marker, 1)[1]))
    return records


def spans(items, prefix):
    stack, completed = [], []
    for record in sorted(items, key=lambda r: r['mono_ms']):
        if record['event'] == prefix + '_begin':
            stack.append(record['mono_ms'])
        elif record['event'] == prefix + '_return' and stack:
            completed.append(record['mono_ms'] - stack.pop())
    return {'completed_ms': completed, 'incomplete': len(stack)}


def analyze(records):
    groups = collections.defaultdict(list)
    for record in records:
        groups[(record['session'], record['transfer'], record['side'])].append(record)
    output = []
    for (session, transfer, side), items in sorted(groups.items()):
        times = collections.defaultdict(list)
        for item in items:
            times[item['event']].append(item['mono_ms'])
        durations, unavailable = {}, []
        for name, begin, end in PAIR[side]:
            if len(times[begin]) == len(times[end]) == 1 and times[end][0] >= times[begin][0]:
                durations[name] = times[end][0] - times[begin][0]
            else:
                unavailable.append(name)
        ids = sorted({r['teleport_id'] for r in items if r.get('teleport_id', -1) >= 0})
        output.append({'session': session, 'transfer': transfer, 'side': side,
                       'target': items[0].get('target'), 'teleport_ids': ids,
                       'durations': durations, 'unavailable': unavailable,
                       'iris_reload': spans(items, 'iris_reload'),
                       'iris_pipeline': spans(items, 'iris_pipeline'),
                       'sodium_shutdown': spans(items, 'sodium_shutdown'),
                       'sodium_reset': spans(items, 'sodium_reset'),
                       'extra_reload_requested': len(times['euphoria_dimension_reload_request']),
                       'extra_reload_skipped': len(times['euphoria_extra_reload_skipped']),
                       'chunk_queue_samples': [r['queue'] for r in items if r['event'] == 'chunk_queue_sample'],
                       'clock_note': 'Local process clock only; server/client sessions are different.'})
    return output


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('logs', type=pathlib.Path, nargs='+')
    args = parser.parse_args()
    records = []
    for log in args.logs:
        records.extend(extract(log.read_text(encoding='utf-8', errors='replace').splitlines()))
    print(json.dumps(analyze(records), ensure_ascii=False, indent=2))
