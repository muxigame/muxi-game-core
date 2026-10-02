$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
$active=Join-Path $root 'receipts\active-run.json'
$state=[IO.File]::ReadAllText($active) | ConvertFrom-Json
if($state.pid -ne 33816 -or $state.lab -ne ($root+'\runs\projection-20261002-044944-d9394440')){throw 'Refuse changed active run'}
$proc=[Diagnostics.Process]::GetProcessById(33816)
if($proc.ProcessName -ne 'java' -or $proc.SessionId -ne 2){throw 'Own Java identity mismatch'}
$proc.EnableRaisingEvents=$true
[IO.File]::WriteAllText((Join-Path $state.lab 'request-normal-exit.flag'),'Task23 failed fixture: request own client Minecraft.disconnect and Minecraft.stop only.')
$exited=$proc.WaitForExit(20000)
$receipt=@{pid=33816;lab=$state.lab;normalExitFlagRequested=$true;exited=$exited;otherProcessesTouched=$false;time=[DateTimeOffset]::UtcNow.ToString('o')}
if($exited){$receipt.exitCode=$proc.ExitCode;$state.processStillRunning=$false;$state | Add-Member -Force NoteProperty normalCloseCompleted $true;[IO.File]::WriteAllText($active,($state | ConvertTo-Json -Depth 8))}
$json=$receipt | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText((Join-Path $state.lab 'normal-close-receipt.json'),$json)
Write-Output $json
