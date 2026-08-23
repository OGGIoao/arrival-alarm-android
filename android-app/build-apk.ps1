param(
    [string]$SdkRoot = "C:\temp\codex-android-sdk"
)

$ErrorActionPreference = "Stop"
$SourceRoot = $PSScriptRoot
$ProjectRoot = "C:\temp\arrival-alarm-android-source"
$BuildRoot = "C:\temp\arrival-alarm-android-build"
$OutputRoot = Join-Path (Split-Path $SourceRoot -Parent) "outputs"
$BuildTools = Join-Path $SdkRoot "build-tools\36.0.0"
$AndroidJar = Join-Path $SdkRoot "platforms\android-36\android.jar"
$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$Aapt = Join-Path $BuildTools "aapt.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$ZipAlign = Join-Path $BuildTools "zipalign.exe"
$ApkSigner = Join-Path $BuildTools "apksigner.bat"

@($AndroidJar, $Aapt2, $Aapt, $D8, $ZipAlign, $ApkSigner) | ForEach-Object {
    if (-not (Test-Path -LiteralPath $_)) { throw "Missing Android build dependency: $_" }
}

foreach ($temporaryPath in @($ProjectRoot, $BuildRoot)) {
    if (Test-Path -LiteralPath $temporaryPath) {
        $resolvedTemporary = (Resolve-Path -LiteralPath $temporaryPath).Path
        if ($resolvedTemporary -ne $temporaryPath -or -not $resolvedTemporary.StartsWith("C:\temp\arrival-alarm-android-")) {
            throw "Unexpected temporary path: $resolvedTemporary"
        }
        Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
    }
}

New-Item -ItemType Directory -Force -Path $ProjectRoot | Out-Null
Copy-Item -LiteralPath (Join-Path $SourceRoot "AndroidManifest.xml"), (Join-Path $SourceRoot "res"), `
    (Join-Path $SourceRoot "src"), (Join-Path $SourceRoot "test") -Destination $ProjectRoot -Recurse -Force

$AmapWebKey = [string]$env:ARRIVAL_AMAP_WEB_KEY
if ($AmapWebKey -notmatch '^[A-Za-z0-9_-]*$') { throw "ARRIVAL_AMAP_WEB_KEY contains unsupported characters" }
$DeveloperConfigPath = Join-Path $ProjectRoot "src\com\example\arrivalalarm\DeveloperConfig.java"
$DeveloperConfigSource = Get-Content -LiteralPath $DeveloperConfigPath -Raw -Encoding UTF8
$DeveloperConfigSource = $DeveloperConfigSource.Replace("__AMAP_WEB_KEY__", $AmapWebKey)
[System.IO.File]::WriteAllText($DeveloperConfigPath, $DeveloperConfigSource, [System.Text.UTF8Encoding]::new($false))

$Classes = Join-Path $BuildRoot "classes"
$Generated = Join-Path $BuildRoot "generated"
$Dex = Join-Path $BuildRoot "dex"
$CompiledResources = Join-Path $BuildRoot "resources.zip"
$UnsignedApk = Join-Path $BuildRoot "unsigned.apk"
$WithDexApk = Join-Path $BuildRoot "with-dex.apk"
$AlignedApk = Join-Path $BuildRoot "aligned.apk"
$KeyStore = Join-Path $SourceRoot ".signing\debug.keystore"
$OutputApk = Join-Path $OutputRoot "arrival-alarm-debug.apk"

New-Item -ItemType Directory -Force -Path $Classes, $Generated, $Dex, (Split-Path $KeyStore -Parent), $OutputRoot | Out-Null

$UnitClasses = Join-Path $BuildRoot "unit-classes"
New-Item -ItemType Directory -Force -Path $UnitClasses | Out-Null
& javac.exe -encoding UTF-8 -source 8 -target 8 -d $UnitClasses `
    (Join-Path $ProjectRoot "src\com\example\arrivalalarm\CoreLogic.java") `
    (Join-Path $ProjectRoot "src\com\example\arrivalalarm\PlaceInputParser.java") `
    (Join-Path $ProjectRoot "src\com\example\arrivalalarm\TransitSignalMatcher.java") `
    (Join-Path $ProjectRoot "src\com\example\arrivalalarm\StationSignalMatcher.java") `
    (Join-Path $ProjectRoot "src\com\example\arrivalalarm\ScheduleLogic.java") `
    (Join-Path $ProjectRoot "src\com\example\arrivalalarm\CoordinateTransform.java") `
    (Join-Path $ProjectRoot "src\com\example\arrivalalarm\PlaceQuality.java") `
    (Join-Path $ProjectRoot "test\com\example\arrivalalarm\CoreLogicTest.java")
if ($LASTEXITCODE -ne 0) { throw "Core tests compilation failed" }
& java.exe -cp $UnitClasses com.example.arrivalalarm.CoreLogicTest
if ($LASTEXITCODE -ne 0) { throw "Core tests failed" }

& $Aapt2 compile --dir (Join-Path $ProjectRoot "res") -o $CompiledResources
if ($LASTEXITCODE -ne 0) { throw "Android resources compilation failed" }

& $Aapt2 link -o $UnsignedApk -I $AndroidJar --manifest (Join-Path $ProjectRoot "AndroidManifest.xml") `
    --java $Generated --min-sdk-version 26 --target-sdk-version 36 --version-code 7 --version-name "0.4.2" `
    $CompiledResources
if ($LASTEXITCODE -ne 0) { throw "APK resources linking failed" }

$JavaFiles = @(
    Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "src") -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
    Get-ChildItem -LiteralPath $Generated -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
)
& javac.exe -encoding UTF-8 -source 8 -target 8 -classpath $AndroidJar -d $Classes $JavaFiles
if ($LASTEXITCODE -ne 0) { throw "Android Java compilation failed" }

$ClassesJar = Join-Path $BuildRoot "classes.jar"
& jar.exe --create --file $ClassesJar -C $Classes .
if ($LASTEXITCODE -ne 0) { throw "Classes archive creation failed" }

& $D8 --min-api 26 --lib $AndroidJar --output $Dex $ClassesJar
if ($LASTEXITCODE -ne 0) { throw "DEX compilation failed" }

Copy-Item -LiteralPath $UnsignedApk -Destination $WithDexApk -Force
Push-Location $Dex
try {
    & $Aapt add $WithDexApk "classes.dex"
    if ($LASTEXITCODE -ne 0) { throw "Adding classes.dex failed" }
} finally {
    Pop-Location
}

& $ZipAlign -f 4 $WithDexApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "APK alignment failed" }

if (-not (Test-Path -LiteralPath $KeyStore)) {
    & keytool.exe -genkeypair -noprompt -keystore $KeyStore -storepass android -alias androiddebugkey -keypass android `
        -dname "CN=Android Debug,O=Arrival Alarm,C=CN" -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw "Debug signing key generation failed" }
}

& $ApkSigner sign --v4-signing-enabled false --ks $KeyStore --ks-pass pass:android --key-pass pass:android --out $OutputApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "APK signing failed" }
& $ApkSigner verify --verbose --print-certs $OutputApk
if ($LASTEXITCODE -ne 0) { throw "APK verification failed" }

Get-Item -LiteralPath $OutputApk | Select-Object FullName, Length, LastWriteTime
