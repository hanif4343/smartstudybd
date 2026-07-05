package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Room Entity — Quiz, QBank, Study তিনটাই এক টেবিলে।
 * `sheet` column দিয়ে আলাদা করা হয়: "QUIZ" | "QBANK" | "STUDY"
 *
 * Primary key: sheet + fbKey (Firebase-এর array index বা push key)
 *
 * Index on (sheet, subject, subTopic) → SubTopic query instant হয়।
 * Index on (sheet, subject)           → Subject list instant হয়।
 */
@Entity(
    tableName = "questions",
    primaryKeys = ["sheet", "fbKey"],
    indices = [
        Index(value = ["sheet", "subject", "subTopic"]),
        Index(value = ["sheet", "subject"]),
        Index(value = ["sheet"])
    ]
)
data class QuestionEntity(
    val sheet       : String = "",   // "QUIZ" | "QBANK" | "STUDY"
    val fbKey       : String = "",   // Firebase array index বা push key — unique per sheet
    val subject     : String = "",
    val subTopic    : String = "",
    val question    : String = "",
    val optionA     : String = "",
    val optionB     : String = "",
    val optionC     : String = "",
    val optionD     : String = "",
    val answer      : String = "",
    val explanation : String = "",
    // ব্যাখ্যা Public নাকি Private (শুধু Admin) — ডিফল্ট Public
    val explanationIsPublic: Boolean = true,
    val technique   : String = "",
    val questionType: String = "mcq",
    val audienceTags: String = "",
    val year        : String = "",   // QBank only
    val examName    : String = "",   // QBank only
    val imageUrl    : String = "",
    val visualUrl   : String = "",
    val syncedAt    : Long   = 0L    // Firebase থেকে কখন এসেছে — delta sync এর জন্য
)
