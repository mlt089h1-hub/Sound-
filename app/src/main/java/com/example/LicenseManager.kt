package com.example

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LicenseManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("shamel_lab_license_pref", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FIRST_RUN_MS = "key_first_run_ms"
        private const val KEY_IS_VIP = "key_is_vip"
        private const val KEY_EXPIRED_TIME_MS = "key_expired_time_ms"
        private const val KEY_ACTIVATED_CODE = "key_activated_code"

        // 120 days trial (4 months)
        private const val TRIAL_DAYS = 120L
        private val TRIAL_MS = TimeUnit.DAYS.toMillis(TRIAL_DAYS)

        // 1 Year duration per license code
        private val YEAR_MS = TimeUnit.DAYS.toMillis(365L)

        // Secret salt for verification checksum
        private const val SECRET_SALT = "SHAMEL_ALJANIBI_LAB_SECRET_SALT_2026_07703333687"
        private const val MASTER_VIP_KEY = "SHAMEL-MASTER-07703333687-VIP"

        // Known exact suffix overrides from the official catalog
        private val KNOWN_CODE_SUFFIXES = mapOf(
            1 to "C13C", 2 to "45BD", 3 to "1E5A", 4 to "6E59", 5 to "7F3E",
            6 to "8CA0", 7 to "C611", 8 to "CA62", 9 to "320C", 10 to "E260",
            11 to "5D40", 12 to "552C", 13 to "4227", 14 to "FE18", 15 to "52E7",
            16 to "435A", 17 to "22C2", 18 to "2DC7", 19 to "A365", 20 to "BE35",
            21 to "F367", 22 to "3DD9", 23 to "55B5", 24 to "957A", 25 to "CB05",
            26 to "116B", 27 to "3EC4", 28 to "2BED", 29 to "473D", 30 to "B82F",
            31 to "E69B", 32 to "AB3A", 33 to "E81C", 34 to "74C2", 35 to "71D6",
            36 to "7C32", 37 to "E6CF", 38 to "AC33", 39 to "44F4", 40 to "527D",
            1000 to "A29D"
        )

        fun calculateExpectedSuffix(serialInt: Int): String {
            val known = KNOWN_CODE_SUFFIXES[serialInt]
            if (known != null) return known
            val serialStr = String.format("%04d", serialInt)
            val raw = "$serialStr:$SECRET_SALT"
            return sha256Hex(raw).take(4).uppercase()
        }

        private fun sha256Hex(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder()
            for (b in digest) {
                sb.append(String.format("%02X", b))
            }
            return sb.toString()
        }

        fun validateKeyStructure(inputCode: String): Pair<Boolean, String> {
            val clean = inputCode.trim().uppercase()
            if (clean == MASTER_VIP_KEY) {
                return Pair(true, "VIP")
            }

            val pattern = Regex("^SHAMEL-LAB-(\\d{4})-([A-Z0-9]{4})$")
            val match = pattern.matchEntire(clean) ?: return Pair(false, "صيغة الكود غير صحيحة")

            val serialStr = match.groupValues[1]
            val checksum = match.groupValues[2]
            val serialInt = serialStr.toIntOrNull() ?: return Pair(false, "رقم الكود غير صالح")

            if (serialInt !in 1..1000) {
                return Pair(false, "رقم الكود خارج نطاق التراخيص المعتمدة (1 إلى 1000)")
            }

            val expected = calculateExpectedSuffix(serialInt)
            return if (expected == checksum) {
                Pair(true, "كود ترخيص سنوي صالح (#$serialStr)")
            } else {
                Pair(false, "رمز التحقق الأمني للكود غير مطابق")
            }
        }
    }

    init {
        // Record initial install time if not present
        if (!prefs.contains(KEY_FIRST_RUN_MS)) {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FIRST_RUN_MS, now).apply()
        }
    }

    fun isVip(): Boolean = prefs.getBoolean(KEY_IS_VIP, false)

    fun isLicensed(): Boolean {
        if (isVip()) return true
        val now = System.currentTimeMillis()

        // 1. Check if user has an activated license extension
        val expiryMs = prefs.getLong(KEY_EXPIRED_TIME_MS, 0L)
        if (expiryMs > now) {
            return true
        }

        // 2. Otherwise check trial period (120 days)
        val firstRunMs = prefs.getLong(KEY_FIRST_RUN_MS, now)
        return (now - firstRunMs) < TRIAL_MS
    }

    fun isTrialActive(): Boolean {
        if (isVip()) return false
        val expiryMs = prefs.getLong(KEY_EXPIRED_TIME_MS, 0L)
        if (expiryMs > System.currentTimeMillis()) {
            return false // Active paid license
        }
        val now = System.currentTimeMillis()
        val firstRunMs = prefs.getLong(KEY_FIRST_RUN_MS, now)
        return (now - firstRunMs) < TRIAL_MS
    }

    fun getRemainingDays(): Long {
        if (isVip()) return 9999L
        val now = System.currentTimeMillis()
        val expiryMs = prefs.getLong(KEY_EXPIRED_TIME_MS, 0L)

        if (expiryMs > now) {
            val diffMs = expiryMs - now
            return (TimeUnit.MILLISECONDS.toDays(diffMs) + 1).coerceAtLeast(1)
        }

        val firstRunMs = prefs.getLong(KEY_FIRST_RUN_MS, now)
        val trialElapsed = now - firstRunMs
        val trialRemaining = TRIAL_MS - trialElapsed
        return if (trialRemaining > 0) {
            (TimeUnit.MILLISECONDS.toDays(trialRemaining) + 1).coerceAtLeast(1)
        } else {
            0L
        }
    }

    fun activateCode(inputCode: String): Pair<Boolean, String> {
        val clean = inputCode.trim().uppercase()
        if (clean == MASTER_VIP_KEY) {
            prefs.edit()
                .putBoolean(KEY_IS_VIP, true)
                .putString(KEY_ACTIVATED_CODE, "VIP_MASTER")
                .apply()
            return Pair(true, "تم تفعيل الترخيص الدائم بنجاح (VIP Master Key)!")
        }

        val validation = validateKeyStructure(clean)
        if (!validation.first) {
            return Pair(false, validation.second)
        }

        // Check if code was already redeemed on this device
        val alreadyUsed = prefs.getStringSet("used_codes_set", emptySet()) ?: emptySet()
        if (alreadyUsed.contains(clean)) {
            return Pair(false, "هذا الكود تم استخدامه وتفعيله مسبقاً على هذا الجهاز!")
        }

        val now = System.currentTimeMillis()
        val currentExpiry = prefs.getLong(KEY_EXPIRED_TIME_MS, 0L)
        val newExpiry = if (currentExpiry > now) {
            currentExpiry + YEAR_MS
        } else {
            now + YEAR_MS
        }

        val updatedSet = alreadyUsed.toMutableSet().apply { add(clean) }

        prefs.edit()
            .putLong(KEY_EXPIRED_TIME_MS, newExpiry)
            .putString(KEY_ACTIVATED_CODE, clean)
            .putStringSet("used_codes_set", updatedSet)
            .apply()

        return Pair(true, "تم تفعيل الكود بنجاح! تم تمديد صلاحية النظام لسنة كاملة (365 يوماً).")
    }
}
