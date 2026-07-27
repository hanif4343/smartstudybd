package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * প্রতিটা কী (ক্যারেক্টার)-এর জন্য ইউজারের cumulative সঠিক/ভুল কীপ্রেস গণনা —
 * TypingPracticeScreen-এর char-loop থেকে সেশন শেষে (finishSession()/finishExamPhase())
 * batch করে যোগ হয় (দেখো util/TypingKeyStatStore.kt)।
 *
 * এটাই "লাইভ হিটম্যাপ" (KeyHeatmapCard) আর "দুর্বল-কী ড্রিল" (startKeyDrillSession())
 * — দুটো ফিচারেরই ভিত্তি — Neonlipi-এর "কোন কী-তে accuracy কম" ফিচারের সমতুল্য।
 */
@Entity(
    tableName   = "typing_key_stats",
    primaryKeys = ["userId", "keyChar", "language"]
)
data class TypingKeyStatEntity(
    val userId       : String = "",
    val keyChar       : String = "",   // একটা ক্যারেক্টার, String হিসেবে রাখা হলো যাতে
                                        // future-এ multi-codepoint কী (যেমন যুক্তবর্ণ) সহজে যোগ করা যায়
    val language      : String = "bn", // "bn" | "en"
    val correctCount  : Int = 0,
    val wrongCount    : Int = 0
)
