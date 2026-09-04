package com.hanif.smartstudy.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * ── 🤖 "AI ব্যাখ্যা" বাটনের রেজাল্ট ক্যাশ — শুধু ইউজারের ফোনেই থাকে ──
 *
 * এই ক্যাশ ইচ্ছাকৃতভাবে সম্পূর্ণ লোকাল: শুধু app-এর নিজস্ব internal storage-এ
 * (`context.filesDir`) একটা সাধারণ JSON ফাইলে (key = question sourceKey,
 * value = AI-জেনারেটেড ব্যাখ্যা) সেভ থাকে। এটা কখনো Firebase/GAS/CDN-এ sync
 * হয় না, backup/restore-এও যায় না (allowBackup আলাদা কনফিগ, তবু নিশ্চিত হতে
 * এখানে কোনো cloud/network কল নেই) — শুধু এই ডিভাইসে, এই ইনস্টলেই থাকবে।
 *
 * Room ব্যবহার করা হয়নি ইচ্ছাকৃতভাবে — একটা নতুন Entity/schema migration যোগ
 * করলে সেটা অন্য সব ইউজারের existing লোকাল ডেটাবেসকে ঝুঁকিতে ফেলতে পারে
 * (দেখো README_FIRST.md-এর "Room entity বদলালে migration ছাড়া মার্জ কোরো না"
 * ওয়ার্নিং)। এই সাধারণ key-value ফাইল-ক্যাশ সেই ঝুঁকি সম্পূর্ণ এড়িয়ে যায়।
 */
object AiExplanationCache {

    private const val TAG = "AiExplanationCache"
    private const val FILE_NAME = "ai_explanations_local.json"
    private const val MAX_ENTRIES = 500 // অ্যাপের স্টোরেজ যেন অসীম বেড়ে না যায়

    private val mutex = Mutex()
    @Volatile private var cache: LinkedHashMap<String, String>? = null

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private suspend fun ensureLoaded(context: Context): LinkedHashMap<String, String> {
        cache?.let { return it }
        return mutex.withLock {
            cache?.let { return it }
            val loaded = LinkedHashMap<String, String>()
            try {
                val f = file(context)
                if (f.exists()) {
                    val obj = JSONObject(f.readText())
                    obj.keys().forEach { k -> loaded[k] = obj.optString(k) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "load failed (ক্ষতিকর না, খালি ক্যাশ ধরে নেওয়া হলো): ${e.message}")
            }
            cache = loaded
            loaded
        }
    }

    /** questionKey হিসেবে item.sourceKey() ব্যবহার করুন ("sheet|id" — Quiz/QBank/
     *  Study মিক্স হলেও ইউনিক থাকে)। আগে থেকে সেভ করা থাকলে সাথে সাথেই রিটার্ন করে,
     *  ফের API কল লাগে না। */
    suspend fun get(context: Context, questionKey: String): String? = withContext(Dispatchers.IO) {
        ensureLoaded(context)[questionKey]
    }

    suspend fun save(context: Context, questionKey: String, explanation: String) {
        if (questionKey.isBlank() || explanation.isBlank()) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val map = cache ?: LinkedHashMap<String, String>().also { cache = it }
                map[questionKey] = explanation
                // ── সবচেয়ে পুরনো এন্ট্রি ছেঁটে ফেলা (map insertion-order অনুযায়ী প্রথমটা
                // সবচেয়ে পুরনো) — যাতে ফাইল/মেমরি অসীম না বাড়ে ──
                while (map.size > MAX_ENTRIES) {
                    val oldestKey = map.keys.firstOrNull() ?: break
                    map.remove(oldestKey)
                }
                try {
                    val obj = JSONObject()
                    map.forEach { (k, v) -> obj.put(k, v) }
                    file(context).writeText(obj.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "save failed (ক্ষতিকর না, শুধু এই সেশনে re-fetch লাগবে): ${e.message}")
                }
            }
        }
    }
}
