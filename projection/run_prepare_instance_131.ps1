$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
& 'C:\Python38\python.exe' -B 'C:\Users\ranzh\Documents\Codex\schematic-task23-20261002\prepare_run_131.py'
if($LASTEXITCODE -ne 0){throw ('Private run preparation failed: '+$LASTEXITCODE)}
