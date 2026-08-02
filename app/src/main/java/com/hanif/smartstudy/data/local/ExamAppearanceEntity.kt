package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * "Exam_Appearances" টেবিলের local cache — একই প্রশ্ন একাধিক পরীক্ষায় (ভিন্ন Post/Institution/Year)
 * এলে সেটা এখানে আলাদা appearance-row হিসেবে থাকে, মূল প্রশ্নের row ডুপ্লিকেট হয় না
 * (Admin App-এর ExamAppearancesTab.jsx এর সাথে সামঞ্জস্যপূর্ণ)।
 */
@Entity(
    tableName = "exam_appearances",
    primaryKeys = ["appearanceId"],
    indices = [
        Index(value = ["questionId"]),
        Index(value = ["postId"]),
        Index(value = ["institutionId"])
    ]
)
data class ExamAppearanceEntity(
    val appearanceId  : String = "",
    val questionId    : String = "",
    val postId        : String = "",
    val institutionId : String = "",
    val year          : String = ""
)
