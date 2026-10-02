"""Read existing client directory metadata over the already authorized SSH link.
No remote file writes, processes, launch, cleanup, or game commands.
"""
import base64
import json
import pathlib
import subprocess

SCRIPT = r"""
$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'Stop'
$roots = @(
 'C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001\qa-runtime',
 'C:\Users\ranzh\Desktop',
 'C:\Users\jbc-1\Desktop',
 'C:\Users\jbc-1\AppData\Local\BatterMC5Remake'
)
$results = @()
foreach ($p in $roots) {
 if (Test-Path -LiteralPath $p) {
  $items = @(Get-ChildItem -LiteralPath $p -Force | Select-Object Name,Mode,LastWriteTimeUtc)
  $results += [pscustomobject]@{path=$p;children=$items}
 }
}
[pscustomobject]@{readOnly=$true;roots=$results} | ConvertTo-Json -Depth 6 -Compress
"""

if __name__ == '__main__':
    encoded = base64.b64encode(SCRIPT.encode('utf-16le')).decode('ascii')
    command = ['ssh', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
               '-o', 'UpdateHostKeys=no', '-o', 'ConnectTimeout=8',
               'administrator@192.168.110.131', 'powershell', '-NoProfile',
               '-NonInteractive', '-EncodedCommand', encoded]
    result = subprocess.run(command, capture_output=True, timeout=25)
    stdout = result.stdout.decode('utf-8', errors='replace')
    pathlib.Path('transfer-131-client-paths.json').write_text(json.dumps({
        'returncode': result.returncode, 'stdout': stdout,
        'stderr': result.stderr.decode('utf-8', errors='replace')
    }, ensure_ascii=True, indent=2), encoding='utf-8')
    print(json.dumps({'ssh_exit': result.returncode, 'stdout': stdout}, ensure_ascii=True))
