package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.data.model.QBankItem
import com.hanif.smartstudy.data.model.QuizItem
import com.hanif.smartstudy.data.model.StudyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ContentFetchService {

    private val TAG = "ContentFetch"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Firebase RTDB base URL — BuildConfig থেকে
    private val FIREBASE_URL get() = BuildConfig.FIREBASE_URL
    // GAS URL (শীটের data GAS দিয়েও আনা যায়)
    private val GAS_URL get() = BuildConfig.GAS_URL

    // ── তিনটি sheet একসাথে parallel fetch ──
    suspend fun fetchAllContent(): ContentResult<AppContent> = coroutineScope {
        try {
            val studyDeferred = async { fetchSheet<StudyItem>("Study") }
            val quizDeferred  = async { fetchSheet<QuizItem>("Quiz") }
            val qbankDeferred = async { fetchSheet<QBankItem>("QBank") }

            val studyResult = studyDeferred.await()
            val quizResult  = quizDeferred.await()
            val qbankResult = qbankDeferred.await()

            // সব সফল না হলেও partial data দেব — offline-এ কাজ করা যাবে
            val study = (studyResult as? ContentResult.Success)?.data ?: emptyList()
            val quiz  = (quizResult  as? ContentResult.Success)?.data ?: emptyList()
            val qbank = (qbankResult as? ContentResult.Success)?.data ?: emptyList()

            if (study.isEmpty() && quiz.isEmpty() && qbank.isEmpty()) {
                val err = listOfNotNull(
                    (studyResult as? ContentResult.Error)?.message,
                    (quizResult  as? ContentResult.Error)?.message,
                    (qbankResult as? ContentResult.Error)?.message
                ).firstOrNull() ?: "ডেটা পাওয়া যায়নি"
                ContentResult.Error(err)
            } else {
                Log.d(TAG, "Fetched: Study=${study.size}, Quiz=${quiz.size}, QBank=${qbank.size}")
                ContentResult.Success(AppContent(
                    study     = study,
                    quiz      = quiz,
                    qbank     = qbank,
                    fetchedAt = System.currentTimeMillis()
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllContent error: ${e.message}")
            ContentResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    // ── একটি sheet fetch — Firebase RTDB .json endpoint ──
    private suspend inline fun <reified T> fetchSheet(sheetName: String): ContentResult<List<T>> =
        withContext(Dispatchers.IO) {
            try {
                // Firebase RTDB: /SheetName.json
                val url = "${FIREBASE_URL.trimEnd('/')}/$sheetName.json"
                Log.d(TAG, "Fetching $sheetName from $url")

                val req  = Request.Builder().url(url).get().build()
                val body = client.newCall(req).execute().body?.string() ?: ""

                if (body.isBlank() || body == "null") {
                    Log.w(TAG, "$sheetName is empty/null — trying GAS fallback")
                    return@withContext fetchSheetViaGas<T>(sheetName)
                }

                parseSheetJson<T>(body, sheetName)
            } catch (e: Exception) {
                Log.e(TAG, "$sheetName fetch error: ${e.message}")
                // GAS fallback
                try { fetchSheetViaGas<T>(sheetName) }
                catch (e2: Exception) { ContentResult.Error(e2.message ?: "Error") }
            }
        }

    // ── GAS fallback — ?action=getData&sheet=Study ──
    private suspend inline fun <reified T> fetchSheetViaGas(sheetName: String): ContentResult<List<T>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$GAS_URL?action=getData&sheet=$sheetName"
                Log.d(TAG, "GAS fallback for $sheetName: $url")
                val req  = Request.Builder().url(url).get().build()
                val body = client.newCall(req).execute().body?.string() ?: ""
                parseSheetJson<T>(body, sheetName)
            } catch (e: Exception) {
                ContentResult.Error("$sheetName: ${e.message}")
            }
        }

    // ── JSON parse — Firebase RTDB দুটো format দিতে পারে ──
    // Format 1: {"key1": {row}, "key2": {row}} — RTDB push keys সহ object map
    // Format 2: [{row}, {row}] — array
    private inline fun <reified T> parseSheetJson(body: String, sheetName: String): ContentResult<List<T>> {
        return try {
            val trimmed = body.trim()
            val list: List<T> = when {
                trimmed.startsWith("[") -> {
                    // Array format
                    val type = object : TypeToken<List<T>>() {}.type
                    gson.fromJson(trimmed, type) ?: emptyList()
                }
                trimmed.startsWith("{") -> {
                    // Object/map format — values নাও
                    val type = object : TypeToken<Map<String, T>>() {}.type
                    val map: Map<String, T> = gson.fromJson(trimmed, type) ?: emptyMap()
                    map.values.toList()
                }
                else -> emptyList()
            }
            Log.d(TAG, "$sheetName parsed: ${list.size} items")
            ContentResult.Success(list)
        } catch (e: Exception) {
            Log.e(TAG, "$sheetName parse error: ${e.message}")
            ContentResult.Error("$sheetName parse করা যায়নি: ${e.message}")
        }
    }
}

// ── Result wrapper ──
sealed class ContentResult<out T> {
    data class Success<T>(val data: T) : ContentResult<T>()
    data class Error(val message: String) : ContentResult<Nothing>()
}
