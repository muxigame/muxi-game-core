$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
$state=[IO.File]::ReadAllText((Join-Path $root 'receipts\active-run.json')) | ConvertFrom-Json
if($state.pid -ne 33816){throw 'Unexpected run identity'}
if(Get-Process -Id 33816 -ErrorAction SilentlyContinue){throw 'Previous own run still alive: refuse preparation'}
$build=[IO.File]::ReadAllText((Join-Path $root 'input\qa-build-receipt.json')) | ConvertFrom-Json
$source=Join-Path $root ('input\'+$build.jar)
if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $build.sha256){throw 'QA source hash mismatch'}
Copy-Item -LiteralPath $source -Destination (Join-Path $root ('client\mods\'+$build.jar)) -Force
& 'C:\Python38\python.exe' -B (Join-Path $root 'prepare_run_131.py')
if($LASTEXITCODE -ne 0){throw 'Preparing independent fresh run failed'}
