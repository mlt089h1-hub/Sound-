@echo off
chcp 65001 >nul
title تثبيت برنامج نداء المرضى - Setup Wizard
color 1F

echo ===============================================================================
echo     معالج تثبيت نظام نداء المرضى للمختبرات (LIS Patient Announcer Pro)
echo     مصمم البرنامج: شامل عبدالامير الطائي ^| 07703333687
echo ===============================================================================
echo.
echo مرحباً بك في معالج التثبيت السريع!
echo سيتم تثبيت البرنامج في مجلد البرامج وإنشاء اختصار رسمي على سطح المكتب.
echo.
pause
cls

echo [1/4] جاري تجهيز بيئة التثبيت على نظام ويندوز...
set "INSTALL_DIR=%LOCALAPPDATA%\LIS_Patient_Announcer"
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

echo [2/4] جاري نسخ ملفات البرنامج وقاعدة البيانات...
if exist "dist\LIS_Patient_Announcer_Pro.exe" (
    copy /y "dist\LIS_Patient_Announcer_Pro.exe" "%INSTALL_DIR%\" >nul
) else (
    echo جاري إنشاء النسخة التنفيذية أولاً...
    python -m pip install -r requirements.txt pyinstaller >nul 2>&1
    pyinstaller --clean LIS_Patient_Announcer.spec >nul 2>&1
    copy /y "dist\LIS_Patient_Announcer_Pro.exe" "%INSTALL_DIR%\" >nul
)

copy /y "activation_keys_1000.txt" "%INSTALL_DIR%\" >nul 2>&1
copy /y "patient_announcer.py" "%INSTALL_DIR%\" >nul 2>&1

echo [3/4] جاري إنشاء أيقونة واختصار البرنامج على سطح المكتب (Desktop Shortcut)...
powershell -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut([System.IO.Path]::Combine([Environment]::GetFolderPath('Desktop'), 'نظام نداء المرضى - المختبر.lnk')); $s.TargetPath = '%INSTALL_DIR%\LIS_Patient_Announcer_Pro.exe'; $s.Description = 'نظام نداء المرضى للمختبر الطبي - شامل عبدالامير الطائي'; $s.Save()"

echo [4/4] جاري إضافة البرنامج لقائمة برامج Start Menu...
set "START_MENU=%APPDATA%\Microsoft\Windows\Start Menu\Programs\LIS Patient Announcer"
if not exist "%START_MENU%" mkdir "%START_MENU%"
powershell -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut('%START_MENU%\نظام نداء المرضى.lnk'); $s.TargetPath = '%INSTALL_DIR%\LIS_Patient_Announcer_Pro.exe'; $s.Save()"

echo.
echo ===============================================================================
echo [مبروك!] تم تثبيت البرنامج بنجاح تام على الحاسبة!
echo تم إنشاء اختصار رسمي على سطح المكتب باسم: "نظام نداء المرضى - المختبر"
echo ===============================================================================
echo.
set /p RUN_NOW="هل ترغب في تشغيل البرنامج الآن؟ (Y/N): "
if /i "%RUN_NOW%"=="Y" (
    start "" "%INSTALL_DIR%\LIS_Patient_Announcer_Pro.exe"
)
exit
