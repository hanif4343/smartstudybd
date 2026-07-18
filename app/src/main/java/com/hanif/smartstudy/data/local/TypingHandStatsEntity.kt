package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * ইউজার প্রতি একটাই রো (primaryKey = userId) — প্রতি সেশন শেষে session-local
 * বাম/ডান হাতের সঠিক-ভুল সংখ্যা এখানে যোগ হয়ে যায়। এখান থেকেই দীর্ঘমেয়াদি
 * error-rate বের করে "কোন হাতে বেশি ভুল হচ্ছে" দেখানো হবে।
 */
@Entity(tableName = "typing_hand_stats", primaryKeys = ["userId"])
data class TypingHandStatsEntity(
    val userId          : String = "",
    val leftCorrectChars : Long = 0L,
    val leftWrongChars   : Long = 0L,
    val rightCorrectChars: Long = 0L,
    val rightWrongChars  : Long = 0L,
    val updatedAt        : Long = 0L
) {
    fun leftErrorRate(): Float {
        val total = leftCorrectChars + leftWrongChars
        return if (total == 0L) 0f else leftWrongChars.toFloat() / total
    }
    fun rightErrorRate(): Float {
        val total = rightCorrectChars + rightWrongChars
        return if (total == 0L) 0f else rightWrongChars.toFloat() / total
    }
}
