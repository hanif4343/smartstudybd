package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * ধারাবাহিক দুই কী (bigram)-এর মাঝের গড় দেরি — Key Analysis কার্ডের "ধীর জুটি"
 * (যেমন "ির 1507ms" মানে "ি" এর পরে "র" চাপতে গড়ে ১৫০৭ms লেগেছে) ফিচারের ভিত্তি।
 * TypingPracticeScreen-এর onInputChange()-এ প্রতি কীপ্রেসে capture হয়, সেশন শেষে
 * batch persist হয় (দেখো util/TypingKeyStatStore.kt)।
 */
@Entity(
    tableName   = "typing_key_pair_stats",
    primaryKeys = ["userId", "fromChar", "toChar", "language"]
)
data class TypingKeyPairStatEntity(
    val userId   : String = "",
    val fromChar : String = "",   // আগের টার্গেট ক্যারেক্টার
    val toChar   : String = "",   // এই টার্গেট ক্যারেক্টার (যেই কী-এর কার্ডে "ধীর জুটি" হিসেবে দেখানো হবে)
    val language : String = "bn",
    val totalMs  : Long = 0L,
    val count    : Int  = 0
)
