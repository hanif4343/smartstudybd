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
 * ── Viva Mode-এর জন্য রায় + ফিডব্যাক ──
 * verdict: "CORRECT" | "PARTIAL" | "WRONG"
 */
data class VivaVerdict(val verdict: String, val feedback: String) {
    val isCorrect: Boolean get() = verdict == "CORRECT"
}

/**
 * ── প্রশ্ন এডিট করার পর "🔄 Regenerate" বাটনে ব্যবহারের জন্য — নতুন লেখা প্রশ্ন
 * থেকে ৪টা অপশন ও সঠিক উত্তর আবার তৈরি করে দেয় (SharedComponents.kt-এর
 * AdminFieldEditDialog)। correctAnswer অবশ্যই optionA-D-এর একটার সাথে হুবহু মিলবে।
 */
data class RegeneratedMcq(
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswer: String
)

/**
 * ── Written উত্তর AI দিয়ে অটো-চেক (স্টাডির ⌨️ রিকল-টাইপিং মোড) + Viva Mode গ্রেডিং ──
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

    // ── 🤖 "AI ব্যাখ্যা" বাটনের জন্য আলাদা, কম-timeout ক্লায়েন্ট ──
    // কারণ: এই বাটন সবসময় Groq→Mistral→Cerebras→Gemini ৪টাই ক্রমে চেষ্টা করতে পারে
    // (grading/viva-এর মতো "একটা ঠিক উত্তর পেলেই থামা" না — এখানেও তাই হয়, কিন্তু
    // ইউজার সরাসরি বাটনে চেপে অপেক্ষা করছে, তাই fail হলে দ্রুত বোঝা জরুরি)। উপরের
    // `http` ক্লায়েন্টের timeout (12+20=32 সেকেন্ড/প্রোভাইডার) দিয়ে সব প্রোভাইডার
    // fail করলে সর্বোচ্চ ~২ মিনিট পর্যন্ত "hang" মনে হতে পারে (ধীর নেটওয়ার্কে এটাই
    // ঘটেছিল)। এই ক্লায়েন্ট দিয়ে প্রতিটা প্রোভাইডার দ্রুত fail করে পরেরটায় চলে যায়,
    // সর্বোচ্চ সময় লাগে ~৪×১৩≈৫২ সেকেন্ড (আগের তুলনায় অনেক কম)। `http`-এর timeout
    // ইচ্ছাকৃতভাবে অপরিবর্তিত রাখা হলো — grading/viva ফিচার এতে প্রভাবিত হবে না। ──
    private val httpFast = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
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
                    model  = keys.groqModel,
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
                    model  = keys.mistralModel,
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
                    model  = keys.cerebrasModel,
                    prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Cerebras failed: ${it.message}") }
                .getOrNull()?.let { return@withContext it }
        }

        // ── Gemini সবার শেষে চেষ্টা করা হয় — এটা প্রায়ই ফেইল করে (free-tier rate limit/region ইস্যু) ──
        if (keys.gemini.isNotBlank()) {
            runCatching { callGemini(keys.gemini, keys.geminiModel, prompt) }
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
                    apiKey = keys.groq, model  = keys.groqModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Groq explain failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        if (keys.mistral.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleText(
                    url = "https://api.mistral.ai/v1/chat/completions",
                    apiKey = keys.mistral, model  = keys.mistralModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Mistral explain failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        if (keys.cerebras.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleText(
                    url = "https://api.cerebras.ai/v1/chat/completions",
                    apiKey = keys.cerebras, model  = keys.cerebrasModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Cerebras explain failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        if (keys.gemini.isNotBlank()) {
            runCatching { callGeminiText(keys.gemini, keys.geminiModel, prompt) }
                .onFailure { Log.w(TAG, "Gemini explain failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        null
    }

    /**
     * ── 🤖 "AI ব্যাখ্যা" বাটন (উত্তর সাবমিট করার পর দেখা যায়) — প্রশ্নের পূর্ণ,
     * শিক্ষকের মতো ব্যাখ্যা এনে দেয়:
     *   - অঙ্ক/গণিত হলে ধাপে ধাপে (step-by-step) পুরো সমাধান দেখায়
     *   - ইংরেজি গ্রামার হলে বাংলা-ইংরেজি মিশিয়ে সহজ ভাষায় বোঝায় (শিক্ষক যেভাবে বোঝান)
     *   - অন্য বিষয় হলে স্পষ্ট, প্রাসঙ্গিক ব্যাখ্যা (দরকারমতো ইংরেজি টার্ম মিশিয়ে)
     * সবসময় কমপক্ষে ৩ লাইন, সর্বোচ্চ ১০ লাইনে উত্তর দিতে বলা হয়েছে।
     * gradeWrittenAnswer/explainMistake-এর মতোই Groq → Mistral → Cerebras → Gemini
     * ক্রমে চেষ্টা করে (tryAllProviders — একটা ব্যর্থ হলে সাথে সাথে পরেরটা, তাই দ্রুত),
     * সব ব্যর্থ হলে null। এই রেজাল্ট UI-তে শুধু ডিভাইসেই ক্যাশ হয় (AiExplanationCache) —
     * কোনো সার্ভার/ডাটাবেসে সেভ হয় না।
     */
    suspend fun explainQuestion(
        question     : String,
        correctAnswer: String,
        subjectTopic : String,
        keys         : AiApiKeys
    ): String? = withContext(Dispatchers.IO) {
        if (question.isBlank() || !keys.hasAnyKey()) return@withContext null
        val prompt = buildExplainQuestionPrompt(question, correctAnswer, subjectTopic)

        // ── httpFast + বড় max_tokens (৪০০) — ৩-১০ লাইনের ব্যাখ্যা যেন মাঝপথে
        // কেটে না যায় (tryAllProviders/callOpenAiCompatibleText-এর শেয়ার্ড
        // max_tokens=120 এই দৈর্ঘ্যের জন্য যথেষ্ট না)। Groq → Mistral → Cerebras →
        // Gemini ক্রমে, প্রতিটা দ্রুত fail করে পরেরটায় চলে যায় (httpFast timeout)। ──
        if (keys.groq.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleTextFast(
                    url = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = keys.groq, model  = keys.groqModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Groq explainQuestion failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it.trim() }
        }
        if (keys.mistral.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleTextFast(
                    url = "https://api.mistral.ai/v1/chat/completions",
                    apiKey = keys.mistral, model  = keys.mistralModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Mistral explainQuestion failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it.trim() }
        }
        if (keys.cerebras.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleTextFast(
                    url = "https://api.cerebras.ai/v1/chat/completions",
                    apiKey = keys.cerebras, model  = keys.cerebrasModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Cerebras explainQuestion failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it.trim() }
        }
        if (keys.gemini.isNotBlank()) {
            runCatching { callGeminiTextFast(keys.gemini, keys.geminiModel, prompt) }
                .onFailure { Log.w(TAG, "Gemini explainQuestion failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it.trim() }
        }
        null
    }

    private fun callOpenAiCompatibleTextFast(url: String, apiKey: String, model: String, prompt: String): String? {
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "user"); put("content", prompt) })
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.3)
            put("max_tokens", 400)
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MT))
            .build()

        httpFast.newCall(req).execute().use { resp ->
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

    private fun callGeminiTextFast(apiKey: String, model: String, prompt: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val parts = JSONArray().apply { put(JSONObject().apply { put("text", prompt) }) }
        val contents = JSONArray().apply { put(JSONObject().apply { put("parts", parts) }) }
        val payload = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 400)
            })
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MT))
            .build()

        httpFast.newCall(req).execute().use { resp ->
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

    /**
     * ── 🔍 Settings-এ প্রতিটা মডেলের পাশের "টেস্ট করুন" বাটন ──
     * key ও মডেল দিয়ে একটা ছোট্ট ("Say OK") রিকোয়েস্ট সরাসরি পাঠিয়ে দেখে key+মডেল
     * আসলেই কাজ করছে কিনা — গেস করার দরকার নেই। httpFast (কম timeout) ব্যবহার করে,
     * তাই দ্রুত ফলাফল আসে। এরর কোড অনুযায়ী স্পষ্ট বাংলা বার্তা দেয় (ভুল key vs
     * বন্ধ/ভুল মডেল vs rate-limit vs নেটওয়ার্ক) — যাতে ইউজার ঠিক বুঝতে পারে কোনটা
     * বদলাতে হবে।
     */
    data class ModelTestResult(val ok: Boolean, val message: String)

    suspend fun testProviderModel(provider: String, apiKey: String, model: String): ModelTestResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext ModelTestResult(false, "❌ আগে API key দিন")
            if (model.isBlank()) return@withContext ModelTestResult(false, "❌ মডেলের নাম ফাঁকা")

            try {
                val req = if (provider == "gemini") {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val payload = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply { put(JSONObject().apply { put("text", "Say OK") }) })
                            })
                        })
                    }
                    Request.Builder().url(url)
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(JSON_MT))
                        .build()
                } else {
                    val url = when (provider) {
                        "groq"     -> "https://api.groq.com/openai/v1/chat/completions"
                        "mistral"  -> "https://api.mistral.ai/v1/chat/completions"
                        "cerebras" -> "https://api.cerebras.ai/v1/chat/completions"
                        else       -> return@withContext ModelTestResult(false, "❌ অজানা প্রোভাইডার")
                    }
                    val payload = JSONObject().apply {
                        put("model", model)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply { put("role", "user"); put("content", "Say OK") })
                        })
                        put("max_tokens", 5)
                    }
                    Request.Builder().url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(JSON_MT))
                        .build()
                }

                httpFast.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> ModelTestResult(true, "✅ কাজ করছে")
                        resp.code == 401 || resp.code == 403 ->
                            ModelTestResult(false, "❌ API key ভুল/অবৈধ")
                        resp.code == 404 ->
                            ModelTestResult(false, "❌ এই মডেল খুঁজে পাওয়া যায়নি — নাম ভুল বা বন্ধ হয়ে গেছে, অন্য মডেল ট্রাই করুন")
                        resp.code == 429 ->
                            ModelTestResult(false, "⚠️ Rate limit — key/মডেল ঠিক আছে, একটু পর আবার চেষ্টা করুন")
                        else ->
                            ModelTestResult(false, "❌ এরর কোড ${resp.code} — অন্য মডেল/key ট্রাই করুন")
                    }
                }
            } catch (e: Exception) {
                ModelTestResult(false, "❌ নেটওয়ার্ক এরর: ${e.message ?: "অজানা সমস্যা"}")
            }
        }

    private fun buildExplainQuestionPrompt(question: String, correctAnswer: String, subjectTopic: String): String = """
তুমি একজন অভিজ্ঞ, বন্ধুত্বপূর্ণ শিক্ষক। নিচের প্রশ্নটা একজন শিক্ষার্থীকে বুঝিয়ে দাও, ঠিক যেভাবে
ক্লাসে সামনাসামনি বোঝাতে — এমনভাবে যেন শিক্ষার্থী পড়েই পুরো ব্যাপারটা বুঝে যায়।

বিষয়/টপিক (প্রসঙ্গের জন্য): ${subjectTopic.ifBlank { "(অজানা)" }}
প্রশ্ন: $question
সঠিক উত্তর: ${correctAnswer.ifBlank { "(দেওয়া নেই — প্রশ্ন থেকেই বুঝে ব্যাখ্যা করো)" }}

নিয়ম (পরিস্থিতি অনুযায়ী বেছে নাও):
১. এটা যদি অঙ্ক/গণিত/হিসাবের প্রশ্ন হয় — ধাপে ধাপে (step by step) পুরো সমাধান দেখাও, প্রতিটা
   ধাপের হিসাব স্পষ্টভাবে লিখে, শেষে চূড়ান্ত উত্তরে পৌঁছাও। কোনো ধাপ বাদ দেবে না।
২. এটা যদি ইংরেজি গ্রামার/Vocabulary-এর প্রশ্ন হয় — বাংলা ও ইংরেজি মিশিয়ে (মূল রুল/টার্মটা
   ইংরেজিতে, বোঝানোটা বাংলায়) এমনভাবে বলো যেন বাংলা-মাধ্যমের শিক্ষার্থী সহজে বুঝে যায়,
   ঠিক যেভাবে একজন শিক্ষক ক্লাসে দুই ভাষা মিশিয়ে বোঝান।
৩. অন্য যেকোনো বিষয় হলে — কেন এই উত্তরটাই সঠিক, মূল ধারণা/কারণ কী, সেটা স্পষ্ট ও সহজ ভাষায়
   ব্যাখ্যা করো (দরকার হলে ইংরেজি পরিভাষা মিশিয়ে)।

দৈর্ঘ্য: কমপক্ষে ৩ লাইন, সর্বোচ্চ ১০ লাইন — এর মধ্যেই সম্পূর্ণ ব্যাখ্যা শেষ করো, না বেশি সংক্ষিপ্ত,
না অপ্রয়োজনীয় লম্বা। ভূমিকা/সম্ভাষণ ("চলো দেখি", "অবশ্যই" ইত্যাদি) ছাড়াই সরাসরি ব্যাখ্যা শুরু করো।
""".trimIndent()

    /**
     * ── "প্রশ্ন এডিট করুন" ডায়ালগে "🔄 Regenerate" বাটনে ব্যবহারের জন্য — নতুন লেখা
     * প্রশ্ন থেকে ৪টা অপশন ও সঠিক উত্তর AI দিয়ে তৈরি করে দেয় (বাল্ক-আপলোড করা প্রশ্নে
     * প্রশ্ন এডিট করলে সাথে সাথে অপশন/উত্তরও মিলিয়ে নেওয়া যায়, ম্যানুয়ালি না লিখে)।
     * gradeWrittenAnswer-এর মতোই Groq → Mistral → Cerebras → Gemini ক্রমে চেষ্টা করে,
     * সব ব্যর্থ হলে বা JSON parse করা না গেলে null রিটার্ন করে। */
    suspend fun regenerateMcqOptions(
        question: String,
        keys    : AiApiKeys
    ): RegeneratedMcq? = withContext(Dispatchers.IO) {
        if (question.isBlank() || !keys.hasAnyKey()) return@withContext null
        val prompt = buildMcqPrompt(question)

        if (keys.groq.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleJson(
                    url = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = keys.groq, model  = keys.groqModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Groq regenerate failed: ${it.message}") }
                .getOrNull()?.let { parseRegeneratedMcq(it) }?.let { return@withContext it }
        }
        if (keys.mistral.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleJson(
                    url = "https://api.mistral.ai/v1/chat/completions",
                    apiKey = keys.mistral, model  = keys.mistralModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Mistral regenerate failed: ${it.message}") }
                .getOrNull()?.let { parseRegeneratedMcq(it) }?.let { return@withContext it }
        }
        if (keys.cerebras.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleJson(
                    url = "https://api.cerebras.ai/v1/chat/completions",
                    apiKey = keys.cerebras, model  = keys.cerebrasModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Cerebras regenerate failed: ${it.message}") }
                .getOrNull()?.let { parseRegeneratedMcq(it) }?.let { return@withContext it }
        }
        if (keys.gemini.isNotBlank()) {
            runCatching { callGeminiJson(keys.gemini, keys.geminiModel, prompt) }
                .onFailure { Log.w(TAG, "Gemini regenerate failed: ${it.message}") }
                .getOrNull()?.let { parseRegeneratedMcq(it) }?.let { return@withContext it }
        }
        null
    }

    private fun buildMcqPrompt(question: String): String = """
তুমি একজন MCQ (বহুনির্বাচনী প্রশ্ন) তৈরির বিশেষজ্ঞ। নিচের প্রশ্নের জন্য ৪টা অপশন ও সঠিক উত্তর তৈরি করো।

প্রশ্ন: $question

নিয়ম (সবগুলো মেনে চলো):
১. ৪টা অপশনই প্রাসঙ্গিক, বিশ্বাসযোগ্য ও একে অপরের কাছাকাছি মানের হতে হবে (কোনো অপশন যেন স্পষ্টভাবে বাতিল/হাস্যকর না লাগে)।
২. ঠিক একটা অপশনই সম্পূর্ণ সঠিক হবে, বাকি তিনটা ভুল/বিভ্রান্তিকর (distractor) হবে।
৩. "correct" ফিল্ডের মান অবশ্যই optionA/B/C/D-এর একটার সাথে অক্ষরে-অক্ষরে (word-for-word, হুবহু) মিলতে হবে।
৪. প্রশ্নের ভাষা যেই ভাষায় (বাংলা/ইংরেজি), অপশনও সেই একই ভাষায় লিখবে।
৫. প্রতিটা অপশন সংক্ষিপ্ত রাখবে (এক লাইনের মধ্যে)।

শুধু নিচের JSON ফরম্যাটে উত্তর দাও — কোনো ভূমিকা, ব্যাখ্যা, বা ```markdown code fence ছাড়া, শুধু কাঁচা JSON:
{"optionA":"...","optionB":"...","optionC":"...","optionD":"...","correct":"এখানে সঠিক অপশনের হুবহু টেক্সট"}
""".trimIndent()

    /** AI মাঝেমধ্যে ```json ... ``` fence দিয়ে মুড়িয়ে দেয় (নিষেধ করা সত্ত্বেও) —
     *  parse করার আগে সেটা ছেঁটে ফেলা হয়, আর JSON-এর বাইরের কোনো বাড়তি টেক্সট থাকলে
     *  প্রথম {..} ব্লকটুকু বের করে নেওয়া হয়। */
    private fun parseRegeneratedMcq(raw: String): RegeneratedMcq? {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val jsonStart = cleaned.indexOf('{')
            val jsonEnd = cleaned.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd < jsonStart) return null
            val obj = JSONObject(cleaned.substring(jsonStart, jsonEnd + 1))
            val a = obj.optString("optionA").trim()
            val b = obj.optString("optionB").trim()
            val c = obj.optString("optionC").trim()
            val d = obj.optString("optionD").trim()
            val correct = obj.optString("correct").trim()
            if (a.isBlank() || b.isBlank() || c.isBlank() || d.isBlank() || correct.isBlank()) return null
            // ── "correct"-এর মান ৪টা অপশনের একটার সাথে মিলছে কিনা যাচাই — না মিললে
            // এই রেজাল্টটা অবিশ্বস্ত (AI নিয়ম ভেঙেছে), null রিটার্ন করে fallback provider-এ যাওয়া ──
            if (correct !in listOf(a, b, c, d)) return null
            RegeneratedMcq(a, b, c, d, correct)
        } catch (e: Exception) {
            Log.w(TAG, "parseRegeneratedMcq failed: ${e.message}")
            null
        }
    }

    // ── MCQ regenerate-এর জন্য বড় max_tokens লাগে (৪টা অপশন + JSON স্ট্রাকচার),
    // তাই grading/explain-এর callOpenAiCompatibleText/callGeminiText (max_tokens=120)
    // পুনরায় ব্যবহার না করে আলাদা ফাংশন — যাতে বিদ্যমান grading ফিচার অপরিবর্তিত থাকে ──
    private fun callOpenAiCompatibleJson(url: String, apiKey: String, model: String, prompt: String): String? {
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "user"); put("content", prompt) })
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.3)
            put("max_tokens", 500)
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

    private fun callGeminiJson(apiKey: String, model: String, prompt: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val parts = JSONArray().apply { put(JSONObject().apply { put("text", prompt) }) }
        val contents = JSONArray().apply { put(JSONObject().apply { put("parts", parts) }) }
        val payload = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 500)
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

    private fun buildExplainPrompt(question: String, correctAnswer: String, userAnswer: String): String = """
তুমি একজন বাংলা পরীক্ষক। নিচের শিক্ষার্থীর উত্তরে কী কী ভুল বা ফাঁক আছে সেটা সংক্ষেপে (সর্বোচ্চ ২টি ছোট বাক্যে) বাংলায় বলো।

প্রশ্ন: $question
সঠিক উত্তর: $correctAnswer
শিক্ষার্থীর উত্তর: ${userAnswer.ifBlank { "(কিছু লেখেনি)" }}

শুধু ভুলটা কোথায় সেটা বলো, কোনো ভূমিকা বা উপসংহার লিখবে না।
""".trimIndent()

    /**
     * ── Viva Mode: ছাত্র মুখে যা বলেছে (STT দিয়ে টেক্সট হওয়া) তার সাথে দেওয়া
     * তালিকার (subjects বা subTopics) সবচেয়ে কাছের মিলটা AI দিয়ে বের করে —
     * সাধারণ string-matching এর বদলে AI ব্যবহার করা হয়েছে কারণ ছাত্র colloquially/
     * ভুল উচ্চারণে বলতে পারে ("গণিতের সমীকরণ" বললেও আসল subTopic "সরল সমীকরণ"
     * হতে পারে) — AI ভাষাগত ভিন্নতা সহনশীলভাবে বুঝে সঠিক entry বেছে দিতে পারে।
     * @return validOptions-এর ভেতরের ঠিক সেই স্ট্রিং (হুবহু casing/spacing) যেটা মিলেছে,
     * অথবা null (কোনো ভালো মিল পাওয়া যায়নি, বা AI কল ব্যর্থ হয়েছে)।
     */
    suspend fun resolveFromList(
        spokenText  : String,
        validOptions: List<String>,
        keys        : AiApiKeys
    ): String? = withContext(Dispatchers.IO) {
        if (spokenText.isBlank() || validOptions.isEmpty() || !keys.hasAnyKey()) return@withContext null
        val prompt = """
তুমি একটা তালিকা মিলানোর কাজ করছ। একজন ছাত্র মুখে বলেছে: "$spokenText"

নিচের তালিকা থেকে সবচেয়ে কাছের মিলটা বের করো (ছাত্র colloquially/ভুল উচ্চারণে/আংশিকভাবে বলতে পারে):
${validOptions.joinToString("\n") { "- $it" }}

নিয়ম:
- তালিকায় যেভাবে লেখা আছে ঠিক হুবহু সেভাবেই (বানান/স্পেসিং অপরিবর্তিত রেখে) একটা লাইন উত্তর দেবে
- কোনোটার সাথেই যুক্তিসঙ্গত মিল না থাকলে ঠিক একটা শব্দ লিখবে: NONE
- অন্য কোনো ব্যাখ্যা/ভূমিকা/উপসংহার লিখবে না
""".trimIndent()

        val raw = tryAllProviders(prompt, keys) ?: return@withContext null
        val cleaned = raw.trim().trim('"', '।', '.')
        if (cleaned.equals("NONE", ignoreCase = true)) return@withContext null
        // AI-এর রেসপন্স তালিকার কোনো একটার সাথে (case-insensitive) মিলছে কিনা যাচাই —
        // না মিললে AI বানিয়ে বলেছে ধরে নিয়ে null (তালিকার বাইরের কিছু গ্রহণ করি না)
        validOptions.firstOrNull { it.equals(cleaned, ignoreCase = true) }
            ?: validOptions.firstOrNull { cleaned.contains(it, ignoreCase = true) || it.contains(cleaned, ignoreCase = true) }
    }

    /**
     * ── Viva Mode: ছাত্রের মুখে-বলা (voice-to-text) উত্তর গ্রেড করে — সাধারণ
     * gradeWrittenAnswer()-এর চেয়ে ইচ্ছাকৃতভাবে ঢিলা (lenient), কারণ voice-to-text
     * transcription নিজেই ছোটখাটো বানান/স্পেসিং ভুল করে যেটা ছাত্রের দোষ না।
     * verdict + feedback একসাথে এক কলেই আসে (efficient — প্রতি প্রশ্নে ২টা আলাদা
     * API কল না করে ১টাই)।
     */
    suspend fun gradeVivaAnswer(
        question        : String,
        correctAnswer   : String,
        explanation     : String,
        studentAnswer   : String,
        keys            : AiApiKeys
    ): VivaVerdict? = withContext(Dispatchers.IO) {
        if (!keys.hasAnyKey()) return@withContext null
        if (studentAnswer.isBlank()) return@withContext VivaVerdict("WRONG", "কিছু শোনা যায়নি — আবার চেষ্টা করো।")

        val prompt = """
তুমি একজন বন্ধুত্বপূর্ণ মৌখিক পরীক্ষক (viva examiner)। ছাত্র মুখে উত্তর দিয়েছে, আর voice-to-text
দিয়ে সেটা লেখায় রূপান্তর করা হয়েছে — তাই ছোটখাটো বানান/স্পেসিং/শব্দ-বিভাজন ভুল থাকতে পারে
transcription-এর কারণে, এটা ছাত্রের ভুল না। এসব উপেক্ষা করে মূল ধারণা/তথ্যটা ঠিক আছে কিনা
সেটাই বিচার করো — সংক্ষিপ্ত বা নিজের ভাষায় বললেও মূল তথ্য ঠিক থাকলে গ্রহণযোগ্য।

প্রশ্ন: $question
সঠিক উত্তর: $correctAnswer
${if (explanation.isNotBlank()) "ব্যাখ্যা (প্রসঙ্গের জন্য): $explanation" else ""}
ছাত্রের (voice-to-text) উত্তর: $studentAnswer

নিচের ফরম্যাটে ঠিক দুই লাইনে উত্তর দেবে, অন্য কিছু লিখবে না:
VERDICT: CORRECT অথবা PARTIAL অথবা WRONG
FEEDBACK: একটা ছোট বাক্যে, ছাত্রকে সরাসরি মুখে বলার মতো ভাষায় (কথ্য, বন্ধুত্বপূর্ণ) — CORRECT হলে
সংক্ষিপ্ত প্রশংসা, PARTIAL/WRONG হলে কী বাদ পড়েছে বা সঠিক উত্তরটা কী সেটা সংক্ষেপে বলবে।
""".trimIndent()

        val raw = tryAllProviders(prompt, keys) ?: return@withContext null
        parseVivaVerdict(raw)
    }

    private fun parseVivaVerdict(raw: String): VivaVerdict {
        val verdictLine = raw.lineSequence().firstOrNull { it.uppercase().contains("VERDICT") }.orEmpty()
        val verdictUpper = verdictLine.uppercase()
        val verdict = when {
            verdictUpper.contains("WRONG") || verdictUpper.contains("INCORRECT") -> "WRONG"
            verdictUpper.contains("PARTIAL") -> "PARTIAL"
            verdictUpper.contains("CORRECT") -> "CORRECT"
            else -> "WRONG"
        }
        // ── লাইন-ভিত্তিক না করে পুরো "FEEDBACK:" এর পরের সব টেক্সট নেওয়া হচ্ছে (একাধিক
        // লাইনে wrap করে গেলেও যেন হারিয়ে না যায়) ──
        val feedback = raw.substringAfter("FEEDBACK:", "").trim()
            .ifBlank {
                when (verdict) {
                    "CORRECT" -> "সঠিক!"
                    "PARTIAL" -> "আংশিক সঠিক।"
                    else      -> "উত্তরটা ঠিক হয়নি।"
                }
            }
        return VivaVerdict(verdict, feedback)
    }

    /** Groq → Mistral → Cerebras → Gemini — প্রথম যেটা সাড়া দেয় সেটাই ব্যবহার হয় */
    private fun tryAllProviders(prompt: String, keys: AiApiKeys): String? {
        if (keys.groq.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleText(
                    url = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = keys.groq, model  = keys.groqModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Groq failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        if (keys.mistral.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleText(
                    url = "https://api.mistral.ai/v1/chat/completions",
                    apiKey = keys.mistral, model  = keys.mistralModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Mistral failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        if (keys.cerebras.isNotBlank()) {
            runCatching {
                callOpenAiCompatibleText(
                    url = "https://api.cerebras.ai/v1/chat/completions",
                    apiKey = keys.cerebras, model  = keys.cerebrasModel, prompt = prompt
                )
            }.onFailure { Log.w(TAG, "Cerebras failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        if (keys.gemini.isNotBlank()) {
            runCatching { callGeminiText(keys.gemini, keys.geminiModel, prompt) }
                .onFailure { Log.w(TAG, "Gemini failed: ${it.message}") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

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

    private fun callGeminiText(apiKey: String, model: String, prompt: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
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

    private fun callGemini(apiKey: String, model: String, prompt: String): Boolean? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
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
