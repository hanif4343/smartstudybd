package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * ইউজার কোন কারিকুলাম স্টেজ পর্যন্ত আনলক করেছে, তার ট্র্যাকিং — প্রতিটা ট্র্যাক
 * (bn/en) আলাদাভাবে। প্রতিটা char-এর নিজস্ব accuracy/keypress ডেটা এখানে রাখা
 * হয় না — সেটা আগে থেকেই TypingKeyStatEntity-তে আছে (Phase ১), unlock-চেক করার
 * সময় সেখান থেকেই পড়া হয় (দেখো util/CurriculumProvider.kt)।
 */
@Entity(
    tableName   = "curriculum_progress",
    primaryKeys = ["userId", "track"]
)
data class CurriculumProgressEntity(
    val userId       : String = "",
    val track        : String = "bn",   // "bn" | "en"
    val currentStage  : Int    = 1,      // ১-ভিত্তিক, এখন পর্যন্ত যে স্টেজে আছে
    val updatedAt     : Long   = 0L
)
