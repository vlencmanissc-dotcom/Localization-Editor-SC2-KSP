Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$cpFile = Join-Path $root "cp.txt"
$classesDir = Join-Path $root "target\classes"
$mainClassFile = Join-Path $classesDir "lv\lenc\AppLauncher.class"
$pomFile = Join-Path $root "pom.xml"
$sourceRoots = @(
    (Join-Path $root "src\main\java"),
    (Join-Path $root "src\main\resources")
)

function Test-AnyFileNewerThan {
    param(
        [string[]]$Paths,
        [datetime]$ReferenceTime
    )

    foreach ($path in $Paths) {
        if (-not (Test-Path $path)) {
            continue
        }

        $newer = Get-ChildItem -Path $path -Recurse -File |
            Where-Object { $_.LastWriteTime -gt $ReferenceTime } |
            Select-Object -First 1

        if ($null -ne $newer) {
            return $true
        }
    }

    return $false
}

if (-not (Test-Path $cpFile) -or (Get-Item $pomFile).LastWriteTime -gt (Get-Item $cpFile).LastWriteTime) {
    mvn -q -DskipTests dependency:build-classpath "-Dmdep.outputFile=cp.txt"
}

if (-not (Test-Path $mainClassFile)) {
    mvn -q -DskipTests compile
} else {
    $mainClassTime = (Get-Item $mainClassFile).LastWriteTime
    if (Test-AnyFileNewerThan -Paths $sourceRoots -ReferenceTime $mainClassTime) {
        mvn -q -DskipTests compile
    }
}

$cp = (Get-Content -Raw $cpFile).Trim()
if ([string]::IsNullOrWhiteSpace($cp)) {
    throw "cp.txt is empty. Run: mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=cp.txt"
}

$javaExe = "C:\Program Files\Eclipse Adoptium\jdk-17.0.5.8-hotspot\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    $javaExe = "java"
}

& $javaExe -cp ("target/classes;" + $cp) "lv.lenc.AppLauncher"
