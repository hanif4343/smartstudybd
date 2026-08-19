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
 * CDN Worker (Cloudflare, private-GitHub প্রক্সি) থেকে প্রশ্ন-কনটেন্ট পড়ার
 * সার্ভিস — GAS_CDN_PLANNING.md-এর Phase ২-৩ (Worker + Publish pipeline)
 * অনুযায়ী। এটাই read path-এর নতুন প্রাইমারি সোর্স হবে; GAS `getQuestionsPage`
 * fallback হিসেবে থেকে যায় (দেখো ContentRepository.cacheNextTopicBatch —
 * CDN ব্যর্থ হলে/কনফিগার না থাকলে সাইলেন্টলি সেই পুরনো পাথে চলে যায়)।
 *
 * ⚠️ BuildConfig-এ CDN_WORKER_URL/CDN_APP_SECRET সেট করা না থাকলে (এখনো
 * টেস্ট/deploy না হওয়া অবস্থায়) isConfigured()=false, পুরো ক্লাস silently
 * no-op — অ্যাপ ভাঙবে না, শুধু GAS পাথই ব্যবহার হবে।
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

    // ⚠️ app/build.gradle-এ buildConfigField হিসেবে যোগ করা হয়েছে (GAS_URL/
    // GAS_SECRET-এর একই secretField() প্যাটার্নে) — env var সেট না থাকলে খালি
    // স্ট্রিং, isConfigured()=false, GAS fallback পাথেই চলবে।
    private val WORKER_URL: String by lazy { BuildConfig.CDN_WORKER_URL.trimEnd('/') }
    private val APP_SECRET: String by lazy { BuildConfig.CDN_APP_SECRET }

    @PublishedApi internal val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @PublishedApi internal val gson = CaseInsensitiveGson.instance

    fun isConfigured(): Boolean = WORKER_URL.isNotBlank() && APP_SECRET.isNotBlank()

    data class TopicManifestEntry(
        val subject  : String? = null,
        val subTopic : String? = null,
        val count    : Int = 0,
        val hash     : String? = null
    )

    data class Manifest(
        val version       : Int = 0,
        val schemaVersion : Int = 1,
        val publishedAt   : Long = 0L,
        val topics        : Map<String, TopicManifestEntry> = emptyMap()
    )

    @PublishedApi internal fun requestBuilder(path: String): Request.Builder =
        Request.Builder().url("$WORKER_URL$path").header("X-App-Secret", APP_SECRET)

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
                Log.w(TAG, "fetchManifest: HTTP ${resp.code}")
                return@withContext null
            }
            gson.fromJson(body, Manifest::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "fetchManifest error: ${e.message}")
            null
        }
    }

    /**
     * একটা Topic-এর পুরো JSON ফাইল — sheetPath: "quiz"|"qbank"|"study", hash
     * manifest থেকে পাওয়া (cache-busting query param — Worker-এর immutable
     * forever-cache লজিক এটার ওপর নির্ভর করে, দেখো worker.js)।
     * @return null মানে fetch ব্যর্থ (network/parse) — caller GAS-এ fallback করবে
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
                    Log.w(TAG, "fetchTopicJson<$sheetPath/$topicId>: HTTP ${resp.code}")
                    return@withContext null
                }
                val arr = JsonParser.parseString(body).asJsonArray
                arr.mapNotNull { el ->
                    try { if (el.isJsonObject) gson.fromJson(el, T::class.java) else null }
                    catch (e: Exception) { null }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchTopicJson<$sheetPath/$topicId> error: ${e.message}")
                null
            }
        }
}
