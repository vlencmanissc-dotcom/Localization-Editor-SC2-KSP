#define MyAppName "Localization Editor SC2 KSP"
#define MyAppPublisher "Lenc"
#define MyAppExeName "Localization Editor SC2 KSP.exe"

#ifndef MyAppVersion
  #define MyAppVersion "2.1"
#endif

#ifndef MyDisplayVersion
  #define MyDisplayVersion "2.1"
#endif

#ifndef MyAppExeDir
  #define MyAppExeDir "..\\target\\installer-app-image\\Localization Editor SC2 KSP"
#endif

#ifndef MyWizardSmallBmp
  #define MyWizardSmallBmp "..\\installer\\generated\\wizard-small.bmp"
#endif

#ifndef MyWizardLargeBmp
  #define MyWizardLargeBmp "..\\installer\\generated\\wizard-large.bmp"
#endif

#ifndef MyOutputDir
  #define MyOutputDir "..\\dist\\beautiful-installer"
#endif

[Setup]
AppId={{A3E8A3DF-9D49-45B9-9149-C6B1E5299AC2}
AppName={#MyAppName}
AppVerName={#MyAppName} {#MyDisplayVersion}
AppVersion={#MyDisplayVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL=https://github.com/VoVanRusLvSC2/Localization-Editor-SC2-KSP
AppSupportURL=https://github.com/VoVanRusLvSC2/Localization-Editor-SC2-KSP/issues
AppUpdatesURL=https://github.com/VoVanRusLvSC2/Localization-Editor-SC2-KSP/releases
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
DisableProgramGroupPage=yes
DisableReadyMemo=no
DisableDirPage=no
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
WizardResizable=no
WizardImageStretch=no
WizardImageBackColor=$07110F
WizardSmallImageBackColor=$07110F
SetupIconFile=..\src\main\resources\Assets\Textures\Icon.ico
WizardImageFile={#MyWizardLargeBmp}
WizardSmallImageFile={#MyWizardSmallBmp}
OutputDir={#MyOutputDir}
OutputBaseFilename=Localization-Editor-SC2-KSP-{#MyDisplayVersion}-setup
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoVersion={#MyAppVersion}
VersionInfoTextVersion={#MyDisplayVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription=Localization Editor SC2 KSP Installer
VersionInfoProductName={#MyAppName}
VersionInfoProductTextVersion={#MyDisplayVersion}
SetupLogging=yes
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog
UsePreviousAppDir=yes
UsePreviousTasks=yes
DisableWelcomePage=no
UninstallDisplayName={#MyAppName}
ChangesAssociations=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Dirs]
Name: "{app}\glossary"; Permissions: users-modify

[Files]
Source: "{#MyAppExeDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs; Excludes: "glossary\*"
Source: "..\src\main\resources\glossary\sc2_word_glossary_KSP.txt"; DestDir: "{app}\glossary"; Flags: ignoreversion onlyifdoesntexist
Source: "..\src\main\resources\glossary\Addition_UnitNames_Detailed_KSP.txt"; DestDir: "{app}\glossary"; Flags: ignoreversion onlyifdoesntexist
Source: "..\src\main\resources\glossary\Addition_Weapons_Detailed_KSP.txt"; DestDir: "{app}\glossary"; Flags: ignoreversion onlyifdoesntexist
Source: "..\src\main\resources\glossary\Addition_Abilities_Detailed_KSP.txt"; DestDir: "{app}\glossary"; Flags: ignoreversion onlyifdoesntexist

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[CustomMessages]
english.WelcomeLabel2=Install the SC2-style localization toolkit with bundled runtime and desktop shortcuts.
russian.WelcomeLabel2=Установка локализационного инструмента в стиле SC2 с комплектным runtime и ярлыками.
english.FinishedHeadingLabel=Localization Editor SC2 KSP is ready.
russian.FinishedHeadingLabel=Localization Editor SC2 KSP готов к работе.
