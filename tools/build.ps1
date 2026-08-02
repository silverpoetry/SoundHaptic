[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$OutputDirectory = Join-Path $ProjectRoot 'dist'
$BuiltApk = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$OutputApk = Join-Path $OutputDirectory 'SoundHaptic-v1.6.0-api102-debug.apk'
$ChecksumFile = "$OutputApk.sha256"
$GradleWrapper = Join-Path $ProjectRoot 'gradlew.bat'

Push-Location $ProjectRoot
try {
    & $GradleWrapper '--no-daemon' 'testDebugUnitTest' 'lintDebug' 'assembleDebug'
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
Copy-Item -LiteralPath $BuiltApk -Destination $OutputApk -Force
$Hash = (Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath $ChecksumFile -Value "$Hash *$(Split-Path -Leaf $OutputApk)" -Encoding ascii

Write-Output $OutputApk
Write-Output "SHA-256: $Hash"
