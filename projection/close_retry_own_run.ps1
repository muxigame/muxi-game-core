$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002';$lab=$root+'\runs\projection-20261002-053523-f39cfd49'
$state=[IO.File]::ReadAllText((Join-Path $root 'receipts\active-run.json')) | ConvertFrom-Json
if($state.pid -ne 4204 -or $state.lab -ne $lab){throw 'Refuse changed active run'}
$process=Get-Process -Id 4204 -ErrorAction SilentlyContinue
if(-not $process){Write-Output 'OWN_PID_ALREADY_EXITED';exit 0}
if($process.ProcessName -ne 'java' -or $process.SessionId -ne 2){throw 'Own Java identity mismatch'}
$process.EnableRaisingEvents=$true
[IO.File]::WriteAllText((Join-Path $lab 'request-normal-exit.flag'),'Task23 request own standard server save/stop and client stop. No forced termination.')
$exited=$process.WaitForExit(45000)
$row=@{pid=4204;lab=$lab;normalExitRequested=$true;exited=$exited;forcedTermination=$false;otherProcessesTouched=$false;time=[DateTimeOffset]::UtcNow.ToString('o')}
if($exited){$row.exitCode=$process.ExitCode}
$json=$row | ConvertTo-Json -Depth 8;[IO.File]::WriteAllText((Join-Path $lab 'normal-close-observation.json'),$json);Write-Output $json
