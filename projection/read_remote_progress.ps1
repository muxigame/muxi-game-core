$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
$data=@{time=[DateTimeOffset]::UtcNow.ToString('o');java=@(Get-Process java,javaw -ErrorAction SilentlyContinue | ForEach-Object{@{pid=$_.Id;session=$_.SessionId;workingSet=$_.WorkingSet64}})}
foreach($name in @('install-progress.json','install-result.json','launch-progress.json','active-run.json','latest-result.json')){
 $path=Join-Path $root ('receipts\'+$name)
 if(Test-Path -LiteralPath $path){
  $value=Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
  if($name -eq 'install-result.json'){$data[$name]=@{installed=$value.installed;runtimeStarted=$value.runtimeStarted;client=$value.client;engine=$value.engine;stockModCount=@($value.stockMods).Count;projectionMods=$value.projectionMods;qaDriver=$value.qaDriver;entryCoordinationPending=$value.entryCoordinationPending}}
  else{$data[$name]=$value}
 }
}
$active=Join-Path $root 'receipts\active-run.json'
if(Test-Path -LiteralPath $active){
 $lab=(Get-Content -LiteralPath $active -Raw -Encoding UTF8 | ConvertFrom-Json).lab
 if($lab.StartsWith($root+'\runs\',[StringComparison]::OrdinalIgnoreCase)){
  foreach($name in @('runtime-progress.json','runtime-result.json','exit.json')){$path=Join-Path $lab $name;if(Test-Path -LiteralPath $path){$data[$name]=Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json}}
  $log=Join-Path $lab 'boot.log';if(Test-Path -LiteralPath $log){$data.bootTail=@(Get-Content -LiteralPath $log -Tail 18 -Encoding UTF8)}
 }
}
$data | ConvertTo-Json -Depth 10 -Compress
