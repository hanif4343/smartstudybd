package com.hanif.smartstudy.data.local

import androidx.room.Entity

/** "Tags" reference-টেবিলের local cache — AudienceTags (Job/Masters/Honours ইত্যাদি)। */
@Entity(tableName = "tags", primaryKeys = ["tagId"])
data class TagEntity(
    val tagId : String = "",
    val name  : String = ""
)
