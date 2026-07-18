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
        'গ', 'ঘ', 'ড়', 'ঢ়',
        ';', '\'', 'ৎ', 'ঃ', 'ঁ',
        ',', '।', '.', '/',
        '(', ')', '[', ']', '{', '}'
    )

    fun handOf(ch: Char): Hand = if (ch in RIGHT_HAND_CHARS) Hand.RIGHT else Hand.LEFT

    /** স্পেস/লাইনব্রেকের মতো নন-ক্যারেক্টার কী বাদ দিয়ে হাত-ভিত্তিক গণনা করা উচিত */
    fun isTrackable(ch: Char): Boolean = !ch.isWhitespace()
}
