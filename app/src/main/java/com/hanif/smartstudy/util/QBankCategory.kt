package com.hanif.smartstudy.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * QBank পরীক্ষা-ক্যাটাগরি (বিসিএস / প্রাথমিক / নিবন্ধন / ব্যাংক / ১৬-২০ গ্রেড) — UI (চিপ-ফিল্টার)
 * ও ViewModel (সাল-মোডে প্রশ্ন ফিল্টার) দুই জায়গায় একই নিয়ম ব্যবহারের জন্য এক জায়গায়।
 * "১৬-২০ গ্রেড" catch-all: বাকি চারটার কোনোটাতে না মিললেই এখানে।
 */
object QBankCategory {
    const val GRADE = "১৬-২০ গ্রেড"
    val ALL = listOf("বিসিএস", "প্রাথমিক", "নিবন্ধন", "ব্যাংক", GRADE)
    /** বর্তমানে সিলেক্টেড চিপ — UI সেট করে, ViewModel (সাল-মোড) পড়ে; back করলেও মনে থাকে */
    var selected by mutableStateOf(ALL.first())

    private val NAMED = listOf("বিসিএস", "প্রাথমিক", "নিবন্ধন", "ব্যাংক")

    fun nameMatches(name: String, category: String): Boolean {
        val n = name.trim().lowercase()
        return when (category) {
            "বিসিএস"   -> n.contains("bcs") || name.contains("বিসিএস")
            "প্রাথমিক" -> n.contains("primary") || name.contains("প্রাথমিক")
            // শিক্ষক নিবন্ধন (NTRCA): কলেজ পর্যায় / স্কুল পর্যায় নামেও আসে
            "নিবন্ধন"  -> n.contains("ntrca") || n.contains("registration") || name.contains("নিবন্ধন") ||
                          name.contains("কলেজ পর্যায়") || name.contains("স্কুল পর্যায়")
            "ব্যাংক"   -> n.contains("bank") || name.contains("ব্যাংক")
            else       -> false
        }
    }

    /** names-এর (কার্ডের নিজের নাম + নেস্টেড নাম) কোনোটা ক্যাটাগরিতে পড়ে কিনা */
    fun matches(names: List<String>, category: String): Boolean {
        fun hit(cat: String) = names.any { nameMatches(it, cat) }
        return when (category) {
            in NAMED -> hit(category)
            GRADE    -> NAMED.none { hit(it) }
            else     -> true
        }
    }
}
