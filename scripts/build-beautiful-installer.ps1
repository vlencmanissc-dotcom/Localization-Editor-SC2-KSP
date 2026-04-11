param(
    [switch]$SkipMavenBuild,
    [string]$VersionOverride,
    [string]$OutputDir
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $projectRoot

[xml]$pom = Get-Content (Join-Path $projectRoot "pom.xml")
$appVersion = if ($VersionOverride) { $VersionOverride } else { $pom.project.version }
$displayVersion = if ($pom.project.properties.'display.version') { $pom.project.properties.'display.version' } else { $appVersion }
$appName = "Localization Editor SC2 KSP"

if (-not $OutputDir) {
    $OutputDir = Join-Path $projectRoot "dist\beautiful-installer"
}

if (-not $SkipMavenBuild) {
    Write-Host "Building project with Maven..."
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed."
    }
}

$targetDir = Join-Path $projectRoot "target"
$preferredJarNames = @(
    "Localization_Editor_SC2_KSP-$appVersion-shaded.jar",
    "Localization_Editor_SC2_KSP-$appVersion.jar"
)

$jar = $null
foreach ($preferredName in $preferredJarNames) {
    $candidate = Join-Path $targetDir $preferredName
    if (Test-Path -LiteralPath $candidate) {
        $jar = Get-Item -LiteralPath $candidate
        break
    }
}

if ($null -eq $jar) {
    $jar = Get-ChildItem $targetDir -Filter "*.jar" -File |
        Where-Object {
            $_.Name -notmatch "sources|javadoc|original" -and
            $_.Name -match ([regex]::Escape($appVersion))
        } |
        Sort-Object @{ Expression = { $_.Name -match "-shaded\.jar$" }; Descending = $true }, LastWriteTime -Descending |
        Select-Object -First 1
}

if ($null -eq $jar) {
    $jar = Get-ChildItem $targetDir -Filter "*.jar" -File |
        Where-Object {
            $_.Name -notmatch "sources|javadoc|original"
        } |
        Sort-Object @{ Expression = { $_.Name -match "-shaded\.jar$" }; Descending = $true }, LastWriteTime -Descending |
        Select-Object -First 1
}

if ($null -eq $jar) {
    throw "Application JAR for version $appVersion was not found in target."
}

$jpackageInput = Join-Path $projectRoot "target\jpackage-input"
if (Test-Path -LiteralPath $jpackageInput) {
    Remove-Item -LiteralPath $jpackageInput -Recurse -Force
}
New-Item -ItemType Directory -Path $jpackageInput -Force | Out-Null
Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $jpackageInput $jar.Name) -Force

$jpackageCmd = Get-Command jpackage -ErrorAction SilentlyContinue
if ($null -eq $jpackageCmd -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    if (Test-Path -LiteralPath $candidate) {
        $jpackageCmd = @{ Source = $candidate }
    }
}
if ($null -eq $jpackageCmd) {
    throw "jpackage was not found. Install JDK 17+ with jpackage."
}

$appImageRoot = Join-Path $projectRoot "target\installer-app-image"
if (Test-Path -LiteralPath $appImageRoot) {
    Remove-Item -LiteralPath $appImageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $appImageRoot -Force | Out-Null

$iconPath = Join-Path $projectRoot "src\main\resources\Assets\Textures\Icon.ico"

Write-Host "Building branded app-image with jpackage..."
& $jpackageCmd.Source `
    --type app-image `
    --dest $appImageRoot `
    --input $jpackageInput `
    --name $appName `
    --main-jar $jar.Name `
    --main-class "lv.lenc.AppLauncher" `
    --icon $iconPath `
    --vendor "Lenc"

if ($LASTEXITCODE -ne 0) {
    throw "jpackage app-image build failed."
}

$appImageDir = Join-Path $appImageRoot $appName
if (-not (Test-Path -LiteralPath $appImageDir)) {
    throw "Expected app-image directory was not created: $appImageDir"
}

Write-Host "Generating installer artwork..."
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $projectRoot "scripts\New-InstallerArt.ps1") -ProjectRoot $projectRoot
if ($LASTEXITCODE -ne 0) {
    throw "Installer artwork generation failed."
}

$wizardSmall = Join-Path $projectRoot "installer\generated\wizard-small.bmp"
$wizardLarge = Join-Path $projectRoot "installer\generated\wizard-large.bmp"
$issPath = Join-Path $projectRoot "installer\LocalizationEditor.iss"

$isccCandidates = @(
    (Get-Command iscc.exe -ErrorAction SilentlyContinue | ForEach-Object Source),
    (Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\ISCC.exe"),
    (Join-Path $env:ProgramFiles "Inno Setup 6\ISCC.exe"),
    (Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe")
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique

if (-not $isccCandidates) {
    Write-Warning "Inno Setup Compiler (ISCC.exe) was not found."
    Write-Host "Prepared app-image:" $appImageDir
    Write-Host "Prepared Inno script:" $issPath
    Write-Host "Prepared wizard art:" $wizardSmall
    Write-Host "Prepared wizard art:" $wizardLarge
    exit 0
}

if (Test-Path -LiteralPath $OutputDir) {
    Remove-Item -LiteralPath $OutputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$iscc = [string](@($isccCandidates)[0])

Write-Host "Compiling beautiful installer with Inno Setup..."
& "$iscc" `
    "/DMyAppVersion=$appVersion" `
    "/DMyDisplayVersion=$displayVersion" `
    "/DMyAppExeDir=$appImageDir" `
    "/DMyWizardSmallBmp=$wizardSmall" `
    "/DMyWizardLargeBmp=$wizardLarge" `
    "/DMyOutputDir=$OutputDir" `
    $issPath

if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup compilation failed."
}

Write-Host "Beautiful installer created in $OutputDir"
