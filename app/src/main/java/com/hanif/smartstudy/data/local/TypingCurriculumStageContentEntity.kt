package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Google Sheet "CurriculumStages" ট্যাব (headers: id, track, stage, content, updatedAt)
 * থেকে fetch করা admin-curated কারিকুলাম-স্টেজ প্র্যাকটিস-কনটেন্টের অফলাইন cache —
 * TypingSheetPassageEntity-এর একই প্যাটার্নে। দেখো CurriculumStageContentProvider.kt।
 */
@Entity(tableName = "typing_curriculum_stage_content")
data class TypingCurriculumStageContentEntity(
    @PrimaryKey val id: String = "",
    val track     : String = "",
    val stage     : String = "",
    val content   : String = "",
    val updatedAt : Long   = 0L
)
