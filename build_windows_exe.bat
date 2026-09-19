@echo off
chcp 65001 >nul
title بناء برنامج نداء المرضى المستقل (EXE) - شامل عبدالامير الطائي
color 0B

echo ===============================================================================
echo     نظام نداء المرضى للمختبرات الطبية (LIS Patient Announcer Pro)
echo     مصمم ومطور البرنامج: شامل عبدالامير الطائي ^| هاتف / واتساب: 07703333687
echo ===============================================================================
echo.

:: 1. فحص بايثون
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [تنبيه هام]: بايثون غير مثبت على هذا الكمبيوتر!
    echo يرجى تنزيل وتثبيت Python من الموقع الرسمي: https://www.python.org/downloads/
    echo وتأكد من تفعيل علامة: "Add python.exe to PATH" أثناء التثبيت.
    echo.
    pause
    exit /b
)

echo [الخطوة 1/3]: فحص وتثبيت كافة المكتبات البرمجية المطلوبة تلقائياً...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt pyinstaller

echo.
echo [الخطوة 2/3]: جاري توليد ملف الـ EXE المستقل وحزم كافة الملفات والأصوات...
if exist "dist" rmdir /s /q dist
if exist "build" rmdir /s /q build

pyinstaller --clean LIS_Patient_Announcer.spec

echo.
if exist "dist\LIS_Patient_Announcer_Pro.exe" (
    echo ===============================================================================
    echo [نجاح باهر]: تم بناء ملف التطبيق التنفيذي بنجاح تام!
    echo المسار المباشر للملف:
    echo      dist\LIS_Patient_Announcer_Pro.exe
    echo ===============================================================================
    echo.
    echo يمكنك الآن نسخ ملف "LIS_Patient_Announcer_Pro.exe" ونقله لأي كمبيوتر أو لابتوب
    echo وتشغيله مباشرة بدون الحاجة لبايثون أو أي برامج أخرى!
    echo.
    explorer dist
) else (
    echo [خطأ]: حدثت مشكلة أثناء التجميع، راجع الرسائل أعلاه.
)

pause
