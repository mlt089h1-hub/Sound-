@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
title مثبت نظام نداء المرضى للمختبرات - شامل عبدالامير الطائي
color 1F

cls
echo ===============================================================================
echo     معالج تثبيت نظام نداء المرضى للمختبرات الطبية (LIS Patient Announcer)
echo     مصمم ومطور البرنامج: شامل عبدالامير الطائي ^| هاتف: 07703333687
echo ===============================================================================
echo.

:: 1. التحقق من وجود بايثون في النظام
echo [1/4] جاري فحص بيئة التشغيل في حاسبتك...
where python >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo -------------------------------------------------------------------------------
    echo [تنبيه مهم جداً]: بايثون غير مثبت على هذا الكمبيوتر!
    echo.
    echo لبناء ملف التثبيت لأول مرة، يحتاج ويندوز لوجود Python مجاني.
    echo 1. افتح متصفحك واكتب: https://www.python.org/downloads/
    echo 2. حمل وثبت بايثون، وضع علامة صح على: [Add python.exe to PATH]
    echo -------------------------------------------------------------------------------
    echo.
    pause
    exit /b
)

for /f "tokens=*" %%i in ('python --version 2^>^&1') do set PYTHON_VER=%%i
echo [+] تم العثور على: %PYTHON_VER% بنجاح!
echo.

:: 2. تثبيت المكتبات وتجهيز الملفات
echo [2/4] جاري تجهيز وتثبيت محرك الصوت وشاشات العرض...
python -m pip install -q --upgrade pip
python -m pip install -q -r requirements.txt pyinstaller

echo.
echo [3/4] جاري بناء وتثبيت البرنامج في مسار التطبيقات الرسمي...
set "INSTALL_DIR=%LOCALAPPDATA%\LIS_Patient_Announcer"
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

:: إذا لم يكن ملف الـ exe مبنياً مسبقاً، نقوم ببنائه
if not exist "dist\LIS_Patient_Announcer_Pro.exe" (
    echo [+] جاري حزم البرنامج في ملف تشغيلي مستقل (قد يستغرق 30 ثانية)...
    pyinstaller --noconsole --onefile --name "LIS_Patient_Announcer_Pro" --add-data "activation_keys_1000.txt;." patient_announcer.py
)

copy /y "dist\LIS_Patient_Announcer_Pro.exe" "%INSTALL_DIR%\" >nul
copy /y "activation_keys_1000.txt" "%INSTALL_DIR%\" >nul 2>&1
copy /y "patient_announcer.py" "%INSTALL_DIR%\" >nul 2>&1

echo.
echo [4/4] جاري إنشاء أيقونة سطح المكتب واختصار قائمة ابدأ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut([System.IO.Path]::Combine([Environment]::GetFolderPath('Desktop'), 'نظام نداء المرضى - المختبر.lnk')); $s.TargetPath = '%INSTALL_DIR%\LIS_Patient_Announcer_Pro.exe'; $s.WorkingDirectory = '%INSTALL_DIR%'; $s.Description = 'نظام نداء المرضى للمختبر الطبي - شامل عبدالامير الطائي'; $s.Save()"

set "START_MENU=%APPDATA%\Microsoft\Windows\Start Menu\Programs\LIS Patient Announcer"
if not exist "%START_MENU%" mkdir "%START_MENU%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut('%START_MENU%\نظام نداء المرضى.lnk'); $s.TargetPath = '%INSTALL_DIR%\LIS_Patient_Announcer_Pro.exe'; $s.WorkingDirectory = '%INSTALL_DIR%'; $s.Save()"

echo.
echo ===============================================================================
echo [تم التثبيت بنجاح تام!]
echo 1. تم تثبيت البرنامج في الحاسبة.
echo 2. تم إنشاء أيقونة واختصار رسمي على سطح المكتب باسم: "نظام نداء المرضى - المختبر".
echo 3. يمكنك تشغيل البرنامج في أي وقت مباشرة من سطح المكتب.
echo ===============================================================================
echo.
echo اضغط أي زر لتشغيل البرنامج الآن على شاشتك...
pause >nul
start "" "%INSTALL_DIR%\LIS_Patient_Announcer_Pro.exe"
exit /b
