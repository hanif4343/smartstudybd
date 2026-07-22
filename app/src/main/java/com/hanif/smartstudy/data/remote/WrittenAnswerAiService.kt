package com.hanif.smartstudy.data.remote

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
 * ── Written উত্তর AI দিয়ে অটো-চেক (স্টাডির ⌨️ রিকল-টাইপিং মোড) ──
 *
 * ইউজার টাইপ-বক্সে নিজের উত্তর লিখে জমা দিলে, Settings-এ সেভ করা API key
 * দিয়ে একে একে চেষ্টা করে সঠিক/ভুল বের করে দেওয়া হয়:
 *   Groq → Mistral → Cerebras → Gemini (Gemini সবার শেষে, কারণ এটা প্রায়ই ফেইল করে)
 *
 * একটা প্রোভাইডারের key ফাঁকা থাকলে সেটা স্কিপ হয়ে পরেরটা চেষ্টা হয়, আর কোনো একটা
 * প্রোভাইডার নেটওয়ার্ক এরর/টাইমআউট/অপ্রত্যাশিত রেসপন্স দিলে পরেরটায় চলে যায়।
 * সব ব্যর্থ হলে বা কোনো key-ই সেভ করা না থাকলে null রিটার্ন হয় — তখন UI সাথে
 * সাথেই আগের ম্যানুয়াল ঠিক/ভুল বাটনে ফলব্যাক করে।
 */
object WrittenAnswerAiService {

