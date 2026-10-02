$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
$state=[IO.File]::ReadAllText((Join-Path $root 'receipts\active-run.json')) | ConvertFrom-Json
if($state.pid -ne 33816 -or $state.lab -ne ($root+'\runs\projection-20261002-044944-d9394440')){throw 'Refuse changed run'}
$proc=Get-Process -Id 33816 -ErrorAction SilentlyContinue
if(-not $proc){Write-Output 'OWN_PID_ALREADY_EXITED';exit 0}
@{pid=$proc.Id;session=$proc.SessionId;responding=$proc.Responding;cpu=$proc.CPU;normalExitFlagPresent=(Test-Path -LiteralPath (Join-Path $state.lab 'request-normal-exit.flag'))} | ConvertTo-Json -Compress
$tool='C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1\bin\jcmd.exe'
& $tool 33816 Thread.print 2>&1 | Set-Content -LiteralPath (Join-Path $state.lab 'hang-thread-dump.txt') -Encoding UTF8
Get-Content -LiteralPath (Join-Path $state.lab 'hang-thread-dump.txt') | Select-String -Pattern '"Render thread"|"Server thread"|"main"|java.lang.Thread.State|at .*Minecraft|at .*Sable|at .*SchematicQA|at .*CompletableFuture|at .*WorldRenderer' -Context 0,6 | Select-Object -First 20
$log=Join-Path $state.lab 'logs\latest.log';Get-Content -LiteralPath $log -Tail 30
