$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$lab='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002\runs\projection-20261002-053523-f39cfd49'
$file=Get-ChildItem -LiteralPath $lab -Filter 'thread-dump-*.txt' -File | Sort-Object Name -Descending | Select-Object -First 1
$text=[IO.File]::ReadAllText($file.FullName)
$blocks=[regex]::Matches($text,'(?ms)^".*?(?=^"|\z)')
foreach($match in $blocks){$block=$match.Value;if($block -match 'WorldGen|ChunkStatus|ChunkGeneration|hasNearbyLantern|PasterDream|Worker-Main|ForkJoinPool.commonPool' -and $block -notmatch '^"Server thread"'){Write-Output $block}}
