package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * টেকনিক/ব্যাখ্যা লিখতে লিখতে সেভ না করেই বন্ধ হয়ে গেলে (বাতিল, back, বাইরে ট্যাপ, প্রশ্ন বদলে যাওয়া,
 * অ্যাপ বন্ধ) লেখাটা যেন হারিয়ে না যায় — তাই ফোনে (DataStore) ড্রাফট হিসেবে জমা থাকে।
 *
 * - প্রতি (প্রশ্ন + এডিট-টার্গেট) জোড়ায় একটা ড্রাফট। নতুন এন্ট্রির জন্য existingId = ""।
 * - সেভ সফল হলেই ড্রাফট মুছে যায়; ইউজার নিজে চাইলেও মুছতে পারে।
 * - writeAsync() নিজের scope-এ চলে, তাই ডায়ালগ/স্ক্রিন বন্ধ হয়ে গেলেও লেখা শেষ হয়।
 */
data class TechniqueDraft(
    val key             : String  = "",
    val questionId      : String  = "",
    val existingId      : String  = "",     // খালি = নতুন এন্ট্রি; নইলে এই id-র টেকনিক এডিট হচ্ছিল
    val userId          : String  = "",
    val text            : String  = "",
    val type            : String  = "technique",
    val isPublic        : Boolean = false,
    val tags            : List<String> = emptyList(),
    val questionPreview : String  = "",
    val updatedAt       : Long    = 0L
)

object TechniqueDraftStore {

    private val KEY = stringPreferencesKey("technique_drafts_json")
    private val gson = Gson()
    private val listType = object : TypeToken<List<TechniqueDraft>>() {}.type
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun keyFor(questionId: String, existingId: String?, userId: String) =
        "$userId|$questionId|${existingId.orEmpty()}"

    private fun parse(json: String?): List<TechniqueDraft> = try {
        if (json.isNullOrBlank()) emptyList() else (gson.fromJson<List<TechniqueDraft>>(json, listType) ?: emptyList())
    } catch (_: Exception) { emptyList() }

    /** সব ড্রাফট (নতুন আগে) — লাইভ আপডেট হয় */
    fun flow(context: Context): Flow<List<TechniqueDraft>> =
        context.applicationContext.dataStore.data
            .map { parse(it[KEY]).sortedByDescending { d -> d.updatedAt } }

    suspend fun get(context: Context, key: String): TechniqueDraft? =
        parse(context.applicationContext.dataStore.data.first()[KEY]).firstOrNull { it.key == key }

    suspend fun put(context: Context, draft: TechniqueDraft) {
        if (draft.text.isBlank()) { delete(context, draft.key); return }
        context.applicationContext.dataStore.edit { prefs ->
            val list = parse(prefs[KEY]).filter { it.key != draft.key } +
                draft.copy(updatedAt = System.currentTimeMillis())
            prefs[KEY] = gson.toJson(list)
        }
    }

    suspend fun delete(context: Context, key: String) {
        context.applicationContext.dataStore.edit { prefs ->
            val list = parse(prefs[KEY])
            val kept = list.filter { it.key != key }
            if (kept.size != list.size) prefs[KEY] = gson.toJson(kept)
        }
    }

    /** ডায়ালগ/স্ক্রিন বন্ধ হওয়ার মুহূর্তেও নিরাপদ — নিজের scope-এ চলে */
    fun putAsync(context: Context, draft: TechniqueDraft) {
        val app = context.applicationContext
        appScope.launch { runCatching { put(app, draft) } }
    }

    fun deleteAsync(context: Context, key: String) {
        val app = context.applicationContext
        appScope.launch { runCatching { delete(app, key) } }
    }
}
