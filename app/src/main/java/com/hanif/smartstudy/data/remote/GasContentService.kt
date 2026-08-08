package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.JsonParser
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.data.model.CaseInsensitiveGson
import com.hanif.smartstudy.data.model.QBankItem
import com.hanif.smartstudy.data.model.QuizItem
import com.hanif.smartstudy.data.model.StudyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * ── "Google Sheet" ডেটা সোর্স — GAS (Google Apps Script) Web App প্রক্সির মাধ্যমে ──
 *
 * Settings-এ "Data Source" ড্রপডাউন থেকে "Google Sheet" সিলেক্ট করলে Quiz/QBank/Study
 * এর read + admin edit/update + subject তালিকা — সবকিছু Firebase বাইপাস করে সরাসরি
 * এই GAS Web App-এর মাধ্যমে Google Sheet-এ/থেকে যায় (`code_updated.gs`-এর doGet/doPost
 * action গুলোর ওপর ভিত্তি করে বানানো — getSheetRows/updateField/deleteByIds/renameField
 * এবং জেনেরিক row-upsert POST)।
 *
 * READ: getSheetRows Firebase বাইপাস করে সরাসরি sheet পড়ে — ধীর হতে পারে (GAS + Sheet API
 * ল্যাটেন্সি), কিন্তু ContentRepository-র cache layer অপরিবর্তিত থাকায় প্রথমবারের পর থেকে
 * সবসময় ইনস্ট্যান্ট cache hit হয়, নতুন/বদলানো ডেটা ব্যাকগ্রাউন্ডে চুপচাপ আসতে থাকে।
 *
 * WRITE: updateField GET action একবারে শুধু ১টা field আপডেট করে, তাই আংশিক এডিট
 * (adminEditQuestion-এর মতো, শুধু কিছু field বদলানো) একাধিক প্যারালাল updateField
 * কলে ভাগ করা হয় — Firebase-এর partial PATCH-এর কাছাকাছি আচরণ পেতে।
 *
 * সীমাবদ্ধতা (documented, ইচ্ছাকৃত ট্রেড-অফ):
 * - SubjectOrder/SubTopicOrder (admin-এর কাস্টম সিরিয়াল/ড্র্যাগ-রিঅর্ডার) এই মোডে খালি
 *   থাকে — GAS script-এ এর কোনো সমতুল্য action নেই। ফলাফলে বিষয়/অধ্যায় নামের ক্রমে
 *   (alphabetical) দেখাবে, যা AppContent-এর কমেন্টেই বলা আছে এমনিতেই একটা হ্যান্ডল-করা
 *   fallback (serial না থাকা সাবজেক্ট সবসময় নামানুসারে শেষে দেখায়)।
 * - "explanationVisibility" ফিল্ডের কোনো সরাসরি Sheet কলাম নেই generic row-schema তে,
 *   তাই এটা আপডেট/সেভ হয় না Sheet মোডে (UNSUPPORTED_FIELDS)।
 * - renameField (subject/sub_topic rename) GAS-এ subject-স্কোপড না — sub_topic rename
 *   পুরো sheet জুড়ে matching সব row-তেই প্রযোজ্য হবে, শুধু নির্দিষ্ট subject-এর মধ্যে না
 *   (Firebase ভার্সনে যেমন subject+subTopic দুটো মিলিয়ে scope করা হয়)।
 */
object GasContentService {

