"""Offline reader for the proposed transfer JSONL trace; never contacts Minecraft.

Input records: session, transfer, side (server/client), event, mono_ms.
Only durations within the same process clock are calculated.
"""
import argparse
import collections
import json
import pathlib

PAIRS = {
    'server': [
        ('target_preparation_ms', 'request', 'target_ready'),
        ('request_to_position_send_ms', 'request', 'position_send'),
        ('position_ack_ms', 'position_send', 'teleport_ack'),
        ('landing_chunk_dispatch_ms', 'position_send', 'landing_chunk_send'),
        ('ack_to_ready_hint_ms', 'teleport_ack', 'ready_hint_receive'),
    ],
    'client': [
        ('respawn_to_position_ms', 'respawn_receive', 'position_receive'),
        ('respawn_to_load_start_ms', 'respawn_receive', 'load_start_receive'),
        ('respawn_to_landing_chunk_ms', 'respawn_receive', 'landing_chunk_receive'),
        ('chunk_to_section_ready_ms', 'landing_chunk_receive', 'section_ready'),
        ('section_ready_to_screen_close_ms', 'section_ready', 'screen_close'),
        ('loading_screen_ms', 'screen_open', 'screen_close'),
    ],
}


def analyze(records):
    grouped = collections.defaultdict(list)
    for record in records:
        side = record['side']
        if side not in PAIRS:
            raise ValueError('side must be server or client')
        if not isinstance(record['mono_ms'], (int, float)):
            raise ValueError('mono_ms must be numeric')
        grouped[(record['session'], record['transfer'], side)].append(record)
    output = []
    for (session, transfer, side), items in sorted(grouped.items()):
        timestamps = collections.defaultdict(list)
        for record in items:
            timestamps[record['event']].append(record['mono_ms'])
        duplicates = sorted(event for event, values in timestamps.items() if len(values) != 1)
        durations, unavailable = {}, []
        for label, begin, end in PAIRS[side]:
            if len(timestamps[begin]) != 1 or len(timestamps[end]) != 1:
                unavailable.append(label)
                continue
            elapsed = timestamps[end][0] - timestamps[begin][0]
            if elapsed < 0:
                unavailable.append(label)
                continue
            durations[label] = elapsed
        output.append({'session': session, 'transfer': transfer, 'side': side,
                       'durations': durations, 'unavailable': unavailable,
                       'duplicate_events': duplicates})
    return output


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jsonl', type=pathlib.Path)
    args = parser.parse_args()
    with args.jsonl.open(encoding='utf-8') as source:
        records = [json.loads(line) for line in source if line.strip()]
    print(json.dumps(analyze(records), ensure_ascii=False, indent=2))
