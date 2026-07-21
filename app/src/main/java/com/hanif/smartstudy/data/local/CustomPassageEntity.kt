package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ইউজার নিজে টাইপিং প্র্যাকটিসের জন্য যোগ করা প্যাসেজ — শুধু লোকালি সেভ থাকে
 * (কোনো সার্ভারে যায় না)। "আমার প্যাসেজ" ফিল্টার বেছে নিলে এখান থেকেই রান হয়।
 * দেখো TypingPracticeScreen.kt-এর "নিজের প্যাসেজ যোগ করুন" ফিচার।
 */
@Entity(tableName = "custom_passages")
data class CustomPassageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String = "",
    val language: String = "bn",   // "bn" | "en" — TypingErrorAnalyzer.detectLanguage() দিয়ে বের করা
    val createdAt: Long = 0L
)
