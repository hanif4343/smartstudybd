package com.hanif.smartstudy.util

import android.content.Context
import android.content.SharedPreferences

/**
 * QBank-এর সাজানোর (sort) পছন্দ — প্রতিটা ক্যাটাগরির (বিসিএস / প্রাথমিক / নিবন্ধন / ব্যাংক / ১৬-২০ গ্রেড)
 * জন্য আলাদা। SharedPreferences-এ থাকে, তাই ইউজার নিজে না বদলানো পর্যন্ত (অ্যাপ আনইনস্টল না করলে)
 * স্থায়ী — রিস্টার্ট, রিফ্রেশ, রিসিঙ্কেও বদলায় না। ডিফল্ট = আগের ক্রম (অ্যাডমিন/সার্ভার সিরিয়াল)।
 */
enum class QBankSort(val key: String, val label: String) {
    DEFAULT("default", "ডিফল্ট ক্রম"),
    NAME_ASC("name_asc", "নাম: অ → হ (১, ২, ৩…)"),
    NAME_DESC("name_desc", "নাম: হ → অ (বড় নম্বর আগে)"),
    YEAR_DESC("year_desc", "সাল: নতুন আগে"),
    YEAR_ASC("year_asc", "সাল: পুরনো আগে"),
    Q_DESC("q_desc", "বেশি প্রশ্ন আগে"),
    PROG_ASC("prog_asc", "কম সম্পন্ন আগে"),
    PROG_DESC("prog_desc", "বেশি সম্পন্ন আগে");

    companion object {
        fun fromKey(k: String?): QBankSort = entries.firstOrNull { it.key == k } ?: DEFAULT
    }
}

object QBankSortStore {
    private const val PREFS = "qbank_sort_v1"
    @Volatile private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /**
     * ইউজার/অ্যাডমিন নিজে কিছু না বাছলে ডিফল্ট: "প্রাথমিক" = সাল (নতুন আগে, শেষের সাল ধরে),
     * বাকি ক্যাটাগরি = আগের ডিফল্ট ক্রম। নিজে বাছলে সেটাই স্থায়ী থাকে।
     */
    fun get(scope: String): QBankSort {
        val saved = prefs?.getString("sort_$scope", null)
        if (saved != null) return QBankSort.fromKey(saved)
        return if (scope == "প্রাথমিক") QBankSort.YEAR_DESC else QBankSort.DEFAULT
    }

    fun set(scope: String, sort: QBankSort) {
        prefs?.edit()?.putString("sort_$scope", sort.key)?.apply()
    }

    // ── বাংলা/ইংরেজি অঙ্ক এক করে নিয়ে "natural" তুলনা: ১০ম > ৯ম, ১১ তম > ১০ম ──
    private fun normDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '০'..'৯') ('0' + (c - '০')) else c)
    }

    private val TOKEN = Regex("(\\d+)|(\\D+)")

    fun naturalCompare(a: String, b: String): Int {
        val ta = TOKEN.findAll(normDigits(a).trim().lowercase()).map { it.value }.toList()
        val tb = TOKEN.findAll(normDigits(b).trim().lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ta.size, tb.size)) {
            val x = ta[i]; val y = tb[i]
            val nx = x.toLongOrNull(); val ny = y.toLongOrNull()
            val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
            if (c != 0) return c
        }
        return ta.size.compareTo(tb.size)
    }

    private val YEAR = Regex("(19|20)\\d{2}")
    private val YEAR_RANGE = Regex("(?<!\\d)((?:19|20)\\d{2})\\s*[-–/]\\s*(\\d{2,4})(?!\\d)")

    /** এক স্ট্রিং-এর সবচেয়ে শেষের (বড়) সাল — "২০২৩-২৪" হলে ২০২৪; না পেলে 0 */
    private fun lastYearIn(raw: String): Int {
        val s = normDigits(raw)
        var best = 0
        for (m in YEAR.findAll(s)) best = maxOf(best, m.value.toInt())
        for (m in YEAR_RANGE.findAll(s)) {
            val start = m.groupValues[1]
            val end = m.groupValues[2]
            val endYear = if (end.length == 4) end.toIntOrNull() ?: 0
                          else ((start.take(2) + end).toIntOrNull() ?: 0)
            best = maxOf(best, endYear)
        }
        return best
    }

    /**
     * নাম(গুলো) থেকে সাল; না পেলে 0। প্রথম যে স্ট্রিং-এ সাল পাওয়া যায় সেটার সবচেয়ে শেষের
     * (বড়) সালটাই ধরা হয়। কল করার সময় আগে `years` তালিকা (কার্ডে যে সাল দেখায়), তারপর নাম।
     */
    fun extractYear(vararg names: String): Int {
        for (n in names) {
            val y = lastYearIn(n)
            if (y > 0) return y
        }
        return 0
    }

    /** কার্ডের "শেষের সাল": কার্ডে দেখানো সালগুলোর মধ্যে সবচেয়ে বড়; না থাকলে নাম/সাব-নাম থেকে */
    fun cardYear(years: List<String>, name: String, subNames: List<String>): Int {
        val fromYears = years.maxOfOrNull { lastYearIn(it) } ?: 0
        if (fromYears > 0) return fromYears
        return extractYear(name, *subNames.toTypedArray())
    }

    /** stable sort — একই মানের আইটেমের আগের আপেক্ষিক ক্রম অক্ষুণ্ণ */
    fun <T> apply(
        items: List<T>, sort: QBankSort,
        name: (T) -> String, subNames: (T) -> List<String>,
        totalQ: (T) -> Int, progressPct: (T) -> Int,
        years: (T) -> List<String> = { emptyList() }
    ): List<T> = when (sort) {
        QBankSort.DEFAULT   -> items
        QBankSort.NAME_ASC  -> items.sortedWith { a, b -> naturalCompare(name(a), name(b)) }
        QBankSort.NAME_DESC -> items.sortedWith { a, b -> naturalCompare(name(b), name(a)) }
        QBankSort.YEAR_DESC -> items.sortedByDescending { cardYear(years(it), name(it), subNames(it)) }
        QBankSort.YEAR_ASC  -> items.sortedWith(compareBy<T> { cardYear(years(it), name(it), subNames(it)) == 0 }
            .thenBy { cardYear(years(it), name(it), subNames(it)) })
        QBankSort.Q_DESC    -> items.sortedByDescending { totalQ(it) }
        QBankSort.PROG_ASC  -> items.sortedBy { progressPct(it) }
        QBankSort.PROG_DESC -> items.sortedByDescending { progressPct(it) }
    }
}
