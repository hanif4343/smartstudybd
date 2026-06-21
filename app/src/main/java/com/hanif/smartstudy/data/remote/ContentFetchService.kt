package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.JsonObject
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.data.model.CaseInsensitiveGson
import com.hanif.smartstudy.data.model.QBankItem
import com.hanif.smartstudy.data.model.QuizItem
import com.hanif.smartstudy.data.model.StudyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class ContentResult<out T> {
    data class Success<T>(val data: T) : ContentResult<T>()
    data class Error(val message: String) : ContentResult<Nothing>()
}

object ContentFetchService {

    private const val TAG = "ContentFetch"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = CaseInsensitiveGson.instance

    private val BASE_URL get() = BuildConfig.FIREBASE_URL.trimEnd('/')

    /** Firebase Auth ID Token দিয়ে secure auth param */
    private suspend fun authParam(): String {
        val token = FirebaseTokenProvider.getToken()
        return if (token.isNotBlank()) "?auth=$token" else ""
    }

    suspend fun fetchAllContent(): ContentResult<AppContent> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== FETCH START ===")
        Log.d(TAG, "BASE_URL: ${BASE_URL.take(50)}")

        try {
            val quiz          = fetchSheet<QuizItem>("Quiz")
            val qbank         = fetchSheet<QBankItem>("QBank")
            val study         = fetchSheet<StudyItem>("Study")
            val subjectOrder  = fetchSubjectOrder()
            val subTopicOrder = fetchSubTopicOrder()

            Log.d(TAG, "=== RESULT: Quiz=${quiz.size} QBank=${qbank.size} Study=${study.size} SubjectOrder=${subjectOrder.size} SubTopicOrder=${subTopicOrder.size} ===")

            if (quiz.isEmpty() && qbank.isEmpty() && study.isEmpty()) {
                ContentResult.Error("Firebase থেকে data আসেনি (সব empty)")
            } else {
                ContentResult.Success(AppContent(
                    quiz = quiz, qbank = qbank, study = study,
                    subjectOrder = subjectOrder,
                    subTopicOrder = subTopicOrder,
                    fetchedAt = System.currentTimeMillis()
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllContent error: ${e.message}", e)
            ContentResult.Error("Error: ${e.message}")
        }
    }

    /**
     * Admin সেট করা subject সিরিয়াল — "SubjectOrder" node থেকে, mode + audience tag ভিত্তিক।
     * নতুন গঠন (v3): { "QUIZ": { "Job": { "subject": serial }, "Masters%201": {...} }, ... }
     * মধ্যবর্তী গঠন (v2, mode-only): { "QUIZ": { "subject": serial }, ... }
     * পুরনো গঠন (v1, flat): { "subject": serial }
     *
     * v2/v1 → fallback: পুরনো data হারিয়ে যায় না; admin আবার save করলেই v3-তে upgrade হবে।
     * Return type: Map<mode, Map<tag, Map<subject, serial>>>
     */
    private suspend fun fetchSubjectOrder(): Map<String, Map<String, Map<String, Int>>> = withContext(Dispatchers.IO) {
        try {
            val auth = authParam()
            val url  = "$BASE_URL/SubjectOrder.json$auth"
            val req  = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || body == "null") return@withContext emptyMap()
            val obj = com.google.gson.JsonParser.parseString(body).asJsonObject

            val modeKeys = setOf("QUIZ", "QBANK", "STUDY")

            // v1 detection: top-level keys are NOT mode names → flat legacy format
            val hasModeLevelKey = obj.entrySet().any { (k, _) -> k in modeKeys }
            if (!hasModeLevelKey) {
                // v1 flat: { "subject": serial } → wrap into all modes under "Job" tag
                val flat = parseSerialMap(obj)
                return@withContext if (flat.isEmpty()) emptyMap()
                else modeKeys.associateWith { mapOf("Job" to flat) }
            }

            // Mode level exists — check if v2 (mode→subject→serial) or v3 (mode→tag→subject→serial)
            obj.entrySet().mapNotNull { (mode, modeVal) ->
                if (!modeVal.isJsonObject) return@mapNotNull null
                val modeObj = modeVal.asJsonObject

                // v3 detection: children of mode are themselves objects (tag level), not numbers
                val looksV3 = modeObj.entrySet().any { (_, v) -> v.isJsonObject }

                val tagMap: Map<String, Map<String, Int>> = if (looksV3) {
                    // v3: mode → tag → subject → serial
                    modeObj.entrySet().mapNotNull { (encodedTag, tagVal) ->
                        if (!tagVal.isJsonObject) return@mapNotNull null
                        val inner = parseSerialMap(tagVal.asJsonObject)
                        if (inner.isEmpty()) null else encodedTag to inner
                    }.toMap()
                } else {
                    // v2: mode → subject → serial → promote to "Job" tag
                    val flat = parseSerialMap(modeObj)
                    if (flat.isEmpty()) emptyMap() else mapOf("Job" to flat)
                }

                if (tagMap.isEmpty()) null else mode to tagMap
            }.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "fetchSubjectOrder error: ${e.message}")
            emptyMap()
        }
    }

