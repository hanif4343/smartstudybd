package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * Room Entity — Sheet Schema v2 (Admin App Phase 5 মাইগ্রেশন)-এর "Subjects" reference-টেবিলের
 * local cache। GAS action `getReferenceData`-এর response-এ আসা {subject_id, subject_name, sheet}
 * এর সরাসরি প্রতিচ্ছবি (দেখো data/model/ReferenceModels.kt এর SubjectRef)।
 *
 * rename/add/delete সব Admin App-এ হয় (ReferenceManagerTab.jsx) — এই টেবিল শুধু read-only cache,
 * প্রতি sync-এ পুরোটা রিফ্রেশ হয় (ছোট টেবিল বলে full-replace নিরাপদ, delta লাগে না)।
 */
@Entity(tableName = "subjects", primaryKeys = ["subjectId"])
data class SubjectEntity(
    val subjectId : String = "",
    val name      : String = "",
    val sheet     : String = ""   // "Quiz" | "QBank" | "Study" — কোন sheet-এর subject
)
