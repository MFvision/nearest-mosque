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
(Get-Content (Join-Path $PSScriptRoot "AppxManifest.template.xml") -Raw) `
  -replace "@IDENTITY_NAME@", $IdentityName -replace "@PUBLISHER@", $Publisher `
  -replace "@PUBLISHER_NAME@", $PublisherName -replace "@VERSION@", $Version |
  Set-Content -Encoding UTF8 (Join-Path $stage "AppxManifest.xml")
$makeappx = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\makeappx.exe" | Sort-Object FullName | Select-Object -Last 1
& $makeappx.FullName pack /d $stage /p $out /o
if ($LASTEXITCODE -ne 0) { throw "makeappx failed" }
Write-Host "MSIX: $out"
