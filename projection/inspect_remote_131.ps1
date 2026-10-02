$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
[Console]::OutputEncoding=[Text.Encoding]::UTF8
$base='C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001'
$data=@{observedUtc=[DateTimeOffset]::UtcNow.ToString('o');computer=$env:COMPUTERNAME;sshSession=(Get-Process -Id $PID).SessionId;readOnly=$true}
Write-Output 'TASK23_READONLY_CONNECTED'
$data.java=@(Get-Process java,javaw -ErrorAction SilentlyContinue | ForEach-Object {@{pid=$_.Id;session=$_.SessionId;workingSet=$_.WorkingSet64}})
$data.directories=@()
foreach($path in @((Join-Path $base 'candidate-pack'),(Join-Path $base 'better-mc-remake'),(Join-Path $base 'unified-app-qa-v2'),(Join-Path $base 'unified-app-qa-v2\desktop'),(Join-Path $base 'unified-app-qa-v2\qa-runtime'))){
 if(Test-Path -LiteralPath $path){$data.directories+=@{path=$path;items=@(Get-ChildItem -LiteralPath $path -Force | Select-Object Name,Mode,Length | Select-Object -First 28)}}
}
$data | ConvertTo-Json -Depth 7 -Compress
