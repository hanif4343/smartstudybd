package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * "স্টাডি টাইপিং" ফিচার — Study sheet-এর কনটেন্ট (question/explanation/technique)
 * টাইপিং প্যাসেজ হিসেবে ব্যবহার হয়। প্রতিটা আইটেম একবার টাইপ করা হয়ে গেলে এই
 * টেবিলে সেভ হয়, যাতে আর দ্বিতীয়বার একই আইটেম না আসে।
 *
 * contentId = QuestionEntity.fbKey (Firebase থেকে আসা আসল id) — sub_topic নিজে
 * সেভ হয় না, শুধু id। subject/subTopic কলাম দুটো রাখা হয়েছে যাতে "কারক: ৮/১০ শেষ"-এর
 * মতো প্রতি-টপিক progress সহজে বের করা যায়, এবং একটা টপিক রিসেট করলে শুধু সেই
 * টপিকেরই id গুলো মুছে ফেলা যায় (পুরো টেবিল না)।
 */
@Entity(
    tableName = "study_typing_progress",
    primaryKeys = ["userId", "contentId"],
    indices = [
        Index(value = ["userId", "subject", "subTopic"])
    ]
)
data class StudyTypingProgressEntity(
    val userId    : String = "",
    val subject   : String = "",
    val subTopic  : String = "",
    val contentId : String = "",
    val typedAt   : Long   = 0L
)
