package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.JsonArray
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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = CaseInsensitiveGson.instance

    private val BASE_URL get() = BuildConfig.FIREBASE_URL.trimEnd('/')
    private val SECRET_KEY get() = BuildConfig.SECRET_KEY

    private fun authParam(): String {
        val key = SECRET_KEY
        return if (key.isNotBlank() && !key.contains("%%")) "?auth=$key" else ""
    }

    suspend fun fetchAllContent(): ContentResult<AppContent> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== FETCH START ===")
        Log.d(TAG, "BASE_URL: ${BASE_URL.take(50)}")
        Log.d(TAG, "SECRET_KEY: ${if (SECRET_KEY.contains("%%")) "NOT SET" else "set(${SECRET_KEY.length})"}")

        try {
            // আলাদা আলাদে fetch — root fetch too large হয়
            val quiz  = fetchSheet<QuizItem>("Quiz")
            val qbank = fetchSheet<QBankItem>("QBank")
            val study = fetchSheet<StudyItem>("Study")

            Log.d(TAG, "=== RESULT: Quiz=${quiz.size} QBank=${qbank.size} Study=${study.size} ===")

            if (quiz.isEmpty() && qbank.isEmpty() && study.isEmpty()) {
                ContentResult.Error("Firebase থেকে data আসেনি (সব empty)")
            } else {
                ContentResult.Success(AppContent(
                    quiz = quiz, qbank = qbank, study = study,
                    fetchedAt = System.currentTimeMillis()
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllContent error: ${e.message}", e)
            ContentResult.Error("Error: ${e.message}")
        }
    }

    private suspend inline fun <reified T> fetchSheet(sheet: String): List<T> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/$sheet.json${authParam()}"
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
                    // Array format: [0: {...}, 1: {...}]
                    trimmed.startsWith("[") -> {
                        val type = object : TypeToken<List<T>>() {}.type
                        gson.fromJson<List<T>>(trimmed, type) ?: emptyList()
                    }
                    // Object format: {"0": {...}, "1": {...}} or {"-key": {...}}
                    trimmed.startsWith("{") -> {
                        val obj = gson.fromJson(trimmed, JsonObject::class.java)
                        // "error" response চেক করো
                        if (obj.has("error")) {
                            Log.e(TAG, "$sheet Firebase error: ${obj.get("error")}")
                            return@withContext emptyList()
                        }
                        obj.entrySet()
                            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
                            .mapNotNull { (_, v) ->
                                try {
                                    if (v.isJsonObject) gson.fromJson(v, T::class.java) else null
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
