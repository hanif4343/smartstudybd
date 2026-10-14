package com.hanif.smartstudy.util

/**
 * Exam_Appearances-এর "year" যেভাবেই আসুক (বাংলা/ইংরেজি অঙ্ক, "2024.0", ISO তারিখ,
 * শিটের Date-সেল থেকে আসা epoch-millis, "২০২৩-২৪" ইত্যাদি) — সবকিছুকে একটাই সালে নামিয়ে
 * UI-তে বাংলা অঙ্কে দেখানোর জন্য। সাল না পেলে ফাঁকা স্ট্রিং।
 */
object YearFormat {
    private val RANGE = Regex("(?<!\\d)(\\d{4})\\s*[-–/]\\s*(\\d{2,4})(?!\\d)")
    private val YEAR4 = Regex("(?<!\\d)((?:19|20)\\d{2})(?!\\d)")

    private fun digitsToLatin(s: String): String = buildString {
        for (c in s) append(if (c in '\u09E6'..'\u09EF') ('0' + (c - '\u09E6')) else c)
    }

    /** ইংরেজি অঙ্কে স্বাভাবিক সাল (যেমন "2024" বা "2023-24"); না পেলে "" */
    fun latin(raw: String?): String {
        val s = digitsToLatin((raw ?: "").trim())
        if (s.isBlank() || s.none { it.isDigit() }) return ""
        // epoch-millis / epoch-seconds (শিটের তারিখ-সেল)
        if (s.all { it.isDigit() } && s.length >= 9) {
            val ms = s.toLongOrNull() ?: return ""
            val millis = if (s.length >= 12) ms else ms * 1000
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = millis
            return cal.get(java.util.Calendar.YEAR).toString()
        }
        RANGE.find(s)?.let { return it.value.replace(" ", "") }
        YEAR4.find(s)?.let { return it.groupValues[1] }
        return s.filter { it.isDigit() }.take(4)
    }

    fun toBn(latin: String): String = buildString {
        for (c in latin) append(if (c in '0'..'9') ('\u09E6' + (c - '0')) else c)
    }

    /** UI-তে দেখানোর জন্য — সবসময় বাংলা অঙ্ক */
    fun bn(raw: String?): String = toBn(latin(raw))

    /** সাজানোর জন্য সংখ্যা মান */
    fun sortKey(raw: String?): Int = latin(raw).takeWhile { it.isDigit() }.toIntOrNull() ?: 0
}
