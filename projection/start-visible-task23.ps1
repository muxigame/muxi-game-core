$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue'
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
$session=(Get-Process -Id $PID).SessionId
if($env:COMPUTERNAME -ne 'JBC_FCRL' -or $session -ne 2){throw 'Coordinated 131 Session 2 required; do not start from SSH Session 0.'}
$log=Join-Path $root ('receipts\entry-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')+'.log')
Start-Transcript -LiteralPath $log
try{
 & 'C:\Python38\python.exe' -B (Join-Path $root 'start_visible_131.py')
 $exit=$LASTEXITCODE
}finally{Stop-Transcript}
exit $exit
