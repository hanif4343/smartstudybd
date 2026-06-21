package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.hanif.smartstudy.data.model.DailyRoutine
import com.hanif.smartstudy.data.model.RoutineItem
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

// ─────────────────────────────────────────────────────────
// RoutineCache — দৈনিক রুটিন চেকলিস্ট (DataStore তে JSON)
// তারিখ পরিবর্তন হলে আগের দিনের আইটেমগুলো (টিক বাদে টাইটেল)
// টেমপ্লেট হিসেবে নতুন দিনে আনচেকড অবস্থায় কপি হয়
// ─────────────────────────────────────────────────────────

class RoutineCache(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val KEY_ROUTINE_JSON = stringPreferencesKey("daily_routine_json")
    }

    private fun todayString(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // ── আজকের রুটিন পড়ো — তারিখ বদলালে আগের আইটেম আনচেকড করে নতুন দিনে রোলওভার করো ──
    suspend fun getTodayRoutine(): DailyRoutine {
        val prefs   = context.dataStore.data.first()
        val json    = prefs[KEY_ROUTINE_JSON]
        val today   = todayString()
        val stored  = parseJson(json)

        return if (stored.date == today) {
            stored
        } else {
            // নতুন দিন — আগের আইটেমগুলো আনচেকড করে রাখো (routine টেমপ্লেট হিসেবে রিপিট হয়)
            val rolledOver = DailyRoutine(
                date  = today,
                items = stored.items.map { it.copy(done = false) }
            )
            saveRoutine(rolledOver)
            rolledOver
        }
    }

    fun todayRoutineFlow(): Flow<DailyRoutine> = context.dataStore.data.map { prefs ->
        val json   = prefs[KEY_ROUTINE_JSON]
        val today  = todayString()
        val stored = parseJson(json)
        if (stored.date == today) stored
        else DailyRoutine(date = today, items = stored.items.map { it.copy(done = false) })
    }

    suspend fun saveRoutine(routine: DailyRoutine) {
        context.dataStore.edit { it[KEY_ROUTINE_JSON] = gson.toJson(routine) }
    }

    suspend fun addItem(
        title: String,
        subject: String,
        subTopic: String,
        minutes: Int,
        reminderEnabled: Boolean = false,
        reminderHour: Int = -1,
        reminderMinute: Int = -1
    ): RoutineItem {
        val routine = getTodayRoutine()
        val newItem = RoutineItem(
            id       = "r_${System.currentTimeMillis()}",
            title    = title,
            subject  = subject,
            subTopic = subTopic,
            minutes  = minutes,
            done     = false,
            reminderEnabled = reminderEnabled,
            reminderHour    = reminderHour,
            reminderMinute  = reminderMinute
        )
        saveRoutine(routine.copy(items = routine.items + newItem))
        return newItem
    }

    suspend fun toggleItem(id: String) {
        val routine = getTodayRoutine()
        val updated = routine.items.map {
            if (it.id == id) it.copy(done = !it.done) else it
        }
        saveRoutine(routine.copy(items = updated))
    }

    suspend fun removeItem(id: String) {
        val routine = getTodayRoutine()
        saveRoutine(routine.copy(items = routine.items.filterNot { it.id == id }))
    }

    // ── একটা আইটেমের নিজস্ব reminder (hour/minute/on-off) সেট বা আপডেট করো ──
    suspend fun setItemReminder(id: String, enabled: Boolean, hour: Int, minute: Int): RoutineItem? {
        val routine = getTodayRoutine()
        var changed: RoutineItem? = null
        val updated = routine.items.map {
            if (it.id == id) {
                val item = it.copy(reminderEnabled = enabled, reminderHour = hour, reminderMinute = minute)
                changed = item
                item
            } else it
        }
        saveRoutine(routine.copy(items = updated))
        return changed
    }

    private fun parseJson(json: String?): DailyRoutine {
        if (json.isNullOrBlank()) return DailyRoutine(date = todayString())
        return try {
            gson.fromJson(json, DailyRoutine::class.java) ?: DailyRoutine(date = todayString())
        } catch (e: Exception) {
            DailyRoutine(date = todayString())
        }
    }
}