    private const val TAG = "GasContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // ── ~১৪,০০০ রো-র মতো বড় sheet একবারে (getDataRange().getValues()) পড়তে GAS-এর
        // নিজেরই কিছু সময় লাগে — আগে ৪৫ সেকেন্ড টাইমআউট ছিল, যেটার কারণে বড় ট্যাব
        // (যেমন QBank) মাঝপথে কেটে গিয়ে খালি/আংশিক রেজাল্ট আসছিল, অথচ কোনো error
        // দেখাচ্ছিল না (নিচের fetchSheetRows-এর error attribution এখন সেটাও ধরে)।
        // এখন first-load একবারই, ধীরে হলেও, পুরোটা লোড করার জন্য যথেষ্ট সময় দেওয়া হলো।
        .readTimeout(280, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson      = CaseInsensitiveGson.instance
    private val plainGson = com.google.gson.Gson()
    private val JSON_MT    = "application/json; charset=utf-8".toMediaType()

    private val BASE_URL get() = BuildConfig.GAS_URL.trim()
    private val SECRET   get() = BuildConfig.GAS_SECRET.trim()

    /** GAS_URL/GAS_SECRET দুটোই সেট আছে কিনা — Settings-এ ড্রপডাউনে "Google Sheet" দেখানোর আগে চেক করা হয় */
    fun isConfigured(): Boolean = BASE_URL.isNotBlank() && SECRET.isNotBlank()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // ══════════════════════════════════════════════════════════
    // READ
    // ══════════════════════════════════════════════════════════

    /** getSheetRows কল করে items + (ব্যর্থ হলে) আসল কারণ — silent empty-list এর বদলে
     * প্রকৃত error message (Unauthorized/Sheet not found/network ইত্যাদি) UI পর্যন্ত পৌঁছায় */
    private data class SheetFetchResult<T>(val items: List<T>, val error: String?)

    private suspend inline fun <reified T> fetchSheetRows(tab: String): SheetFetchResult<T> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL?action=getSheetRows&tab=$tab&secret=${enc(SECRET)}"
                val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
                val code = resp.code
                val body = resp.body?.string() ?: ""
                resp.close()
                if (!resp.isSuccessful) {
                    return@withContext SheetFetchResult(emptyList(), "$tab: HTTP $code")
                }
                if (body.isBlank()) {
                    return@withContext SheetFetchResult(emptyList(), "$tab: খালি response")
                }
                val obj = JsonParser.parseString(body).asJsonObject
                if (obj.get("status")?.asString != "success") {
                    val msg = obj.get("message")?.asString ?: body.take(150)
                    Log.w(TAG, "$tab getSheetRows non-success: $msg")
                    return@withContext SheetFetchResult(emptyList(), "$tab: $msg")
                }
                val rows = obj.getAsJsonArray("rows") ?: return@withContext SheetFetchResult(emptyList(), null)
                var parseFails = 0
                var lastParseError: String? = null
                val items = rows.mapNotNull { el ->
                    try {
                        if (!el.isJsonObject) return@mapNotNull null
                        val o = el.asJsonObject.deepCopy()
                        val idVal = o.get("id")
                        if (idVal == null || idVal.isJsonNull || idVal.asString.isBlank()) {
                            o.get("_fbKey")?.takeIf { !it.isJsonNull }?.let { o.addProperty("id", it.asString) }
                        }
                        gson.fromJson(o, T::class.java)
                    } catch (e: Exception) {
                        parseFails++
                        lastParseError = e.message
                        null
                    }
                }
                if (parseFails > 0) {
                    Log.w(TAG, "$tab: $parseFails/${rows.size()} row parse failed, e.g. $lastParseError")
                }
                // ── প্রায় সব row (>৫০%) parse-ব্যর্থ হলে এটা GAS/network সমস্যা না, বরং
                // মডেল-মিসম্যাচ (যেমন আগে "updatedAt":"" থাকায় সব row বাদ পড়ছিল) — এটাকে
                // "সফল, ০টা" হিসেবে চুপচাপ দেখানোর বদলে স্পষ্ট error হিসেবে জানানো হয় ──
                if (rows.size() > 0 && parseFails * 2 > rows.size()) {
                    SheetFetchResult(items, "$tab: ${parseFails}/${rows.size()} row parse ব্যর্থ (${lastParseError ?: "unknown"})")
                } else {
                    SheetFetchResult(items, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchSheetRows<$tab> error: ${e.message}")
                SheetFetchResult(emptyList(), "$tab: ${e.message ?: "network error"}")
            }
        }

    /** ContentFetchService.fetchAllContent() এর সমতুল্য — Firebase বাইপাস করে সরাসরি Sheet থেকে */
    suspend fun fetchAllContent(): ContentResult<AppContent> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext ContentResult.Error("Google Sheet মোড চালু করতে GAS_URL/GAS_SECRET লাগবে")
        try {
            coroutineScope {
                val quizD  = async { fetchSheetRows<QuizItem>("Quiz") }
                val qbankD = async { fetchSheetRows<QBankItem>("QBank") }
                val studyD = async { fetchSheetRows<StudyItem>("Study") }
                val quizR  = quizD.await()
                val qbankR = qbankD.await()
                val studyR = studyD.await()
                Log.d(TAG, "fetchAllContent: quiz=${quizR.items.size} qbank=${qbankR.items.size} study=${studyR.items.size}")

                // ── কোনো ট্যাবে real fetch error (timeout/HTTP fail/parse fail) হলে —
                // অন্য ট্যাব সফল হলেও পুরো ফলাফলকে "সফল" ধরে আংশিক ডেটা cache করা
                // হবে না। ইউজার স্পষ্ট error দেখবে, চুপচাপ কম প্রশ্ন cache হয়ে যাবে না। ──
                val errors = listOfNotNull(quizR.error, qbankR.error, studyR.error)
                if (errors.isNotEmpty()) {
                    ContentResult.Error("আংশিক ব্যর্থ (retry করো) — ${errors.joinToString(" | ")}")
                } else if (quizR.items.isEmpty() && qbankR.items.isEmpty() && studyR.items.isEmpty()) {
                    ContentResult.Error("Google Sheet থেকে data আসেনি (সব empty)")
                } else {
                    val now = System.currentTimeMillis()
                    ContentResult.Success(
                        AppContent(
                            quiz = quizR.items, qbank = qbankR.items, study = studyR.items,
                            fetchedAt = now,
                            // ── এই fetch-এর সময়টাই "remoteUpdatedAt" হিসেবে সেভ থাকে —
                            // ContentFetchService.fetchMetaUpdatedAt() এটা পড়ে বোঝে শেষ
                            // কবে পুরো (~১৪,০০০ row) Sheet সফলভাবে টানা হয়েছিল, যাতে
                            // প্রতি ১৫ মিনিটে না টেনে অনেক কম ঘন ঘন (SHEET_META_SAFE_GAP_MS)
                            // চেক করে — GAS/Sheets quota বাঁচাতে। ──
                            remoteUpdatedAt = now
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllContent error: ${e.message}", e)
            ContentResult.Error("Google Sheet error: ${e.message}")
        }
    }

    /**
     * ContentFetchService.fetchIncrementalContent() এর সমতুল্য — কিন্তু GAS-এর getSheetRows
     * delta/updatedAt filter সাপোর্ট করে না, তাই পুরো sheet-ই "changed" হিসেবে ফেরত যায়।
     * ContentRepository-র mergeById() যেহেতু id দিয়ে merge করে, ফলাফল সঠিকই হয় —
     * শুধু bandwidth-এ Firebase delta-sync-এর চেয়ে বেশি খরচ হয় (accepted trade-off)।
     */
    suspend fun fetchIncrementalContent(): ContentResult<ContentFetchService.IncrementalContent> =
        withContext(Dispatchers.IO) {
            when (val full = fetchAllContent()) {
                is ContentResult.Success -> ContentResult.Success(
                    ContentFetchService.IncrementalContent(
                        quiz  = full.data.quiz,
                        qbank = full.data.qbank,
                        study = full.data.study
                    )
                )
                is ContentResult.Error -> ContentResult.Error(full.message)
            }
        }

    // ══════════════════════════════════════════════════════════
    // PHASE 6 — Reference data (Subjects/Topics/SubTopics/Tags/Posts/Institutions)
    // ══════════════════════════════════════════════════════════

    /**
     * GAS action=getReferenceData — Admin App-এর ReferenceManagerTab.jsx যেই একই action
     * ব্যবহার করে (Phase 5, GAS `code_updated.gs`)। ছোট রেফারেন্স-টেবিল, একবারে বাল্ক-ফেচ।
     * ব্যর্থ হলে null রিটার্ন করে — caller (ContentRepository.syncReferenceData) তখন
     * Room-এর পুরনো cache অপরিবর্তিত রাখবে।
     */
    suspend fun fetchReferenceData(): com.hanif.smartstudy.data.model.ReferenceData? =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext null
            try {
                val url = "$BASE_URL?action=getReferenceData&secret=${enc(SECRET)}"
                val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
                val body = resp.body?.string() ?: ""
                resp.close()
                if (!resp.isSuccessful || body.isBlank()) return@withContext null
                val obj = JsonParser.parseString(body).asJsonObject
                if (obj.get("status")?.asString != "success") {
                    Log.w(TAG, "fetchReferenceData non-success: ${body.take(150)}")
                    return@withContext null
                }
                val dataEl = obj.get("data") ?: return@withContext null
                val raw = plainGson.fromJson(dataEl, com.hanif.smartstudy.data.model.ReferenceData::class.java)
                // ⚠️ vanilla Gson Kotlin data class instantiate করার সময় constructor call করে
                // না (Unsafe.allocateInstance ব্যবহার করে) — তাই JSON-এ কোনো key না থাকলে
                // Kotlin-এর non-null default (= emptyList()) কাজ করে না, ফিল্ডটা আসলে null
                // থেকে যায় যদিও compiler টাইপ non-null বলছে। code_updated.gs-এর REF_TABS
                // (Subjects/Topics/Tags/Posts/Institutions) দেখে কনফার্ম করা গেছে "subtopics"
                // key আদৌ পাঠানোই হয় না — তাই raw?.subtopics runtime-এ null থাকবেই, প্রতিটা
                // ফিল্ড এখানে ডিফেন্সিভলি null-coalesce করা হলো যাতে .map/.isEmpty() কল করলে
                // NPE না হয়।
                com.hanif.smartstudy.data.model.ReferenceData(
                    subjects     = raw?.subjects ?: emptyList(),
                    topics       = raw?.topics ?: emptyList(),
                    subtopics    = raw?.subtopics ?: emptyList(),   // GAS আপাতত এই key পাঠায় না — সবসময় খালি থাকবে
                    tags         = raw?.tags ?: emptyList(),
                    posts        = raw?.posts ?: emptyList(),
                    institutions = raw?.institutions ?: emptyList()
                )
            } catch (e: Exception) {
                Log.e(TAG, "fetchReferenceData error: ${e.message}")
                null
            }
        }

    /**
     * getQuestionsPage-এর রেসপন্স — এই page-এর rows + পরের page-এর cursor (null হলে আর পেজ নেই)।
     *
     * ⚠️ BUG FIX ("টপিকে ক্লিক করলে প্রশ্ন দেখা যাচ্ছে না, permanently ফাঁকা থেকে যাচ্ছে"):
     * `ok` ফিল্ডটা নতুন যোগ হলো। আগে নেটওয়ার্ক এরর/টাইমআউট/খারাপ JSON/non-success
     * status — সবগুলোই একই খালি রেজাল্ট (items=[], hasMore=false) ফেরত দিত, ঠিক
     * যেমনটা GAS থেকে "সত্যিই এই topic-এ আর কোনো প্রশ্ন নেই" রেসপন্স এলে হতো। ফলে
     * ContentRepository.cacheNextTopicBatch() একটা সাময়িক নেটওয়ার্ক/GAS ব্যর্থতাকেও
     * TopicSyncEntity-তে hasMore=false লিখে "সম্পূর্ণ সিঙ্ক" হিসেবে সেভ করে ফেলত —
     * সেই topic-টা তখন থেকে চিরস্থায়ীভাবে "০ প্রশ্ন, আর ফেচ করার কিছু নেই" ধরে
     * নিত, নেটওয়ার্ক ঠিক হয়ে গেলেও আর কখনো রিট্রাই হতো না (টপিকে ক্লিক করলেই ফাঁকা)।
     * এখন `ok=true` শুধু তখনই সেট হয় যখন GAS থেকে সত্যিকারের status=success পার্স
     * করা রেসপন্স এসেছে — ব্যর্থতায় `ok=false`, আর caller (cacheNextTopicBatch)
     * `ok=false` হলে TopicSyncEntity লিখবে না, তাই পরের চেষ্টায় আবার ফেচ হবে।
     */
    data class QuestionsPageResult<T>(
        val items      : List<T>,
        val nextCursor : String?,
        val hasMore    : Boolean,
        val ok         : Boolean = false
    )

    /**
     * GAS action=getQuestionsPage — topicId (+ ঐচ্ছিক subtopicId, QBank-এ) + cursor + limit
     * নিয়ে সেই topic-এর প্রশ্ন page-by-page আনে, Topics ট্যাবের row_start/row_count index
     * ব্যবহার করে (পুরো sheet স্ক্যান করে না — দেখো Phase 3 GAS `rebuildIndex`)।
     *
     * ⚠️ response-এর ঠিক ফিল্ড-নাম (`rows`/`nextCursor`/`hasMore`) `code_updated.gs`-এর
     * বাস্তব response-এর সাথে মিলিয়ে verify করে নেওয়া উচিত এই মেথড প্রথমবার UI-তে wire করার
     * আগে — এখানে getSheetRows-এর কনভেনশন (status/rows) অনুসরণ করা হয়েছে, `hasMore` field
     * না থাকলে rows.size >= limit থেকে অনুমান করা হয়।
     */
    private suspend inline fun <reified T> fetchQuestionsPage(
        sheet: String, topicId: String, subtopicId: String?, cursor: String?, limit: Int
    ): QuestionsPageResult<T> = withContext(Dispatchers.IO) {
        if (!isConfigured() || topicId.isBlank()) return@withContext QuestionsPageResult(emptyList(), null, false)
        try {
            var url = "$BASE_URL?action=getQuestionsPage&sheet=$sheet&topicId=${enc(topicId)}&limit=$limit&secret=${enc(SECRET)}"
            if (!subtopicId.isNullOrBlank()) url += "&subtopicId=${enc(subtopicId)}"
            if (!cursor.isNullOrBlank())     url += "&cursor=${enc(cursor)}"
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (!resp.isSuccessful || body.isBlank()) return@withContext QuestionsPageResult(emptyList(), null, false)
            val obj = JsonParser.parseString(body).asJsonObject
            if (obj.get("status")?.asString != "success") {
                Log.w(TAG, "getQuestionsPage non-success: ${body.take(150)}")
                return@withContext QuestionsPageResult(emptyList(), null, false)
            }
            val rows = obj.getAsJsonArray("rows") ?: return@withContext QuestionsPageResult(emptyList(), null, false)
            val items = rows.mapNotNull { el ->
                try {
                    if (!el.isJsonObject) return@mapNotNull null
                    val o = el.asJsonObject.deepCopy()
                    val idVal = o.get("id")
                    if (idVal == null || idVal.isJsonNull || idVal.asString.isBlank()) {
                        o.get("_fbKey")?.takeIf { !it.isJsonNull }?.let { o.addProperty("id", it.asString) }
                    }
                    gson.fromJson(o, T::class.java)
                } catch (e: Exception) { null }
            }
            val nextCursor = obj.get("nextCursor")?.takeIf { !it.isJsonNull }?.asString
            val hasMore = obj.get("hasMore")?.takeIf { !it.isJsonNull }?.asBoolean ?: (rows.size() >= limit)
            QuestionsPageResult(items, nextCursor, hasMore, ok = true)
        } catch (e: Exception) {
            Log.e(TAG, "getQuestionsPage<$sheet> error: ${e.message}")
            QuestionsPageResult(emptyList(), null, false, ok = false)
        }
    }

    /** Quiz sheet-এর জন্য getQuestionsPage — QuizItem হিসেবে parse */
    suspend fun fetchQuizPage(topicId: String, cursor: String?, limit: Int = 50) =
        fetchQuestionsPage<com.hanif.smartstudy.data.model.QuizItem>("Quiz", topicId, null, cursor, limit)

    /** QBank sheet-এর জন্য getQuestionsPage — QBankItem হিসেবে parse, subtopicId ঐচ্ছিক (৩-লেভেল cascading) */
    suspend fun fetchQBankPage(topicId: String, subtopicId: String?, cursor: String?, limit: Int = 50) =
        fetchQuestionsPage<com.hanif.smartstudy.data.model.QBankItem>("QBank", topicId, subtopicId, cursor, limit)

    /** Study sheet-এর জন্য getQuestionsPage — StudyItem হিসেবে parse */
    suspend fun fetchStudyPage(topicId: String, cursor: String?, limit: Int = 50) =
        fetchQuestionsPage<com.hanif.smartstudy.data.model.StudyItem>("Study", topicId, null, cursor, limit)

    /**
     * ── FIX ("পদবী/প্রতিষ্ঠান-মোডে প্রশ্ন ০/০" বাগ) — GAS action=getQuestionsByIds:
     * Exam_Appearances থেকে পাওয়া questionId-লিস্টের মধ্যে যেগুলো Room-এ এখনো নেই,
     * সেগুলো সরাসরি id দিয়ে GAS থেকে টার্গেটেড আনার জন্য (পুরো sheet স্ক্যান/ডাউনলোড
     * করা লাগে না — getSheetRows-এর মতো পুরো ট্যাব না এনে, ঠিক এই কয়েকটা id-ই আনে)।
     * দেখো ContentRepository.ensureRoomQuestionsByIds ও code_updated.gs-এর নতুন action।
     */
    private suspend inline fun <reified T> fetchQuestionsByIdsGeneric(sheet: String, ids: List<String>): List<T>? =
        withContext(Dispatchers.IO) {
            if (!isConfigured() || ids.isEmpty()) return@withContext null
            try {
                val idsParam = ids.joinToString(",") { enc(it) }
                val url = "$BASE_URL?action=getQuestionsByIds&sheet=$sheet&ids=$idsParam&secret=${enc(SECRET)}"
                val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
                val body = resp.body?.string() ?: ""
                resp.close()
                if (!resp.isSuccessful || body.isBlank()) return@withContext null
                val obj = JsonParser.parseString(body).asJsonObject
                if (obj.get("status")?.asString != "success") {
                    Log.w(TAG, "getQuestionsByIds non-success: ${body.take(150)}")
                    return@withContext null
                }
                val rows = obj.getAsJsonArray("rows") ?: return@withContext emptyList()
                rows.mapNotNull { el ->
                    try {
                        if (!el.isJsonObject) return@mapNotNull null
                        val o = el.asJsonObject.deepCopy()
                        val idVal = o.get("id")
                        if (idVal == null || idVal.isJsonNull || idVal.asString.isBlank()) {
                            o.get("_fbKey")?.takeIf { !it.isJsonNull }?.let { o.addProperty("id", it.asString) }
                        }
                        gson.fromJson(o, T::class.java)
                    } catch (e: Exception) { null }
                }
            } catch (e: Exception) {
                Log.e(TAG, "getQuestionsByIds<$sheet> error: ${e.message}")
                null
            }
        }

    /** Quiz sheet-এর জন্য id দিয়ে টার্গেটেড ফেচ */
    suspend fun fetchQuizByIds(ids: List<String>) =
        fetchQuestionsByIdsGeneric<com.hanif.smartstudy.data.model.QuizItem>("Quiz", ids)

    /** QBank sheet-এর জন্য id দিয়ে টার্গেটেড ফেচ */
    suspend fun fetchQBankByIds(ids: List<String>) =
        fetchQuestionsByIdsGeneric<com.hanif.smartstudy.data.model.QBankItem>("QBank", ids)

    /** Study sheet-এর জন্য id দিয়ে টার্গেটেড ফেচ */
    suspend fun fetchStudyByIds(ids: List<String>) =
        fetchQuestionsByIdsGeneric<com.hanif.smartstudy.data.model.StudyItem>("Study", ids)

    /**
     * Phase 6 — GAS action=getAllExamAppearances (code_updated.gs-এ যোগ করা হয়েছে, getReferenceData-এর
     * বাল্ক-ফেচ প্যাটার্নেই) পুরো Exam_Appearances টেবিল একবারে ফেচ করে। Admin App-এর
     * `getExamAppearances` action-এর থেকে আলাদা — সেটা একটা নির্দিষ্ট questionId-scoped
     * (single-question), এটা পুরো টেবিল (bulk, "পদ অনুযায়ী ব্রাউজ" ফ্লো-র জন্য)।
     */
    suspend fun fetchAllExamAppearances(): List<com.hanif.smartstudy.data.model.ExamAppearanceRef>? =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext null
            try {
                val url = "$BASE_URL?action=getAllExamAppearances&secret=${enc(SECRET)}"
                val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
                val body = resp.body?.string() ?: ""
                resp.close()
                if (!resp.isSuccessful || body.isBlank()) return@withContext null
                val obj = JsonParser.parseString(body).asJsonObject
                if (obj.get("status")?.asString != "success") {
                    Log.w(TAG, "fetchAllExamAppearances non-success: ${body.take(150)}")
                    return@withContext null
                }
                val arr = obj.getAsJsonArray("appearances") ?: return@withContext emptyList()
                arr.mapNotNull { el ->
                    try { plainGson.fromJson(el, com.hanif.smartstudy.data.model.ExamAppearanceRef::class.java) }
                    catch (e: Exception) { null }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchAllExamAppearances error: ${e.message}")
                null
            }
        }

    /** GAS `getReviewProgress`-এর ফলাফল — subject_id/topic_id ধরে {total, reviewed} */
    data class ReviewCount(val total: Int, val reviewed: Int) {
        val pct: Int get() = if (total > 0) (reviewed * 100) / total else 0
    }
    data class ReviewProgress(
        val subjects: Map<String, ReviewCount> = emptyMap(),
        val topics: Map<String, ReviewCount> = emptyMap()
    )

    /**
     * Review System (Admin-only) — GAS `getReviewProgress` কল করে একটা sheet-এর প্রতিটা
     * subject_id/topic_id-এ মোট প্রশ্ন ও তার কতগুলো reviewed, হালকা অ্যাগ্রিগেট আকারে
     * (পুরো প্রশ্ন ডেটা না)। SubjectListScreen/SubTopicListScreen-এ progress bar দেখানোর জন্য।
     */
    suspend fun fetchReviewProgress(sheet: String): ReviewProgress = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext ReviewProgress()
        try {
            val url = "$BASE_URL?action=getReviewProgress&sheet=${enc(sheet)}&secret=${enc(SECRET)}"
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (!resp.isSuccessful || body.isBlank()) return@withContext ReviewProgress()
            val obj = JsonParser.parseString(body).asJsonObject
            if (obj.get("status")?.asString != "success") {
                Log.w(TAG, "fetchReviewProgress non-success: ${body.take(150)}")
                return@withContext ReviewProgress()
            }
            fun parseMap(el: com.google.gson.JsonElement?): Map<String, ReviewCount> {
                if (el == null || !el.isJsonObject) return emptyMap()
                val out = mutableMapOf<String, ReviewCount>()
                el.asJsonObject.entrySet().forEach { (key, v) ->
                    if (v.isJsonObject) {
                        val o = v.asJsonObject
                        val total = o.get("total")?.asInt ?: 0
                        val reviewed = o.get("reviewed")?.asInt ?: 0
                        out[key] = ReviewCount(total, reviewed)
                    }
                }
                return out
            }
            ReviewProgress(
                subjects = parseMap(obj.get("subjects")),
                topics   = parseMap(obj.get("topics"))
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchReviewProgress error: ${e.message}")
            ReviewProgress()
        }
    }

    // ══════════════════════════════════════════════════════════
    // WRITE (admin)
    // ══════════════════════════════════════════════════════════

    // Android-side field key → GAS/Sheet কলাম নাম। বেশিরভাগ field-এর নাম দুই দিকেই একই
    // (subject, sub_topic, question, correct, explanation, technique) — শুধু এই কয়েকটাতে
    // AdminPage/FirebaseDataService-এর Firebase-style key আলাদা কেসিং/নাম ব্যবহার করে।
    private val ANDROID_TO_GAS_FIELD = mapOf(
        "option1"      to "opt1",
        "option2"      to "opt2",
        "option3"      to "opt3",
        "option4"      to "opt4",
        "AudienceTags" to "audienceTags",
        "type"         to "qType"
    )

    // GAS-এর Quiz/QBank/Study sheet schema-তে এই কলামগুলোর সরাসরি জায়গা নেই — পাঠালে
    // updateField "Column not found" error দেবে, তাই আগেই বাদ দেওয়া হয় (silent skip)
    private val UNSUPPORTED_FIELDS = setOf("explanationVisibility")

    private fun gasFieldName(androidKey: String): String = ANDROID_TO_GAS_FIELD[androidKey] ?: androidKey

    /** সাধারণ GET-action কল (updateField/deleteByIds/renameField) — response {result:"success"|"error"} */
    private fun callGetAction(params: Map<String, String>): Boolean {
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val url = "$BASE_URL?secret=${enc(SECRET)}&$query"
        return try {
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            val obj = JsonParser.parseString(body).asJsonObject
            (obj.get("result")?.asString == "success").also {
                if (!it) Log.w(TAG, "callGetAction failed: ${body.take(200)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "callGetAction error: ${e.message}")
            false
        }
    }

    /** একটামাত্র field PATCH করে (GAS doGet action=updateField) */
    private suspend fun updateSingleField(sheet: String, rowKey: String, field: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            callGetAction(
                mapOf(
                    "action"  to "updateField",
                    "sheet"   to sheet,
                    "id"      to rowKey,
                    "field"   to gasFieldName(field),
                    "content" to content
                )
            )
        }

    /**
     * adminEditQuestion()/adminSwapOptions() এর জন্য — Firebase-এর partial PATCH-এর সমতুল্য।
     * fields-এ যতগুলো key থাকে, ততগুলো আলাদা updateField কল প্যারালালি চলে (প্রতিটাই
     * আলাদা sheet column, তাই সমান্তরালে চালানো নিরাপদ)। Unsupported field গুলো skip হয়।
     */
    suspend fun updateFields(sheet: String, rowKey: String, fields: Map<String, String>): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val toSend = fields.filterKeys { it !in UNSUPPORTED_FIELDS }
            if (toSend.isEmpty()) return@withContext ApiResult.Success(Unit)
            try {
                val results = coroutineScope {
                    toSend.map { (k, v) -> async { updateSingleField(sheet, rowKey, k, v) } }.map { it.await() }
                }
                if (results.all { it }) ApiResult.Success(Unit)
                else ApiResult.Error("কিছু field আপডেট ব্যর্থ হয়েছে (Google Sheet)")
            } catch (e: Exception) {
                ApiResult.Error(e.message ?: "Network error")
            }
        }

    /** adminDeleteQuestion() এর জন্য — একটা id ডিলিট (deleteByIds কমা-সেপারেটেড, এখানে একটাই) */
    suspend fun deleteQuestion(sheet: String, rowKey: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val ok = callGetAction(mapOf("action" to "deleteByIds", "sheet" to sheet, "ids" to rowKey))
            if (ok) ApiResult.Success(Unit) else ApiResult.Error("Google Sheet থেকে ডিলিট ব্যর্থ হয়েছে")
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    /**
     * adminRenameSubjectOrTopic() এর জন্য — একাধিক sheet-এ subject/sub_topic rename।
     * GAS-এর renameField subject-স্কোপড না (দেখো ফাইলের ওপরের কমেন্ট) — oldSubTopic দেওয়া
     * থাকলে sub_topic কলামেই rename হয়, subject মিলিয়ে filter হয় না।
     */
    suspend fun renameSubjectOrTopic(
        sheets        : List<String>,
        oldSubject    : String,
        oldSubTopic   : String,
        newName       : String,
        renameSubTopic: Boolean
    ): ApiResult<Int> = withContext(Dispatchers.IO) {
        try {
            var successSheets = 0
            for (sheet in sheets) {
                val field  = if (renameSubTopic) "sub_topic" else "subject"
                val oldVal = if (renameSubTopic) oldSubTopic else oldSubject
                val ok = callGetAction(
                    mapOf("action" to "renameField", "sheet" to sheet, "field" to field, "oldVal" to oldVal, "newVal" to newName)
                )
                if (ok) successSheets++
            }
            if (successSheets == 0) ApiResult.Error("কোনো sheet-এ rename সফল হয়নি")
            else ApiResult.Success(successSheets)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Rename failed")
        }
    }

    /**
     * adminDeleteSubjectOrTopic()-এর Sheet-দিকের implementation — renameSubjectOrTopic-এর
     * মতোই কাজ করে, শুধু rename এর বদলে delete। প্রতিটা sheet-এ typed model (QuizItem/
     * QBankItem/StudyItem, যেগুলোতে id/subject/subTopic আছে) দিয়ে matching row-গুলোর id
     * বের করা হয়, তারপর GAS-এর existing "deleteByIds" action-এ একসাথে (comma-separated)
     * পাঠানো হয় — প্রতিটা id-র জন্য আলাদা কল না করে একটাই কল প্রতি sheet।
     */
    suspend fun deleteBySubjectOrTopic(
        sheets        : List<String>,
        subject       : String,
        subTopic      : String,   // ফাঁকা হলে পুরো subject ডিলিট (deleteSubTopic=false এর সময়)
        deleteSubTopic: Boolean
    ): ApiResult<Int> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext ApiResult.Error("Google Sheet মোড কনফিগার নেই")
        fun matches(s: String?, st: String?): Boolean {
            val sNorm  = normalizeFieldValue(s)
            val stNorm = normalizeFieldValue(st)
            return if (deleteSubTopic)
                sNorm.equals(normalizeFieldValue(subject), ignoreCase = true) && stNorm.equals(normalizeFieldValue(subTopic), ignoreCase = true)
            else
                sNorm.equals(normalizeFieldValue(subject), ignoreCase = true)
        }
        try {
            var totalDeleted = 0
            var anyMatch = false
            for (sheet in sheets) {
                val ids: List<String> = when (sheet) {
                    "Quiz"  -> fetchSheetRows<QuizItem>("Quiz").items
                        .filter { matches(it.subject, it.subTopic) }.mapNotNull { it.id }
                    "QBank" -> fetchSheetRows<QBankItem>("QBank").items
                        .filter { matches(it.subject, it.subTopic) }.mapNotNull { it.id }
                    "Study" -> fetchSheetRows<StudyItem>("Study").items
                        .filter { matches(it.subject, it.subTopic) }.mapNotNull { it.id }
                    else -> emptyList()
                }
                if (ids.isEmpty()) continue
                anyMatch = true
                val ok = callGetAction(mapOf("action" to "deleteByIds", "sheet" to sheet, "ids" to ids.joinToString(",")))
                if (ok) totalDeleted += ids.size else Log.w(TAG, "deleteBySubjectOrTopic: $sheet bulk delete ব্যর্থ")
            }
            when {
                !anyMatch        -> ApiResult.Error("কোনো matching প্রশ্ন Sheet-এ পাওয়া যায়নি")
                totalDeleted == 0-> ApiResult.Error("Sheet থেকে delete ব্যর্থ হয়েছে")
                else              -> ApiResult.Success(totalDeleted)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Sheet delete failed")
        }
    }

    /**
     * Google Sheet মোডে "Typing" ট্যাব — ঠিক Quiz/QBank/Study-এর মতোই getSheetRows
     * action দিয়ে Firebase বাইপাস করে সরাসরি sheet থেকে পড়ে (headers: id, language,
     * content, updatedAt)। ব্যর্থ হলে খালি লিস্ট রিটার্ন করে — caller (TypingPassageProvider)
     * তখন Room-এর পুরনো cache ব্যবহার করে। দেখো util/TypingPassageProvider.kt।
     */
    suspend fun fetchTypingPassages(): List<com.hanif.smartstudy.data.model.TypingSheetPassage> {
        if (!isConfigured()) return emptyList()
        val result = fetchSheetRows<com.hanif.smartstudy.data.model.TypingSheetPassage>("Typing")
        if (result.error != null) Log.w(TAG, "fetchTypingPassages: ${result.error}")
        return result.items
    }

    /**
     * fetchUser() (RemoteServices.kt) Firebase quota/permission-এ ব্যর্থ হলে fallback —
     * GAS "getSheetRows" (tab=Users) দিয়ে সরাসরি Google Sheet থেকে ওই phone-এর row খুঁজে
     * User বানায় (role/status/xp সহ)। এতে Firebase read-quota শেষ হয়ে গেলেও admin
     * নিজের role/admin-menu হারায় না — Sheet-ই ব্যাকআপ সোর্স হিসেবে কাজ করে।
     * GAS_URL/GAS_SECRET সেট না থাকলে (isConfigured()==false) চুপচাপ null রিটার্ন করে।
     */
    suspend fun fetchUserFromSheet(phone: String): com.hanif.smartstudy.data.model.User? =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext null
            try {
                val url = "$BASE_URL?action=getSheetRows&tab=Users&secret=${enc(SECRET)}"
                val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
                val body = resp.body?.string() ?: ""
                resp.close()
                if (!resp.isSuccessful || body.isBlank()) return@withContext null
                val obj = JsonParser.parseString(body).asJsonObject
                if (obj.get("status")?.asString != "success") {
                    Log.w(TAG, "fetchUserFromSheet non-success: ${body.take(150)}")
                    return@withContext null
                }
                val rows = obj.getAsJsonArray("rows") ?: return@withContext null
                val cleanPhone = phone.trim()
                for (el in rows) {
                    if (!el.isJsonObject) continue
                    val o = el.asJsonObject
                    val p = (o.get("Phone")?.takeIf { !it.isJsonNull }?.asString
                        ?: o.get("phone")?.takeIf { !it.isJsonNull }?.asString)?.trim()
                    if (p == cleanPhone) {
                        @Suppress("UNCHECKED_CAST")
                        val map = plainGson.fromJson(o, Map::class.java) as? Map<String, Any> ?: continue
                        Log.d(TAG, "fetchUserFromSheet: found $cleanPhone via Sheet fallback")
                        return@withContext com.hanif.smartstudy.data.model.User.fromFirebaseMap(map)
                    }
                }
                Log.w(TAG, "fetchUserFromSheet: $cleanPhone not found in Sheet either")
                null
            } catch (e: Exception) {
                Log.e(TAG, "fetchUserFromSheet error: ${e.message}")
                null
            }
        }

    /**
     * adminAddQuestion() এর জন্য — নতুন প্রশ্ন POST দিয়ে GAS-এর জেনেরিক row-upsert
     * endpoint-এ পাঠানো হয় (editId ছাড়া → নতুন row হিসেবে appendRow হয়)। GAS নিজেই
     * নতুন sequential id বানিয়ে response-এ ফেরত দেয় — সেটাই rowKey হিসেবে ব্যবহার হবে।
     */
    suspend fun addQuestion(sheet: String, fields: Map<String, String>): ApiResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val params = mutableMapOf<String, Any>("secret" to SECRET, "targetTab" to sheet)
                fields.filterKeys { it !in UNSUPPORTED_FIELDS }
                    .forEach { (k, v) -> params[gasFieldName(k)] = v }
                val body = plainGson.toJson(params).toRequestBody(JSON_MT)
                val resp = client.newCall(Request.Builder().url(BASE_URL).post(body).build()).execute()
                val respBody = resp.body?.string() ?: ""
                resp.close()
                val obj = JsonParser.parseString(respBody).asJsonObject
                if (obj.get("result")?.asString == "success") {
                    ApiResult.Success(obj.get("id")?.asString ?: "")
                } else {
                    ApiResult.Error(obj.get("error")?.asString ?: "Google Sheet-এ যোগ করা ব্যর্থ হয়েছে")
                }
            } catch (e: Exception) {
                ApiResult.Error(e.message ?: "Network error")
            }
        }
}
