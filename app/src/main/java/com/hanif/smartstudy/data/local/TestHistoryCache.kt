package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.data.model.ResultReviewItem
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
        // মক/মডেল টেস্টের প্রশ্ন-সহ রিভিউ — আলাদা key-তে, সর্বশেষ ৩০টা টেস্টের (ডেটা হালকা রাখতে)
        private val KEY_REVIEWS_JSON = stringPreferencesKey("test_history_reviews_json")
        private const val MAX_REVIEWS = 30
    }

    suspend fun getHistory(): List<TestHistoryEntry> {
        val json = context.dataStore.data.first()[KEY_HISTORY_JSON]
        return parseJson(json)
    }

    fun historyFlow(): Flow<List<TestHistoryEntry>> = context.dataStore.data.map { prefs ->
        parseJson(prefs[KEY_HISTORY_JSON])
    }

    suspend fun addEntry(entry: TestHistoryEntry, review: List<ResultReviewItem> = emptyList()) {
        val current = getHistory()
        val updated = (listOf(entry) + current).take(MAX_ENTRIES)
        context.dataStore.edit { prefs ->
            prefs[KEY_HISTORY_JSON] = gson.toJson(updated)
            if (review.isNotEmpty() && entry.id.isNotBlank()) {
                val map = parseReviews(prefs[KEY_REVIEWS_JSON])
                map[entry.id] = review
                // শুধু যেসব টেস্ট এখনো হিস্ট্রিতে আছে সেগুলোর, সর্বশেষ MAX_REVIEWS টা
                val keep = updated.map { it.id }.filter { it in map }.take(MAX_REVIEWS).toSet()
                val trimmed = LinkedHashMap<String, List<ResultReviewItem>>()
                keep.forEach { k -> map[k]?.let { trimmed[k] = it } }
                prefs[KEY_REVIEWS_JSON] = gson.toJson(trimmed)
            }
        }
    }

    /** একটা টেস্টের সংরক্ষিত প্রশ্ন-তালিকা (না থাকলে ফাঁকা) */
    suspend fun getReview(entryId: String): List<ResultReviewItem> =
        parseReviews(context.dataStore.data.first()[KEY_REVIEWS_JSON])[entryId] ?: emptyList()

    /** কোন কোন টেস্টের প্রশ্ন-রিভিউ সংরক্ষিত আছে */
    fun reviewIdsFlow(): Flow<Set<String>> = context.dataStore.data.map { parseReviews(it[KEY_REVIEWS_JSON]).keys.toSet() }

    private fun parseReviews(json: String?): MutableMap<String, List<ResultReviewItem>> {
        if (json.isNullOrBlank()) return LinkedHashMap()
        return try {
            val type = object : TypeToken<LinkedHashMap<String, List<ResultReviewItem>>>() {}.type
            gson.fromJson<LinkedHashMap<String, List<ResultReviewItem>>>(json, type) ?: LinkedHashMap()
        } catch (e: Exception) { LinkedHashMap() }
    }

    suspend fun clearHistory() {
        context.dataStore.edit {
            it[KEY_HISTORY_JSON] = gson.toJson(emptyList<TestHistoryEntry>())
            it[KEY_REVIEWS_JSON] = "{}"
        }
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
