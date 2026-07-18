package com.hanif.smartstudy.util

import android.content.Context
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.TypingMistakeEntity

/**
 * TypingPracticeScreen থেকে প্রতিটা শব্দ-বাউন্ডারিতে কল হবে — ভুল হলে logMistake(),
 * ঠিক হলে logCorrect()। এই অবজেক্টই TypingErrorAnalyzer (classification) আর
 * TypingMistakeDao (persistence)-এর মাঝে সেতু হিসেবে কাজ করে, UI কোড পরিষ্কার রাখতে।
 */
object TypingMistakeLogger {

    private const val GUEST_USER_ID = "guest"

    // SM-2 সরলীকৃত ভার্সন — নতুন ভুল হলে সবসময় ৪ ঘণ্টা পরেই আবার review (interval index ০-এ রিসেট),
    // পরপর সঠিক হতে থাকলে ধাপে ধাপে বড় interval-এ চলে যায়
    private val REVIEW_INTERVALS_HOURS = longArrayOf(4, 24, 72, 168, 336) // ৪ঘ, ১দিন, ৩দিন, ৭দিন, ১৪দিন

    private fun currentUserId(context: Context): String =
        SessionManager(context).getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: GUEST_USER_ID

    /** টাইপ করা শব্দ target-এর সাথে না মিললে কল করো */
    suspend fun logMistake(context: Context, targetWord: String, typedWord: String, language: String) {
        if (targetWord.isBlank()) return
        val userId = currentUserId(context)
        val dao = AppDatabase.getInstance(context).typingMistakeDao()
        val existing = dao.findByWord(userId, targetWord, language)
        val errorType = TypingErrorAnalyzer.classify(targetWord, typedWord)
        val distance = TypingErrorAnalyzer.levenshtein(targetWord, typedWord)
        val now = System.currentTimeMillis()

        dao.upsert(
            TypingMistakeEntity(
                userId       = userId,
                targetWord   = targetWord,
                language     = language,
                typedWord    = typedWord,
                errorType    = errorType.name,
                editDistance = distance,
                mistakeCount = (existing?.mistakeCount ?: 0) + 1,
                correctStreak = 0,   // নতুন ভুল হলে streak রিসেট হয়ে যায়
                lastSeenAt   = now,
                nextReviewAt = now + REVIEW_INTERVALS_HOURS[0] * 3_600_000L
            )
        )
    }

    /** টাইপ করা শব্দ target-এর সাথে মিললে কল করো — শুধু আগে থেকে ট্র্যাক-হওয়া (কখনো ভুল হয়েছিল
     *  এমন) শব্দের জন্যই streak/review-time আপডেট হয়, নতুন করে ট্র্যাকিং শুরু হয় না */
    suspend fun logCorrect(context: Context, targetWord: String, language: String) {
        val userId = currentUserId(context)
        val dao = AppDatabase.getInstance(context).typingMistakeDao()
        val existing = dao.findByWord(userId, targetWord, language) ?: return
        val newStreak = existing.correctStreak + 1
        val intervalIdx = newStreak.coerceIn(0, REVIEW_INTERVALS_HOURS.size - 1)
        val now = System.currentTimeMillis()
        dao.upsert(
            existing.copy(
                correctStreak = newStreak,
                lastSeenAt    = now,
                nextReviewAt  = now + REVIEW_INTERVALS_HOURS[intervalIdx] * 3_600_000L
            )
        )
    }
}
