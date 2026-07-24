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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)   // Sheet read Firebase-এর চেয়ে ধীর হতে পারে
        .writeTimeout(20, TimeUnit.SECONDS)
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

    private suspend inline fun <reified T> fetchSheetRows(tab: String): List<T> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL?action=getSheetRows&tab=$tab&secret=${enc(SECRET)}"
                val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
                val body = resp.body?.string() ?: ""
                resp.close()
                if (body.isBlank()) return@withContext emptyList()
                val obj = JsonParser.parseString(body).asJsonObject
                if (obj.get("status")?.asString != "success") {
                    Log.w(TAG, "$tab getSheetRows non-success: ${body.take(200)}")
                    return@withContext emptyList()
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
                Log.e(TAG, "fetchSheetRows<$tab> error: ${e.message}")
                emptyList()
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
                val quiz  = quizD.await()
                val qbank = qbankD.await()
                val study = studyD.await()
                Log.d(TAG, "fetchAllContent: quiz=${quiz.size} qbank=${qbank.size} study=${study.size}")
                if (quiz.isEmpty() && qbank.isEmpty() && study.isEmpty()) {
                    ContentResult.Error("Google Sheet থেকে data আসেনি (সব empty)")
                } else {
                    ContentResult.Success(
                        AppContent(quiz = quiz, qbank = qbank, study = study, fetchedAt = System.currentTimeMillis())
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
