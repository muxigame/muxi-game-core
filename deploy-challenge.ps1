[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$serverRoot = 'C:\Users\Administrator\WorkSpace\muxigame\bmc5server'
$repoRoot = $PSScriptRoot
$release = Get-Content -LiteralPath (Join-Path $repoRoot 'build\release.json') -Raw | ConvertFrom-Json
$artifact = Join-Path $repoRoot ('build\libs\' + $release.artifact)
if ((Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant() -ne $release.sha256) { throw 'Build checksum mismatch' }
$rcon = Join-Path $serverRoot 'tools\rcon.ps1'
$online = (& $rcon 'list' | Out-String).Trim()
Write-Output $online
if ($online -notmatch 'There are 0 of a max of \d+ players online') { throw 'Players are online, or the online count could not be verified. No restart or live file changes.' }
if (-not $Apply) { Write-Output "Ready to deploy $($release.version). Re-run with -Apply during an empty-server window."; return }
$serverPids = @(Get-NetTCPConnection -State Listen -LocalPort 25565,25575 -ErrorAction Stop | Select-Object -ExpandProperty OwningProcess -Unique)

# Both commands are processed by the Minecraft server. The second checks the actual
# online entity list atomically with stop, even if someone joined after the first query.
& $rcon 'save-all flush'
& $rcon 'execute unless entity @a run stop'
$deadline = (Get-Date).AddSeconds(120)
do {
    Start-Sleep -Seconds 2
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort 25565,25575 -ErrorAction SilentlyContinue)
    $running = @($serverPids | ForEach-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue })
    if ($listeners.Count -eq 0 -and $running.Count -eq 0) { break }
} while ((Get-Date) -lt $deadline)
if ($listeners.Count -ne 0 -or $running.Count -ne 0) { throw 'Server did not stop. No files replaced; no forced process termination.' }

$backup = Join-Path $serverRoot ('.muxi-game-core-backups\challenge-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup | Out-Null
# A full offline backup precedes the new dimension's first activation.
Copy-Item -LiteralPath (Join-Path $serverRoot 'world') -Destination (Join-Path $backup 'world') -Recurse
Copy-Item -LiteralPath (Join-Path $serverRoot 'config') -Destination (Join-Path $backup 'config') -Recurse
Get-ChildItem -LiteralPath (Join-Path $serverRoot 'mods') -Filter 'muxi-game-core-*.jar' | Copy-Item -Destination $backup
& py (Join-Path $repoRoot 'install.py') --server $serverRoot
if ($LASTEXITCODE -ne 0) { throw "Install failed; server remains stopped. Backup: $backup" }
Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $serverRoot 'start-muxi.ps1')) -WorkingDirectory $serverRoot -WindowStyle Hidden
Write-Output "Started Minecraft only. Backup: $backup. Verify Done and RCON status before publishing the prepared client manifest."
