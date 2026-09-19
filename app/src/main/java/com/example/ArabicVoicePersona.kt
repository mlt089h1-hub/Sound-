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
        titleAr = "نبرة هادئة وناعمة (أنثوي)",
        descriptionAr = "نبرة صوتية طبية دافئة، ناعمة ومريحة لمرضى العيادة",
        titleEn = "Gentle & Soft (Female)",
        engineRateModifier = 0.90f,
        pitch = 1.22f,
        ttsSpeechRate = 0.92f,
        iconEmoji = "🌸"
    ),
    ENERGETIC_FEMALE(
        id = "energetic_female",
        titleAr = "نبرة نشطة ومشرقة (أنثوي حيوي)",
        descriptionAr = "نبرة صوتية واضحة، سريعة ومفعمة بالنشاط والتنبيه المريح",
        titleEn = "Lively & Bright (Female)",
        engineRateModifier = 1.08f,
        pitch = 1.15f,
        ttsSpeechRate = 1.06f,
        iconEmoji = "⚡"
    ),
    CONFIDENT_MALE(
        id = "confident_male",
        titleAr = "نبرة رسمية وقورة (ذكوري)",
        descriptionAr = "نبرة مستشفيات احترافية ورسمية واضحة ومخارج حروف متزنة",
        titleEn = "Formal & Calm (Male)",
        engineRateModifier = 0.96f,
        pitch = 0.88f,
        ttsSpeechRate = 0.96f,
        iconEmoji = "🎙️"
    ),
    DYNAMIC_ANNOUNCER(
        id = "dynamic_announcer",
        titleAr = "نبرة المطار والنداء السريع",
        descriptionAr = "نبرة إذاعية تنبيهية بارزة للمختبرات المزدحمة لضمان لفت الانتباه",
        titleEn = "Airport & Express Announcer",
        engineRateModifier = 1.15f,
        pitch = 1.05f,
        ttsSpeechRate = 1.12f,
        iconEmoji = "📢"
    )
}
