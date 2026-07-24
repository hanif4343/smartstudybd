package com.hanif.smartstudy.util

import android.content.Context
import android.util.Log
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.TypingSheetPassageEntity
import com.hanif.smartstudy.data.model.DataSourceMode
import com.hanif.smartstudy.data.remote.ContentFetchService
import com.hanif.smartstudy.data.remote.GasContentService
import com.hanif.smartstudy.ui.typing.PassageInfo

/**
 * টাইপিং প্র্যাকটিসের ডিফল্ট প্যাসেজ পুল — আগে TypingPracticeScreen.kt-এ হার্ডকোডেড
 * তালিকা ছিল, এখন Google Sheet-এর "Typing" ট্যাব (headers: id, language, content,
 * updatedAt) থেকে রানটাইমে লোড হয়। Admin App থেকে Typing ট্যাবে যোগ করা যেকোনো
 * কনটেন্ট এই একই পথে সরাসরি অ্যাপে চলে আসবে।
 *
 * Settings-এ "Data Source" ড্রপডাউন অনুযায়ী উৎস বদলায় — ঠিক Quiz/QBank/Study-এর
 * মতোই (দেখো ContentFetchService.kt/GasContentService.kt):
 *   - FIREBASE     → Firebase "Typing" node (GAS syncToFirebase() যেটা সিঙ্ক করে)
 *   - GOOGLE_SHEET → GAS getSheetRows action দিয়ে সরাসরি Google Sheet (Firebase বাইপাস)
 *
 * ক্রম: (১) এই app-process-এ একবার fetch হয়ে গেলে (একই mode-এর জন্য) RAM cache থেকেই সার্ভ হয়
 *       (২) নেট থাকলে ফ্রেশ fetch, সফল হলে Room-এ cache করে রাখে (অফলাইন fallback-এর জন্য)
 *       (৩) নেট না থাকলে/fetch ব্যর্থ হলে Room-এর আগের cache থেকে রিটার্ন করে
 *       (৪) দুটোই খালি হলে (একদম প্রথমবার, নেট ছাড়া) খালি লিস্ট — caller-রা এটা
 *           নিরাপদে হ্যান্ডেল করে (দেখো TypingPracticeScreen.kt-এর fallbackPassageFor)
 */
object TypingPassageProvider {
    private const val TAG = "TypingPassageProvider"

    @Volatile private var ramCache: List<PassageInfo>? = null
    @Volatile private var ramCacheMode: DataSourceMode? = null

    suspend fun getPassages(context: Context): List<PassageInfo> {
        val mode = SessionManager(context).getDataSourceMode()
        ramCache?.let { if (ramCacheMode == mode) return it }

        val dao = AppDatabase.getInstance(context).typingSheetPassageDao()

        val fresh = try {
            if (mode == DataSourceMode.GOOGLE_SHEET) GasContentService.fetchTypingPassages()
            else ContentFetchService.fetchTypingPassages()
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed (mode=$mode): ${e.message}")
            emptyList()
        }

        val result: List<PassageInfo> = if (fresh.isNotEmpty()) {
            try {
                dao.replaceAll(fresh.map {
                    TypingSheetPassageEntity(
                        id        = it.id,
                        language  = it.language,
                        content   = it.content,
                        updatedAt = it.updatedAt
                    )
                })
            } catch (e: Exception) {
                Log.w(TAG, "cache write failed: ${e.message}")
            }
            fresh.filter { it.content.isNotBlank() }
                .map { PassageInfo(it.content, "all") }
        } else {
            // ── fetch ব্যর্থ/খালি হলে Room-এর পুরনো cache — এটা যেই mode-এই আগে fetch
            // হয়ে থাকুক না কেন (Firebase/Google Sheet), সম্পূর্ণ কিছু না-থাকার চেয়ে
            // পুরনো প্যাসেজ পুল দেখানো ভালো ──
            try {
                dao.getAll()
                    .filter { it.content.isNotBlank() }
                    .map { PassageInfo(it.content, "all") }
            } catch (e: Exception) {
                Log.w(TAG, "cache read failed: ${e.message}")
                emptyList()
            }
        }

        if (result.isNotEmpty()) { ramCache = result; ramCacheMode = mode }
        return result
    }

    /** Settings-এ Data Source বদলালে (Firebase ↔ Google Sheet) MenuViewModel এটা কল করে,
     *  যাতে পরের getPassages() পুরনো mode-এর RAM cache না ব্যবহার করে নতুন সোর্স থেকে
     *  আবার fetch করে। */
    fun forceRefreshNextTime() {
        ramCache = null
        ramCacheMode = null
    }
}
