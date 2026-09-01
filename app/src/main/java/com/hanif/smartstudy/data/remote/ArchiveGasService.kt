package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.JsonParser
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.ArchiveMoveResult
import com.hanif.smartstudy.data.model.ArchivePageResult
import com.hanif.smartstudy.data.model.ArchiveQuestion
import com.hanif.smartstudy.data.model.ArchiveSheet
import com.hanif.smartstudy.data.model.ArchiveTopicRef
import com.hanif.smartstudy.data.model.CaseInsensitiveGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * ── Archive সেকশনের নেটওয়ার্ক কল — সম্পূর্ণ নতুন, স্বতন্ত্র ফাইল ──
 *
 * ইচ্ছাকৃতভাবে `GasContentService.kt` স্পর্শ করা হয়নি (existing Quiz/QBank/Study
 * ডেটাফ্লো ১০০% অপরিবর্তিত রাখতে) — তাই এখানে একটা ছোট নিজস্ব OkHttp client/BASE_URL/
 * SECRET আছে, সামান্য ডুপ্লিকেশন হলেও ঝুঁকি শূন্য।
 *
 * GAS ব্যাকএন্ডের ৪টা নতুন action + ১টা existing generic action (getSheetRows,
 * tab="Topics Archive") ব্যবহার করে — কোনোটাই existing action-এর লজিক বদলায়নি।
 */
object ArchiveGasService {

    private const val TAG = "ArchiveGas"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = CaseInsensitiveGson.instance

    private val BASE_URL get() = BuildConfig.GAS_URL.trim()
    private val SECRET   get() = BuildConfig.GAS_SECRET.trim()

