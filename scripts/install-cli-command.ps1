param(
    [string]$CommandName = "sc2loc",
    [string]$InstallDir = "$env:LOCALAPPDATA\Microsoft\WindowsApps",
    [ValidateSet("auto", "jar", "script")]
    [string]$Mode = "auto"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$jarPath = $null
$jarCandidates = Get-ChildItem -Path (Join-Path $repoRoot "target") -Filter "*-cli.jar" -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending
if ($jarCandidates -and $jarCandidates.Count -gt 0) {
    $jarPath = $jarCandidates[0].FullName
}

$resolvedMode = $Mode
if ($resolvedMode -eq "auto") {
    $resolvedMode = if ($jarPath) { "jar" } else { "script" }
}

if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}

$shimPath = Join-Path $InstallDir ($CommandName + ".cmd")

if ($resolvedMode -eq "script") {
    $runScriptPath = Join-Path $repoRoot "scripts\run-cli.ps1"
    if (-not (Test-Path $runScriptPath)) {
        throw "Cannot find run script: $runScriptPath"
    }

    $shimContent = @(
        "@echo off",
        ('powershell -NoProfile -ExecutionPolicy Bypass -File "{0}" %*' -f $runScriptPath)
    ) -join "`r`n"
} else {
    if (-not $jarPath) {
        throw "No CLI jar found in target. Build it first with: mvn -DskipTests package, or use -Mode script"
    }

    $shimContent = @(
        "@echo off",
        ('java -jar "{0}" %*' -f $jarPath)
    ) -join "`r`n"
}

Set-Content -Path $shimPath -Value $shimContent -Encoding Ascii

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ([string]::IsNullOrWhiteSpace($userPath)) {
    $userPath = ""
}

$existing = $userPath.Split(';', [System.StringSplitOptions]::RemoveEmptyEntries)
$hasInstallDir = $false
foreach ($pathEntry in $existing) {
    if ($pathEntry.Trim().ToLowerInvariant() -eq $InstallDir.Trim().ToLowerInvariant()) {
        $hasInstallDir = $true
        break
    }
}

if (-not $hasInstallDir) {
    $newUserPath = if ([string]::IsNullOrWhiteSpace($userPath)) { $InstallDir } else { $userPath + ";" + $InstallDir }
    [Environment]::SetEnvironmentVariable("Path", $newUserPath, "User")
}

$processPath = [Environment]::GetEnvironmentVariable("Path", "Process")
if ($processPath -notlike "*$InstallDir*") {
    [Environment]::SetEnvironmentVariable("Path", ($processPath + ";" + $InstallDir), "Process")
}

Write-Host "Installed command shim: $shimPath"
if ($resolvedMode -eq "script") {
    Write-Host "Mode: repo run script"
} else {
    Write-Host "Mode: standalone jar"
    Write-Host "Jar: $jarPath"
}
Write-Host "Command name: $CommandName"
Write-Host "Install dir: $InstallDir"
Write-Host ""
Write-Host "Test command:"
Write-Host "  $CommandName --help"
Write-Host ""
Write-Host "If your current terminal still cannot find the command, open a new terminal window."
