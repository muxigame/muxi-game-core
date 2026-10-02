$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
$state=[IO.File]::ReadAllText((Join-Path $root 'receipts\active-run.json')) | ConvertFrom-Json
if(-not $state.lab.StartsWith($root+'\runs\',[StringComparison]::OrdinalIgnoreCase)){throw 'Outside own task23 runs'}
$log=Join-Path $state.lab 'logs\latest.log'
$stream=[IO.File]::Open($log,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
try{$reader=New-Object IO.StreamReader($stream);$lines=$reader.ReadToEnd() -split "`n"; $lines | Select-String -Pattern 'schematic|litemat|forgemat|malilib|mafglib|ERROR|Exception|spawn|teleport|dimension|disconnect|Stopping|chunk' | Select-Object -Last 120 | ForEach-Object {$_.Line}}finally{$stream.Dispose()}
