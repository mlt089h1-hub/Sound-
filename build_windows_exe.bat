@echo off
chcp 65001 >nul
echo ======================================================================
echo    بناء وتوليد تطبيق ويندوز المستقل (EXE) - LIS Patient Announcer Pro
echo    مصمم البرنامج: شامل عبدالامير الطائي ^| هاتف: 07703333687
echo ======================================================================
echo.

echo [1/3] جاري التحقق وتثبيت مكتبات بايثون المطلوبة...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt pyinstaller

echo.
echo [2/3] جاري بناء ملف LIS_Patient_Announcer.exe المستقل...
pyinstaller --noconsole ^
            --onefile ^
            --name "LIS_Patient_Announcer" ^
            --add-data "activation_keys_1000.txt;." ^
            patient_announcer.py

echo.
echo [3/3] تم البناء بنجاح!
echo ستجد ملف التشغيل التنفيذي داخل مجلد dist باسم:
echo       dist\LIS_Patient_Announcer.exe
echo.
pause
