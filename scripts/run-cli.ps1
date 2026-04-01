Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$javaExe = "C:\Program Files\Eclipse Adoptium\jdk-17.0.5.8-hotspot\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    $javaExe = "java"
}

$jarCandidates = @(Get-ChildItem -Path (Join-Path $root "target") -Filter "*-cli.jar" -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending)
if ($jarCandidates.Count -gt 0) {
    & $javaExe -jar $jarCandidates[0].FullName @args
    exit $LASTEXITCODE
}

$mainClassFile = Join-Path $root "target\classes\lv\lenc\cli\LocalizationCli.class"
if (-not (Test-Path $mainClassFile)) {
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($null -ne $mvn) {
        mvn -q -DskipTests compile
    } else {
        $javac = Get-Command javac -ErrorAction SilentlyContinue
        if ($null -eq $javac) {
            throw "Could not build CLI classes. Install Maven or a JDK with javac, or build the CLI jar with: mvn -DskipTests package"
        }

        $classesDir = Join-Path $root "target\classes"
        if (-not (Test-Path $classesDir)) {
            New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
        }

        $cliSources = @(Get-ChildItem -Path (Join-Path $root "src\main\java\lv\lenc\cli\*.java") -File |
            Select-Object -ExpandProperty FullName)
        if ($cliSources.Count -eq 0) {
            throw "No CLI source files found in src/main/java/lv/lenc/cli"
        }

        & javac -d $classesDir $cliSources
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
}

if (Test-Path $mainClassFile) {
    & $javaExe -cp (Join-Path $root "target\classes") "lv.lenc.cli.LocalizationCli" @args
    exit $LASTEXITCODE
}

throw "Could not locate CLI entrypoint after build. Expected: $mainClassFile"

exit $LASTEXITCODE