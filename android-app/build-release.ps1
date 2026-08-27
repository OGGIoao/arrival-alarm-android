param(
    [string]$SdkRoot = "C:\temp\codex-android-sdk",
    [switch]$VerifyPlayBundle
)

$ErrorActionPreference = "Stop"
$GradleWrapper = Join-Path $PSScriptRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $GradleWrapper)) {
    throw "Missing Gradle Wrapper: $GradleWrapper"
}
if (-not (Test-Path -LiteralPath $SdkRoot)) {
    throw "Missing Android SDK: $SdkRoot"
}

$PreviousAndroidHome = $env:ANDROID_HOME
$PreviousAndroidSdkRoot = $env:ANDROID_SDK_ROOT
$PreviousGradleUserHome = $env:GRADLE_USER_HOME
$PreviousArrivalBuildDir = $env:ARRIVAL_BUILD_DIR
$env:ANDROID_HOME = (Resolve-Path -LiteralPath $SdkRoot).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME) -or $env:GRADLE_USER_HOME -match '[^\x00-\x7F]') {
    $env:GRADLE_USER_HOME = Join-Path $env:TEMP "arrival-alarm-gradle-home"
}
New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME | Out-Null
if ([string]::IsNullOrWhiteSpace($env:ARRIVAL_BUILD_DIR) -or $env:ARRIVAL_BUILD_DIR -match '[^\x00-\x7F]') {
    $env:ARRIVAL_BUILD_DIR = Join-Path $env:TEMP "arrival-alarm-gradle-build"
}
$BuildOutputRoot = $env:ARRIVAL_BUILD_DIR

Push-Location $PSScriptRoot
try {
    $Tasks = @("clean", "testDebugUnitTest", "lintRelease")
    if ($VerifyPlayBundle) {
        $Tasks += "verifyPlayBundle"
    } else {
        $Tasks += @("bundleRelease", "validateReleaseBundle")
    }
    $Tasks += "--no-daemon"
    & $GradleWrapper @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle release verification failed" }
} finally {
    Pop-Location
    $env:ANDROID_HOME = $PreviousAndroidHome
    $env:ANDROID_SDK_ROOT = $PreviousAndroidSdkRoot
    $env:GRADLE_USER_HOME = $PreviousGradleUserHome
    $env:ARRIVAL_BUILD_DIR = $PreviousArrivalBuildDir
}

$ReleaseBundles = @(
    Get-ChildItem -LiteralPath (Join-Path $BuildOutputRoot "outputs\bundle\release") -Filter "*.aab" |
        Select-Object -ExpandProperty FullName
)
if ($ReleaseBundles.Count -ne 1) {
    throw "Expected one release AAB, found $($ReleaseBundles.Count)"
}
Get-Item -LiteralPath $ReleaseBundles[0] | Select-Object FullName, Length, LastWriteTime
