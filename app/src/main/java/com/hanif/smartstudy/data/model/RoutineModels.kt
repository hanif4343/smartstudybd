package com.hanif.smartstudy.data.model

// ─────────────────────────────────────────────────────────
// Daily Routine Models — হোম স্ক্রিন চেকলিস্ট + Widget
// DataStore-এ JSON হিসেবে সংরক্ষিত হয়, তারিখ-ভিত্তিক রিসেট হয়
// ─────────────────────────────────────────────────────────

data class RoutineItem(
    val id      : String  = "",
    val title   : String  = "",     // যেমন: "বাংলাদেশের সংবিধান"
    val subject : String  = "",     // যেমন: "বাংলাদেশ বিষয়াবলী"
    val minutes : Int     = 20,     // আনুমানিক সময় (মিনিট)
    val done    : Boolean = false
)

data class DailyRoutine(
    val date  : String           = "",   // yyyy-MM-dd
    val items : List<RoutineItem> = emptyList()
) {
    val totalCount: Int get() = items.size
    val doneCount : Int get() = items.count { it.done }
    val progressPct: Int get() = if (items.isEmpty()) 0 else (doneCount * 100) / totalCount
    val isComplete: Boolean get() = items.isNotEmpty() && doneCount == totalCount
}
