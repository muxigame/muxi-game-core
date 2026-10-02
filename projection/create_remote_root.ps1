$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
if(Test-Path -LiteralPath $root){throw 'Task23 root already exists; inspect it before reuse.'}
$null=New-Item -ItemType Directory -Path $root
$null=New-Item -ItemType Directory -Path (Join-Path $root 'input')
$null=New-Item -ItemType Directory -Path (Join-Path $root 'receipts')
@{created=$root;computer=$env:COMPUTERNAME;session=(Get-Process -Id $PID).SessionId;startedMinecraft=$false} | ConvertTo-Json -Compress
