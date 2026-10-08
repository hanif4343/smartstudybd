package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.JsonParser
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.CaseInsensitiveGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * ══════════════════════════════════════════════════════════════════════════
 * CDN Worker (Cloudflare, private-GitHub প্রক্সি) থেকে সব read (প্রশ্ন-কনটেন্ট +
 * reference data + exam-appearances) পড়ার সার্ভিস।
 *
 * FIX (Speed Plan): "Gas diye kuno read noy — never" সিদ্ধান্ত অনুযায়ী CDN-ই
 * একমাত্র read সোর্স, GAS আর read fallback হিসেবে ব্যবহার হয় না। CDN
 * fetch ব্যর্থ হলে (network/timeout/৪xx/৫xx/misconfigured) ContentRepository
 * সরাসরি Room cache থেকে দেখায় + local notification দেখায় (দেখো
 * ContentRepository.notifyCdnFailure) — GAS-এ কোনো retry হয় না। GAS শুধু
 * write path-এ (add/edit/delete/move/reorder) ব্যবহার হয়, সেটার জন্য
 * GasContentService আলাদাই থাকে।
 * ══════════════════════════════════════════════════════════════════════════
 */
object CdnService {
    // ── Kotlin visibility নিয়ম: `suspend inline fun <reified T>` (নিচে
    // fetchTopicJson) পাবলিক inline ফাংশন — inline ফাংশনের বাইটকোড call-site-এ
    // কপি হয়ে যায় বলে ভিতরে `private` মেম্বার ব্যবহার করা যায় না (Kotlin
    // কম্পাইলার এরর দেয়: "Public-API inline function cannot access non-public-
    // API")। তাই এই ৪টা মেম্বার `private`-এর বদলে `@PublishedApi internal` —
    // এতে module-এর বাইরে এক্সপোজ হয় না (কার্যত private-ই থাকে), কিন্তু
    // inline ফাংশন থেকে ব্যবহার করা যায়। ──
    @PublishedApi internal const val TAG = "CdnService"

    // ── TEMP DIAGNOSTIC (root-cause খোঁজার জন্য সাময়িক) — শেষ CDN fetch ব্যর্থ
    // হলে কী কারণে হলো (HTTP কোড / exception) সেটা এখানে রাখা হয়, যাতে
    // ContentRepository notification-এর গায়েই দেখাতে পারে। সমস্যা ধরা পড়ে
    // গেলে এই ভ্যারিয়েবল আর এটাকে সেট করা লাইনগুলো ফেরত মুছে ফেলা উচিত। ──
    @PublishedApi internal var lastDiag: String = "none"

    // ⚠️ app/build.gradle-এ buildConfigField হিসেবে যোগ করা হয়েছে (GAS_URL/
    // GAS_SECRET-এর একই secretField() প্যাটার্নে) — env var সেট না থাকলে খালি
    // স্ট্রিং, isConfigured()=false, GAS fallback পাথেই চলবে।
    private val WORKER_URL: String by lazy { BuildConfig.CDN_WORKER_URL.trimEnd('/') }
    private val APP_SECRET: String by lazy { BuildConfig.CDN_APP_SECRET }

    // ── Admin bypass (KV rate-limit শুধু owner-এর জন্য স্কিপ) ──
    // ADMIN_BYPASS বিল্ড-টাইম secret, শুধু Worker আর এই app-এর মধ্যে শেয়ার করা —
    // কিন্তু হেডারে পাঠানো হয় শুধুমাত্র বর্তমানে লগইন-করা account সত্যিই admin হলে
    // (isAdminSession, SessionManager.saveUser/clearUser থেকে runtime-এ সেট হয়)।
    // তাই শুধু build secret জানলেই bypass হয় না — সাথে সার্ভার-ভেরিফায়েড admin
    // account দিয়ে লগইন থাকাও লাগে। অন্য সব ইউজারের জন্য normal per-IP KV
    // rate limiting আগের মতোই চলবে (দেখো worker.js-এর X-Admin-Bypass চেক)।
    private val ADMIN_BYPASS: String by lazy { BuildConfig.CDN_ADMIN_BYPASS }
    @Volatile var isAdminSession: Boolean = false

    // FIX (স্লো-অ্যাপ ডায়াগনসিস): আগে connectTimeout=10s/readTimeout=15s ছিল —
    // Worker misconfigured/unreachable হলে প্রতিটা CDN কল ~২৫ সেকেন্ড পর্যন্ত
    // আটকে থাকত (তারপর Room fallback)। এখন ৫s/৬s — misconfigured অবস্থায়ও
    // দ্রুত fail করে Room cache-এ চলে যাবে, ব্যবহারকারীকে দীর্ঘ hang সহ্য
    // করতে হবে না। Worker সত্যিই ঠিকঠাক থাকলে এই কমানো টাইমআউটে কোনো সমস্যা
    // হবে না (CDN response সাধারণত < ১ সেকেন্ডে আসার কথা)।
    @PublishedApi internal val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    @PublishedApi internal val gson = CaseInsensitiveGson.instance

    fun isConfigured(): Boolean = WORKER_URL.isNotBlank() && APP_SECRET.isNotBlank()

    /** 🆕 Unified topic: একই topic_id Quiz/QBank/Study তিন sheet-এই থাকতে পারে — manifest-এ
     *  `sheets{quiz,qbank,study}` আলাদা count/hash থাকে (পুরনো ফরম্যাটে এই ফিল্ড থাকে না → null)। */
    data class SheetManifestEntry(
        val count : Int = 0,
        val hash  : String? = null
    )

