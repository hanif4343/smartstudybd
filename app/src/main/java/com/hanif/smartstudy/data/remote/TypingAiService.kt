package com.hanif.smartstudy.data.remote

import android.util.Log
import com.hanif.smartstudy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * টাইপিং AI Adaptive Session-এর জন্য real-time sentence-generation কল —
 * admin-app-এর cloudflare-worker/typing-sentence-worker.js endpoint-কে হিট করে।
 *
 * গুরুত্বপূর্ণ: এখানে কোনো AI provider key (Gemini/Groq/...) নেই — শুধু
 * TYPING_AI_ENDPOINT (URL) আর TYPING_AI_SECRET (shared token, আসল AI key না)।
 * আসল provider key গুলো শুধু Cloudflare Worker-এর secret store-এ থাকে,
 * main app বা তার কোনো ইউজারের কাছে কখনো পৌঁছায় না।
 */
object TypingAiService {

    private const val TAG = "TypingAI"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // টাইমআউট ইচ্ছাকৃতভাবে ছোট — এটা রিয়েল-টাইম সেশনের মাঝে কল হয়, তাই
    // দীর্ঘক্ষণ অপেক্ষা করানো যাবে না, দ্রুত fallback-এ চলে যাওয়াই ভালো
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    data class AiPassageResult(val passage: String, val provider: String)

    /** নেটওয়ার্ক/সার্ভার সমস্যা হলে null ফেরত দেয় — caller-এর দায়িত্ব fallback দেখানো */
    suspend fun generatePassage(
        weakWords : List<String>,
        language  : String,   // "bn" | "en"
        difficulty: String    // "easy" | "medium" | "hard"
    ): AiPassageResult? = withContext(Dispatchers.IO) {
        if (weakWords.isEmpty() || BuildConfig.TYPING_AI_ENDPOINT.isBlank()) return@withContext null
        try {
            val bodyJson = JSONObject().apply {
                put("weakWords", JSONArray(weakWords))
                put("language", language)
                put("difficulty", difficulty)
            }
            val request = Request.Builder()
                .url(BuildConfig.TYPING_AI_ENDPOINT)
                .addHeader("Authorization", "Bearer ${BuildConfig.TYPING_AI_SECRET}")
                .post(bodyJson.toString().toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val respText = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.w(TAG, "generatePassage HTTP ${response.code}: $respText")
                return@withContext null
            }
            val json = JSONObject(respText)
            val passage = json.optString("passage").trim()
            if (passage.length < 10) return@withContext null
            AiPassageResult(passage, json.optString("provider", "unknown"))
        } catch (e: Exception) {
            Log.w(TAG, "generatePassage failed: ${e.message}")
            null
        }
    }
}
