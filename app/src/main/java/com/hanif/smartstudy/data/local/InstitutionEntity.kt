package com.hanif.smartstudy.data.local

import androidx.room.Entity

/** "Institutions" (প্রতিষ্ঠান) reference-টেবিলের local cache — "পদ অনুযায়ী ব্রাউজ" ফ্লো-তে ব্যবহৃত। */
@Entity(tableName = "institutions", primaryKeys = ["institutionId"])
data class InstitutionEntity(
    val institutionId : String = "",
    val name           : String = ""
)
