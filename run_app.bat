@echo off
chcp 65001 >nul
title LIS Patient Announcer - تشغيل فوري
cls
echo ===============================================================================
echo     تشغيل نظام نداء المرضى مباشرة على ويندوز
echo     مصمم البرنامج: شامل عبدالامير الطائي ^| 07703333687
echo ===============================================================================
echo.

if exist "dist\LIS_Patient_Announcer_Pro.exe" (
    echo جاري تشغيل النسخة التنفيذية EXE...
    start "" "dist\LIS_Patient_Announcer_Pro.exe"
    exit
)

python -m pip install -r requirements.txt >nul 2>&1
python patient_announcer.py
