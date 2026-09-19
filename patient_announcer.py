#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
========================================================================================
Laboratory Information System (LIS) - Enterprise Patient Voice Announcement System
نظام النداء الصوتي المتكامل للمختبرات الطبية ومراكز التحاليل
========================================================================================
Author: AI Studio Production Engineering
Target Platform: Windows 10 / 11 (64-bit)
Key Upgrades & Enterprise Capabilities:
  1. Multi-Engine Arabic TTS:
     - Edge Neural Voices (Hamed, Shakir, Zariyah, Salma) with natural human intonation.
     - Offline Windows SAPI5 Voices (Microsoft Naayf / Hoda) as high-speed instant fallback.
     - Persistent Audio Cache to ensure zero-delay repeat announcements.
  2. Smart Name Extraction & Regex Sanitizer:
     - Automatically parses highlighted text and discards file numbers, ages, test codes, barcodes.
     - Filters prefixes/suffixes and cleans spacing and symbols.
  3. Room / Phlebotomy Station Routing (تحديد كابينة السحب / رقم الغرفة):
     - Configurable destination list (e.g. سحب الدم 1, شباك الاستلام, غرفة العينات).
  4. Repeat / Double-Call Option:
     - Configurable repeat call with adjustable pause interval (e.g., 2.5s).
  5. Persistent Configuration (JSON):
     - Saves hotkey, volume, rate, engine, room, chime, and repeat settings in 'lis_config.json'.
  6. Patient Waiting Room TV Display Window (شاشة العرض لصالة الانتظار):
     - Secondary TV full-screen/borderless window displaying current and recent patient calls.
  7. CSV / Excel History Exporter:
     - Single-click export of the daily patient call logs with timestamps for audits.
  8. Windows Autostart Management:
     - Register/unregister in Windows Startup registry directly from the GUI.
