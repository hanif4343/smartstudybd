package com.hanif.smartstudy.util

import com.hanif.smartstudy.data.local.MistakeErrorType
import kotlin.math.max
import kotlin.math.min

/**
 * শব্দ-লেভেল টাইপিং ভুল বিশ্লেষণের মূল ইঞ্জিন।
 *
 * ব্যবহার: TypingPracticeScreen প্রতিটা শব্দ-বাউন্ডারিতে (স্পেস/প্যাসেজ-শেষ) এসে
 * targetWord বনাম typedWord পাঠাবে, এখান থেকে ফেরত আসবে ভুলের ধরন — সেটাই
 * TypingMistakeLogger দিয়ে Room-এ সেভ হবে।
 *
 * রেফারেন্স: SmartStudyBD-টাইপিং-অডিট-ও-রোডম্যাপ.md সেকশন ৪ (ধরন A বনাম ধরন B)
 */
object TypingErrorAnalyzer {

    // বাংলা কার/মাত্রা-চিহ্ন — এই সেটের মধ্যে হওয়া substitution-কে KAR_MATRA ধরা হয়,
    // কারণ এগুলো গুলিয়ে ফেলা (ি/ী, ু/ূ ইত্যাদি) বাংলা টাইপিং-এ সবচেয়ে কমন ভুল
    private val KAR_MATRA_CHARS = setOf(
        'া', 'ি', 'ী', 'ু', 'ূ', 'ৃ', 'ে', 'ৈ', 'ো', 'ৌ', 'ং', 'ঃ', 'ঁ'
    )

    /** ক্লাসিক Levenshtein (edit distance) — insertion/deletion/substitution প্রতিটার cost ১ */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * targetWord আর typedWord তুলনা করে ভুলের ধরন বের করে।
     * শুধু তখনই কল করা উচিত যখন দুটো শব্দ ইতিমধ্যে অসমান (ভুল আছে) — এটা নিজে
     * সমতা যাচাই করে না।
     */
    fun classify(targetWord: String, typedWord: String): MistakeErrorType {
        val distance = levenshtein(targetWord, typedWord)

        // ── ধরন B (sync-loss): edit-distance শব্দের দৈর্ঘ্যের তুলনায় অস্বাভাবিক বেশি
        // (অর্ধেকের বেশি অক্ষর ভিন্ন) — এটা বানান-ভুল না, টেক্সট-ট্র্যাকিং হারানো ──
        val longerLen = max(targetWord.length, typedWord.length)
        if (longerLen > 0 && distance.toFloat() / longerLen > 0.5f && longerLen >= 4) {
            return MistakeErrorType.SYNC_LOSS
        }

        // ── দৈর্ঘ্য সমান হলে — ঠিক কোন পজিশনগুলো ভিন্ন, সেখান থেকে substitution/kar-matra বের করা ──
        if (targetWord.length == typedWord.length) {
            val diffIndices = targetWord.indices.filter { targetWord[it] != typedWord[it] }
            // সবগুলো ভিন্ন অক্ষরই যদি কার/মাত্রা-চিহ্ন হয়, তাহলে KAR_MATRA
            val allKarMatra = diffIndices.isNotEmpty() && diffIndices.all {
                targetWord[it] in KAR_MATRA_CHARS || typedWord[it] in KAR_MATRA_CHARS
            }
            return if (allKarMatra) MistakeErrorType.KAR_MATRA else MistakeErrorType.SUBSTITUTION
        }

        // ── দৈর্ঘ্য ভিন্ন — typed target-এর চেয়ে লম্বা মানে অতিরিক্ত অক্ষর ঢুকেছে (INSERTION),
        // ছোট হলে অক্ষর বাদ পড়েছে (DELETION) ──
        return if (typedWord.length > targetWord.length) MistakeErrorType.INSERTION
        else MistakeErrorType.DELETION
    }

    /**
     * প্যাসেজকে শব্দে ভাগ করে, প্রতিটা শব্দের (word, startIndex, endIndex) রাখে —
     * endIndex exclusive, অর্থাৎ passage.substring(start, end) == word।
     * TypingPracticeScreen এটা দিয়ে ঠিক কোন index-এ কোন শব্দ শেষ হচ্ছে বের করে,
     * ইউজার সেই index পার হলেই ওই শব্দটা compare করে।
     */
    data class WordSpan(val word: String, val start: Int, val end: Int)

    /** প্যাসেজে বাংলা ইউনিকোড ব্লকের (U+0980–U+09FF) কোনো অক্ষর থাকলে "bn", নাহলে "en" */
    fun detectLanguage(text: String): String =
        if (text.any { it.code in 0x0980..0x09FF }) "bn" else "en"

    fun wordSpans(passage: String): List<WordSpan> {
        val spans = mutableListOf<WordSpan>()
        var i = 0
        while (i < passage.length) {
            while (i < passage.length && passage[i].isWhitespace()) i++
            if (i >= passage.length) break
            val start = i
            while (i < passage.length && !passage[i].isWhitespace()) i++
            spans.add(WordSpan(passage.substring(start, i), start, i))
        }
        return spans
    }
}
