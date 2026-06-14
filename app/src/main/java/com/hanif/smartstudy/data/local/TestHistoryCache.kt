package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.data.model.TestHistoryEntry
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// ─────────────────────────────────────────────────────────
// TestHistoryCache — "এখন টেস্ট দাও" রেজাল্ট হিস্ট্রি (DataStore JSON)
// সর্বোচ্চ ১০০টি রেজাল্ট রাখা হয়, নতুনগুলো সবার আগে
// ─────────────────────────────────────────────────────────

class TestHistoryCache(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val KEY_HISTORY_JSON = stringPreferencesKey("test_history_json")
        private const val MAX_ENTRIES = 100
    }

    suspend fun getHistory(): List<TestHistoryEntry> {
        val json = context.dataStore.data.first()[KEY_HISTORY_JSON]
        return parseJson(json)
    }

    fun historyFlow(): Flow<List<TestHistoryEntry>> = context.dataStore.data.map { prefs ->
        parseJson(prefs[KEY_HISTORY_JSON])
    }

    suspend fun addEntry(entry: TestHistoryEntry) {
        val current = getHistory()
        val updated = (listOf(entry) + current).take(MAX_ENTRIES)
        context.dataStore.edit { it[KEY_HISTORY_JSON] = gson.toJson(updated) }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it[KEY_HISTORY_JSON] = gson.toJson(emptyList<TestHistoryEntry>()) }
    }

    private fun parseJson(json: String?): List<TestHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<TestHistoryEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
