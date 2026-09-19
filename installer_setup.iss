; ==============================================================================
; Inno Setup Script: LIS Patient Announcer Pro
; Designer: شامل عبدالامير الطائي | 07703333687
; ==============================================================================

#define MyAppName "نظام نداء المرضى للمختبرات الطبية"
#define MyAppNameEn "LIS Patient Announcer Pro"
#define MyAppVersion "2.5.0"
#define MyAppPublisher "شامل عبدالامير الطائي"
#define MyAppContact "07703333687"
#define MyAppExeName "LIS_Patient_Announcer_Pro.exe"

[Setup]
AppId={{D8A13B4E-520C-4A42-9988-81D2C97E7A1C}
AppName={#MyAppName} ({#MyAppNameEn})
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL=tel:{#MyAppContact}
AppSupportURL=tel:{#MyAppContact}
AppUpdatesURL=tel:{#MyAppContact}
DefaultDirName={autopf}\{#MyAppNameEn}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=Output_Installer
OutputBaseFilename=LIS_Patient_Announcer_Setup
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=lowest
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "arabic"; MessagesFile: "compiler:Languages\Arabic.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "startupicon"; Description: "تشغيل البرنامج تلقائياً مع بدء تشغيل ويندوز (Windows Startup)"; GroupDescription: "خيارات التشغيل:"

[Files]
Source: "dist\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "activation_keys_1000.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "ACTIVATION_KEYS_1000.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "README.md"; DestDir: "{app}"; Flags: isreadme ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\إلغاء تثبيت البرنامج (Uninstall)"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppNameEn}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startupicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent
