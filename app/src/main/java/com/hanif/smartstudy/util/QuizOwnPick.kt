package com.hanif.smartstudy.util

import java.text.Normalizer

/**
 * Quiz-এর শেষে "+ Set Own Subject" কার্ডের ড্রপডাউন তালিকা — SSC, HSC আর ছবিসহ বিষয়গুলো।
 *
 *  • SSC / HSC  → audience-tag ওভাররাইড (SSC = "Class 10", HSC = "Class 12") — ওই
 *                 audience-এর সব Subject/Topic/প্রশ্ন Quiz-এ দেখায়।
 *  • একটা বিষয় → শিটের Subject-এর নামের সাথে মিলিয়ে (matches()) শুধু সেই বিষয় দেখায়;
 *                 audience, ওই Subject-এর tag_id থেকে QuizViewModel.setOwnPick()-এ বের হয়।
 *
 * key-ই DataStore-এ সেভ হয় (SessionManager.setQuizOwnPick), তাই অ্যাপ বন্ধ করে খুললেও থাকে।
 */
object QuizOwnPick {

    data class Option(
        val key: String,                       // স্থায়ী আইডি (সেভ হয়)
        val label: String,                     // ড্রপডাউন/কার্ডে দেখানো নাম
        val audienceTag: String? = null,       // শুধু SSC/HSC-এর জন্য
        val bn: List<String> = emptyList(),    // নামে এর যেকোনোটা থাকলেই মিলবে
        val en: List<List<String>> = emptyList() // ইংরেজি নামের শব্দ-তালিকা (সবগুলো শব্দ থাকলে মিলবে)
    )

    val SSC = Option("SSC", "SSC", audienceTag = "Class 10")
    val HSC = Option("HSC", "HSC", audienceTag = "Class 12")

    // ⚠️ "য়" (U+09DF) এড়াতে "রসায" ব্যবহার — সিটের নামে য় একক/ভাঙা দু-রূপেই আসতে পারে
    val ALL: List<Option> = listOf(
        SSC, HSC,
        Option("physics",   "পদার্থবিজ্ঞান",  bn = listOf("পদার্থ"), en = listOf(listOf("physics"))),
        Option("chemistry", "রসায়ন",          bn = listOf("রসায"), en = listOf(listOf("chemistry"))),
        Option("math",      "গণিত",           bn = listOf("গণিত"), en = listOf(listOf("math"), listOf("maths"), listOf("mathematics"))),
        Option("psychology","মনোবিজ্ঞান",     bn = listOf("মনোবিজ্ঞান"), en = listOf(listOf("psychology"))),
        Option("civics",    "রাষ্ট্রবিজ্ঞান",  bn = listOf("রাষ্ট্রবিজ্ঞান", "পৌরনীতি"), en = listOf(listOf("civics"), listOf("political", "science"))),
        Option("geography", "ভূগোল",          bn = listOf("ভূগোল"), en = listOf(listOf("geography"))),
        Option("english_literature", "ইংরেজি সাহিত্য", bn = listOf("ইংরেজি সাহিত্য"), en = listOf(listOf("english", "literature"))),
        Option("english_grammar",    "ইংরেজি ব্যাকরণ", bn = listOf("ইংরেজি ব্যাকরণ", "ইংরেজি গ্রামার"), en = listOf(listOf("english", "grammar"))),
        Option("bangla_literature",  "বাংলা সাহিত্য",  bn = listOf("বাংলা সাহিত্য"), en = listOf(listOf("bangla", "literature"))),
        Option("bangla_grammar",     "বাংলা ব্যাকরণ",  bn = listOf("বাংলা ব্যাকরণ", "বাংলা গ্রামার"), en = listOf(listOf("bangla", "grammar"))),
        Option("history",   "ইতিহাস",         bn = listOf("ইতিহাস"), en = listOf(listOf("history"))),
        Option("islamic",   "ইসলামিক স্টাডিজ", bn = listOf("ইসলাম"), en = listOf(listOf("islamic"), listOf("islam"))),
        Option("economics", "অর্থনীতি",        bn = listOf("অর্থনীতি"), en = listOf(listOf("economics"))),
        Option("sociology", "সমাজবিজ্ঞান",     bn = listOf("সমাজবিজ্ঞান"), en = listOf(listOf("sociology"))),
        Option("law",       "আইন",            bn = listOf("আইন"), en = listOf(listOf("law"))),
        Option("computer",  "কম্পিউটার বিজ্ঞান ও আইসিটি", bn = listOf("কম্পিউটার", "আইসিটি", "তথ্য ও যোগাযোগ"), en = listOf(listOf("computer"), listOf("ict"))),
        Option("environment","পরিবেশ বিজ্ঞান", bn = listOf("পরিবেশ"), en = listOf(listOf("environment"), listOf("environmental"))),
        Option("fine_arts", "চারুকলা",         bn = listOf("চারুকলা"), en = listOf(listOf("fine", "arts"))),
        Option("philosophy","দর্শন",           bn = listOf("দর্শন"), en = listOf(listOf("philosophy"))),
        // ছবিতে যেমন লেখা আছে তেমনই ("পশ্চিম বিজ্ঞান") — নাম বদলাতে চাইলে শুধু label আর bn বদলান
        Option("west_science","পশ্চিম বিজ্ঞান", bn = listOf("পশ্চিম বিজ্ঞান", "মহাকাশ", "জ্যোতির্বিজ্ঞান"), en = listOf(listOf("astronomy"), listOf("space", "science")))
    )

    fun find(key: String?): Option? = if (key.isNullOrBlank()) null else ALL.firstOrNull { it.key == key }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    /** শিটের Subject-এর নাম এই বিষয়ের সাথে মেলে কিনা (SSC/HSC-এর জন্য সবসময় false — ওগুলো audience-ভিত্তিক) */
    fun matches(opt: Option, subjectName: String): Boolean {
        if (opt.audienceTag != null) return false
        val name = nfc(subjectName).lowercase()
        if (opt.bn.any { name.contains(nfc(it).lowercase()) }) return true
        val tokens = name.split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }.toSet()
        return opt.en.any { group -> group.all { it in tokens } }
    }
}
