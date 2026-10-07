package com.hanif.smartstudy.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * QBank-এর পদবী (post) / প্রতিষ্ঠান (institution) / সাল (year) লিস্টের সিরিয়াল — একমাত্র সোর্স।
 *
 * Quiz-এর টপিক-সিরিয়ালের মতোই "একবার সেট করলে স্থায়ী":
 *  • LOCAL (admin-এর সেট করা) — SharedPreferences-এ, কখনো expire/মুছে যায় না; অনলাইন/অফলাইন,
 *    রিফ্রেশ, Force-Resync, reference-sync, অ্যাপ রিস্টার্ট — কোনোটাতেই বদলায় না।
 *  • SERVER (CDN-এর posts.json/institutions.json-এর "sort_order" কলাম) — সাধারণ ইউজারদের জন্য;
 *    শুধু যেসব আইটেমে LOCAL ক্রম নেই সেগুলোতে কাজ করে।
 * যে আইটেমের কোনো ক্রম নেই সেটা আগের ডিফল্ট ক্রমে (নাম / সাল-ডিসেন্ডিং) শেষে থাকে।
 */
object QBankOrderStore {
    const val POST = "post"
    const val INSTITUTION = "institution"
    const val YEAR = "year"

    private const val PREFS = "qbank_order_v1"
    @Volatile private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getLocal(kind: String): List<String> = try {
        val raw = prefs?.getString("local_$kind", null) ?: return emptyList()
        val a = JSONArray(raw)
        (0 until a.length()).map { a.getString(it) }
    } catch (_: Exception) { emptyList() }

    fun setLocal(kind: String, list: List<String>) {
        prefs?.edit()?.putString("local_$kind", JSONArray(list).toString())?.apply()
    }

    fun getServer(kind: String): Map<String, Int> = try {
        val raw = prefs?.getString("server_$kind", null) ?: return emptyMap()
        val o = JSONObject(raw)
        o.keys().asSequence().associateWith { o.getInt(it) }
    } catch (_: Exception) { emptyMap() }

    /** CDN থেকে আসা সিরিয়াল (খালি হলে আগেরটা অপরিবর্তিত) */
    fun setServer(kind: String, map: Map<String, Int>) {
        if (map.isEmpty()) return
        val o = JSONObject(); map.forEach { (k, v) -> o.put(k, v) }
        prefs?.edit()?.putString("server_$kind", o.toString())?.apply()
    }

    /** items-কে সংরক্ষিত ক্রমে সাজায়; ক্রম-না-থাকা আইটেম শেষে, আগের আপেক্ষিক ক্রমে (stable)। */
    fun <T> apply(kind: String, items: List<T>, keyOf: (T) -> String): List<T> {
        val local = getLocal(kind)
        val server = getServer(kind)
        if (local.isEmpty() && server.isEmpty()) return items
        val localIdx = HashMap<String, Int>().also { m -> local.forEachIndexed { i, k -> m[k] = i } }
        return items.withIndex().sortedWith(
            compareBy<IndexedValue<T>>(
                { val k = keyOf(it.value); if (localIdx.containsKey(k)) 0 else if (server.containsKey(k)) 1 else 2 },
                { val k = keyOf(it.value); localIdx[k] ?: server[k] ?: 0 },
                { it.index }
            )
        ).map { it.value }
    }

    /**
     * newSeq (দেখানো আইটেমগুলোর নতুন ক্রম) পুরনো গ্লোবাল লিস্টে বসায় — শুধু ওই আইটেমগুলোর
     * নিজেদের "স্লটে"; বাকি (ফিল্টার/নেস্টেড কারণে অদেখা) আইটেমের আপেক্ষিক অবস্থান অক্ষুণ্ণ।
     */
    fun mergeOrder(oldGlobal: List<String>, shownBefore: List<String>, newSeq: List<String>): List<String> {
        val seq = newSeq.distinct()
        val result = oldGlobal.toMutableList()
        shownBefore.forEach { if (it !in result) result.add(it) }
        seq.forEach { if (it !in result) result.add(it) }
        val set = seq.toSet()
        val slots = result.indices.filter { result[it] in set }
        seq.forEachIndexed { i, k -> if (i < slots.size) result[slots[i]] = k }
        return result
    }
}
