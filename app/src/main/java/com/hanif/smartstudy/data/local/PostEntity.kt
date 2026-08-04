package com.hanif.smartstudy.data.local

import androidx.room.Entity

/** "Posts" (পদ) reference-টেবিলের local cache — QBank-এর "পদ অনুযায়ী ব্রাউজ" ফ্লো-তে ব্যবহৃত। */
@Entity(tableName = "posts", primaryKeys = ["postId"])
data class PostEntity(
    val postId : String = "",
    val name   : String = ""
)
