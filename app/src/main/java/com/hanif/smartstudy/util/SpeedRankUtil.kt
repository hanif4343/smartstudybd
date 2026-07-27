package com.hanif.smartstudy.util

/** একটা WPM-রেঞ্জ ও তার সাথে মিলিয়ে একটা "বাহন" — WPM যত বাড়ে, বাহন তত দ্রুতগামী হয়। */
data class SpeedRank(val emoji: String, val name: String, val minWpm: Int)

/**
 * Neonlipi-এর "৪৫টা র‍্যাংক" গেমিফিকেশনের সরলীকৃত সংস্করণ — নৌকা থেকে স্পেসশিপ পর্যন্ত।
 * WPM-কে সরাসরি সংখ্যা হিসেবে না দেখিয়ে একটা পরিচিত/মজার প্রতীকে রূপান্তর করে, যাতে
 * উন্নতিটা visually আরও স্পষ্ট বোঝা যায়।
 */
object SpeedRankUtil {

    // ── সর্বনিম্ন থেকে সর্বোচ্চ WPM অনুযায়ী সাজানো — নতুন WPM এলে সবচেয়ে উঁচু যে
    // rank-এর minWpm ছাড়িয়ে গেছে, সেটাই বর্তমান rank ──
    private val RANKS = listOf(
        SpeedRank("🚣", "নৌকা",           0),
        SpeedRank("🚲", "সাইকেল",         5),
        SpeedRank("🛺", "রিকশা",          10),
        SpeedRank("🛵", "ইজিবাইক",        15),
        SpeedRank("🛴", "স্কুটার",         20),
        SpeedRank("🏍️", "মোটরসাইকেল",     25),
        SpeedRank("🚗", "গাড়ি",           30),
        SpeedRank("🏎️", "স্পোর্টস কার",    40),
        SpeedRank("🚆", "ট্রেন",           50),
        SpeedRank("🚄", "বুলেট ট্রেন",     60),
        SpeedRank("✈️", "প্লেন",          70),
        SpeedRank("🛩️", "জেট",            80),
        SpeedRank("🚀", "রকেট",           100),
        SpeedRank("🛸", "স্পেসশিপ",        120)
    )

    /** বর্তমান WPM অনুযায়ী rank বের করে */
    fun rankFor(wpm: Int): SpeedRank =
        RANKS.lastOrNull { wpm >= it.minWpm } ?: RANKS.first()

    /** পরের rank-এ যেতে আর কত WPM লাগবে — সর্বোচ্চ rank-এ থাকলে null */
    fun nextRank(wpm: Int): SpeedRank? =
        RANKS.firstOrNull { wpm < it.minWpm }
}
