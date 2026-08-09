Param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
Push-Location $root

if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $root 'sdk' }

Write-Host "Building release APK..."
& "$root\gradlew.bat" assembleRelease

$unsigned = Join-Path $root 'app\build\outputs\apk\release\app-release-unsigned.apk'
$signed = Join-Path $root 'TrebleApp.apk'
$keyPem = Join-Path $root 'keys\platform.x509.pem'
$keyPk8 = Join-Path $root 'keys\platform.pk8'

if (-not (Test-Path $unsigned)) { Throw "Unsigned APK not found: $unsigned" }
if (-not (Test-Path $keyPem)) { Throw "Key certificate not found: $keyPem" }
if (-not (Test-Path $keyPk8)) { Throw "Key private key not found: $keyPk8" }

# Try to find zipalign in the Android SDK and align the APK before signing
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools'
$zipalign = @()
if (Test-Path $buildTools) {
    $zipalign = Get-ChildItem -Path $buildTools -Recurse -Filter zipalign.exe -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

$aligned = Join-Path $root 'app-release-aligned.apk'
if ($zipalign) {
    Write-Host "Aligning unsigned APK with $($zipalign.FullName)..."
    & $zipalign.FullName -f 4 $unsigned $aligned
} else {
    Write-Host "zipalign not found; copying unsigned APK instead..."
    Copy-Item -Force $unsigned $aligned
}

Write-Host "Signing APK with apksigner..."
$apksigner = Get-ChildItem -Path (Join-Path $env:ANDROID_HOME 'build-tools') -Recurse -Filter 'apksigner*' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $apksigner) { Throw "No apksigner found in SDK build-tools" }
Write-Host "Using apksigner: $($apksigner.FullName)"
& $apksigner.FullName sign --key $keyPk8 --cert $keyPem --out $signed $aligned

Remove-Item -Force $aligned

Write-Host "Done: $signed"
Pop-Location
