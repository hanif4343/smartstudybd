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

    /** "+", space বাদ দিয়ে normalize করো। ভ্যালিড না হলে null। */
    fun sanitize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace("+", "").replace(" ", "").replace("-", "")
        return if (DIGITS_ONLY.matches(cleaned)) cleaned else null
    }

    fun isValid(raw: String?): Boolean = sanitize(raw) != null
}
