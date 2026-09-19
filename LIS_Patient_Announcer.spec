# ==============================================================================
# PyInstaller Spec File: LIS Patient Announcer Pro
# Designer: شامل عبدالامير الطائي | 07703333687
# ==============================================================================

# -*- mode: python ; coding: utf-8 -*-
import sys
from PyInstaller.utils.hooks import collect_data_files, collect_submodules

block_cipher = None

datas = [
    ('activation_keys_1000.txt', '.'),
    ('ACTIVATION_KEYS_1000.txt', '.')
]

# Collect customtkinter assets
try:
    datas += collect_data_files('customtkinter')
except Exception:
    pass

hiddenimports = [
    'customtkinter',
    'pyttsx3',
    'pyttsx3.drivers',
    'pyttsx3.drivers.sapi5',
    'edge_tts',
    'keyboard',
    'pyperclip',
    'pystray',
    'PIL',
    'PIL.Image',
    'win32api',
    'win32con',
    'win32gui',
    'asyncio'
]

a = Analysis(
    ['patient_announcer.py'],
    pathex=[],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)
pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name='LIS_Patient_Announcer_Pro',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
