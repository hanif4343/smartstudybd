package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.Index

/** "SubTopics" reference-টেবিলের local cache — একটা Topic-এর আন্ডারে একটা SubTopic (QBank-এ ব্যবহৃত)। */
@Entity(
    tableName = "subtopics",
    primaryKeys = ["subtopicId"],
    indices = [Index(value = ["topicId"])]
)
data class SubTopicEntity(
    val subtopicId : String = "",
    val topicId    : String = "",
    val name       : String = ""
)
