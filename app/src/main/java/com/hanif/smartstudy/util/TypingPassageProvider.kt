package com.hanif.smartstudy.util

import android.content.Context
import android.util.Log
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.TypingSheetPassageEntity
import com.hanif.smartstudy.data.remote.ContentFetchService
import com.hanif.smartstudy.ui.typing.PassageInfo

/**
 * টাইপিং প্র্যাকটিসের ডিফল্ট প্যাসেজ পুল — আগে TypingPracticeScreen.kt-এ হার্ডকোডেড
 * তালিকা ছিল, এখন Google Sheet-এর "Typing" ট্যাব (headers: id, language, content,
 * updatedAt — Firebase হয়ে সিঙ্ক হয়) থেকে রানটাইমে লোড হয়। Admin App থেকে Typing
 * ট্যাবে যোগ করা যেকোনো কনটেন্ট এই একই পথে সরাসরি অ্যাপে চলে আসবে।
 *
 * ক্রম: (১) এই app-process-এ একবার fetch হয়ে গেলে RAM cache থেকেই সার্ভ হয়
 *       (২) নেট থাকলে ফ্রেশ fetch, সফল হলে Room-এ cache করে রাখে (অফলাইন fallback-এর জন্য)
 *       (৩) নেট না থাকলে/fetch ব্যর্থ হলে Room-এর আগের cache থেকে রিটার্ন করে
 *       (৪) দুটোই খালি হলে (একদম প্রথমবার, নেট ছাড়া) খালি লিস্ট — caller-রা এটা
 *           নিরাপদে হ্যান্ডেল করে (দেখো TypingPracticeScreen.kt-এর fallbackPassageFor)
 */
object TypingPassageProvider {
    private const val TAG = "TypingPassageProvider"

    @Volatile private var ramCache: List<PassageInfo>? = null

    suspend fun getPassages(context: Context): List<PassageInfo> {
        ramCache?.let { return it }

        val dao = AppDatabase.getInstance(context).typingSheetPassageDao()

        val fresh = try {
            ContentFetchService.fetchTypingPassages()
        } catch (e: Exception) {
            Log.w(TAG, "fetchTypingPassages failed: ${e.message}")
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
            try {
                dao.getAll()
                    .filter { it.content.isNotBlank() }
                    .map { PassageInfo(it.content, "all") }
            } catch (e: Exception) {
                Log.w(TAG, "cache read failed: ${e.message}")
                emptyList()
            }
        }

        if (result.isNotEmpty()) ramCache = result
        return result
    }
}