    private const val TAG = "WrittenAiGrade"

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    /**
     * @return true = সঠিক, false = ভুল, null = AI দিয়ে বোঝা যায়নি (কোনো key নেই বা সব প্রোভাইডার ব্যর্থ)।
     * null এলে ViewModel/UI ম্যানুয়াল সেলফ-গ্রেডিং এ ফলব্যাক করবে।
     */
    suspend fun gradeWrittenAnswer(
        question     : String,
        correctAnswer: String,
        userAnswer   : String,
        keys         : AiApiKeys
    ): Boolean? = withContext(Dispatchers.IO) {
        if (userAnswer.isBlank()) return@withContext false
        if (!keys.hasAnyKey()) return@withContext null

        val prompt = buildPrompt(question, correctAnswer, userAnswer)

        if (keys.groq.isNotBlank()) {
            runCatching {
                callOpenAiCompatible(
                    url    = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = keys.groq,
                    model  = "llama-3.3-70b-versatile",
                    prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Groq failed: ${it.message}") }
                .getOrNull()?.let { return@withContext it }
        }

        if (keys.mistral.isNotBlank()) {
            runCatching {
                callOpenAiCompatible(
                    url    = "https://api.mistral.ai/v1/chat/completions",
                    apiKey = keys.mistral,
                    model  = "mistral-small-latest",
                    prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Mistral failed: ${it.message}") }
                .getOrNull()?.let { return@withContext it }
        }

        if (keys.cerebras.isNotBlank()) {
            runCatching {
                callOpenAiCompatible(
                    url    = "https://api.cerebras.ai/v1/chat/completions",
                    apiKey = keys.cerebras,
                    model  = "llama-3.3-70b",
                    prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Cerebras failed: ${it.message}") }
                .getOrNull()?.let { return@withContext it }
        }

        // ── Gemini সবার শেষে চেষ্টা করা হয় — এটা প্রায়ই ফেইল করে (free-tier rate limit/region ইস্যু) ──
        if (keys.gemini.isNotBlank()) {
            runCatching { callGemini(keys.gemini, prompt) }
                .onFailure { Log.w(TAG, "Gemini failed: ${it.message}") }
                .getOrNull()?.let { return@withContext it }
        }

        null
    }

    /**
     * ── "বিস্তারিত" (details) বাটনে ব্যবহারের জন্য — ভুল হলে ঠিক কোথায় ভুল
     * হয়েছে তার এক-দুই লাইনের সংক্ষিপ্ত বাংলা ব্যাখ্যা এনে দেয়। gradeWrittenAnswer-এর
     * মতোই Groq → Mistral → Cerebras → Gemini ক্রমে চেষ্টা করে, সব ব্যর্থ হলে null। ──
     */
    suspend fun explainMistake(
        question     : String,
        correctAnswer: String,
        userAnswer   : String,
        keys         : AiApiKeys
    ): String? = withContext(Dispatchers.IO) {
        if (!keys.hasAnyKey()) return@withContext null
        val prompt = buildExplainPrompt(question, correctAnswer, userAnswer)

        if (keys.groq.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleText(
                    url = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = keys.groq, model = "llama-3.3-70b-versatile", prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Groq explain failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        if (keys.mistral.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleText(
                    url = "https://api.mistral.ai/v1/chat/completions",
                    apiKey = keys.mistral, model = "mistral-small-latest", prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Mistral explain failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        if (keys.cerebras.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleText(
                    url = "https://api.cerebras.ai/v1/chat/completions",
                    apiKey = keys.cerebras, model = "llama-3.3-70b", prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Cerebras explain failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        if (keys.gemini.isNotBlank()) {
            runCatching { callGeminiText(keys.gemini, prompt) }
                .onFailure { Log.w(TAG, "Gemini explain failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        null
    }

    private fun buildExplainPrompt(question: String, correctAnswer: String, userAnswer: String): String = """
তুমি একজন বাংলা পরীক্ষক। নিচের শিক্ষার্থীর উত্তরে কী কী ভুল বা ফাঁক আছে সেটা সংক্ষেপে (সর্বোচ্চ ২টি ছোট বাক্যে) বাংলায় বলো।

প্রশ্ন: $question
সঠিক উত্তর: $correctAnswer
শিক্ষার্থীর উত্তর: ${userAnswer.ifBlank { "(কিছু লেখেনি)" }}

শুধু ভুলটা কোথায় সেটা বলো, কোনো ভূমিকা বা উপসংহার লিখবে না।
""".trimIndent()

    private fun callOpenAiCompatibleText(url: String, apiKey: String, model: String, prompt: String): String? {
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "user"); put("content", prompt) })
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.2)
            put("max_tokens", 120)
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
            return JSONObject(txt)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
        }
    }

    private fun callGeminiText(apiKey: String, prompt: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val parts = JSONArray().apply { put(JSONObject().apply { put("text", prompt) }) }
        val contents = JSONArray().apply { put(JSONObject().apply { put("parts", parts) }) }
        val payload = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 120)
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
            return JSONObject(txt)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
        }
    }

    private fun buildPrompt(question: String, correctAnswer: String, userAnswer: String): String = """
তুমি একজন কঠোর কিন্তু ন্যায্য বাংলা পরীক্ষক। নিচের তথ্য দেখে বলো শিক্ষার্থীর উত্তরটি সঠিক নাকি ভুল। মূল্যায়নের নিয়মগুলো নিচে দেওয়া হলো, প্রতিটি মেনে চলো:

১. সবচেয়ে গুরুত্বপূর্ণ বিষয় হলো — "সঠিক উত্তর"-এ থাকা মূল তথ্য/মূল বিষয় (নাম, সংজ্ঞা, তারিখ, পূর্ণরূপ ইত্যাদি) শিক্ষার্থীর উত্তরে সঠিকভাবে আছে কিনা। মূল তথ্যের সাথে মিলছে কিনা সেটাই আসল বিচার্য বিষয়, পুরো বাক্যের সাথে হুবহু মিল দরকার নেই।
২. শিক্ষার্থী সংক্ষেপে লিখলে, নিজের ভাষায়/নিজের বাক্য গঠনে লিখলে, অথবা সঠিক উত্তরের শুধু মূল অংশটুকু (যেমন শুধু নাম বা মূল টার্ম) লিখলে — সেটা দোষের কিছু না, যদি মূল তথ্যটা ঠিক থাকে। ভাষাগত পার্থক্য বা সংক্ষিপ্ততার কারণে ভুল ধরবে না।
৩. কিন্তু গুরুত্বপূর্ণ শব্দ/নাম/পরিভাষার বানান ভুল থাকলে সেটাকে ভুল ধরবে (যেমন 'Council'-এর জায়গায় 'Counsil', বা ভিন্ন কোনো বানান/বিকৃত রূপ)। বানান স্পষ্টভাবে ভুল হলে ছাড় দেবে না।
৪. পূর্ণরূপ (Full form/Acronym) জাতীয় প্রশ্নে প্রতিটি শব্দ ও তার বানান প্রায় হুবহু সঠিক হতে হবে; কোনো একটি শব্দ ভুল, বাদ পড়া, বা বানান ভুল থাকলে সেটাকে ভুল ধরবে।
৫. সঠিক উত্তরের মূল নাম/তথ্যের বদলে সম্পূর্ণ ভিন্ন কোনো নাম/তথ্য/শব্দ দিলে (যেমন সঠিক উত্তরে একজনের নাম থাকলে আর শিক্ষার্থী ভিন্ন একজনের নাম লিখলে) সেটাকে ভুল ধরবে, এমনকি শুনতে কাছাকাছি লাগলেও।
৬. উত্তর একদম ফাঁকা বা প্রশ্নের সাথে সম্পূর্ণ অপ্রাসঙ্গিক হলে সেটা ভুল ধরবে।

প্রশ্ন: $question
সঠিক উত্তর: $correctAnswer
শিক্ষার্থীর উত্তর: $userAnswer

তোমার উত্তর শুধু একটি শব্দে দাও — হয় CORRECT, না হয় INCORRECT। অন্য কোনো ব্যাখ্যা লিখবে না।
""".trimIndent()

    // "INCORRECT" এর মধ্যেও "CORRECT" সাবস্ট্রিং থাকে, তাই আগে INCORRECT/WRONG চেক করতে হবে
    private fun parseVerdict(text: String?): Boolean? {
        if (text.isNullOrBlank()) return null
        val upper = text.trim().uppercase()
        return when {
            upper.contains("INCORRECT") || upper.contains("WRONG") -> false
            upper.contains("CORRECT")   || upper.contains("RIGHT")  -> true
            else -> null
        }
    }

    // ── Groq / Mistral / Cerebras — তিনটাই OpenAI-compatible chat completions ফরম্যাট ──
    private fun callOpenAiCompatible(url: String, apiKey: String, model: String, prompt: String): Boolean? {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0)
            put("max_tokens", 6)
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
            return parseVerdict(content)
        }
    }

    private fun callGemini(apiKey: String, prompt: String): Boolean? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val parts = JSONArray().apply { put(JSONObject().apply { put("text", prompt) }) }
        val contents = JSONArray().apply { put(JSONObject().apply { put("parts", parts) }) }
        val payload = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0)
                put("maxOutputTokens", 6)
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
            return parseVerdict(content)
        }
    }
}
