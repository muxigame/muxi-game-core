$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
$lab=$root+'\runs\projection-20261002-044944-d9394440'
$active=Join-Path $root 'receipts\active-run.json';$state=[IO.File]::ReadAllText($active) | ConvertFrom-Json
if($state.pid -ne 33816 -or $state.lab -ne $lab){throw 'Active identity changed'}
$process=Get-Process -Id 33816 -ErrorAction SilentlyContinue
if(-not $process){Write-Output 'OWN_PID_ALREADY_EXITED';exit 0}
if($process.ProcessName -ne 'java' -or $process.SessionId -ne 2){throw 'Own PID identity differs'}
$process.EnableRaisingEvents=$true
$java='C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1\bin\java.exe'
& $java --add-modules jdk.attach -cp (Join-Path $root 'input') Task23Attach 33816 (Join-Path $root 'input\task23-normal-close-agent.jar') $lab
if($LASTEXITCODE -ne 0){throw 'Normal stop agent failed'}
$exited=$process.WaitForExit(20000)
$receipt=@{pid=33816;lab=$lab;standardIntegratedServerHaltRequested=$true;forcedTermination=$false;exited=$exited;otherProcessesTouched=$false;time=[DateTimeOffset]::UtcNow.ToString('o')}
if($exited){$receipt.exitCode=$process.ExitCode;$state.processStillRunning=$false;$state | Add-Member -Force NoteProperty normalCloseCompleted $true;[IO.File]::WriteAllText($active,($state | ConvertTo-Json -Depth 8))}
$json=$receipt | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText((Join-Path $lab 'normal-close-recovery-receipt.json'),$json)
Write-Output $json
