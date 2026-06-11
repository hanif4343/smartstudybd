package com.hanif.smartstudy.data.remote

import android.util.Log
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

    /** Firebase Auth ID Token দিয়ে secure auth param */
    private suspend fun authParam(): String {
        val token = FirebaseTokenProvider.getToken()
        return if (token.isNotBlank()) "?auth=$token" else ""
    }

    suspend fun fetchAllContent(): ContentResult<AppContent> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== FETCH START ===")
        Log.d(TAG, "BASE_URL: ${BASE_URL.take(50)}")

        try {
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
                        val type = object : TypeToken<List<T>>() {}.type
                        gson.fromJson<List<T>>(trimmed, type) ?: emptyList()
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
                                        // Firebase push key (যেমন -NxAbc123) টা "id" হিসেবে inject করো
                                        // এটা ছাড়া admin edit এ correct Firebase path পাওয়া যায় না
                                        val obj2 = v.asJsonObject.deepCopy()
                                        if (!obj2.has("id") || obj2.get("id").asString.isNullOrBlank()) {
                                            obj2.addProperty("id", key)
                                        }
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
