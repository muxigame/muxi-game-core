$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002';$active=Join-Path $root 'receipts\active-run.json'
$state=[IO.File]::ReadAllText($active) | ConvertFrom-Json
if($state.pid -ne 33816 -or $state.lab -ne ($root+'\runs\projection-20261002-044944-d9394440')){throw 'Changed own run'}
if(Get-Process -Id 33816 -ErrorAction SilentlyContinue){throw 'Own process still alive'}
$log=[IO.File]::ReadAllText((Join-Path $state.lab 'boot.log'))
$receipt=@{pid=33816;pidAlive=$false;standardStopRequestPresent=(Test-Path -LiteralPath (Join-Path $state.lab 'standard-server-stop-request.txt'));forcedTermination=$false;exitCode=$null;exitCodeUnavailableReason='Original launcher had already timed out; bounded recovery monitor ended before process exited.';time=[DateTimeOffset]::UtcNow.ToString('o');bootTail=$log.Substring([Math]::Max(0,$log.Length-5000))}
$state.processStillRunning=$false;$state | Add-Member -Force NoteProperty normalCloseCompleted $true
[IO.File]::WriteAllText($active,($state | ConvertTo-Json -Depth 8))
$json=$receipt | ConvertTo-Json -Depth 8;[IO.File]::WriteAllText((Join-Path $state.lab 'final-normal-exit-observation.json'),$json);Write-Output $json
