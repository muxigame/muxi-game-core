$ErrorActionPreference='Stop';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$lab='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002\runs\projection-20261002-044944-d9394440'
$text=[IO.File]::ReadAllText((Join-Path $lab 'hang-thread-dump.txt'))
foreach($name in @('Render thread','Server thread')){ $match=[regex]::Match($text,'(?ms)^"'+[regex]::Escape($name)+'".*?(?=^"|\z)'); Write-Output $match.Value }
$log=[IO.File]::ReadAllText((Join-Path $lab 'logs\latest.log'))
($log -split "`n") | Where-Object {$_ -match 'Projection|schematic|QA_HARDWARE|ERROR|Exception' -and $_ -notmatch 'cursor|shaderpack'} | Select-Object -Last 25