    fun isConfigured(): Boolean = BASE_URL.isNotBlank() && SECRET.isNotBlank()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private suspend fun getJson(url: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (!resp.isSuccessful || body.isBlank()) {
                Log.w(TAG, "HTTP ${resp.code} for $url")
                return@withContext null
            }
            JsonParser.parseString(body).asJsonObject
        } catch (e: Exception) {
            Log.e(TAG, "getJson error: ${e.message}", e)
            null
        }
    }

    /** "Topics Archive" শিটের সব রো — Subject→Topic ব্রাউজ করার জন্য (existing
     * generic getSheetRows action, শুধু tab নাম আলাদা — কোনো নতুন backend কোড লাগেনি) */
    suspend fun fetchArchiveTopics(): List<ArchiveTopicRef> {
        val url = "$BASE_URL?action=getSheetRows&tab=${enc("Topics Archive")}&secret=${enc(SECRET)}"
        val obj = getJson(url) ?: return emptyList()
        if (obj.get("status")?.asString != "success") return emptyList()
        val rows = obj.getAsJsonArray("rows") ?: return emptyList()
        return rows.mapNotNull { el ->
            try { gson.fromJson(el, ArchiveTopicRef::class.java) } catch (e: Exception) { null }
        }
    }

    /** একটা টপিকের একটা পেজ (default ৫০টা) — শুধু unreviewed প্রশ্ন, resume-safe cursor */
    suspend fun fetchQuestionsPage(
        sheet   : ArchiveSheet,
        topicId : String,
        cursor  : Int = 0,
        limit   : Int = 50
    ): ArchivePageResult {
        if (!isConfigured()) return ArchivePageResult(error = "GAS_URL/GAS_SECRET সেট নেই")
        val url = "$BASE_URL?action=getArchiveQuestionsPage&sheet=${sheet.gasKey}" +
                "&topicId=${enc(topicId)}&cursor=$cursor&limit=$limit&secret=${enc(SECRET)}"
        val obj = getJson(url) ?: return ArchivePageResult(error = "নেটওয়ার্ক/সার্ভার এরর")
        if (obj.get("status")?.asString != "success") {
            return ArchivePageResult(error = obj.get("message")?.asString ?: "unknown error")
        }
        val rowsArr = obj.getAsJsonArray("rows") ?: com.google.gson.JsonArray()
        val rows = rowsArr.mapNotNull { el ->
            try { gson.fromJson(el, ArchiveQuestion::class.java) } catch (e: Exception) { null }
        }
        return ArchivePageResult(
            rows       = rows,
            hasMore    = obj.get("hasMore")?.asBoolean ?: false,
            nextCursor = obj.get("nextCursor")?.asInt ?: cursor,
            total      = obj.get("total")?.asInt ?: 0
        )
    }

    /** A-Z Sort — একটা টপিকের সব unreviewed প্রশ্ন একসাথে (পুরো রো একসাথে নড়ে), সাজানো */
    suspend fun fetchQuestionsSorted(sheet: ArchiveSheet, topicId: String): ArchivePageResult {
        if (!isConfigured()) return ArchivePageResult(error = "GAS_URL/GAS_SECRET সেট নেই")
        val url = "$BASE_URL?action=getArchiveQuestionsSorted&sheet=${sheet.gasKey}" +
                "&topicId=${enc(topicId)}&secret=${enc(SECRET)}"
        val obj = getJson(url) ?: return ArchivePageResult(error = "নেটওয়ার্ক/সার্ভার এরর")
        if (obj.get("status")?.asString != "success") {
            return ArchivePageResult(error = obj.get("message")?.asString ?: "unknown error")
        }
        val rowsArr = obj.getAsJsonArray("rows") ?: com.google.gson.JsonArray()
        val rows = rowsArr.mapNotNull { el ->
            try { gson.fromJson(el, ArchiveQuestion::class.java) } catch (e: Exception) { null }
        }
        return ArchivePageResult(rows = rows, hasMore = false, nextCursor = 0, total = obj.get("total")?.asInt ?: rows.size)
    }

    /** সিলেক্ট করা প্রশ্নগুলোতে review_status="duplicate" বসায় (row ডিলিট হয় না) */
    suspend fun markDuplicate(sheet: ArchiveSheet, ids: List<String>): Boolean {
        if (!isConfigured() || ids.isEmpty()) return false
        val url = "$BASE_URL?action=archiveMarkDuplicate&sheet=${sheet.gasKey}" +
                "&ids=${enc(ids.joinToString(","))}&secret=${enc(SECRET)}"
        val obj = getJson(url) ?: return false
        return obj.get("status")?.asString == "success"
    }

    /** সিলেক্ট করা (ভালো) প্রশ্নগুলো Active Quiz/QBank-এ subject+topic সহ কপি করে,
     * Archive-এ review_status="moved"+moved_to_id বসায়। existing topic dropdown-থেকে
     * সিলেক্ট হলে সেই নামই পাঠালে হবে (backend নিজেই existing id খুঁজে নেবে),
     * না থাকলে নতুন নাম দিলে backend নতুন Subject/Topic নিজে থেকেই বানাবে। */
    suspend fun moveToActive(
        sheet      : ArchiveSheet,
        ids        : List<String>,
        newSubject : String,
        newSubTopic: String
    ): ArchiveMoveResult {
        if (!isConfigured() || ids.isEmpty()) return ArchiveMoveResult(error = "ids/কনফিগ প্রয়োজন")
        val url = "$BASE_URL?action=archiveMoveToActive&sheet=${sheet.gasKey}" +
                "&ids=${enc(ids.joinToString(","))}" +
                "&newSubject=${enc(newSubject)}&newSubTopic=${enc(newSubTopic)}&secret=${enc(SECRET)}"
        val obj = getJson(url) ?: return ArchiveMoveResult(error = "নেটওয়ার্ক/সার্ভার এরর")
        if (obj.get("status")?.asString != "success") {
            return ArchiveMoveResult(error = obj.get("message")?.asString ?: "unknown error")
        }
        val newIds = obj.getAsJsonArray("newIds")?.mapNotNull { it.asString } ?: emptyList()
        return ArchiveMoveResult(
            success   = true,
            moved     = obj.get("moved")?.asInt ?: newIds.size,
            newIds    = newIds,
            subjectId = obj.get("subjectId")?.asString,
            topicId   = obj.get("topicId")?.asString
        )
    }
}
