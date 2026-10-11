package com.hanif.smartstudy.util

import android.util.Log
import com.hanif.smartstudy.data.model.AiApiKeys
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
 * টেকনিক-লিংকের "✨ AI দিয়ে খুঁজুন" বাটনের জন্য ছোট AI ক্লায়েন্ট।
 * Settings-এ সেভ করা key-ই ব্যবহার হয়: Groq → Mistral → Cerebras → Gemini ক্রমে, একটা ব্যর্থ হলে পরেরটা।
 * কখনো নিজে থেকে চলে না — শুধু Admin বাটন চাপলে TechniqueSuggester.aiSuggest() এটা ডাকে।
 * (WrittenAnswerAiService-এ হাত দেওয়া হয়নি, যাতে গ্রেডিং/ব্যাখ্যা ফিচার অক্ষত থাকে)
 */
object TechniqueAiClient {
    private const val TAG = "TechniqueAi"
    private val JSON_MT = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)      // ২০টা প্রশ্নের তালিকা বিচার — একটু সময় লাগে
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** AI-র কাঁচা টেক্সট; সব প্রোভাইডার ব্যর্থ হলে বা key না থাকলে null */
    suspend fun ask(keys: AiApiKeys, prompt: String, maxTokens: Int = 1500): String? = withContext(Dispatchers.IO) {
        if (keys.groq.isNotBlank()) {
            runCatching { openAi("https://api.groq.com/openai/v1/chat/completions", keys.groq, keys.groqModel, prompt, maxTokens) }
                .onFailure { Log.w(TAG, "Groq: ${it.message}") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        if (keys.mistral.isNotBlank()) {
            runCatching { openAi("https://api.mistral.ai/v1/chat/completions", keys.mistral, keys.mistralModel, prompt, maxTokens) }
                .onFailure { Log.w(TAG, "Mistral: ${it.message}") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        if (keys.cerebras.isNotBlank()) {
            runCatching { openAi("https://api.cerebras.ai/v1/chat/completions", keys.cerebras, keys.cerebrasModel, prompt, maxTokens) }
                .onFailure { Log.w(TAG, "Cerebras: ${it.message}") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        if (keys.gemini.isNotBlank()) {
            runCatching { gemini(keys.gemini, keys.geminiModel, prompt, maxTokens) }
                .onFailure { Log.w(TAG, "Gemini: ${it.message}") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        null
    }

    private fun openAi(url: String, apiKey: String, model: String, prompt: String, maxTokens: Int): String? {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().apply { put("role", "user"); put("content", prompt) }))
            put("temperature", 0.1)
            put("max_tokens", maxTokens)
        }
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $apiKey").addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MT)).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val txt = resp.body?.string() ?: return null
            return JSONObject(txt).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")?.trim()
        }
    }

    private fun gemini(apiKey: String, model: String, prompt: String, maxTokens: Int): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            put("generationConfig", JSONObject().apply { put("temperature", 0.1); put("maxOutputTokens", maxTokens) })
        }
        val req = Request.Builder().url(url).addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MT)).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val txt = resp.body?.string() ?: return null
            return JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?.trim()
        }
    }

    /** AI-র উত্তর থেকে প্রথম JSON অংশ ({…} বা […]) আলাদা করে — ```json বেড়া থাকলেও চলে */
    fun extractJson(raw: String): String? {
        val t = raw.replace("```json", "").replace("```", "").trim()
        val s = t.indexOfFirst { it == '{' || it == '[' }
        if (s < 0) return null
        val close = if (t[s] == '{') '}' else ']'
        val e = t.lastIndexOf(close)
        return if (e > s) t.substring(s, e + 1) else null
    }
}
