package com.hanif.smartstudy.focus

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

// ═══════════════════════════════════════════════════════════════════
//  FocusModeStore — ফোকাস মোডের সিলেকশন (সাবজেক্ট + পরীক্ষার তারিখ)
//  DataStore-এ সেভ থাকে (RoutineCache.kt-এর একই প্যাটার্নে), যাতে অ্যাপ
//  বন্ধ করে খুললেও এবং ব্যাকগ্রাউন্ড alarm/notification থেকেও পড়া যায়।
// ═══════════════════════════════════════════════════════════════════

/** ফোকাস মোডের বর্তমান অবস্থার একটা immutable স্ন্যাপশট */
data class FocusModeState(
    val enabled           : Boolean = false,
    val subject           : String  = "",
    val examDateMillis    : Long    = 0L,   // পরীক্ষার তারিখ (দিনের যেকোনো সময়, দরকার হলে normalize হয়)
    val activatedAtMillis : Long    = 0L    // কবে চালু হয়েছিল — ৭ দিন hard-cap হিসাবের জন্য
) {

    /** আজ থেকে পরীক্ষার তারিখ পর্যন্ত কত দিন বাকি (আজ হলে 0, তারিখ পার হয়ে গেলে ঋণাত্মক) */
    fun daysUntilExam(): Int {
        if (examDateMillis <= 0L) return 0
        val today = startOfDay(System.currentTimeMillis())
        val exam  = startOfDay(examDateMillis)
        return ((exam - today) / DAY_MS).toInt()
    }

    /**
     * এই মুহূর্তে ফোকাস মোড আসলেই কার্যকর কিনা — মাস্টার সুইচ, ইউজারের
     * enable flag, পরীক্ষার তারিখ পার হয়ে গেছে কিনা, এবং ৭ দিনের hard-cap —
     * সবকটা শর্ত একসাথে চেক করে। এই একটামাত্র ফাংশনের ওপর ভিত্তি করেই
     * warning screen / nudge / notification দেখানো উচিত কিনা ঠিক হবে।
     */
    fun isEffectivelyActive(): Boolean {
        if (!FocusModeConfig.ENABLED) return false
        if (!enabled || subject.isBlank()) return false
        if (daysUntilExam() < 0) return false   // পরীক্ষা পার হয়ে গেছে
        if (activatedAtMillis > 0L) {
            val elapsedDays = ((System.currentTimeMillis() - activatedAtMillis) / DAY_MS).toInt()
            if (elapsedDays >= FocusModeConfig.MAX_ACTIVE_DAYS) return false // hard cap
        }
        return true
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        private fun startOfDay(millis: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = millis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}

class FocusModeStore(private val context: Context) {

    companion object {
        private val KEY_ENABLED      = booleanPreferencesKey("focus_mode_enabled")
        private val KEY_SUBJECT      = stringPreferencesKey("focus_mode_subject")
        private val KEY_EXAM_DATE    = longPreferencesKey("focus_mode_exam_date")
        private val KEY_ACTIVATED_AT = longPreferencesKey("focus_mode_activated_at")

        /** আজ দিনের শুরু (00:00) millis — "আজ" প্রিসেট বাছাই করলে ব্যবহার হয় */
        fun todayStartMillis(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        /** আগামীকাল দিনের শুরু (00:00) millis — "আগামীকাল" প্রিসেট বাছাই করলে ব্যবহার হয় */
        fun tomorrowStartMillis(): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }

    /** Live state — Menu প্যানেল ও Home/Study card গুলো এটা collect করে সাথে সাথে UI আপডেট রাখে */
    val stateFlow: Flow<FocusModeState> = context.dataStore.data.map { prefs ->
        FocusModeState(
            enabled           = prefs[KEY_ENABLED] ?: false,
            subject           = prefs[KEY_SUBJECT] ?: "",
            examDateMillis    = prefs[KEY_EXAM_DATE] ?: 0L,
            activatedAtMillis = prefs[KEY_ACTIVATED_AT] ?: 0L
        )
    }

    suspend fun getState(): FocusModeState = stateFlow.first()

    /** সাবজেক্ট + পরীক্ষার তারিখ দিয়ে ফোকাস মোড চালু করো (Study ট্যাবের "🎯 আজ ফোকাস" কার্ড থেকে) */
    suspend fun activate(subject: String, examDateMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLED]      = true
            prefs[KEY_SUBJECT]      = subject
            prefs[KEY_EXAM_DATE]    = examDateMillis
            prefs[KEY_ACTIVATED_AT] = System.currentTimeMillis()
        }
    }

    /** এক ট্যাপে সব বন্ধ/রিসেট — Menu প্যানেলের "🔴 এখনই বন্ধ করো" বাটনের জন্য (Part ৫ এ যুক্ত হবে) */
    suspend fun deactivate() {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = false
        }
    }
}