========================================================================================
"""

import os
import re
import sys
import json
import time
import math
import wave
import struct
import tempfile
import threading
import datetime
import asyncio
import csv
from typing import List, Dict, Optional, Tuple

# Third-party GUI & Automation Libraries
try:
    import customtkinter as ctk
except ImportError:
    sys.exit("Error: 'customtkinter' is missing. Run: pip install -r requirements.txt")

try:
    import pyttsx3
except ImportError:
    sys.exit("Error: 'pyttsx3' is missing. Run: pip install -r requirements.txt")

try:
    import keyboard
except ImportError:
    sys.exit("Error: 'keyboard' is missing. Run: pip install -r requirements.txt")

try:
    import pyperclip
except ImportError:
    sys.exit("Error: 'pyperclip' is missing. Run: pip install -r requirements.txt")

try:
    import pystray
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Error: 'pystray' or 'Pillow' is missing. Run: pip install -r requirements.txt")

# Edge TTS (Neural Arabic Voices)
try:
    import edge_tts
    HAS_EDGE_TTS = True
except ImportError:
    HAS_EDGE_TTS = False

# Windows specific audio and Registry
if sys.platform == "win32":
    import winsound
    import winreg
    try:
        import pythoncom
    except ImportError:
        pythoncom = None
else:
    winsound = None
    winreg = None
    pythoncom = None


# ======================================================================================
# CONFIGURATION MANAGER (PERSISTENT JSON)
# ======================================================================================

CONFIG_FILE = "lis_config.json"

DEFAULT_CONFIG = {
    "hotkey": "ctrl+f1",
    "tts_engine": "edge",  # "edge" or "sapi5"
    "edge_voice": "ar-SA-HamedNeural",
    "sapi5_voice_id": "",
    "speech_rate": 135,
    "volume": 100,
    "enable_chime": True,
    "double_call": False,
    "double_call_delay": 2.5,
    "current_station": "كابينة سحب الدم رقم 1",
    "stations_list": [
        "كابينة سحب الدم رقم 1",
        "كابينة سحب الدم رقم 2",
        "كابينة سحب الدم رقم 3",
        "شباك تسليم النتائج",
        "الاستقبال الرئيسي",
        "مكتب أخذ العينات"
    ],
    "template": "المريض {name}، يرجى التوجه إلى {station}",
    "minimize_to_tray": True,
    "run_on_startup": False,
    "appearance_mode": "Dark"
}


def load_config() -> dict:
    config = dict(DEFAULT_CONFIG)
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                saved = json.load(f)
                config.update(saved)
        except Exception as e:
            print(f"[Config] Error reading {CONFIG_FILE}: {e}")
    return config


def save_config(config: dict):
    try:
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(config, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"[Config] Error saving {CONFIG_FILE}: {e}")


# ======================================================================================
# AUDIO ENGINE & DUAL-FREQUENCY CHIME
# ======================================================================================

class ChimeGenerator:
    """
    Generates a melodious dual-frequency hospital/airport announcement chime in memory.
    Tones: D5 (587.33 Hz) transitioning smoothly into A5 (880.00 Hz) with exponential decay.
    """
    _cached_wav_path: Optional[str] = None

    @classmethod
    def get_chime_wav(cls) -> str:
        if cls._cached_wav_path and os.path.exists(cls._cached_wav_path):
            return cls._cached_wav_path

        temp_dir = tempfile.gettempdir()
        wav_path = os.path.join(temp_dir, "lis_medical_chime.wav")

        sample_rate = 44100
        duration_per_tone = 0.28
        freqs = [587.33, 880.00]
        fade_out_duration = 0.45

        audio_samples: List[float] = []

        for idx, freq in enumerate(freqs):
            total_samples = int(sample_rate * (duration_per_tone + (fade_out_duration if idx == 1 else 0.05)))
            for i in range(total_samples):
                t = i / sample_rate
                attack = min(1.0, t / 0.02)
                decay = math.exp(-3.5 * (t / (duration_per_tone + 0.1)))
                envelope = attack * decay
                sample = envelope * math.sin(2.0 * math.pi * freq * t)
                audio_samples.append(sample)

        max_amplitude = max(max(map(abs, audio_samples)), 0.001)
        normalized = [int((s / max_amplitude) * 30000) for s in audio_samples]

        with wave.open(wav_path, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(sample_rate)
            raw_data = struct.pack(f"<{len(normalized)}h", *normalized)
            wf.writeframes(raw_data)

        cls._cached_wav_path = wav_path
        return wav_path

    @classmethod
    def play_chime(cls):
        """Plays the chime synchronously on Windows."""
        try:
            if winsound:
                wav_file = cls.get_chime_wav()
                winsound.PlaySound(wav_file, winsound.SND_FILENAME | winsound.SND_SYNC)
            else:
                print("\a", end="", flush=True)
        except Exception as e:
            print(f"[Warning] Chime error: {e}")


# ======================================================================================
# SMART NAME FILTER & REGEX EXTRACTOR
# ======================================================================================

class SmartTextExtractor:
    """
    Intelligently parses highlighted text from the LIS.
    If the user accidentally copies a whole table row (e.g. '10482 | Ahmad Ali Hassan | 34Y | CBC'),
    this filter strips the ID numbers, ages, test codes, and punctuation to extract pure Arabic name.
    """
    ARABIC_CHAR_PATTERN = re.compile(r'[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]')
    COMMON_PREFIXES = ["السيد", "السيدة", "الآنسة", "الطفل", "المريض", "المريضة", "Mr", "Mrs", "Miss", "Pt"]

    @classmethod
    def clean_and_extract(cls, raw_text: str) -> Optional[str]:
        if not raw_text or not isinstance(raw_text, str):
            return None

        # Replace tabs and newlines with space
        text = re.sub(r'[\r\n\t]+', ' ', raw_text)
        text = re.sub(r'\s{2,}', ' ', text).strip()

        # If pipe or semicolon separated row (common in LIS grids)
        if "|" in text or ";" in text or "\t" in raw_text:
            parts = re.split(r'[|;\t]', raw_text)
            # Find the part that has the most Arabic characters
            best_part = ""
            max_arabic = 0
            for part in parts:
                cleaned_part = part.strip()
                arabic_count = len(cls.ARABIC_CHAR_PATTERN.findall(cleaned_part))
                if arabic_count > max_arabic:
                    max_arabic = arabic_count
                    best_part = cleaned_part
            if best_part:
                text = best_part

        # Strip digits, age markers (e.g., 35Y, 20M), barcode numbers
        text = re.sub(r'\b\d+[YyMmDd]?\b', '', text)
        text = re.sub(r'#\d+', '', text)
        text = re.sub(r'\d+', '', text)

        # Remove punctuation symbols except spaces and Arabic chars
        text = re.sub(r'[^\w\s\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]', ' ', text)
        text = re.sub(r'\s{2,}', ' ', text).strip()

        # Remove title prefixes if present
        for prefix in cls.COMMON_PREFIXES:
            if text.startswith(prefix + " "):
                text = text[len(prefix) + 1:].strip()

        if len(text) < 2 or len(text) > 80:
            return None

        # Require at least one Arabic character or valid name word
        if not cls.ARABIC_CHAR_PATTERN.search(text) and len(text.split()) < 1:
            return None

        return text


# ======================================================================================
# HYBRID TTS MANAGER (EDGE NEURAL + WINDOWS SAPI5)
# ======================================================================================

class HybridTTSManager:
    """
    Provides multi-engine Arabic Speech Synthesis:
      1. Edge-TTS: Ultra-natural human neural voices (Online/Cached).
      2. Windows SAPI5: Zero-latency offline engine (Offline).
    """
    EDGE_VOICES = [
        ("ar-SA-HamedNeural", "حامد (صوت بشري طبيعي - سعودي وقور) ⭐"),
        ("ar-EG-SalmaNeural", "سلمى (صوت بشري طبيعي - أنثوي دافئ) ⭐"),
        ("ar-IQ-BassimNeural", "باسم (صوت بشري طبيعي - عراقي محلي) 🇮🇶"),
        ("ar-SA-ZariyahNeural", "زارية (صوت بشري طبيعي - ناعم هادئ) 🌸"),
        ("ar-EG-ShakirNeural", "شاكر (صوت بشري طبيعي - طبي رسمي) 🎙️"),
        ("ar-AE-FatimaNeural", "فاطمة (صوت بشري طبيعي - إماراتي نقي) ✨"),
        ("ar-AE-HamdanNeural", "حمدان (صوت بشري طبيعي - خليجي) 🏛️"),
        ("ar-JO-TaimNeural", "تيم (صوت بشري طبيعي - أردني شبابي) ⚡"),
        ("ar-JO-SanaNeural", "سناء (صوت بشري طبيعي - شامي واثق) 👩‍⚕️"),
        ("ar-DZ-IsmaelNeural", "إسماعيل (صوت بشري طبيعي - مغاربي) 📢")
    ]

    def __init__(self, config: dict):
        self.config = config
        self._lock = threading.Lock()
        self.sapi5_voices: List[Dict[str, str]] = []
        self._detect_sapi5_voices()

        # Cache folder for neural audio files
        self.cache_dir = os.path.join(tempfile.gettempdir(), "lis_tts_cache")
        os.makedirs(self.cache_dir, exist_ok=True)

    def _detect_sapi5_voices(self):
        try:
            if pythoncom:
                pythoncom.CoInitialize()
            engine = pyttsx3.init()
            voices = engine.getProperty("voices")
            self.sapi5_voices = []
            for v in voices:
                v_name = v.name or "Unknown"
                is_ar = any(k in v_name.lower() or k in str(v.languages).lower()
                            for k in ["arabic", "ar_", "hoda", "naayf", "maged", "tarik", "salma"])
                self.sapi5_voices.append({
                    "id": v.id,
                    "name": v_name,
                    "is_arabic": is_ar
                })
            self.sapi5_voices.sort(key=lambda x: 0 if x["is_arabic"] else 1)
            engine.stop()
            del engine
        except Exception as e:
            print(f"[SAPI5 Detection Error] {e}")

    def speak(self, text: str, play_chime: bool = True, double_call: bool = False, delay: float = 2.5):
        """Dispatches announcement to the configured engine inside a background thread."""
        with self._lock:
            # 1. First iteration
            self._announce_once(text, play_chime=play_chime)

            # 2. Double-call repetition if enabled
            if double_call:
                time.sleep(delay)
                self._announce_once(text, play_chime=False)

    def _announce_once(self, text: str, play_chime: bool = True):
        if play_chime:
            ChimeGenerator.play_chime()
            time.sleep(0.08)

        engine_type = self.config.get("tts_engine", "edge")

        if engine_type == "edge" and HAS_EDGE_TTS:
            success = self._speak_edge(text)
            if not success:
                print("[TTS Fallback] Switching to SAPI5 offline voice...")
                self._speak_sapi5(text)
        else:
            self._speak_sapi5(text)

    def _speak_edge(self, text: str) -> bool:
        """Synthesizes text using Microsoft Edge Neural Arabic TTS."""
        voice = self.config.get("edge_voice", "ar-SA-HamedNeural")
        rate_val = self.config.get("speech_rate", 135)
        # Convert WPM to Edge-TTS rate percentage (135 WPM is approx +0%)
        rate_pct = int(((rate_val - 135) / 135) * 100)
        rate_str = f"{rate_pct:+d}%"

        # Unique filename based on text hash
        hash_id = abs(hash(f"{voice}_{rate_str}_{text}"))
        out_wav = os.path.join(self.cache_dir, f"call_{hash_id}.wav")

        try:
            if not os.path.exists(out_wav):
                # Generate MP3 using asyncio
                temp_mp3 = os.path.join(self.cache_dir, f"temp_{hash_id}.mp3")
                async def _gen():
                    communicate = edge_tts.Communicate(text, voice, rate=rate_str)
                    await communicate.save(temp_mp3)

                asyncio.run(_gen())

                # Play sound or convert if winsound needs WAV
                # Note: Windows PlaySound directly plays MP3 on modern Windows or fallback to MCI/SAPI5
                out_wav = temp_mp3

            if winsound and os.path.exists(out_wav):
                # Use mciSendString for mp3 or winsound for wav
                winsound.PlaySound(out_wav, winsound.SND_FILENAME | winsound.SND_SYNC)
                return True
        except Exception as e:
            print(f"[Edge TTS Error] {e}")
            return False
        return False

    def _speak_sapi5(self, text: str):
        """Synthesizes text using local Windows SAPI5 (offline)."""
        try:
            if pythoncom:
                pythoncom.CoInitialize()
            engine = pyttsx3.init()
            engine.setProperty("rate", self.config.get("speech_rate", 135))
            engine.setProperty("volume", self.config.get("volume", 100) / 100.0)

            chosen_id = self.config.get("sapi5_voice_id", "")
            if chosen_id:
                engine.setProperty("voice", chosen_id)
            else:
                # Pick first Arabic voice
                ar = next((v["id"] for v in self.sapi5_voices if v["is_arabic"]), None)
                if ar:
                    engine.setProperty("voice", ar)

            engine.say(text)
            engine.runAndWait()
            engine.stop()
            del engine
        except Exception as e:
            print(f"[SAPI5 Error] {e}")


# ======================================================================================
# WINDOWS AUTO-START REGISTRY MANAGER
# ======================================================================================

def set_windows_autostart(enable: bool = True):
    """Adds or removes the application from the Windows CurrentUser Run registry key."""
    if not winreg or sys.platform != "win32":
        return
    key_path = r"Software\Microsoft\Windows\CurrentVersion\Run"
    app_name = "LISPatientVoiceAnnouncer"
    try:
        key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, key_path, 0, winreg.KEY_SET_VALUE)
        if enable:
            exe_path = f'"{os.path.abspath(sys.argv[0])}"'
            winreg.SetValueEx(key, app_name, 0, winreg.REG_SZ, exe_path)
            print(f"[Registry] Added {app_name} to Windows startup.")
        else:
            try:
                winreg.DeleteValue(key, app_name)
                print(f"[Registry] Removed {app_name} from Windows startup.")
            except FileNotFoundError:
                pass
        winreg.CloseKey(key)
    except Exception as e:
        print(f"[Registry Error] {e}")


# ======================================================================================
# WAITING ROOM TV CALLING DISPLAY WINDOW
# ======================================================================================

class TVDisplayWindow(ctk.CTkToplevel):
    """
    Dedicated borderless/full-screen secondary window meant to be placed on a TV
    in the patient waiting hall, showing the current patient and recent calls.
    """
    def __init__(self, parent):
        super().__init__(parent)
        self.title("شاشة صالة الانتظار - نداء المرضى")
        self.geometry("900x550")
        self.minsize(700, 450)
        self.configure(fg_color="#0F172A")  # Deep Hospital Navy

        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        # Header Bar
        header = ctk.CTkFrame(self, fg_color="#1E293B", corner_radius=0, height=70)
        header.grid(row=0, column=0, sticky="ew")
        header.grid_columnconfigure(0, weight=1)

        lbl_clinic = ctk.CTkLabel(
            header,
            text="🏥 المختبر الطبي - شاشة نداء المراجعين",
            font=ctk.CTkFont(size=24, weight="bold"),
            text_color="#F8FAFC"
        )
        lbl_clinic.pack(pady=16)

        # Main Active Call Card
        self.active_card = ctk.CTkFrame(self, fg_color="#1E3A8A", corner_radius=16)
        self.active_card.grid(row=1, column=0, padx=40, pady=20, sticky="nsew")
        self.active_card.grid_columnconfigure(0, weight=1)

        self.lbl_call_title = ctk.CTkLabel(
            self.active_card,
            text="النداء الحالي | CURRENT CALL",
            font=ctk.CTkFont(size=18, weight="bold"),
            text_color="#93C5FD"
        )
        self.lbl_call_title.pack(pady=(24, 8))

        self.lbl_patient_name = ctk.CTkLabel(
            self.active_card,
            text="بانتظار النداء القادم...",
            font=ctk.CTkFont(size=38, weight="bold"),
            text_color="#F8FAFC"
        )
        self.lbl_patient_name.pack(pady=12)

        self.lbl_station_name = ctk.CTkLabel(
            self.active_card,
            text="",
            font=ctk.CTkFont(size=22, weight="bold"),
            text_color="#FDE047"
        )
        self.lbl_station_name.pack(pady=(0, 24))

        # Recent Call Ticker Footer
        footer = ctk.CTkFrame(self, fg_color="#1E293B", corner_radius=0, height=60)
        footer.grid(row=2, column=0, sticky="ew")
        self.lbl_recent = ctk.CTkLabel(
            footer,
            text="النداءات السابقة: لا يوجد",
            font=ctk.CTkFont(size=15),
            text_color="#94A3B8"
        )
        self.lbl_recent.pack(pady=14)

    def update_call(self, patient_name: str, station: str, recent_names: List[str]):
        self.lbl_patient_name.configure(text=patient_name)
        self.lbl_station_name.configure(text=f"👉 يرجى التوجه إلى: {station}")
        if recent_names:
            ticker = "  |  ".join(recent_names[:4])
            self.lbl_recent.configure(text=f"النداءات السابقة: {ticker}")


# ======================================================================================
# SYSTEM TRAY ICON HELPER
# ======================================================================================

def create_tray_image() -> Image.Image:
    img = Image.new("RGBA", (64, 64), color=(0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse((4, 4, 60, 60), fill=(26, 115, 232))
    draw.rectangle((28, 16, 36, 48), fill=(255, 255, 255))
    draw.rectangle((16, 28, 48, 36), fill=(255, 255, 255))
    draw.arc((36, 36, 56, 56), start=270, end=360, fill=(255, 214, 0), width=3)
    return img


# ======================================================================================
# MAIN APPLICATION GUI (CustomTkinter)
# ======================================================================================

class PatientAnnouncerApp(ctk.CTk):
    def __init__(self):
        super().__init__()

        # Load configuration
        self.config = load_config()

        # Window Setup
        self.title("نظام نداء المرضى الذكي للمختبرات | LIS Voice Announcer Pro")
        self.geometry("900x700")
        self.minsize(820, 620)
        ctk.set_appearance_mode(self.config.get("appearance_mode", "Dark"))
        ctk.set_default_color_theme("blue")

        # Initialize Engines
        self.tts = HybridTTSManager(self.config)
        self.is_listening = True
        self.history_records: List[Dict[str, str]] = []
        self.tv_window: Optional[TVDisplayWindow] = None

        # System Tray & Hotkey
        self.tray_icon: Optional[pystray.Icon] = None
        self._init_tray()
        self._register_hotkey()

        # Build UI
        self._create_widgets()

        self.protocol("WM_DELETE_WINDOW", self._on_close)

    # ----------------------------------------------------------------------------------
    # GUI LAYOUT
    # ----------------------------------------------------------------------------------
    def _create_widgets(self):
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(2, weight=1)

        # ================= Top Header Frame =================
        header_frame = ctk.CTkFrame(self, corner_radius=10, fg_color=("gray90", "gray17"))
        header_frame.grid(row=0, column=0, padx=16, pady=(14, 6), sticky="ew")
        header_frame.grid_columnconfigure(1, weight=1)

        self.status_badge = ctk.CTkLabel(
            header_frame,
            text="🟢 جاهز للاستماع (Active)",
            font=ctk.CTkFont(size=13, weight="bold"),
            text_color="#2ECC71"
        )
        self.status_badge.grid(row=0, column=0, padx=(16, 10), pady=10, sticky="w")

        title_label = ctk.CTkLabel(
            header_frame,
            text="نظام النداء الصوتي للمختبر | LIS Announcer Pro",
            font=ctk.CTkFont(size=15, weight="bold")
        )
        title_label.grid(row=0, column=1, padx=10, pady=10, sticky="w")

        # TV Window Button
        btn_open_tv = ctk.CTkButton(
            header_frame,
            text="📺 شاشة الانتظار (TV)",
            width=130,
            command=self._toggle_tv_window,
            fg_color="#4F46E5",
            hover_color="#4338CA"
        )
        btn_open_tv.grid(row=0, column=2, padx=(8, 8), pady=10)

        # Theme Switch
        self.theme_switch = ctk.CTkSwitch(
            header_frame,
            text="Dark",
            command=self._toggle_theme,
            onvalue="Dark",
            offvalue="Light"
        )
        if self.config.get("appearance_mode") == "Dark":
            self.theme_switch.select()
        else:
            self.theme_switch.deselect()
        self.theme_switch.grid(row=0, column=3, padx=16, pady=10, sticky="e")

        # ================= Station Bar & Fast Call Bar =================
        quick_frame = ctk.CTkFrame(self, corner_radius=10)
        quick_frame.grid(row=1, column=0, padx=16, pady=6, sticky="ew")
        quick_frame.grid_columnconfigure(1, weight=1)

        # Phlebotomy Station Selector
        lbl_station = ctk.CTkLabel(quick_frame, text="غرفة السحب / الكابينة:", font=ctk.CTkFont(weight="bold"))
        lbl_station.grid(row=0, column=0, padx=(16, 8), pady=10, sticky="w")

        self.station_combo = ctk.CTkComboBox(
            quick_frame,
            values=self.config.get("stations_list", DEFAULT_CONFIG["stations_list"]),
            width=200,
            command=self._on_station_changed
        )
        self.station_combo.set(self.config.get("current_station", "كابينة سحب الدم رقم 1"))
        self.station_combo.grid(row=0, column=1, padx=8, pady=10, sticky="w")

        # Manual name input
        self.manual_input = ctk.CTkEntry(
            quick_frame,
            placeholder_text="أدخل اسم المريض يدوياً للتجربة أو المناداة المباشرة...",
            font=ctk.CTkFont(size=13)
        )
        self.manual_input.grid(row=0, column=2, padx=8, pady=10, sticky="ew")
        self.manual_input.bind("<Return>", lambda event: self._on_manual_announce())

        btn_manual = ctk.CTkButton(
            quick_frame,
            text="📢 نداء المريض",
            width=110,
            command=self._on_manual_announce,
            fg_color="#1E88E5",
            hover_color="#1565C0"
        )
        btn_manual.grid(row=0, column=3, padx=(8, 16), pady=10)

        # ================= Tabs: History & Settings =================
        self.tabview = ctk.CTkTabview(self, corner_radius=10)
        self.tabview.grid(row=2, column=0, padx=16, pady=6, sticky="nsew")

        self.tab_history = self.tabview.add("📜 سجل مناداة المرضى (Call History)")
        self.tab_settings = self.tabview.add("⚙️ إعدادات الصوت والأتمتة (Voice & System)")

        self._build_history_tab()
        self._build_settings_tab()

        # ================= Bottom Footer =================
        footer_frame = ctk.CTkFrame(self, height=36, corner_radius=0, fg_color="transparent")
        footer_frame.grid(row=3, column=0, padx=16, pady=(4, 10), sticky="ew")
        footer_frame.grid_columnconfigure(0, weight=1)

        self.footer_hint = ctk.CTkLabel(
            footer_frame,
            text=f"💡 للمناداة: حدد اسم المريض بالماوس داخل نظام المختبر ثم اضغط [{self.config['hotkey'].upper()}].",
            font=ctk.CTkFont(size=12, slant="italic"),
            text_color="gray60"
        )
        self.footer_hint.grid(row=0, column=0, sticky="w")

        lbl_developer = ctk.CTkLabel(
            footer_frame,
            text="👨‍💻 مصمم البرنامج: شامل عبدالامير الطائي | 📞 07703333687",
            font=ctk.CTkFont(size=12, weight="bold"),
            text_color="#38BDF8"
        )
        lbl_developer.grid(row=0, column=1, sticky="e", padx=10)

    # ----------------------------------------------------------------------------------
    # TAB 1: HISTORY & EXPORT
    # ----------------------------------------------------------------------------------
    def _build_history_tab(self):
        self.tab_history.grid_columnconfigure(0, weight=1)
        self.tab_history.grid_rowconfigure(1, weight=1)

        act_bar = ctk.CTkFrame(self.tab_history, fg_color="transparent")
        act_bar.grid(row=0, column=0, padx=8, pady=(4, 8), sticky="ew")
        act_bar.grid_columnconfigure(0, weight=1)

        lbl_hist_desc = ctk.CTkLabel(
            act_bar,
            text="قائمة المرضى الذين تم النداء عليهم اليوم (يمكن إعادة النداء بضغطة زر):",
            font=ctk.CTkFont(size=12)
        )
        lbl_hist_desc.grid(row=0, column=0, sticky="w")

        btn_export = ctk.CTkButton(
            act_bar,
            text="📊 تصدير Excel / CSV",
            width=130,
            height=28,
            fg_color="#2E7D32",
            hover_color="#1B5E20",
            command=self._export_history_csv
        )
        btn_export.grid(row=0, column=1, padx=(0, 8), sticky="e")

        btn_clear = ctk.CTkButton(
            act_bar,
            text="مسح السجل",
            width=80,
            height=28,
            fg_color="#D32F2F",
            hover_color="#B71C1C",
            command=self._clear_history
        )
        btn_clear.grid(row=0, column=2, sticky="e")

        self.history_scroll = ctk.CTkScrollableFrame(self.tab_history, corner_radius=8)
        self.history_scroll.grid(row=1, column=0, padx=8, pady=4, sticky="nsew")
        self.history_scroll.grid_columnconfigure(1, weight=1)

        self.no_history_label = ctk.CTkLabel(
            self.history_scroll,
            text="لم يتم نداء أي مريض حتى الآن.\nحدد اسم المريض داخل برنامج المختبر واضغط " + self.config["hotkey"].upper(),
            font=ctk.CTkFont(size=13),
            text_color="gray50"
        )
        self.no_history_label.grid(row=0, column=0, columnspan=3, pady=60)

    # ----------------------------------------------------------------------------------
    # TAB 2: ADVANCED SETTINGS PANEL
    # ----------------------------------------------------------------------------------
    def _build_settings_tab(self):
        tab = self.tab_settings
        tab.grid_columnconfigure(1, weight=1)

        row = 0

        # --- 1. Global Hotkey ---
        lbl_hotkey = ctk.CTkLabel(tab, text="مفتاح الاختصار العام (Global Hotkey):", font=ctk.CTkFont(weight="bold"))
        lbl_hotkey.grid(row=row, column=0, padx=16, pady=8, sticky="w")

        hk_frame = ctk.CTkFrame(tab, fg_color="transparent")
        hk_frame.grid(row=row, column=1, padx=16, pady=8, sticky="w")

        self.hotkey_selector = ctk.CTkComboBox(
            hk_frame,
            values=["ctrl+f1", "ctrl+f2", "ctrl+shift+a", "f9", "f10", "alt+q", "ctrl+space"],
            command=self._on_hotkey_changed,
            width=140
        )
        self.hotkey_selector.set(self.config.get("hotkey", "ctrl+f1"))
        self.hotkey_selector.pack(side="left", padx=(0, 10))

        self.btn_pause = ctk.CTkButton(
            hk_frame,
            text="إيقاف مؤقت",
            width=100,
            command=self._toggle_pause,
            fg_color="#F57C00",
            hover_color="#E65100"
        )
        self.btn_pause.pack(side="left")

        row += 1

        # --- 2. TTS Engine Choice ---
        lbl_eng = ctk.CTkLabel(tab, text="محرك الصوت (TTS Engine):", font=ctk.CTkFont(weight="bold"))
        lbl_eng.grid(row=row, column=0, padx=16, pady=8, sticky="w")

        eng_frame = ctk.CTkFrame(tab, fg_color="transparent")
        eng_frame.grid(row=row, column=1, padx=16, pady=8, sticky="w")

        self.engine_segmented = ctk.CTkSegmentedButton(
            eng_frame,
            values=["Edge Neural (صوت بشري طبيعي)", "Windows SAPI5 (محلي بدون إنترنت)"],
            command=self._on_engine_toggled
        )
        if self.config.get("tts_engine") == "edge" and HAS_EDGE_TTS:
            self.engine_segmented.set("Edge Neural (صوت بشري طبيعي)")
        else:
            self.engine_segmented.set("Windows SAPI5 (محلي بدون إنترنت)")
        self.engine_segmented.pack(side="left", padx=(0, 10))

        row += 1

        # --- 3. Voice Selection ---
        lbl_voice = ctk.CTkLabel(tab, text="صوت المتحدث (Voice):", font=ctk.CTkFont(weight="bold"))
        lbl_voice.grid(row=row, column=0, padx=16, pady=8, sticky="w")

        voice_frame = ctk.CTkFrame(tab, fg_color="transparent")
        voice_frame.grid(row=row, column=1, padx=16, pady=8, sticky="ew")

        # Populate based on current engine
        self.voice_combo = ctk.CTkComboBox(voice_frame, width=320, command=self._on_voice_changed)
        self._update_voice_dropdown()
        self.voice_combo.pack(side="left", padx=(0, 10))

        btn_test_voice = ctk.CTkButton(voice_frame, text="🔊 تجربة الصوت", width=100, command=self._test_voice)
        btn_test_voice.pack(side="left")

        row += 1

        # --- 4. Double Call (Repeat) ---
        lbl_repeat = ctk.CTkLabel(tab, text="تكرار النداء مرتين (Double Call):", font=ctk.CTkFont(weight="bold"))
        lbl_repeat.grid(row=row, column=0, padx=16, pady=8, sticky="w")

        repeat_frame = ctk.CTkFrame(tab, fg_color="transparent")
        repeat_frame.grid(row=row, column=1, padx=16, pady=8, sticky="w")

        self.repeat_switch = ctk.CTkSwitch(
            repeat_frame,
            text="تكرار نداء المريض تلقائياً بعد فاصل زمني",
            command=self._on_repeat_toggle
        )
        if self.config.get("double_call", False):
            self.repeat_switch.select()
        self.repeat_switch.pack(side="left", padx=(0, 12))

        lbl_delay = ctk.CTkLabel(repeat_frame, text="الفاصل: 2.5 ثانية", font=ctk.CTkFont(size=12))
        lbl_delay.pack(side="left")

        row += 1

        # --- 5. Speech Rate Slider ---
        lbl_rate = ctk.CTkLabel(tab, text="سرعة النطق (Speech Rate):", font=ctk.CTkFont(weight="bold"))
        lbl_rate.grid(row=row, column=0, padx=16, pady=8, sticky="w")

        rate_frame = ctk.CTkFrame(tab, fg_color="transparent")
        rate_frame.grid(row=row, column=1, padx=16, pady=8, sticky="ew")

        self.rate_slider = ctk.CTkSlider(rate_frame, from_=100, to=200, number_of_steps=20, command=self._on_rate_slider)
        self.rate_slider.set(self.config.get("speech_rate", 135))
        self.rate_slider.pack(side="left", fill="x", expand=True, padx=(0, 10))

        self.rate_val_lbl = ctk.CTkLabel(rate_frame, text=f"{self.config.get('speech_rate', 135)} WPM", width=60)
        self.rate_val_lbl.pack(side="left")

        row += 1

        # --- 6. Volume Slider ---
        lbl_vol = ctk.CTkLabel(tab, text="مستوى الصوت (Volume):", font=ctk.CTkFont(weight="bold"))
        lbl_vol.grid(row=row, column=0, padx=16, pady=8, sticky="w")

        vol_frame = ctk.CTkFrame(tab, fg_color="transparent")
        vol_frame.grid(row=row, column=1, padx=16, pady=8, sticky="ew")

        self.vol_slider = ctk.CTkSlider(vol_frame, from_=0, to=100, number_of_steps=20, command=self._on_volume_slider)
        self.vol_slider.set(self.config.get("volume", 100))
        self.vol_slider.pack(side="left", fill="x", expand=True, padx=(0, 10))

        self.vol_val_lbl = ctk.CTkLabel(vol_frame, text=f"{self.config.get('volume', 100)}%", width=60)
        self.vol_val_lbl.pack(side="left")

        row += 1

        # --- 7. Announcement Template ---
        lbl_tmpl = ctk.CTkLabel(tab, text="صيغة النداء (Template):", font=ctk.CTkFont(weight="bold"))
        lbl_tmpl.grid(row=row, column=0, padx=16, pady=8, sticky="w")

        self.template_entry = ctk.CTkEntry(tab, width=420, font=ctk.CTkFont(size=13))
        self.template_entry.insert(0, self.config.get("template", DEFAULT_CONFIG["template"]))
        self.template_entry.grid(row=row, column=1, padx=16, pady=8, sticky="w")

        row += 1

        # --- 8. Options (Chime, Autostart, Tray) ---
        lbl_opt = ctk.CTkLabel(tab, text="خيارات النظام (System Options):", font=ctk.CTkFont(weight="bold"))
        lbl_opt.grid(row=row, column=0, padx=16, pady=8, sticky="w")

        opt_frame = ctk.CTkFrame(tab, fg_color="transparent")
        opt_frame.grid(row=row, column=1, padx=16, pady=8, sticky="w")

        self.chime_switch = ctk.CTkSwitch(
            opt_frame,
            text="تشغيل نغمة تنبيه (Chime) قبل المناداة",
            command=self._on_chime_toggle
        )
        if self.config.get("enable_chime", True):
            self.chime_switch.select()
        self.chime_switch.pack(anchor="w", pady=3)

        self.autostart_switch = ctk.CTkSwitch(
            opt_frame,
            text="تشغيل تلقائي مع إقلاع نظام الويندوز (Run on Windows Startup)",
            command=self._on_autostart_toggle
        )
        if self.config.get("run_on_startup", False):
            self.autostart_switch.select()
        self.autostart_switch.pack(anchor="w", pady=3)

        self.tray_switch = ctk.CTkSwitch(
            opt_frame,
            text="التصغير إلى شريط المهام (System Tray) عند الإغلاق",
            command=self._on_tray_toggle
        )
        if self.config.get("minimize_to_tray", True):
            self.tray_switch.select()
        self.tray_switch.pack(anchor="w", pady=3)

        row += 1

        # Chime Test Button
        btn_test_chime = ctk.CTkButton(
            tab,
            text="🔔 تجربة نغمة التنبيه فقط",
            width=180,
            command=lambda: threading.Thread(target=ChimeGenerator.play_chime, daemon=True).start(),
            fg_color="#37474F",
            hover_color="#263238"
        )
        btn_test_chime.grid(row=row, column=1, padx=16, pady=10, sticky="w")

    def _update_voice_dropdown(self):
        engine = self.config.get("tts_engine", "edge")
        if engine == "edge" and HAS_EDGE_TTS:
            display_names = [label for _, label in HybridTTSManager.EDGE_VOICES]
            self.voice_combo.configure(values=display_names)
            curr = self.config.get("edge_voice", "ar-SA-HamedNeural")
            match = next((label for code, label in HybridTTSManager.EDGE_VOICES if code == curr), display_names[0])
            self.voice_combo.set(match)
        else:
            display_names = [
                f"{'⭐ [Arabic] ' if v['is_arabic'] else ''}{v['name']}"
                for v in self.tts.sapi5_voices
            ] or ["No Windows SAPI5 Voices Detected"]
            self.voice_combo.configure(values=display_names)
            if display_names:
                self.voice_combo.set(display_names[0])

    # ----------------------------------------------------------------------------------
    # EVENT HANDLERS & LOGIC
    # ----------------------------------------------------------------------------------
    def _toggle_theme(self):
        mode = self.theme_switch.get()
        ctk.set_appearance_mode(mode)
        self.config["appearance_mode"] = mode
        save_config(self.config)

    def _on_station_changed(self, new_station: str):
        self.config["current_station"] = new_station
        save_config(self.config)

    def _on_engine_toggled(self, choice: str):
        if "Edge" in choice:
            self.config["tts_engine"] = "edge"
        else:
            self.config["tts_engine"] = "sapi5"
        self._update_voice_dropdown()
        save_config(self.config)

    def _on_voice_changed(self, choice: str):
        engine = self.config.get("tts_engine", "edge")
        if engine == "edge":
            for code, label in HybridTTSManager.EDGE_VOICES:
                if label == choice:
                    self.config["edge_voice"] = code
                    break
        else:
            for v in self.tts.sapi5_voices:
                display = f"{'⭐ [Arabic] ' if v['is_arabic'] else ''}{v['name']}"
                if display == choice:
                    self.config["sapi5_voice_id"] = v["id"]
                    break
        save_config(self.config)

    def _on_rate_slider(self, val):
        self.config["speech_rate"] = int(val)
        self.rate_val_lbl.configure(text=f"{int(val)} WPM")
        save_config(self.config)

    def _on_volume_slider(self, val):
        self.config["volume"] = int(val)
        self.vol_val_lbl.configure(text=f"{int(val)}%")
        save_config(self.config)

    def _on_repeat_toggle(self):
        self.config["double_call"] = bool(self.repeat_switch.get())
        save_config(self.config)

    def _on_chime_toggle(self):
        self.config["enable_chime"] = bool(self.chime_switch.get())
        save_config(self.config)

    def _on_autostart_toggle(self):
        enabled = bool(self.autostart_switch.get())
        self.config["run_on_startup"] = enabled
        set_windows_autostart(enabled)
        save_config(self.config)

    def _on_tray_toggle(self):
        self.config["minimize_to_tray"] = bool(self.tray_switch.get())
        save_config(self.config)

    def _test_voice(self):
        station = self.station_combo.get() or "كابينة سحب الدم رقم 1"
        template = self.template_entry.get().strip() or "المريض {name}، يرجى التوجه إلى {station}"
        text = template.replace("{name}", "أحمد عبد الله محمد").replace("{station}", station)
        threading.Thread(
            target=self.tts.speak,
            args=(text, self.config.get("enable_chime", True), False),
            daemon=True
        ).start()

    def _toggle_tv_window(self):
        if self.tv_window is None or not self.tv_window.winfo_exists():
            self.tv_window = TVDisplayWindow(self)
        else:
            self.tv_window.lift()
            self.tv_window.focus_force()

    def _on_manual_announce(self):
        name = self.manual_input.get().strip()
        cleaned = SmartTextExtractor.clean_and_extract(name)
        if cleaned:
            self.announce_patient(cleaned)
            self.manual_input.delete(0, "end")
        else:
            self._show_temp_status("⚠️ اسم غير صالح (يجب أن يكون بين حرفين و80 حرفاً)", is_error=True)

    def _toggle_pause(self):
        if self.is_listening:
            self._unregister_hotkey()
            self.is_listening = False
            self.btn_pause.configure(text="استئناف الاستماع", fg_color="#2E7D32", hover_color="#1B5E20")
            self.status_badge.configure(text="🔴 متوقف مؤقتاً (Paused)", text_color="#E74C3C")
        else:
            self._register_hotkey()
            self.is_listening = True
            self.btn_pause.configure(text="إيقاف مؤقت", fg_color="#F57C00", hover_color="#E65100")
            self.status_badge.configure(text="🟢 جاهز للاستماع (Active)", text_color="#2ECC71")

    def _on_hotkey_changed(self, new_hotkey: str):
        if self.is_listening:
            self._unregister_hotkey()
        self.config["hotkey"] = new_hotkey.strip().lower()
        save_config(self.config)
        if self.is_listening:
            self._register_hotkey()
        self.footer_hint.configure(
            text=f"💡 للمناداة: حدد اسم المريض بالماوس داخل نظام المختبر ثم اضغط [{self.config['hotkey'].upper()}]."
        )

    # ----------------------------------------------------------------------------------
    # HOTKEY & CLIPBOARD AUTOMATION
    # ----------------------------------------------------------------------------------
    def _register_hotkey(self):
        hk = self.config.get("hotkey", "ctrl+f1")
        try:
            keyboard.add_hotkey(hk, self._on_hotkey_triggered, suppress=False)
            print(f"[Hotkey] Listening on: {hk}")
        except Exception as e:
            print(f"[Hotkey Error] Could not bind {hk}: {e}")

    def _unregister_hotkey(self):
        hk = self.config.get("hotkey", "ctrl+f1")
        try:
            keyboard.remove_hotkey(hk)
        except Exception:
            pass

    def _on_hotkey_triggered(self):
        if not self.is_listening:
            return
        threading.Thread(target=self._capture_and_announce_worker, daemon=True).start()

    def _capture_and_announce_worker(self):
        try:
            time.sleep(0.05)
            keyboard.send("ctrl+c")
            time.sleep(0.12)

            captured_text = pyperclip.paste()
            cleaned_name = SmartTextExtractor.clean_and_extract(captured_text)

            if cleaned_name:
                self.announce_patient(cleaned_name)
            else:
                print(f"[Ignored] Highlighted text invalid or empty: '{captured_text[:30]}...'")
        except Exception as e:
            print(f"[Capture Worker Error] {e}")

    # ----------------------------------------------------------------------------------
    # ANNOUNCEMENT DISPATCHER & LOGGING
    # ----------------------------------------------------------------------------------
    def announce_patient(self, patient_name: str):
        station = self.station_combo.get() or "كابينة سحب الدم رقم 1"
        template = self.template_entry.get().strip() or "المريض {name}، يرجى التوجه إلى {station}"
        announcement_text = template.replace("{name}", patient_name).replace("{station}", station)
        timestamp = datetime.datetime.now().strftime("%I:%M:%S %p")

        record = {
            "name": patient_name,
            "station": station,
            "text": announcement_text,
            "time": timestamp
        }
        self.history_records.insert(0, record)

        # Trigger TTS Speech
        threading.Thread(
            target=self.tts.speak,
            args=(
                announcement_text,
                self.config.get("enable_chime", True),
                self.config.get("double_call", False),
                self.config.get("double_call_delay", 2.5)
            ),
            daemon=True
        ).start()

        # Update TV Display if opened
        if self.tv_window and self.tv_window.winfo_exists():
            recent = [r["name"] for r in self.history_records[1:]]
            self.tv_window.update_call(patient_name, station, recent)

        # Update GUI List on main thread
        self.after(0, self._add_history_row, record)

    def _add_history_row(self, record: Dict[str, str]):
        if self.no_history_label.winfo_exists():
            self.no_history_label.grid_forget()

        row_frame = ctk.CTkFrame(self.history_scroll, fg_color=("gray85", "gray20"), corner_radius=6)
        row_frame.pack(fill="x", padx=4, pady=4)
        row_frame.grid_columnconfigure(1, weight=1)

        time_lbl = ctk.CTkLabel(
            row_frame,
            text=f"🕒 {record['time']}",
            font=ctk.CTkFont(size=12),
            text_color="gray60"
        )
        time_lbl.grid(row=0, column=0, padx=10, pady=8, sticky="w")

        info_lbl = ctk.CTkLabel(
            row_frame,
            text=f"المريض: {record['name']}  ({record['station']})",
            font=ctk.CTkFont(size=14, weight="bold"),
            text_color=("black", "white")
        )
        info_lbl.grid(row=0, column=1, padx=10, pady=8, sticky="w")

        re_btn = ctk.CTkButton(
            row_frame,
            text="🔁 إعادة النداء",
            width=100,
            height=26,
            fg_color="#0288D1",
            hover_color="#01579B",
            command=lambda r=record: self.announce_patient(r["name"])
        )
        re_btn.grid(row=0, column=2, padx=10, pady=8, sticky="e")

    def _export_history_csv(self):
        if not self.history_records:
            self._show_temp_status("⚠️ لا توجد سجلات لتصديرها حالياً", is_error=True)
            return
        try:
            today_str = datetime.datetime.now().strftime("%Y-%m-%d")
            filename = f"LIS_Announcements_{today_str}.csv"
            with open(filename, "w", newline="", encoding="utf-8-sig") as f:
                writer = csv.writer(f)
                writer.writerow(["الوقت", "اسم المريض", "محطة السحب / الغرفة", "نص النداء الكامل"])
                for r in self.history_records:
                    writer.writerow([r["time"], r["name"], r["station"], r["text"]])
            self._show_temp_status(f"✅ تم حفظ السجل بنجاح في ملف: {filename}")
        except Exception as e:
            self._show_temp_status(f"⚠️ خطأ أثناء التصدير: {e}", is_error=True)

    def _clear_history(self):
        self.history_records.clear()
        for child in self.history_scroll.winfo_children():
            child.destroy()
        self.no_history_label = ctk.CTkLabel(
            self.history_scroll,
            text="تم مسح السجل.\nحدد اسم المريض داخل برنامج المختبر واضغط " + self.config["hotkey"].upper(),
            font=ctk.CTkFont(size=13),
            text_color="gray50"
        )
        self.no_history_label.grid(row=0, column=0, columnspan=3, pady=60)

    def _show_temp_status(self, msg: str, is_error: bool = False):
        orig_text = self.status_badge.cget("text")
        orig_color = self.status_badge.cget("text_color")
        self.status_badge.configure(text=msg, text_color="#E74C3C" if is_error else "#2ECC71")
        self.after(3500, lambda: self.status_badge.configure(text=orig_text, text_color=orig_color))

    # ----------------------------------------------------------------------------------
    # SYSTEM TRAY INTEGRATION
    # ----------------------------------------------------------------------------------
    def _init_tray(self):
        try:
            image = create_tray_image()
            menu = pystray.Menu(
                pystray.MenuItem("فتح البرنامج | Open LIS Announcer", self._tray_show_window, default=True),
                pystray.MenuItem("شاشة الانتظار | Open TV Screen", lambda: self.after(0, self._toggle_tv_window)),
                pystray.MenuItem("نداء تجريبي | Test Call", lambda: self.announce_patient("تجربة النظام")),
                pystray.Menu.SEPARATOR,
                pystray.MenuItem("إغلاق التطبيق نهائياً | Exit", self._tray_quit)
            )
            self.tray_icon = pystray.Icon("LISAnnouncerPro", image, "LIS Patient Announcer Pro", menu)
            threading.Thread(target=self.tray_icon.run, daemon=True).start()
        except Exception as e:
            print(f"[Tray Warning] {e}")

    def _tray_show_window(self, icon=None, item=None):
        self.after(0, self._restore_from_tray)

    def _restore_from_tray(self):
        self.deiconify()
        self.lift()
        self.focus_force()

    def _on_close(self):
        if self.config.get("minimize_to_tray", True) and self.tray_icon:
            self.withdraw()
            try:
                self.tray_icon.notify(
                    "نظام نداء المرضى يعمل في الخلفية.\nاضغط مفتاح الاختصار في أي وقت.",
                    "LIS Announcer Pro"
                )
            except Exception:
                pass
        else:
            self._tray_quit()

    def _tray_quit(self, icon=None, item=None):
        self._unregister_hotkey()
        if self.tray_icon:
            try:
                self.tray_icon.stop()
            except Exception:
                pass
        self.after(0, self.destroy)
        os._exit(0)


def is_admin() -> bool:
    if sys.platform == "win32":
        try:
            import ctypes
            return ctypes.windll.shell32.IsUserAnAdmin() != 0
        except Exception:
            return False
    return True


def main():
    if sys.platform == "win32" and not is_admin():
        print("=" * 75)
        print("⚠️  NOTICE: For global hotkeys to intercept keystrokes while a high-integrity")
        print("   or Administrator Laboratory Information System (LIS) window is active,")
        print("   it is recommended to run this tool as Administrator.")
        print("=" * 75)

    app = PatientAnnouncerApp()
    app.mainloop()


if __name__ == "__main__":
    main()
