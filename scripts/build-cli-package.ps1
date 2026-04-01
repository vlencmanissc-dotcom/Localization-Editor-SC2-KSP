Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$packageRoot = Join-Path $repoRoot "Release ZIP\cli"
$jarTarget = Join-Path $packageRoot "LocalizationCli.jar"
$distDir = Join-Path $repoRoot "dist"
$zipPath = Join-Path $distDir "LocalizationCli-Package.zip"
$manualJarPath = Join-Path $repoRoot "target\LocalizationCli-manual-cli.jar"

$jarCandidates = @(Get-ChildItem -Path (Join-Path $repoRoot "target") -Filter "*-cli.jar" -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending)

if (-not $jarCandidates -or $jarCandidates.Count -eq 0) {
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($null -ne $mvn) {
        mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    } else {
        $javac = Get-Command javac -ErrorAction SilentlyContinue
        $jarTool = Get-Command jar -ErrorAction SilentlyContinue
        if ($null -eq $javac -or $null -eq $jarTool) {
            throw "No CLI jar found in target and Maven is not installed. Install Maven or a JDK with javac and jar."
        }

        $classesDir = Join-Path $repoRoot "target\cli-package-classes"
        if (Test-Path $classesDir) {
            Remove-Item $classesDir -Recurse -Force
        }
        New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

        $cliSources = @(Get-ChildItem -Path (Join-Path $repoRoot "src\main\java\lv\lenc\cli\*.java") -File |
            Select-Object -ExpandProperty FullName)
        if (-not $cliSources -or $cliSources.Count -eq 0) {
            throw "No CLI source files found in src/main/java/lv/lenc/cli"
        }

        & javac -d $classesDir $cliSources
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }

        if (Test-Path $manualJarPath) {
            Remove-Item $manualJarPath -Force
        }

        Push-Location $classesDir
        try {
            & jar --create --file $manualJarPath --main-class lv.lenc.cli.LocalizationCli .
            if ($LASTEXITCODE -ne 0) {
                exit $LASTEXITCODE
            }
        } finally {
            Pop-Location
        }
    }

    $jarCandidates = @(Get-ChildItem -Path (Join-Path $repoRoot "target") -Filter "*-cli.jar" -File |
        Sort-Object LastWriteTime -Descending)
    if ((-not $jarCandidates -or $jarCandidates.Count -eq 0) -and (Test-Path $manualJarPath)) {
        $jarCandidates = @(Get-Item $manualJarPath)
    }
}

if (-not $jarCandidates -or $jarCandidates.Count -eq 0) {
    throw "CLI jar was not produced. Expected a *-cli.jar in target."
}

Copy-Item $jarCandidates[0].FullName $jarTarget -Force

if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
}

if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

Compress-Archive -Path (Join-Path $packageRoot "*") -DestinationPath $zipPath

Write-Host "CLI package ready: $zipPath"
Write-Host "Contents:"
Get-ChildItem $packageRoot | Select-Object Name, Length
