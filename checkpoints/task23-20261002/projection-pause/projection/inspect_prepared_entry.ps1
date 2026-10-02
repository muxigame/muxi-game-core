$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
$paths=@('C:\Python38\pythonw.exe','C:\Python38\python.exe',($root+'\start_visible_131.py'),($root+'\client\launch.args'),($root+'\client\mods\forgematica-0.4.2+mc1.21.1.jar'),($root+'\client\mods\mafglib-0.4.3+mc1.21.1.jar'))
$files=@();foreach($path in $paths){$files+=@{path=$path;exists=(Test-Path -LiteralPath $path);sha256=if(Test-Path -LiteralPath $path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}else{$null}}}
@{readOnly=$true;runtimeStarted=$false;files=$files;java=@(Get-Process java,javaw -ErrorAction SilentlyContinue|ForEach-Object{@{pid=$_.Id;session=$_.SessionId}})} | ConvertTo-Json -Depth 5 -Compress
