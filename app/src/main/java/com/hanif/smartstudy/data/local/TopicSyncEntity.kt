package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * প্রতিটা Topic-এ কতদূর পর্যন্ত প্রশ্ন আনা হয়েছে তার ট্র্যাকিং।
 *
 * FIX (Speed Plan Task 3, CDN migration): আগে GAS `getQuestionsPage`-এর
 * cursor-ভিত্তিক pagination ট্র্যাক করত (৫০-৫০ ব্যাচে)। এখন read পুরোপুরি
 * CDN-only — প্রতিটা topic-এর পুরো JSON একবারেই আসে (pagination লাগে না),
 * তাই nextCursor/hasMore এখন কার্যত "পুরো topic ইতিমধ্যে cache করা আছে কিনা"
 * বোঝাতে ব্যবহার হয় (hasMore=false মানে সম্পূর্ণ)। নতুন `lastHash` — manifest-এ
 * থাকা hash-এর সাথে মিললে network call-ই স্কিপ হয়ে যায় (CDN ফাইল immutable-
 * per-hash, তাই একই hash দ্বিতীয়বার ডাউনলোড করার দরকার নেই)।
 */
@Entity(tableName = "topic_sync", primaryKeys = ["topicId"])
data class TopicSyncEntity(
    val topicId       : String  = "",
    val nextCursor     : String? = null,
    val hasMore       : Boolean = true,   // ডিফল্ট true — এখনো ফেচ করা না হলে "আরও আছে" ধরে নেওয়া হয়
    val lastFetchedAt : Long    = 0L,
    val lastHash      : String? = null    // CDN manifest-এর hash — একই থাকলে re-fetch স্কিপ
)
