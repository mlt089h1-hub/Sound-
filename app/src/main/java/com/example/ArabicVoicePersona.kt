package com.example

enum class ArabicVoicePersona(
    val id: String,
    val titleAr: String,
    val descriptionAr: String,
    val titleEn: String,
    val engineRateModifier: Float,
    val pitch: Float,
    val ttsSpeechRate: Float,
    val iconEmoji: String
) {
    GENTLE_FEMALE(
        id = "gentle_female",
        titleAr = "سلمى - نبرة هادئة وناعمة (أنثوي)",
        descriptionAr = "نبرة صوتية طبية دافئة، ناعمة ومريحة لمرضى العيادة",
        titleEn = "Salma - Gentle & Soft (Female)",
        engineRateModifier = 0.90f,
        pitch = 1.22f,
        ttsSpeechRate = 0.92f,
        iconEmoji = "🌸"
    ),
    ENERGETIC_FEMALE(
        id = "energetic_female",
        titleAr = "مريم - نبرة نشطة ومشرقة (أنثوي حيوي)",
        descriptionAr = "نبرة صوتية واضحة، سريعة ومفعمة بالنشاط والتنبيه المريح",
        titleEn = "Maryam - Lively & Bright (Female)",
        engineRateModifier = 1.08f,
        pitch = 1.15f,
        ttsSpeechRate = 1.06f,
        iconEmoji = "⚡"
    ),
    CALM_DOCTOR_FEMALE(
        id = "calm_doctor_female",
        titleAr = "د. نور - استشارية طبية رصينة (أنثوي)",
        descriptionAr = "نبرة استشارية طبية هادئة توحي بالثقة والراحة النفسية",
        titleEn = "Dr. Noor - Calm Medical Doctor (Female)",
        engineRateModifier = 0.92f,
        pitch = 1.05f,
        ttsSpeechRate = 0.94f,
        iconEmoji = "👩‍⚕️"
    ),
    CONFIDENT_MALE(
        id = "confident_male",
        titleAr = "حامد - نبرة رسمية وقورة (ذكوري)",
        descriptionAr = "نبرة مستشفيات احترافية ورسمية واضحة ومخارج حروف متزنة",
        titleEn = "Hamed - Formal & Calm (Male)",
        engineRateModifier = 0.96f,
        pitch = 0.88f,
        ttsSpeechRate = 0.96f,
        iconEmoji = "🎙️"
    ),
    BASSIM_IRAQI_MALE(
        id = "bassim_iraqi_male",
        titleAr = "باسم - نبرة محلية وقورة (عراقي دافئ)",
        descriptionAr = "نبرة بصوت ذكوري عميق دافئ بلهجة فصحى قريبة للمرضى العراقيين",
        titleEn = "Bassim - Warm Local Announcer (Male)",
        engineRateModifier = 0.94f,
        pitch = 0.80f,
        ttsSpeechRate = 0.95f,
        iconEmoji = "🇮🇶"
    ),
    DYNAMIC_ANNOUNCER(
        id = "dynamic_announcer",
        titleAr = "عمر - نبرة المطار والنداء السريع",
        descriptionAr = "نبرة إذاعية تنبيهية بارزة للمختبرات المزدحمة لضمان لفت الانتباه",
        titleEn = "Omar - Airport & Express Announcer",
        engineRateModifier = 1.15f,
        pitch = 1.05f,
        ttsSpeechRate = 1.12f,
        iconEmoji = "📢"
    ),
    DEEP_BROADCAST_MALE(
        id = "deep_broadcast_male",
        titleAr = "طارق - مذيع إذاعي فخم (جهوري)",
        descriptionAr = "صوت رجالي عميق وفخم ذو ترددات واضحة في الصالات الكبيرة",
        titleEn = "Tariq - Deep Broadcast Voice (Male)",
        engineRateModifier = 0.92f,
        pitch = 0.72f,
        ttsSpeechRate = 0.90f,
        iconEmoji = "🏛️"
    )
}
