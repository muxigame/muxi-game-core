$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
for($i=0;$i -lt 4;$i++){
 $active=[IO.File]::ReadAllText((Join-Path $root 'receipts\active-run.json')) | ConvertFrom-Json
 if($active.pid -ne 4204 -or $active.lab -ne ($root+'\runs\projection-20261002-053523-f39cfd49')){throw 'Unexpected run: no interaction'}
 $process=Get-Process -Id 4204 -ErrorAction SilentlyContinue
 $row=@{time=[DateTimeOffset]::UtcNow.ToString('o');pid=4204;alive=($null -ne $process);lab=$active.lab}
 foreach($name in @('runtime-progress.json','runtime-result.json','exit.json')){$path=Join-Path $active.lab $name;if(Test-Path -LiteralPath $path){$row[$name]=[IO.File]::ReadAllText($path)}}
 $path=Join-Path $active.lab 'boot.log';$stream=[IO.File]::Open($path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
 try{$reader=New-Object IO.StreamReader($stream);$log=$reader.ReadToEnd();$row.bootTail=$log.Substring([Math]::Max(0,$log.Length-900))}finally{$stream.Dispose()}
 $row | ConvertTo-Json -Depth 8 -Compress
 if(-not $process -or $row.ContainsKey('runtime-result.json')){break}
 if($i -lt 3){Start-Sleep -Seconds 10}
}
