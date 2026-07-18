package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * টাইপিং ভুলের ধরন — TypingErrorAnalyzer এই enum অনুযায়ী প্রতিটা ভুল classify করে।
 *
 * SUBSTITUTION — একটা অক্ষরের জায়গায় ভিন্ন অক্ষর (যেমন ম→স)
 * KAR_MATRA    — কার/মাত্রা-চিহ্ন গুলিয়ে ফেলা (ি/ী, ু/ূ ইত্যাদি) — বাংলায় সবচেয়ে কমন ভুল
 * INSERTION    — অতিরিক্ত অক্ষর ঢুকে গেছে (যেমন মন্দির→সমন্দির)
 * DELETION     — অক্ষর বাদ পড়েছে
 * SYNC_LOSS    — consecutive অনেক অক্ষর একসাথে ভুল, edit-distance অস্বাভাবিক বেশি —
 *                এটা বানান-ভুল না, চোখ-হাত সিঙ্ক হারানো (এর সমাধান শব্দ-পুনরাবৃত্তি না,
 *                UI/attention — দেখো SmartStudyBD-টাইপিং-অডিট-ও-রোডম্যাপ.md সেকশন ৪)
 */
enum class MistakeErrorType {
    SUBSTITUTION,
    KAR_MATRA,
    INSERTION,
    DELETION,
    SYNC_LOSS
}

/**
 * প্রতিটা রো একটা নির্দিষ্ট ইউজারের একটা নির্দিষ্ট "দুর্বল শব্দ"-এর সারসংক্ষেপ —
 * প্রতিবার নতুন ভুল হলে upsert হয়ে mistakeCount বাড়ে, lastSeenAt/nextReviewAt আপডেট হয়।
 * এটাই AI sentence-generation (ধাপ ২/৩) আর hand-balance analysis (ধাপ ৪)-এর ভিত্তি।
 */
@Entity(
    tableName = "typing_mistakes",
    primaryKeys = ["userId", "targetWord", "language"],
    indices = [
        Index(value = ["userId", "lastSeenAt"]),
        Index(value = ["userId", "nextReviewAt"])
    ]
)
data class TypingMistakeEntity(
    val userId        : String = "",              // SessionManager.getCurrentUser()?.phone ?: "guest"
    val targetWord     : String = "",              // সঠিক শব্দ (প্যাসেজ অনুযায়ী)
    val language       : String = "bn",            // "bn" | "en"
    val typedWord      : String = "",              // সর্বশেষ ভুল-টাইপ ভার্সন (ডিবাগ/উদাহরণ দেখানোর জন্য)
    val errorType      : String = MistakeErrorType.SUBSTITUTION.name,
    val editDistance    : Int = 0,
    val mistakeCount   : Int = 1,                  // এই শব্দে মোট কতবার ভুল হয়েছে
    val correctStreak  : Int = 0,                  // পরপর কতবার ঠিক হয়েছে (spaced repetition)
    val lastSeenAt     : Long = 0L,                // শেষ কবে এই শব্দে ভুল হলো (millis)
    val nextReviewAt   : Long = 0L                 // পরের বার কবে প্র্যাকটিসে আনা উচিত (SM-2 simplified)
)
