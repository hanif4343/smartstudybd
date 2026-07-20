package com.hanif.smartstudy.util

/**
 * বিজয় কীবোর্ড লেআউটে কোন অক্ষর বাম হাতে, কোন অক্ষর ডান হাতে পড়ে — এই তালিকা
 * অনুমান করে বানানো হয়নি, ডেভেলপার নিজে তার আসল বিজয় কীবোর্ড থেকে verified করে
 * দিয়েছেন (দেখো SmartStudyBD-টাইপিং-অডিট-ও-রোডম্যাপ.md সেকশন ৫.২)।
 *
 * তালিকায় নেই এমন যেকোনো অক্ষর ডিফল্টভাবে বাম হাত ধরা হয়েছে।
 */
enum class Hand { LEFT, RIGHT }

object HandKeyMap {

    private val RIGHT_HAND_CHARS: Set<Char> = setOf(
        'ক', 'খ', 'ত', 'থ', 'দ', 'ধ', 'ব', 'ভ', 'ন', 'ণ',
        'স', 'ষ', 'ম', 'শ', 'চ', 'ছ', 'জ', 'ঝ', 'ঞ', 'হ',
        'গ', 'ঘ',
        ';', '\'', 'ৎ', 'ঃ', 'ঁ',
        ',', '।', '.', '/',
        '(', ')', '[', ']', '{', '}'
    )

    // ── ড়/ঢ়/য় — Unicode-এর composition-exclusion নিয়মের কারণে NFC normalize করলেও
    // এরা কখনো একক কোডপয়েন্টে ফেরে না, সবসময় base+nukta (২ ক্যারেক্টার) হিসেবেই থাকে
    // (দেখো normalizeBn()-এর কমেন্ট, TypingPracticeScreen.kt)। কিন্তু বাস্তবে এগুলো
    // কীবোর্ডে একটাই আলাদা ডেডিকেটেড কী দিয়ে টাইপ হয় (এক কী-প্রেস), তাই হাত-ভিত্তিক
    // পরিসংখ্যানে base+nukta জোড়াকে একসাথে ONE কী-প্রেস হিসেবে গোনা দরকার — আলাদা
    // দুইটা না, নাহলে ভুল হাতে (ডিফল্ট LEFT) গণনা হয়ে যায়। ──
    private const val NUKTA = '\u09BC'
    private val NUKTA_BASE_HAND: Map<Char, Hand> = mapOf(
        'ড' to Hand.RIGHT,  // ড় — ডেভেলপার তার আসল কীবোর্ড থেকে verified
        'ঢ' to Hand.RIGHT,  // ঢ় — ডেভেলপার verified
        'য' to Hand.RIGHT   // য় — ড়/ঢ়-এর পাশেই একই দিকে থাকার সম্ভাবনায় ধরে নেওয়া হলো,
                            // কিন্তু এটা আলাদাভাবে আসল কীবোর্ডে verify করা হয়নি — ভিন্ন
                            // হলে এখানে শুধু এই একটা লাইন বদলালেই যথেষ্ট
    )

    fun handOf(ch: Char): Hand = if (ch in RIGHT_HAND_CHARS) Hand.RIGHT else Hand.LEFT

    /** স্পেস/লাইনব্রেকের মতো নন-ক্যারেক্টার কী বাদ দিয়ে হাত-ভিত্তিক গণনা করা উচিত */
    fun isTrackable(ch: Char): Boolean = !ch.isWhitespace()

    /**
     * `passage`-এর `idx` পজিশনের ক্যারেক্টারটা আসলে কোন হাতের কোন কী-প্রেসের অংশ —
     * ড়/ঢ়/য়-এর মতো base+nukta জোড়াকে একটা মাত্র কী-প্রেস হিসেবে ধরে (দুইটা আলাদা না)।
     * রিটার্ন null মানে এই পজিশনটা আলাদাভাবে গোনার দরকার নেই (নুক্তা অংশ — ঠিক আগের
     * base ক্যারেক্টারের সাথেই ইতিমধ্যে একবার গোনা হয়ে গেছে)।
     */
    fun clusterHandOf(passage: String, idx: Int): Hand? {
        val ch = passage.getOrNull(idx) ?: return null
        if (ch == NUKTA) {
            val prev = passage.getOrNull(idx - 1)
            return if (prev != null && prev in NUKTA_BASE_HAND) null else handOf(ch)
        }
        if (ch in NUKTA_BASE_HAND && passage.getOrNull(idx + 1) == NUKTA) {
            return NUKTA_BASE_HAND.getValue(ch)
        }
        return handOf(ch)
    }
}
