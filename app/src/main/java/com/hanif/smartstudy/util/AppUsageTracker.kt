package com.hanif.smartstudy.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

/**
 * "আমি SmartStudy-তে কতক্ষণ সময় দিলাম" — দিন-ভিত্তিক bucket হিসেবে, সম্পূর্ণ লোকাল (ইউজারের
 * সিদ্ধান্ত অনুযায়ী কোনো সার্ভারে যায় না)।
 *
 * নতুন কোনো টাইমার বানানো হয়নি — MainActivity.onResume()/onPause()-এ আগে থেকেই একটা নির্ভুল
 * সেশন-টাইমার আছে (যেটা session.recordSessionMinutes() দিয়ে লাইফটাইম টোটাল রাখে, StatsScreen-এর
 * "অ্যাপ সময়" badge-এ দেখানো হয়)। এই ফাইল শুধু সেই একই sessionMin সংখ্যাটা দিনের bucket-এও
 * যোগ করে রাখে (দেখো MainActivity.kt-তে addMinutes() কল), যাতে সাপ্তাহিক চার্ট আর "আজ কত
 * মিনিট" আলাদাভাবে দেখানো যায় — কোনো দ্বিতীয় প্যারালাল টাইমার নেই, তাই দুই জায়গার হিসাব
 * কখনো আলাদা হবে না।
 */
object AppUsageTracker {

    private val KEY = stringPreferencesKey("app_usage_daily_minutes")
    private const val MAX_DAYS_KEPT = 60
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun addMinutes(context: Context, minutes: Int) {
        if (minutes <= 0) return
        val today = dayFmt.format(Date())
        context.dataStore.edit { prefs ->
            val map = readMap(prefs[KEY]).toMutableMap()
            map[today] = (map[today] ?: 0) + minutes
            val cutoff = System.currentTimeMillis() - MAX_DAYS_KEPT * 24L * 3600_000L
            val trimmed = map.filterKeys {
                try { (dayFmt.parse(it)?.time ?: 0L) >= cutoff } catch (_: Exception) { true }
            }
            prefs[KEY] = Gson().toJson(trimmed)
        }
    }

    private fun readMap(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            Gson().fromJson<Map<String, Int>>(json, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
    }

    /** আজ, এই সপ্তাহ, আর শেষ ৭ দিনের (চার্টের জন্য, পুরনো→নতুন ক্রমে) মিনিট-সংখ্যা। */
    suspend fun summary(context: Context): Summary {
        val map = readMap(context.dataStore.data.first()[KEY])
        val todayKey = dayFmt.format(Date())
        val last7 = (6 downTo 0).map { offset ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -offset)
            val key = dayFmt.format(c.time)
            key to (map[key] ?: 0)
        }
        return Summary(todayMin = map[todayKey] ?: 0, weekMin = last7.sumOf { it.second }, last7Days = last7)
    }

    data class Summary(
        val todayMin  : Int,
        val weekMin   : Int,
        val last7Days : List<Pair<String, Int>>   // "yyyy-MM-dd" → মিনিট, পুরনো থেকে নতুন
    )
}
