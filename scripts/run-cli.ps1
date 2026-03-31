Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$cpFile = Join-Path $root "cp.txt"
$mainClassFile = Join-Path $root "target\classes\lv\lenc\LocalizationCli.class"

if (-not (Test-Path $cpFile)) {
    mvn -q -DskipTests dependency:build-classpath "-Dmdep.outputFile=cp.txt"
}

if (-not (Test-Path $mainClassFile)) {
    mvn -q -DskipTests compile
}

$cp = (Get-Content -Raw $cpFile).Trim()
if ([string]::IsNullOrWhiteSpace($cp)) {
    throw "cp.txt is empty. Run: mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=cp.txt"
}

$javaExe = "C:\Program Files\Eclipse Adoptium\jdk-17.0.5.8-hotspot\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    $javaExe = "java"
}

& $javaExe -cp ("target/classes;" + $cp) "lv.lenc.cli.LocalizationCli" @args
exit $LASTEXITCODE