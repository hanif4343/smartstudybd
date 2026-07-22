package com.hanif.smartstudy.data.remote

import android.util.Log
import com.hanif.smartstudy.data.model.AiApiKeys
import com.hanif.smartstudy.data.model.AiChatMessage
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
 * ── AI Chat (ডাউট সলভার) — সাধারণ প্রশ্ন-উত্তর কথোপকথন ──
 *
 * [WrittenAnswerAiService]-এর মতোই Settings-এ সেভ করা key দিয়ে একই ক্রমে
 * চেষ্টা করা হয়: Groq → Mistral → Cerebras → Gemini (Gemini সবার শেষে,
 * কারণ এটা প্রায়ই ফেইল করে)। পার্থক্য শুধু — এখানে true/false ভার্ডিক্টের
 * বদলে AI-এর পূর্ণ টেক্সট রিপ্লাই ফেরত আসে, এবং প্রম্পটে পুরো কথোপকথনের
 * ইতিহাস (history) পাঠানো হয় যাতে AI আগের প্রসঙ্গ মনে রাখতে পারে।
 */
object AiChatService {

    private const val TAG = "AiChatService"

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    private const val SYSTEM_PROMPT = """
তুমি "SmartStudy AI" — বাংলাদেশের শিক্ষার্থীদের জন্য একজন বন্ধুত্বপূর্ণ, ধৈর্যশীল পড়াশোনার সহকারী।
শিক্ষার্থীরা তোমাকে পড়াশোনা সংক্রান্ত যেকোনো প্রশ্ন, ডাউট, কনসেপ্ট বোঝার অনুরোধ, রিভিশন টিপস ইত্যাদি জিজ্ঞেস করতে পারে।

নিয়মাবলী:
- সংক্ষিপ্ত, স্পষ্ট, সহজ ভাষায় উত্তর দাও (বাংলায়; প্রয়োজনে ইংরেজি টার্ম বন্ধনীতে দাও)।
- জটিল বিষয় হলে ধাপে ধাপে বুঝিয়ে বলো, সহজ উদাহরণ দাও।
- নিশ্চিত না হলে অনুমান করে ভুল তথ্য দিও না — স্পষ্টভাবে বলো যে নিশ্চিত না।
- পড়াশোনার সাথে সম্পর্কহীন ক্ষতিকর/স্পর্শকাতর অনুরোধে সাহায্য কোরো না, ভদ্রভাবে না বলো।
"""

    /**
     * @return AI-এর উত্তর টেক্সট, অথবা null যদি কোনো key সেভ করা না থাকে বা সব প্রোভাইডার ব্যর্থ হয়।
     * null এলে UI-তে "একটু পর আবার চেষ্টা করো / key চেক করো" বার্তা দেখানো হয়।
     */
    suspend fun sendMessage(
        history: List<AiChatMessage>,
        keys   : AiApiKeys
    ): String? = withContext(Dispatchers.IO) {
        if (!keys.hasAnyKey()) return@withContext null

        if (keys.groq.isNotBlank()) {
            runCatching {
                callOpenAiCompatible(
                    url     = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey  = keys.groq,
                    model   = "llama-3.3-70b-versatile",
                    history = history
                )
            }.onFailure { Log.w(TAG, "Groq failed: ${it.message}") }
                .getOrNull()?.let { return@withContext it }
        }

        if (keys.mistral.isNotBlank()) {
            runCatching {
                callOpenAiCompatible(
                    url     = "https://api.mistral.ai/v1/chat/completions",
                    apiKey  = keys.mistral,
                    model   = "mistral-small-latest",
                    history = history
                )
            }.onFailure { Log.w(TAG, "Mistral failed: ${it.message}") }
                .getOrNull()?.let { return@withContext it }
        }

        if (keys.cerebras.isNotBlank()) {
            runCatching {
                callOpenAiCompatible(
                    url     = "https://api.cerebras.ai/v1/chat/completions",
                    apiKey  = keys.cerebras,
                    model   = "llama-3.3-70b",
                    history = history
                )
            }.onFailure { Log.w(TAG, "Cerebras failed: ${it.message}") }
                .getOrNull()?.let { return@withContext it }
        }

        // ── Gemini সবার শেষে — এটা প্রায়ই ফেইল করে (free-tier rate limit/region ইস্যু) ──
        if (keys.gemini.isNotBlank()) {
            runCatching { callGemini(keys.gemini, history) }
                .onFailure { Log.w(TAG, "Gemini failed: ${it.message}") }
                .getOrNull()?.let { return@withContext it }
        }

        null
    }

    // ── Groq / Mistral / Cerebras — তিনটাই OpenAI-compatible chat completions ফরম্যাট ──
    private fun callOpenAiCompatible(url: String, apiKey: String, model: String, history: List<AiChatMessage>): String? {
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", SYSTEM_PROMPT) })
            history.forEach { m ->
                put(JSONObject().apply {
                    put("role", if (m.role == "assistant") "assistant" else "user")
                    put("content", m.content)
                })
            }
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.4)
            put("max_tokens", 700)
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MT))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val txt = resp.body?.string() ?: return null
            val content = JSONObject(txt)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
            return content?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun callGemini(apiKey: String, history: List<AiChatMessage>): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val contents = JSONArray().apply {
            history.forEach { m ->
                put(JSONObject().apply {
                    put("role", if (m.role == "assistant") "model" else "user")
                    put("parts", JSONArray().apply { put(JSONObject().apply { put("text", m.content) }) })
                })
            }
        }
        val payload = JSONObject().apply {
            put("contents", contents)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply { put(JSONObject().apply { put("text", SYSTEM_PROMPT) }) })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 700)
            })
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MT))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val txt = resp.body?.string() ?: return null
            val content = JSONObject(txt)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
            return content?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}
