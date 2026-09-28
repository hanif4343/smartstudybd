package com.hanif.smartstudy.util

import com.hanif.smartstudy.data.model.SubTopicEntry
import java.math.BigInteger

/**
 * টপিক-লিস্টের ক্রমের একমাত্র সোর্স-অফ-ট্রুথ।
 *
 * আগে দুটো আলাদা কোড-পথ দুইভাবে সাজাত (Room পথে শুধু নামের ক্রম, পুরনো cache-পথে নাম-ভিত্তিক
 * admin-map) — তাই অনলাইন/অফলাইনে আলাদা ক্রম দেখাত। এখন অনলাইন, অফলাইন, রিফ্রেশ, back,
 * Move/Rename-এর পর — সব জায়গা এই ফাইলের displayOrder()/serialOrder() ব্যবহার করে।
 *
 * নিয়ম (displayOrder — সাধারণ ইউজার ও admin-এর স্বাভাবিক ভিউ):
 *   ১) অসম্পূর্ণ টপিক আগে, ১০০% সম্পন্ন টপিক একদম নিচে
 *   ২) প্রতিটা ভাগের ভেতরে admin-এর সিরিয়াল (sortOrder ১,২,৩…) অনুযায়ী
 *   ৩) সিরিয়াল-না-দেওয়া (sortOrder ≤ ০) টপিক নিজের ভাগের শেষে, নামের "প্রাকৃতিক" ক্রমে
 *      ("অধ্যায় ২" < "অধ্যায় ১০", বাংলা/ইংরেজি অঙ্ক দুটোই)
 *   ৪) তারপরও সমান হলে topicId দিয়ে — যাতে ফলাফল সবসময় একই (deterministic)
 * সিরিয়াল কখনো বদলায় না, শুধু সাজানোর সময় সম্পন্নরা নিচে নামে — তাই প্রগ্রেস রিসেট করলে
 * টপিক নিজের আসল জায়গায় ফিরে আসে।
 *
 * serialOrder — admin-এর সিরিয়াল সাজানোর ভিউ (সম্পন্ন-নিচে-নামা বন্ধ), যাতে admin আসল সিরিয়াল দেখে।
 */
object TopicOrdering {

    fun displayOrder(list: List<SubTopicEntry>): List<SubTopicEntry> =
        list.sortedWith(comparator(sinkCompleted = true))

    fun serialOrder(list: List<SubTopicEntry>): List<SubTopicEntry> =
        list.sortedWith(comparator(sinkCompleted = false))

    private fun comparator(sinkCompleted: Boolean): Comparator<SubTopicEntry> {
        val cmp = Comparator<SubTopicEntry> { a, b ->
            // model-test (virtual) কার্ড সবসময় সবার আগে
            val m = (if (a.isModelTest) 0 else 1).compareTo(if (b.isModelTest) 0 else 1)
            if (m != 0) return@Comparator m
            if (sinkCompleted) {
                val c = a.isComplete.compareTo(b.isComplete)      // false(অসম্পূর্ণ) আগে
                if (c != 0) return@Comparator c
            }
            val sa = if (a.sortOrder > 0) a.sortOrder else Int.MAX_VALUE
            val sb = if (b.sortOrder > 0) b.sortOrder else Int.MAX_VALUE
            if (sa != sb) return@Comparator sa.compareTo(sb)
            val n = naturalCompare(a.name, b.name)
            if (n != 0) n else a.topicId.compareTo(b.topicId)
        }
        return cmp
    }

    // ── প্রাকৃতিক নাম-তুলনা: অঙ্কের সারি সংখ্যা হিসেবে, বাকিটা case-insensitive ──
    fun naturalCompare(x: String, y: String): Int {
        val a = normalizeDigits(x).trim().lowercase()
        val b = normalizeDigits(y).trim().lowercase()
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]; val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i; while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j; while (ej < b.length && b[ej].isDigit()) ej++
                val na = BigInteger(a.substring(i, ei)); val nb = BigInteger(b.substring(j, ej))
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei; j = ej
            } else {
                if (ca != cb) return ca.compareTo(cb)
                i++; j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }

    /** বাংলা অঙ্ক (০-৯) → ইংরেজি, যাতে "১০" আর "10" একই সংখ্যা হিসেবে ধরা হয় */
    private fun normalizeDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch in '\u09E6'..'\u09EF') ('0' + (ch - '\u09E6')) else ch)
        return sb.toString()
    }
}
