package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.first

/**
 * QBank Model Test-এ সম্প্রতি যেসব প্রশ্ন এসেছে তার id (ক্যাটাগরি অনুযায়ী) — পরের টেস্টে এগুলোকে
 * সবার শেষে রাখা হয় (দেখো util/QBankModelTestEngine)। শুধু এই ডিভাইসে, DataStore-এ JSON;
 * Room schema বদলানো লাগেনি। প্রতি ক্যাটাগরিতে সর্বশেষ MAX টা রাখা হয় (~৩টা বড় টেস্ট)।
 */
class QBankModelTestStore(private val context: Context) {
    private val gson = Gson()

    companion object {
        private val KEY = stringPreferencesKey("qbank_model_test_recent_json")
        private const val MAX = 600
    }

    private suspend fun readAll(): MutableMap<String, List<String>> {
        val json = context.dataStore.data.first()[KEY]
        if (json.isNullOrBlank()) return mutableMapOf()
        return try {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            (gson.fromJson<Map<String, List<String>>>(json, type) ?: emptyMap()).toMutableMap()
        } catch (e: Exception) { mutableMapOf() }
    }

    suspend fun recentIds(category: String): Set<String> = readAll()[category].orEmpty().toSet()

    suspend fun addRecent(category: String, ids: List<String>) {
        val all = readAll()
        val merged = (all[category].orEmpty().filterNot { it in ids } + ids).takeLast(MAX)
        all[category] = merged
        context.dataStore.edit { it[KEY] = gson.toJson(all) }
    }
}
