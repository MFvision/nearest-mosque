# Packs the Compose for Desktop app image into an MSIX for the Microsoft Store (run on Windows after
# `gradlew createDistributable`). The Store signs submitted packages, so this one is left unsigned.
param(
  [Parameter(Mandatory)] [string] $IdentityName,
  [Parameter(Mandatory)] [string] $Publisher,
  [Parameter(Mandatory)] [string] $PublisherName,
  [Parameter(Mandatory)] [string] $Version
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$app = Join-Path $root "build\compose\binaries\main\app"
$stage = Join-Path $root "build\msix\stage"
$out = Join-Path $root "build\msix\NearMosque-$Version.msix"
Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item -Recurse (Join-Path $app "Near Mosque") $stage
Copy-Item -Recurse (Join-Path $PSScriptRoot "Assets") $stage
# Start at sign-in in the notification area, as the MSI's Run key does: a startup task cannot pass arguments, so it
# runs a copy of the launcher whose settings (app\<launcher>.cfg, read when no arguments are given) add --minimized.
$dir = Join-Path $stage "Near Mosque"
$cfg = Join-Path $dir "app\Near Mosque.cfg"
$startupCfg = Join-Path $dir "app\Near Mosque Startup.cfg"
if (Select-String -Path $cfg -Pattern "^\[ArgOptions\]" -Quiet) { throw "The launcher already has arguments: add --minimized to the startup copy's by hand" }
Copy-Item (Join-Path $dir "Near Mosque.exe") (Join-Path $dir "Near Mosque Startup.exe")
Copy-Item $cfg $startupCfg
[System.IO.File]::AppendAllText($startupCfg, "`r`n[ArgOptions]`r`narguments=--minimized`r`n", [System.Text.Encoding]::ASCII)
if (-not (Select-String -Path $startupCfg -Pattern "^arguments=--minimized$" -Quiet)) { throw "The startup launcher does not pass --minimized" }
(Get-Content (Join-Path $PSScriptRoot "AppxManifest.template.xml") -Raw) `
  -replace "@IDENTITY_NAME@", $IdentityName -replace "@PUBLISHER@", $Publisher `
  -replace "@PUBLISHER_NAME@", $PublisherName -replace "@VERSION@", $Version |
  Set-Content -Encoding UTF8 (Join-Path $stage "AppxManifest.xml")
$makeappx = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\makeappx.exe" | Sort-Object FullName | Select-Object -Last 1
& $makeappx.FullName pack /d $stage /p $out /o
if ($LASTEXITCODE -ne 0) { throw "makeappx failed" }
Write-Host "MSIX: $out"
