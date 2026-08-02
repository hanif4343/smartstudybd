package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * "Topics" reference-টেবিলের local cache — প্রতিটা row একটা Subject-এর আন্ডারে একটা Topic।
 * rowStart/rowCount — GAS-এর `rebuildIndex` action Topics ট্যাবে বসায় (Quiz/QBank/Study
 * sheet-এ ওই topic_id-এর row-range) — future `getQuestionsPage` GAS pagination call-এ
 * পুরো sheet স্ক্যান না করে সরাসরি এই range থেকে পড়ার জন্য কাজে লাগবে।
 */
@Entity(
    tableName = "topics",
    primaryKeys = ["topicId"],
    indices = [Index(value = ["subjectId"])]
)
data class TopicEntity(
    val topicId   : String = "",
    val subjectId : String = "",
    val name      : String = "",
    val rowStart  : Int = 0,
    val rowCount  : Int = 0
)