    data class TopicManifestEntry(
        val subject  : String? = null,
        val subTopic : String? = null,
        val count    : Int = 0,     // সব sheet-এর যোগফল (পুরনো ক্লায়েন্টের জন্য)
        val hash     : String? = null, // সব sheet-এর সমন্বিত hash
        val sheets   : Map<String, SheetManifestEntry>? = null
    )

    data class Manifest(
        val version       : Int = 0,
        val schemaVersion : Int = 1,
        val publishedAt   : Long = 0L,
        val topics        : Map<String, TopicManifestEntry> = emptyMap(),
        // FIX (Speed Plan Task 2/3): প্রতিটা subject-এর মোট প্রশ্নসংখ্যা এখন
        // manifest-এই আগে থেকে আসে (GAS doPublish_-এ যোগ করা হয়েছে) — Subject
        // list-এ প্রতিটা topic আলাদা করে না ডাউনলোড করেই instant "মোট প্রশ্ন"
        // দেখানো যায় (আগে এটা hardcoded 0 থাকত)।
        val subjectTotals : Map<String, Int> = emptyMap()
    )

    @PublishedApi internal fun requestBuilder(path: String): Request.Builder {
        val b = Request.Builder().url("$WORKER_URL$path").header("X-App-Secret", APP_SECRET)
        if (isAdminSession && ADMIN_BYPASS.isNotBlank()) {
            b.header("X-Admin-Bypass", ADMIN_BYPASS)
        }
        return b
    }

    /** manifest.json — no-cache (Worker-সাইডে সবসময় fresh), তাই এখানেও কোনো
     *  local caching নেই — caller (ContentRepository) ৫-মিনিট TTL দিয়ে
     *  in-memory cache রাখে (App খোলা/periodic/pull-to-refresh নীতি অনুযায়ী)। */
    suspend fun fetchManifest(): Manifest? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        try {
            val resp = client.newCall(requestBuilder("/manifest.json").get().build()).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (!resp.isSuccessful || body.isBlank()) {
                lastDiag = "HTTP_${resp.code}"
                Log.w(TAG, "fetchManifest: HTTP ${resp.code}")
                return@withContext null
            }
            gson.fromJson(body, Manifest::class.java)
        } catch (e: Exception) {
            lastDiag = "EXC_${e.javaClass.simpleName}_${e.message}"
            Log.w(TAG, "fetchManifest error: ${e.message}")
            null
        }
    }

    /**
     * ── FIX (Speed Plan Task 3): reference ডেটা (subjects/topics/tags/posts/
     * institutions) এবং বাল্ক exam-appearances — এখন সব CDN থেকেই আসে, GAS
     * `getReferenceData`/`getAllExamAppearances` আর read-path-এ ব্যবহার হয় না।
     * এই ফাইলগুলো manifest-এর মতো cache-busting hash query লাগে না (রেফারেন্স
     * ডেটা প্রতিবার publish-এ আপডেট হয়, Worker-সাইডে ছোট TTL cache যথেষ্ট) — তাই
     * সরাসরি ফাইলনাম দিয়ে GET করা হয়।
     * @return null মানে fetch ব্যর্থ (network/404/parse) — caller Room cache
     * থেকে দেখাবে, কোনো GAS fallback নেই।
     */
    suspend inline fun <reified T> fetchReferenceJson(fileName: String): List<T>? =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext null
            try {
                val resp = client.newCall(requestBuilder("/$fileName").get().build()).execute()
                val body = resp.body?.string() ?: ""
                resp.close()
                if (!resp.isSuccessful || body.isBlank()) {
                    lastDiag = "HTTP_${resp.code}"
                    Log.w(TAG, "fetchReferenceJson<$fileName>: HTTP ${resp.code}")
                    return@withContext null
                }
                val arr = JsonParser.parseString(body).asJsonArray
                arr.mapNotNull { el ->
                    try { if (el.isJsonObject) gson.fromJson(el, T::class.java) else null }
                    catch (e: Exception) { null }
                }
            } catch (e: Exception) {
                lastDiag = "EXC_${e.javaClass.simpleName}_${e.message}"
                Log.w(TAG, "fetchReferenceJson<$fileName> error: ${e.message}")
                null
            }
        }

    /**
     * একটা Topic-এর পুরো JSON ফাইল — sheetPath: "quiz"|"qbank"|"study", hash
     * manifest থেকে পাওয়া (cache-busting query param — Worker-এর immutable
     * forever-cache লজিক এটার ওপর নির্ভর করে, দেখো worker.js)।
     * @return null মানে fetch ব্যর্থ (network/parse) — caller-কে Room cache
     * থেকে দেখাতে হবে, কোনো GAS fallback নেই (Read path পুরোপুরি CDN-only)।
     */
    suspend inline fun <reified T> fetchTopicJson(sheetPath: String, topicId: String, hash: String): List<T>? =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext null
            try {
                val url = "/$sheetPath/$topicId.json?v=${hash}"
                val resp = client.newCall(requestBuilder(url).get().build()).execute()
                val body = resp.body?.string() ?: ""
                resp.close()
                if (!resp.isSuccessful || body.isBlank()) {
                    lastDiag = "HTTP_${resp.code}"
                    Log.w(TAG, "fetchTopicJson<$sheetPath/$topicId>: HTTP ${resp.code}")
                    return@withContext null
                }
                val arr = JsonParser.parseString(body).asJsonArray
                arr.mapNotNull { el ->
                    try { if (el.isJsonObject) gson.fromJson(el, T::class.java) else null }
                    catch (e: Exception) { null }
                }
            } catch (e: Exception) {
                lastDiag = "EXC_${e.javaClass.simpleName}_${e.message}"
                Log.w(TAG, "fetchTopicJson<$sheetPath/$topicId> error: ${e.message}")
                null
            }
        }
}
