package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val gson = CaseInsensitiveGson.instance

    private val FIREBASE_URL get() = BuildConfig.FIREBASE_URL.trimEnd('/')
    private val SECRET_KEY   get() = BuildConfig.SECRET_KEY
    private val GAS_URL      get() = BuildConfig.GAS_URL

    // Validate config — কোনো placeholder থাকলে log করো
    private fun logConfig() {
        val url = BuildConfig.FIREBASE_URL
        val key = BuildConfig.SECRET_KEY
        val gas = BuildConfig.GAS_URL
        Log.d(TAG, "=== CONFIG CHECK ===")
        Log.d(TAG, "FIREBASE_URL: ${if(url.contains("%%")) "❌ NOT INJECTED" else "✅ ${url.take(40)}"}")
        Log.d(TAG, "SECRET_KEY:   ${if(key.contains("%%")) "❌ NOT INJECTED" else "✅ set (${key.length} chars)"}")
        Log.d(TAG, "GAS_URL:      ${if(gas.contains("%%")) "❌ NOT INJECTED" else "✅ ${gas.take(40)}"}")
        Log.d(TAG, "===================")
    }

    private fun firebaseConfigured(): Boolean {
        val url = BuildConfig.FIREBASE_URL
        val key = BuildConfig.SECRET_KEY
        return !url.contains("%%") && url.isNotBlank() &&
               !key.contains("%%") && key.isNotBlank()
    }

    private fun gasConfigured(): Boolean {
        val gas = BuildConfig.GAS_URL
        return !gas.contains("%%") && gas.isNotBlank()
    }

    // ── একবারে সব fetch ──
    suspend fun fetchAllContent(): ContentResult<AppContent> = withContext(Dispatchers.IO) {
        logConfig()
        try {
            // Firebase try
            if (firebaseConfigured()) {
                val authParam = "?auth=$SECRET_KEY"
                val url = "$FIREBASE_URL.json$authParam"
                Log.d(TAG, "Fetching Firebase: ${url.take(60)}...")
                val req  = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                Log.d(TAG, "Firebase response: HTTP ${resp.code}, body length=${body.length}, preview=${body.take(100)}")

                if (body.isNotBlank() && body != "null" && !body.contains("error")) {
                    val result = parseRootJson(body)
                    if (result is ContentResult.Success && !result.data.isEmpty()) {
                        return@withContext result
                    }
                    Log.w(TAG, "Firebase parse empty — trying GAS")
                } else {
                    Log.w(TAG, "Firebase bad response: ${body.take(100)}")
                }
            } else {
                Log.w(TAG, "Firebase not configured — trying GAS directly")
            }

            // GAS fallback
            if (gasConfigured()) {
                return@withContext fetchViaGas()
            }

            ContentResult.Error("Firebase ও GAS দুটোই configured নেই। GitHub Secrets চেক করো।")
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllContent error: ${e.message}")
            if (gasConfigured()) {
                try { fetchViaGas() }
                catch (e2: Exception) { ContentResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}") }
            } else {
                ContentResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
            }
        }
    }

    // ── Root JSON parse: { "Study": [...], "Quiz": [...], "QBank": [...] } ──
    private fun parseRootJson(body: String): ContentResult<AppContent> {
        return try {
            val root = gson.fromJson(body, JsonObject::class.java)

            val study = parseSheetFromJson<StudyItem>(root, "Study")
            val quiz  = parseSheetFromJson<QuizItem>(root,  "Quiz")
            val qbank = parseSheetFromJson<QBankItem>(root, "QBank")

            Log.d(TAG, "Parsed — Study:${study.size} Quiz:${quiz.size} QBank:${qbank.size}")

            if (study.isEmpty() && quiz.isEmpty() && qbank.isEmpty()) {
                ContentResult.Error("ডেটা পাওয়া যায়নি — Firebase empty")
            } else {
                ContentResult.Success(AppContent(
                    study     = study,
                    quiz      = quiz,
                    qbank     = qbank,
                    fetchedAt = System.currentTimeMillis()
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseRootJson error: ${e.message}")
            ContentResult.Error("JSON parse error: ${e.message}")
        }
    }

    // ── একটা key থেকে List<T> বের করো (Array বা Object map দুটোই handle) ──
    private inline fun <reified T> parseSheetFromJson(root: JsonObject, key: String): List<T> {
        return try {
            val el = root.get(key) ?: return emptyList()
            when {
                el.isJsonArray -> {
                    val type = object : TypeToken<List<T>>() {}.type
                    gson.fromJson<List<T>>(el, type) ?: emptyList()
                }
                el.isJsonObject -> {
                    // Firebase push-key format: { "-abc": {...}, "-def": {...} }
                    val obj = el.asJsonObject
                    obj.entrySet().mapNotNull { (_, v) ->
                        try { gson.fromJson(v, T::class.java) } catch (e: Exception) { null }
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseSheetFromJson<$key>: ${e.message}")
            emptyList()
        }
    }

    // ── GAS fallback — আলাদাভাবে প্রতিটা sheet ──
    private suspend fun fetchViaGas(): ContentResult<AppContent> = withContext(Dispatchers.IO) {
        try {
            val study = fetchGasSheet<StudyItem>("Study")
            val quiz  = fetchGasSheet<QuizItem>("Quiz")
            val qbank = fetchGasSheet<QBankItem>("QBank")
            Log.d(TAG, "GAS — Study:${study.size} Quiz:${quiz.size} QBank:${qbank.size}")
            ContentResult.Success(AppContent(
                study = study, quiz = quiz, qbank = qbank,
                fetchedAt = System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            ContentResult.Error("GAS error: ${e.message}")
        }
    }

    private suspend inline fun <reified T> fetchGasSheet(sheet: String): List<T> =
        withContext(Dispatchers.IO) {
            try {
                val url  = "$GAS_URL?action=getData&sheet=$sheet"
                val req  = Request.Builder().url(url).get().build()
                val body = client.newCall(req).execute().body?.string() ?: ""
                if (body.isBlank() || body == "null") return@withContext emptyList()
                val trimmed = body.trim()
                when {
                    trimmed.startsWith("[") -> {
                        val type = object : TypeToken<List<T>>() {}.type
                        gson.fromJson<List<T>>(trimmed, type) ?: emptyList()
                    }
                    trimmed.startsWith("{") -> {
                        val root = gson.fromJson(trimmed, JsonObject::class.java)
                        // GAS sometimes returns { "data": [...] }
                        val arr = root.get("data") ?: root.get(sheet)
                        if (arr?.isJsonArray == true) {
                            val type = object : TypeToken<List<T>>() {}.type
                            gson.fromJson<List<T>>(arr, type) ?: emptyList()
                        } else emptyList()
                    }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchGasSheet<$sheet>: ${e.message}")
                emptyList()
            }
        }
}
