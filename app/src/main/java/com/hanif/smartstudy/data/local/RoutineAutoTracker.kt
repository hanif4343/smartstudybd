package com.hanif.smartstudy.data.local

import android.content.Context
import com.hanif.smartstudy.receiver.RoutineItemReminderReceiver
import com.hanif.smartstudy.widget.RoutineWidgetProvider

// ── Routine নিজে নিজে টিক ──
// ইউজার যেকোনো জায়গা (Quiz/QBank/Study) থেকে কোনো টপিকের প্রশ্নের উত্তর দিলে এখানে গোনা হয়।
// আজকের রুটিনে সেই বিষয়/টপিকের আইটেম থাকলে এবং আজ TARGET টা উত্তর হয়ে গেলে আইটেম ✅ হয়ে যায়।
// গণনা প্রতিদিন নতুন করে শুরু। কোনো কারণে ব্যর্থ হলে চুপচাপ বাদ — উত্তর দেওয়া কখনো আটকায় না।
object RoutineAutoTracker {

    const val TARGET = 10
    private const val PREFS = "routine_auto_progress"

    private fun norm(s: String) = s.trim().lowercase()

    suspend fun onAnswered(context: Context, subject: String, subTopic: String) {
        try {
            val sub = norm(subject); val top = norm(subTopic)
            if (sub.isEmpty() && top.isEmpty()) return

            val cache = RoutineCache(context)
            val routine = cache.getTodayRoutine()
            if (routine.items.none { !it.done && matches(it.subject, it.subTopic, sub, top) }) return

            // আজকের গণনা (তারিখ বদলালে রিসেট)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = routine.date
            if (prefs.getString("date", "") != today) prefs.edit().clear().putString("date", today).apply()
            val key = "c|$sub|$top"
            val count = prefs.getInt(key, 0) + 1
            prefs.edit().putInt(key, count).apply()
            if (count < TARGET) return

            var changed = false
            for (item in routine.items) {
                if (!item.done && matches(item.subject, item.subTopic, sub, top)) {
                    cache.toggleItem(item.id)
                    if (item.hasReminder) RoutineItemReminderReceiver.cancel(context, item.id)
                    changed = true
                }
            }
            if (changed) RoutineWidgetProvider.updateAll(context)
        } catch (e: Exception) {
            android.util.Log.w("RoutineAuto", "skip: ${e.message}")
        }
    }

    // আইটেমে subTopic থাকলে সেটাই মেলে; শুধু subject থাকলে subject মেলে
    private fun matches(itemSubject: String, itemSubTopic: String, sub: String, top: String): Boolean {
        val iSub = norm(itemSubject); val iTop = norm(itemSubTopic)
        return when {
            iTop.isNotEmpty() -> iTop == top
            iSub.isNotEmpty() -> iSub == sub
            else -> false
        }
    }
}
