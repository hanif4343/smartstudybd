package com.hanif.smartstudy.util

/**
 * ════════════════════════════════════════════════════════════════
 *  PhoneValidator
 * ════════════════════════════════════════════════════════════════
 * ফোন নম্বর Firebase REST URL এ ব্যবহারের আগে অবশ্যই এই দিয়ে validate
 * করতে হবে। কারণ: raw user input সরাসরি URL এ জুড়ে দিলে (যেমন
 * "$baseUrl/Users/$phone.json") phone এর ভেতরে "/" ".." ইত্যাদি character
 * থাকলে সেটা অন্য Firebase path এ গিয়ে read/write করার সুযোগ তৈরি করে
 * (path-injection) — signup ফর্মে কেউ phone ফিল্ডে এমন value দিলে এটা
 * ঘটতে পারত। এই validator দিয়ে নিশ্চিত হয় যে normalize করার পরে phone এ
 * শুধু ০-৯ digit আছে, আর কিছু না।
 */
object PhoneValidator {

    // normalize এর পর শুধু digit, ৬-১৫ অক্ষর (বাংলাদেশি নম্বরের জন্য যথেষ্ট প্রশস্ত)
    private val DIGITS_ONLY = Regex("^[0-9]{6,15}$")

    /** "+", space, dash বাদ দিয়ে normalize করো। ভ্যালিড না হলে null। */
    fun sanitize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace("+", "").replace(" ", "").replace("-", "")
        return if (DIGITS_ONLY.matches(cleaned)) cleaned else null
    }

    fun isValid(raw: String?): Boolean = sanitize(raw) != null

    // ── বাংলাদেশি নম্বর ⇄ E.164 কনভার্সন ──
    // DB তে phone "01XXXXXXXXX" (লোকাল, ১১ ডিজিট) ফরম্যাটে key হিসেবে আছে,
    // কিন্তু Firebase Phone Auth (OTP) এর জন্য অবশ্যই E.164 ("+8801XXXXXXXXX")
    // ফরম্যাট লাগে। এই দুটো ফাংশন এই দুই ফরম্যাটের মধ্যে নিরাপদে রূপান্তর করে।
    private val BD_MOBILE = Regex("^1[0-9]{9}$")

    /** যেকোনো reasonable ইনপুট ("01XX...", "8801XX...", "+8801XX...") থেকে E.164 বানায়, না মিললে null */
    fun toE164BD(rawLocal: String?): String? {
        val digits = sanitize(rawLocal) ?: return null
        val tenDigit = when {
            digits.length == 11 && digits.startsWith("0")   -> digits.substring(1)
            digits.length == 13 && digits.startsWith("880")  -> digits.substring(3)
            digits.length == 10 && digits.startsWith("1")    -> digits
            else -> return null
        }
        if (!BD_MOBILE.matches(tenDigit)) return null
        return "+880$tenDigit"
    }

    /** E.164 ("+8801XXXXXXXXX") থেকে DB-key ফরম্যাট ("01XXXXXXXXX") এ ফেরায়, না মিললে null */
    fun fromE164BD(e164: String?): String? {
        if (e164.isNullOrBlank()) return null
        val digits = e164.removePrefix("+")
        if (!digits.startsWith("880")) return null
        val tenDigit = digits.substring(3)
        if (!BD_MOBILE.matches(tenDigit)) return null
        return "0$tenDigit"
    }
}
