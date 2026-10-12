package com.hanif.smartstudy.util

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * গুগল সার্চ চালু করার একটাই জায়গা — সিলেকশন-টুলবারের "ওয়েব সার্চ" আর প্রশ্নের পাশের 🔍 বাটন, দুটোই এটা ব্যবহার করে।
 *
 * সমস্যা ছিল: Google অ্যাপের আগের সার্চ-স্ক্রিন ব্যাকগ্রাউন্ডে থেকে যেত, আর নতুন সার্চ পাঠালে অ্যাপ সেই পুরনো
 * স্ক্রিনটাই সামনে এনে আগের শব্দ দেখাত (ব্যাক করলেও সার্চ বারে আগেরটাই)। এখন Google অ্যাপের পুরনো টাস্ক
 * পরিষ্কার (CLEAR_TASK) করে প্রতিবার একদম নতুন সার্চ খোলা হয় — তাই সবসময় সর্বশেষ সার্চটাই আসবে।
 * Google অ্যাপ না থাকলে সাধারণ web-search হ্যান্ডলার, তাও না থাকলে ব্রাউজারে google.com/search।
 */
object WebSearchLauncher {

    private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    private val HTML = Regex("<[^>]+>")
    private val SPACES = Regex("\\s+")
    private val LEAD_NO = Regex("^\\s*[\\d০-৯]+\\s*[.)।:]\\s*")

    /** সার্চের জন্য টেক্সট পরিষ্কার: HTML/প্রশ্ন-নম্বর বাদ, বাড়তি স্পেস কমানো, সর্বোচ্চ ২৫০ অক্ষর */
    fun cleanQuery(raw: String): String =
        raw.replace(HTML, " ").replace(LEAD_NO, "").replace(SPACES, " ").trim().take(250)

    /** সার্চ চালু হলে true */
    fun search(context: Context, raw: String): Boolean {
        val q = cleanQuery(raw)
        if (q.isBlank()) return false
        val app = context.applicationContext

        // ১) Google অ্যাপ — প্রতিবার নতুন করে (পুরনো সার্চ-স্ক্রিন মুছে)
        try {
            app.startActivity(Intent(Intent.ACTION_WEB_SEARCH).apply {
                setPackage(GOOGLE_APP)
                putExtra(SearchManager.QUERY, q)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            return true
        } catch (_: ActivityNotFoundException) { } catch (_: Exception) { }

        // ২) অন্য যেকোনো web-search হ্যান্ডলার
        try {
            app.startActivity(Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, q)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return true
        } catch (_: ActivityNotFoundException) { } catch (_: Exception) { }

        // ৩) ব্রাউজার
        return try {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: Exception) { false }
    }
}