    private fun parseSerialMap(obj: JsonObject): Map<String, Int> =
        obj.entrySet().mapNotNull { (key, v) ->
            try {
                val serial = if (v.isJsonPrimitive) v.asJsonPrimitive.asNumber.toInt() else null
                serial?.let { key to it }
            } catch (e: Exception) { null }
        }.toMap()

    /**
     * Admin সেট করা subTopic সিরিয়াল — "SubTopicOrder" node থেকে, mode + tag ভিত্তিক।
     * নতুন গঠন (v3): { "QUIZ": { "Job": { "subject": { "subTopic": serial } } }, ... }
     * মধ্যবর্তী গঠন (v2): { "QUIZ": { "subject": { "subTopic": serial } }, ... }
     * পুরনো গঠন (v1): { "subject": { "subTopic": serial } }
     *
     * Return type: Map<mode, Map<tag, Map<subject, Map<subtopic, serial>>>>
     */
    private suspend fun fetchSubTopicOrder(): Map<String, Map<String, Map<String, Map<String, Int>>>> = withContext(Dispatchers.IO) {
        try {
            val auth = authParam()
            val url  = "$BASE_URL/SubTopicOrder.json$auth"
            val req  = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || body == "null") return@withContext emptyMap()
            val obj = com.google.gson.JsonParser.parseString(body).asJsonObject

            val modeKeys = setOf("QUIZ", "QBANK", "STUDY")

            fun parseSubjectMap(subjectObj: JsonObject): Map<String, Map<String, Int>> =
                subjectObj.entrySet().mapNotNull { (subject, subTree) ->
                    if (!subTree.isJsonObject) return@mapNotNull null
                    val inner = parseSerialMap(subTree.asJsonObject)
                    if (inner.isEmpty()) null else subject to inner
                }.toMap()

            val hasModeLevelKey = obj.entrySet().any { (k, _) -> k in modeKeys }
            if (!hasModeLevelKey) {
                // v1: { subject → { subtopic → serial } } → promote to Job tag, all modes
                val flat = parseSubjectMap(obj)
                return@withContext if (flat.isEmpty()) emptyMap()
                else modeKeys.associateWith { mapOf("Job" to flat) }
            }

            obj.entrySet().mapNotNull { (mode, modeVal) ->
                if (!modeVal.isJsonObject) return@mapNotNull null
                val modeObj = modeVal.asJsonObject

                // v3 detection: child values of mode-obj are objects whose OWN children are objects
                // (tag → subject → subtopic → serial means two nesting levels under mode)
                val looksV3 = modeObj.entrySet().any { (_, tagOrSubjVal) ->
                    if (!tagOrSubjVal.isJsonObject) return@any false
                    // If the grand-children are objects (not numbers), this is tag→subject level
                    tagOrSubjVal.asJsonObject.entrySet().any { (_, v) -> v.isJsonObject }
                }

                val tagMap: Map<String, Map<String, Map<String, Int>>> = if (looksV3) {
                    // v3: mode → encodedTag → subject → subtopic → serial
                    modeObj.entrySet().mapNotNull { (encodedTag, tagVal) ->
                        if (!tagVal.isJsonObject) return@mapNotNull null
                        val inner = parseSubjectMap(tagVal.asJsonObject)
                        if (inner.isEmpty()) null else encodedTag to inner
                    }.toMap()
                } else {
                    // v2: mode → subject → subtopic → serial → promote to Job tag
                    val flat = parseSubjectMap(modeObj)
                    if (flat.isEmpty()) emptyMap() else mapOf("Job" to flat)
                }

                if (tagMap.isEmpty()) null else mode to tagMap
            }.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "fetchSubTopicOrder error: ${e.message}")
            emptyMap()
        }
    }

    private suspend inline fun <reified T> fetchSheet(sheet: String): List<T> =
        withContext(Dispatchers.IO) {
            try {
                val auth = authParam()
                val url  = "$BASE_URL/$sheet.json$auth"
                Log.d(TAG, "Fetching $sheet: ${url.take(60)}...")

                val req  = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""

                Log.d(TAG, "$sheet response: HTTP ${resp.code}, len=${body.length}, preview=${body.take(80)}")

                if (body.isBlank() || body == "null") {
                    Log.w(TAG, "$sheet: empty response")
                    return@withContext emptyList()
                }

                val trimmed = body.trim()
                val items: List<T> = when {
                    trimmed.startsWith("[") -> {
                        // Quiz/Study/QBank Firebase এ JSON array হিসেবে আছে (GAS syncToFirebase: jsonData.push(...))
                        // — admin app এর toArr() এর মতো array index টাই "id" / _fbKey হিসেবে ব্যবহার করো।
                        // নইলে item.id খালি/ভুল থাকে আর admin edit ভুল path এ PATCH করে — Firebase এ জমা হয় না।
                        val arr = com.google.gson.JsonParser.parseString(trimmed).asJsonArray
                        arr.mapIndexedNotNull { idx, el ->
                            try {
                                if (el != null && el.isJsonObject) {
                                    val obj2 = el.asJsonObject.deepCopy()
                                    obj2.addProperty("id", idx.toString())
                                    gson.fromJson(obj2, T::class.java)
                                } else null
                            } catch (e: Exception) { null }
                        }
                    }
                    trimmed.startsWith("{") -> {
                        val obj = gson.fromJson(trimmed, JsonObject::class.java)
                        if (obj.has("error")) {
                            Log.e(TAG, "$sheet Firebase error: ${obj.get("error")}")
                            return@withContext emptyList()
                        }
                        obj.entrySet()
                            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
                            .mapNotNull { (key, v) ->
                                try {
                                    if (v.isJsonObject) {
                                        // Firebase path key (numeric index বা push key যেমন -NxAbc123)
                                        // সবসময় "id" হিসেবে override করো — এটাই admin edit-এ rowKey হিসেবে ব্যবহার হয়।
                                        // পুরনো questions-এ "id: 1092" numeric field থাকলেও Firebase path
                                        // (যেমন "137") দিয়ে replace করতে হবে, নইলে PATCH ভুল path-এ যাবে।
                                        val obj2 = v.asJsonObject.deepCopy()
                                        obj2.addProperty("id", key)
                                        gson.fromJson(obj2, T::class.java)
                                    } else null
                                } catch (e: Exception) { null }
                            }
                    }
                    else -> emptyList()
                }

                Log.d(TAG, "$sheet parsed: ${items.size} items")
                items
            } catch (e: Exception) {
                Log.e(TAG, "fetchSheet<$sheet> error: ${e.message}")
                emptyList()
            }
        }
}
