"""Use the established SSH link and save bounded task23 receipts locally."""
import argparse
import base64
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
FLAGS = ['-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'UpdateHostKeys=no', '-o', 'ConnectTimeout=8']
HOST = 'administrator@192.168.110.131'

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    parser = argparse.ArgumentParser()
    parser.add_argument('script')
    parser.add_argument('receipt')
    parser.add_argument('--timeout', type=int, default=45)
    args = parser.parse_args()
    code = Path(args.script).read_text(encoding='utf-8-sig')
    encoded = base64.b64encode(code.encode('utf-16le')).decode('ascii')
    try:
        result = subprocess.run(['ssh', *FLAGS, HOST, 'powershell', '-NoProfile', '-NonInteractive',
                                 '-OutputFormat', 'Text', '-EncodedCommand', encoded],
                                stdin=subprocess.DEVNULL, capture_output=True, timeout=args.timeout)
    except subprocess.TimeoutExpired as error:
        stdout = (error.stdout or b'').decode('utf-8-sig', errors='replace')
        stderr = (error.stderr or b'').decode('utf-8-sig', errors='replace')
        (ROOT / args.receipt).write_text(json.dumps({'timeout': args.timeout, 'stdout': stdout, 'stderr': stderr},
                                                  ensure_ascii=False, indent=2), encoding='utf-8')
        print('SSH operation timed out after', args.timeout, 'seconds; receipt saved.')
        print(stdout[:1200], stderr[:500])
        raise SystemExit(124)
    stdout = result.stdout.decode('utf-8-sig', errors='replace')
    stderr = result.stderr.decode('utf-8-sig', errors='replace')
    receipt = {'returncode': result.returncode, 'stdout': stdout, 'stderr': stderr}
    target = ROOT / args.receipt
    if ROOT not in target.resolve().parents:
        raise ValueError('Receipt must remain in task23 candidate folder')
    target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(stdout[:22000])
    if result.returncode:
        print(stderr[:1200])
    raise SystemExit(result.returncode)
