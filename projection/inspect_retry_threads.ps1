$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002';$lab=$root+'\runs\projection-20261002-053523-f39cfd49'
$state=[IO.File]::ReadAllText((Join-Path $root 'receipts\active-run.json')) | ConvertFrom-Json
if($state.pid -ne 4204 -or $state.lab -ne $lab){throw 'Wrong own run'}
$process=Get-Process -Id 4204 -ErrorAction SilentlyContinue
if(-not $process){Write-Output 'OWN_PID_EXITED';exit 0}
@{pid=4204;session=$process.SessionId;cpu=$process.CPU;responding=$process.Responding;time=[DateTimeOffset]::UtcNow.ToString('o')} | ConvertTo-Json -Compress
$tool='C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1\bin\jcmd.exe'
$output=& $tool 4204 Thread.print 2>&1
$dump=Join-Path $lab ('thread-dump-'+[DateTimeOffset]::UtcNow.ToString('HHmmss')+'.txt')
$output | Set-Content -LiteralPath $dump -Encoding UTF8
$text=$output -join "`n"
foreach($name in @('Render thread','Server thread')){[regex]::Match($text,'(?ms)^"'+[regex]::Escape($name)+'".*?(?=^"|\z)').Value}
($text -split "`n") | Select-String -Pattern 'Wmi|Oshi|oshi|WindowsComputer|Rapier|deadlock' -Context 3,6 | Select-Object -First 8
Write-Output ('THREAD_DUMP_PATH='+$dump)
