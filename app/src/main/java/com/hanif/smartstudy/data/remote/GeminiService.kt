package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.hanif.smartstudy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiService {

    private const val TAG = "Gemini"

    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey    = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0.2f
                maxOutputTokens = 256
            }
        )
    }

    /**
     * Compare user's written answer with the correct answer.
     * Returns a score 0–100 (percentage match).
     */
    suspend fun scoreWrittenAnswer(
        question    : String,
        correctAnswer: String,
        userAnswer  : String
    ): Int = withContext(Dispatchers.IO) {
        try {
            val prompt = """
You are a Bengali exam evaluator. Compare the student's answer to the correct answer.

Question: $question
Correct Answer: $correctAnswer  
Student's Answer: $userAnswer

Reply with ONLY a number from 0 to 100 representing how correct the student's answer is.
Consider partial credit. 100 = perfect, 0 = completely wrong.
Reply with just the number, nothing else.
""".trimIndent()
            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: "0"
            text.filter { it.isDigit() }.take(3).toIntOrNull()?.coerceIn(0, 100) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "scoreWrittenAnswer: ${e.message}")
            // Fallback: simple keyword match
            fallbackScore(correctAnswer, userAnswer)
        }
    }

    /**
     * Get a hint/explanation for a question.
     */
    suspend fun getHint(question: String, answer: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = """
আমি একজন বাংলাদেশের ছাত্র। নিচের প্রশ্নের উত্তর সম্পর্কে সহজ বাংলায় একটি ছোট힌ট দাও (২-৩ বাক্য)।

প্রশ্ন: $question
সঠিক উত্তর: $answer

শুধু힌ট দাও, উত্তর সরাসরি বলো না।
""".trimIndent()
            model.generateContent(prompt).text?.trim() ?: "কোনো힌ট পাওয়া যায়নি।"
        } catch (e: Exception) {
            Log.e(TAG, "getHint: ${e.message}")
            "AI সংযোগ নেই। পরে চেষ্টা করুন।"
        }
    }

    private fun fallbackScore(correct: String, user: String): Int {
        if (user.isBlank()) return 0
        val correctWords = correct.lowercase().split(" ", "।", ",").filter { it.length > 2 }.toSet()
        val userWords    = user.lowercase().split(" ", "।", ",").filter { it.length > 2 }.toSet()
        if (correctWords.isEmpty()) return if (user.isNotBlank()) 50 else 0
        val matches = correctWords.count { userWords.contains(it) }
        return (matches * 100 / correctWords.size).coerceIn(0, 100)
    }
}
