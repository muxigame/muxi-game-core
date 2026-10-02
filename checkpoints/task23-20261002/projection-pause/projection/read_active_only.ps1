$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
Write-Output 'TASK23_DIRECT_RECEIPT_READ'
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
$active=Join-Path $root 'receipts\active-run.json'
$data=@{activeExists=(Test-Path -LiteralPath $active);time=[DateTimeOffset]::UtcNow.ToString('o')}
if($data.activeExists){
 $value=[IO.File]::ReadAllText($active);Write-Output $value
 $state=$value | ConvertFrom-Json
 $process=Get-Process -Id $state.pid -ErrorAction SilentlyContinue
 $data.pid=$state.pid;$data.pidAlive=($null -ne $process)
 if($process){$data.processName=$process.ProcessName;$data.session=$process.SessionId}
 if($state.lab.StartsWith($root+'\runs\',[StringComparison]::OrdinalIgnoreCase)){
  foreach($name in @('runtime-progress.json','runtime-result.json','exit.json')){$path=Join-Path $state.lab $name;if(Test-Path -LiteralPath $path){$data[$name]=[IO.File]::ReadAllText($path)}}
  $log=Join-Path $state.lab 'boot.log';if(Test-Path -LiteralPath $log){$stream=[IO.File]::Open($log,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite);try{$reader=New-Object IO.StreamReader($stream);$text=$reader.ReadToEnd();$data.bootTail=$text.Substring([Math]::Max(0,$text.Length-6000))}finally{$stream.Dispose()}}
 }
}
else{
 $data.entryLogs=@(Get-ChildItem -LiteralPath (Join-Path $root 'receipts') -File -Filter 'entry-*.log' | Sort-Object Name -Descending | Select-Object -First 2 | ForEach-Object{@{path=$_.FullName;tail=[IO.File]::ReadAllText($_.FullName)}})
}
$data | ConvertTo-Json -Depth 8 -Compress
