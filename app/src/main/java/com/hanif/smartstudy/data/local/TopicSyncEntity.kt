package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * প্রতিটা Topic-এ GAS `getQuestionsPage`-এর মাধ্যমে কতদূর পর্যন্ত প্রশ্ন আনা হয়েছে তার
 * ট্র্যাকিং — একই ব্যাচ দুইবার নেটওয়ার্কে আনা হয় না, প্রতিবার (অনলাইন থাকলে ও এখনো সব
 * প্রশ্ন লোকালে না এলে) পরের নতুন ব্যাচ যোগ হয়, ধীরে ধীরে পুরো Topic লোকালি জমা হয়ে যায়।
 *
 * hasMore=false মানে এই Topic-এর সব প্রশ্ন ইতিমধ্যে Room-এ আছে — তখন থেকে সেই Topic
 * ১০০% অফলাইন-সক্ষম, আর কোনো নেটওয়ার্ক কল লাগে না।
 */
@Entity(tableName = "topic_sync", primaryKeys = ["topicId"])
data class TopicSyncEntity(
    val topicId       : String  = "",
    val nextCursor    : String? = null,
    val hasMore       : Boolean = true,   // ডিফল্ট true — এখনো ফেচ করা না হলে "আরও আছে" ধরে নেওয়া হয়
    val lastFetchedAt : Long    = 0L
)
