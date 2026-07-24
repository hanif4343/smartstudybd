package com.hanif.smartstudy.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.data.model.CaseInsensitiveGson
import com.hanif.smartstudy.data.model.DataSourceMode
import com.hanif.smartstudy.data.model.QBankItem
import com.hanif.smartstudy.data.model.QuizItem
import com.hanif.smartstudy.data.model.StudyItem
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = CaseInsensitiveGson.instance

    private val BASE_URL get() = BuildConfig.FIREBASE_URL.trimEnd('/')

    // ── Settings-এ "Data Source" ড্রপডাউন থেকে "Google Sheet" সিলেক্ট করা থাকলে
    // Firebase বাইপাস করে GasContentService (GAS Web App প্রক্সি) ব্যবহার হয়।
    // দেখো GasContentService.kt-এর ডকুমেন্টেশন কমেন্ট — READ/WRITE উভয় পাশেই। ──
    private fun isGoogleSheetMode(context: Context): Boolean =
        SessionManager(context).getDataSourceMode() == DataSourceMode.GOOGLE_SHEET

    /** Firebase Auth ID Token দিয়ে secure auth param */
    private suspend fun authParam(): String {
        val token = FirebaseTokenProvider.getToken()
        return if (token.isNotBlank()) "?auth=$token" else ""
    }

    /**
     * FAST PATH — শুধু SubjectOrder + SubTopicOrder fetch করে।
     * এই দুটো ছোট node, খুব দ্রুত আসে।
     * Subject list + SubTopic list দেখাতে এটুকুই যথেষ্ট।
     * Questions এর জন্য fetchAllContent() আলাদাভাবে background-এ চলবে।
     */
    suspend fun fetchSubjectsOnly(context: Context): ContentResult<AppContent> = withContext(Dispatchers.IO) {
        // Google Sheet মোডে subjects-only দ্রুত fetch করার আলাদা GAS action নেই
        // (SubjectOrder/SubTopicOrder Firebase-only node) — পুরো content-ই আনা হয়,
        // subject list সেখান থেকেই বের করা যাবে (item.subject ফিল্ড থেকে)।
        if (isGoogleSheetMode(context)) return@withContext GasContentService.fetchAllContent()
        Log.d(TAG, "=== FAST FETCH: SubjectOrder + SubTopicOrder only ===")
        try {
            coroutineScope {
                val subjectOrderDeferred  = async { fetchSubjectOrder() }
                val subTopicOrderDeferred = async { fetchSubTopicOrder() }
                val subjectOrder  = subjectOrderDeferred.await()
                val subTopicOrder = subTopicOrderDeferred.await()
                Log.d(TAG, "FAST FETCH done: SubjectOrder=${subjectOrder.size} SubTopicOrder=${subTopicOrder.size}")
                ContentResult.Success(AppContent(
                    quiz = emptyList(), qbank = emptyList(), study = emptyList(),
                    subjectOrder = subjectOrder, subTopicOrder = subTopicOrder,
                    fetchedAt = 0L   // 0L মানে questions এখনো আসেনি
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchSubjectsOnly error: ${e.message}", e)
            ContentResult.Error("Error: ${e.message}")
        }
    }

    /**
     * LIGHTWEIGHT CHECK — শুধু "/meta/updatedAt" (একটা ছোট নাম্বার) fetch করে, পুরো
     * Quiz/QBank/Study নয়। Admin কোনো প্রশ্ন এডিট/যোগ করলেই এই ভ্যালু বাড়ে
     * (FirebaseDataService.touchMetaUpdatedAt দেখুন)। SyncWorker ও ContentRepository
     * এটা দিয়ে আগে চেক করে নেয় — সার্ভারে আসলেই নতুন কিছু আছে কিনা — তারপর দরকার
     * হলেই শুধু পুরো কনটেন্ট আবার ডাউনলোড করে। এতে বেকার বারবার পুরো Quiz+QBank+Study
     * রিফেচ হয়ে bandwidth নষ্ট হয় না।
     * Node না থাকলে বা error হলে 0L রিটার্ন করে (caller তখন পুরনো TTL-fallback ব্যবহার করবে)।
     */
    suspend fun fetchMetaUpdatedAt(context: Context): Long = withContext(Dispatchers.IO) {
        // Google Sheet মোডে "/meta/updatedAt"-এর কোনো সমতুল্য (ছোট, দ্রুত) GAS action
        // নেই — 0L রিটার্ন করলে ContentRepository এটাকে "server has newer" ধরে নেয়
        // (দেখো ContentRepository.getContent()), ফলে BG_REFRESH_MIN_GAP_MS (১৫ মিনিট)
        // গ্যাপ মেনেই ব্যাকগ্রাউন্ডে refresh চলতে থাকে — user-এর নির্দেশনা অনুযায়ী
        // ("ধীর হোক, ব্যাকগ্রাউন্ডে আসবে") এটাই সবচেয়ে সহজ, নিরাপদ আচরণ।
        if (isGoogleSheetMode(context)) return@withContext 0L
        try {
            val auth = authParam()
            val url  = "$BASE_URL/meta/updatedAt.json$auth"
            val req  = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || body == "null") 0L
            else body.trim().toDoubleOrNull()?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "fetchMetaUpdatedAt error: ${e.message}")
            0L
        }
    }

    /**
     * DELTA/INCREMENTAL FETCH — পুরো sheet না টেনে, শুধু "updatedAt > since" এমন row
     * গুলো আনে (Firebase RTDB এর orderBy+startAt query দিয়ে, filter সার্ভার-সাইডে হয়,
     * তাই bandwidth শুধু বদলানো/নতুন row গুলোর সমান খরচ হয়)।
     *
     * since = 0L হলে (কখনো sync হয়নি) পুরো sheet ফেরত আসবে — caller-দের উচিত সেক্ষেত্রে
     * এমনিতেই fetchAllContent() ব্যবহার করা, এটা শুধু true delta case-এর জন্য।
     */
    private suspend inline fun <reified T> fetchSheetChangedSince(sheet: String, since: Long): List<T> =
        withContext(Dispatchers.IO) {
            try {
                val auth = FirebaseTokenProvider.getToken()
                val authQ = if (auth.isNotBlank()) "&auth=$auth" else ""
                // orderBy="updatedAt" → percent-encoded quotes লাগবে। startAt শুধু সেই row গুলোই
                // আনবে যাদের updatedAt ফিল্ড আছে এবং since এর চেয়ে বেশি/সমান — পুরনো row যেগুলোতে
                // updatedAt ফিল্ডই নেই, সেগুলো এমনিতেই বাদ পড়বে (তারা আগেই full sync-এ এসে গেছে)।
                val url = "$BASE_URL/$sheet.json?orderBy=%22updatedAt%22&startAt=$since$authQ"
                Log.d(TAG, "Delta fetch $sheet since=$since")

                val req  = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                resp.close()

                if (body.isBlank() || body == "null") return@withContext emptyList()

                val trimmed = body.trim()
                val items: List<T> = when {
                    trimmed.startsWith("[") -> {
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
                            Log.e(TAG, "$sheet delta Firebase error: ${obj.get("error")}")
                            return@withContext emptyList()
                        }
                        obj.entrySet().mapNotNull { (key, v) ->
                            try {
                                if (v.isJsonObject) {
                                    val obj2 = v.asJsonObject.deepCopy()
                                    obj2.addProperty("id", key)
                                    gson.fromJson(obj2, T::class.java)
                                } else null
                            } catch (e: Exception) { null }
                        }
                    }
                    else -> emptyList()
                }
                Log.d(TAG, "$sheet delta parsed: ${items.size} changed row(s)")
                items
            } catch (e: Exception) {
                Log.w(TAG, "fetchSheetChangedSince<$sheet> error: ${e.message}")
                emptyList()
            }
        }

    data class IncrementalContent(
        val quiz  : List<QuizItem>  = emptyList(),
        val qbank : List<QBankItem> = emptyList(),
        val study : List<StudyItem> = emptyList(),
        val subjectOrder : Map<String, Map<String, Map<String, Int>>> = emptyMap(),
        val subTopicOrder: Map<String, Map<String, Map<String, Map<String, Int>>>> = emptyMap(),
        val modelTests   : Map<String, List<com.hanif.smartstudy.data.model.ModelTestMeta>> = emptyMap()
    )

    /**
     * তিনটা sheet-এই একসাথে delta fetch চালায় (parallel), সাথে SubjectOrder/SubTopicOrder/
     * ModelTests — এই তিনটা ছোট node বলে প্রতিবারই পুরোটা রিফ্রেশ করা bandwidth-এ কোনো সমস্যা করে না।
     */
    suspend fun fetchIncrementalContent(
        context   : Context,
        sinceQuiz : Long,
        sinceQBank: Long,
        sinceStudy: Long
    ): ContentResult<IncrementalContent> = withContext(Dispatchers.IO) {
        // Google Sheet মোডে delta filter সাপোর্ট নেই (দেখো GasContentService.fetchIncrementalContent
        // এর কমেন্ট) — পুরো sheet-ই "changed" হিসেবে ফেরত যায়, mergeById() ঠিকভাবেই সামলে নেয়।
        if (isGoogleSheetMode(context)) return@withContext GasContentService.fetchIncrementalContent()
        try {
            coroutineScope {
                val quizDeferred         = async { fetchSheetChangedSince<QuizItem>("Quiz", sinceQuiz) }
                val qbankDeferred        = async { fetchSheetChangedSince<QBankItem>("QBank", sinceQBank) }
                val studyDeferred        = async { fetchSheetChangedSince<StudyItem>("Study", sinceStudy) }
                val subjectOrderDeferred = async { fetchSubjectOrder() }
                val subTopicOrderDeferred = async { fetchSubTopicOrder() }
                val modelTestsDeferred   = async { fetchModelTests() }

                val result = IncrementalContent(
                    quiz          = quizDeferred.await(),
                    qbank         = qbankDeferred.await(),
                    study         = studyDeferred.await(),
                    subjectOrder  = subjectOrderDeferred.await(),
                    subTopicOrder = subTopicOrderDeferred.await(),
                    modelTests    = modelTestsDeferred.await()
                )
                Log.d(TAG, "=== DELTA RESULT: Quiz=${result.quiz.size} QBank=${result.qbank.size} Study=${result.study.size} (changed only) ===")
                ContentResult.Success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchIncrementalContent error: ${e.message}", e)
            ContentResult.Error("Error: ${e.message}")
        }
    }

    suspend fun fetchAllContent(context: Context): ContentResult<AppContent> = withContext(Dispatchers.IO) {
        if (isGoogleSheetMode(context)) return@withContext GasContentService.fetchAllContent()

        Log.d(TAG, "=== FETCH START (parallel) ===")
        Log.d(TAG, "BASE_URL: ${BASE_URL.take(50)}")

        try {
            // সব ৫টা fetch একসাথে parallel — sequential এর বদলে
            // আগে sequential ছিল: Quiz → QBank → Study → SubjectOrder → SubTopicOrder
            // এখন সব একই সময়ে শুরু হয় → মোট সময় সবচেয়ে slow টার সমান
            val result = coroutineScope {
                val quizDeferred         = async { fetchSheet<QuizItem>("Quiz") }
                val qbankDeferred        = async { fetchSheet<QBankItem>("QBank") }
                val studyDeferred        = async { fetchSheet<StudyItem>("Study") }
                val subjectOrderDeferred = async { fetchSubjectOrder() }
                val subTopicOrderDeferred = async { fetchSubTopicOrder() }
                val modelTestsDeferred   = async { fetchModelTests() }

                val quiz          = quizDeferred.await()
                val qbank         = qbankDeferred.await()
                val study         = studyDeferred.await()
                val subjectOrder  = subjectOrderDeferred.await()
                val subTopicOrder = subTopicOrderDeferred.await()
                val modelTests    = modelTestsDeferred.await()

                Log.d(TAG, "=== RESULT: Quiz=${quiz.size} QBank=${qbank.size} Study=${study.size} SubjectOrder=${subjectOrder.size} SubTopicOrder=${subTopicOrder.size} ModelTests=${modelTests.size} ===")

                if (quiz.isEmpty() && qbank.isEmpty() && study.isEmpty()) {
                    ContentResult.Error("Firebase থেকে data আসেনি (সব empty)")
                } else {
                    ContentResult.Success(AppContent(
                        quiz = quiz, qbank = qbank, study = study,
                        subjectOrder = subjectOrder,
                        subTopicOrder = subTopicOrder,
                        modelTests = modelTests,
                        fetchedAt = System.currentTimeMillis()
                    ))
                }
            }
            result
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
                    // Firebase key may be raw ("Masters 1") or already encoded ("Masters%201").
                    // Normalize to URL-encoded form so QuizViewModel lookup always matches.
                    modeObj.entrySet().mapNotNull { (rawTag, tagVal) ->
                        if (!tagVal.isJsonObject) return@mapNotNull null
                        val inner = parseSerialMap(tagVal.asJsonObject)
                        if (inner.isEmpty()) null else com.hanif.smartstudy.data.model.AppContent.normalizedTagForPath(rawTag) to inner
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
                    // Normalize Firebase tag key to URL-encoded form to match QuizViewModel lookup.
                    modeObj.entrySet().mapNotNull { (rawTag, tagVal) ->
                        if (!tagVal.isJsonObject) return@mapNotNull null
                        val inner = parseSubjectMap(tagVal.asJsonObject)
                        if (inner.isEmpty()) null else com.hanif.smartstudy.data.model.AppContent.normalizedTagForPath(rawTag) to inner
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

    /**
     * Model Test — "ModelTests" node থেকে, subject ভিত্তিক।
     * গঠন: { "Bangla": { "1": {title, type, totalMarks, createdAt, questions:{"0":"Quiz|123", ...}}, "2": {...} }, ... }
     * Return type: Map<subject, List<ModelTestMeta>> (testNumber অনুযায়ী sorted)
     */
    private suspend fun fetchModelTests(): Map<String, List<com.hanif.smartstudy.data.model.ModelTestMeta>> =
        withContext(Dispatchers.IO) {
            try {
                val auth = authParam()
                val url  = "$BASE_URL/ModelTests.json$auth"
                val req  = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                resp.close()
                if (body.isBlank() || body == "null") return@withContext emptyMap()
                val root = com.google.gson.JsonParser.parseString(body).asJsonObject

                root.entrySet().mapNotNull { (subject, subjVal) ->
                    if (!subjVal.isJsonObject) return@mapNotNull null
                    val tests = subjVal.asJsonObject.entrySet().mapNotNull { (testNumKey, testVal) ->
                        if (!testVal.isJsonObject) return@mapNotNull null
                        try {
                            val t = testVal.asJsonObject
                            val testNumber = testNumKey.toIntOrNull() ?: return@mapNotNull null
                            val questionIds = t.getAsJsonObject("questions")?.entrySet()
                                ?.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
                                ?.mapNotNull { it.value?.takeIf { v -> v.isJsonPrimitive }?.asString }
                                ?: t.getAsJsonArray("questions")?.mapNotNull {
                                    it?.takeIf { v -> v.isJsonPrimitive }?.asString
                                }
                                ?: emptyList()
                            com.hanif.smartstudy.data.model.ModelTestMeta(
                                subject     = subject,
                                testNumber  = testNumber,
                                title       = t.get("title")?.asString ?: "",
                                type        = t.get("type")?.asString ?: "both",
                                totalMarks  = t.get("totalMarks")?.asInt ?: questionIds.size,
                                questionIds = questionIds,
                                createdAt   = t.get("createdAt")?.asLong ?: 0L
                            )
                        } catch (e: Exception) { null }
                    }.sortedBy { it.testNumber }
                    if (tests.isEmpty()) null else subject to tests
                }.toMap()
            } catch (e: Exception) {
                Log.e(TAG, "fetchModelTests error: ${e.message}")
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
