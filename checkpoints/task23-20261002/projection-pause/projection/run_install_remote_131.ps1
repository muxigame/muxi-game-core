$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
Write-Output 'TASK23_PREPARATION_ONLY_NO_MINECRAFT'
& 'C:\Python38\python.exe' -B (Join-Path $root 'install_remote_131.py')
if($LASTEXITCODE -ne 0){throw ('Independent install failed with exit '+$LASTEXITCODE)}
Write-Output 'TASK23_INSTALLED_NOT_YET_LAUNCHED'
