package com.hanif.smartstudy.data.model

/**
 * Phase ৩ (আইটেম #1+#2): Key-unlock প্রগ্রেসিভ কারিকুলাম-এর স্ট্যাটিক ডেটা।
 *
 * ⚠️ গুরুত্বপূর্ণ সীমাবদ্ধতা: Android soft keyboard থেকে শুধু ফলাফল ক্যারেক্টার পাওয়া
 * যায় (কোন ফিজিক্যাল কী চাপা হলো তা না — বিশেষত Avro-এর মতো ফোনেটিক লেআউটে
 * এক-কী-এক-অক্ষরের সম্পর্কই নেই)। তাই এই কারিকুলাম **ক্যারেক্টার-ভিত্তিক** —
 * "কী" মানে এখানে physical keyboard key না, বরং একটা নির্দিষ্ট অক্ষর/চিহ্ন।
 * এটা বরং ভালো — যেকোনো keyboard app (Bijoy/Avro/Ridmik ইত্যাদি) দিয়ে কাজ করে।
 *
 * ⚠️ String ব্যবহার করা হয়েছে, Char না: ড়/ঢ়/য়-এর মতো কিছু বাংলা অক্ষর আসলে একাধিক
 * Unicode codepoint-এর সমন্বয়ে গঠিত (যেমন ড় = ড + nukta চিহ্ন, দুইটা codepoint) —
 * এগুলো একটা Kotlin Char literal-এ ধরানো যায় না ("Too many characters in a character
 * literal" কম্পাইল এরর দেয়), তাই পুরো কারিকুলাম String ব্যবহার করে (এক-অক্ষরের String
 * হলেও)। এটা future-এ যুক্তবর্ণ/ফলা (একাধিক codepoint) যোগ করতে গেলেও কাজে দেবে।
 *
 * বাংলা ৫৭ স্টেজ ও ইংরেজি ৩০ স্টেজ — Neonlipi-এর স্ক্রিনশটে যতটুকু স্পষ্ট দেখা
 * গেছে (স্টেজ ১-১৬, ২৯-৩৮) সেটা অনুসরণ করে, বাকি গ্যাপ (স্ক্রিনশটে দেখা যায়নি)
 * প্রচলিত Bijoy52 শেখার ক্রম (ঘনঘন-ব্যবহৃত ব্যঞ্জনবর্ণ/কার আগে, বিরল বর্ণ ও চিহ্ন
 * পরে) অনুযায়ী ভরাট করা হয়েছে — এটা best-effort reconstruction, প্র্যাকটিস ডেটা
 * জমার সাথে সাথে ভবিষ্যতে টিউন করা যাবে।
 */
object BijoyCurriculum {

    /** স্টেজ ইনডেক্স ১-ভিত্তিক — index 0 = স্টেজ ১ */
    val BANGLA_STAGES: List<List<String>> = listOf(
        listOf("ক", "া", "র", "ন", "ত", "ঁ"),   // স্টেজ ১ — "মাত্র ৬টি কী দিয়ে শুরু"
        listOf("স"), listOf("ম"), listOf("ব"), listOf("দ"), listOf("প"),
        listOf("ু"), listOf("গ"), listOf("জ"), listOf("হ"), listOf("ো"),
        listOf("ল"), listOf("শ"), listOf("চ"), listOf("য"), listOf("ৈ"),   // স্টেজ ১৬
        listOf("ি"), listOf("ী"), listOf("ূ"), listOf("ৃ"), listOf("ে"), listOf("ৌ"),
        listOf("খ"), listOf("ঘ"), listOf("ঙ"), listOf("ছ"), listOf("ঝ"), listOf("ঞ"),
        listOf("ট"), listOf("ঠ"),                                          // স্টেজ ৩০ পর্যন্ত (মাইলফলক)
        listOf("ড"), listOf("ঢ"), listOf("ণ"), listOf("থ"), listOf("ধ"),
        listOf("ফ"), listOf("ভ"), listOf("ষ"),
        listOf("ড়", "ঢ়"), listOf("য়"), listOf("ৎ"), listOf("ং"), listOf("ঃ"),
        listOf("অ"), listOf("আ"), listOf("ই"), listOf("ঈ"), listOf("উ"),
        listOf("ঊ"), listOf("ঋ"), listOf("এ"), listOf("ঐ"), listOf("ও"), listOf("ঔ"),  // স্টেজ ৫৪
        listOf(",", "."), listOf("@", "%", "&", "#"), listOf("!", "?", ";", ":"),          // স্টেজ ৫৫-৫৭ চিহ্ন
        // ── পর্ব ২.৩ ফিচার #২ (যুক্তাক্ষর কারিকুলাম): হসন্ত (্, U+09CD) পুরো আগের ৫৭
        // স্টেজে কোথাও ছিল না — অথচ প্রায় সব বাংলা যুক্তাক্ষর (ক্ত, ন্দ, স্প ইত্যাদি)
        // টাইপ করতে এটা অপরিহার্য। বিদ্যমান স্টেজ-নাম্বারিং না ভেঙে (পুরনো progress
        // ডেটা যেন invalid না হয়ে যায়) এটাকে **শেষে যোগ করা হলো**, মাঝে বসানো হয়নি।
        // এতক্ষণে (স্টেজ ৫৭) সব ব্যঞ্জনবর্ণ+স্বরবর্ণ আনলক হয়ে গেছে, তাই হসন্ত আনলক
        // হওয়ামাত্র সব common conjunct টাইপ করার সব "উপাদান" ইউজারের হাতে চলে আসে —
        // দেখো CurriculumProvider.buildDrillPassage()-এর CONJUNCT_STAGE লজিক ──
        listOf("্")   // স্টেজ ৫৮ — হসন্ত/বিরাম চিহ্ন (Hasanta/Virama)
    )

    /** ইংরেজি — Bangla-র (BANGLA_STAGES) একই নিয়ম অনুসরণ করে: স্টেজ ১-এ একসাথে কয়েকটা
     *  কী (৬টা, হোম-রো'র index/middle/ring আঙুলের কী — f j d k s l), তারপর প্রতিটা
     *  স্টেজে একটা করে নতুন কী। আগে প্রতিটা স্টেজে মাত্র ২টা কী ছিল (৩০ স্টেজ) — এখন
     *  বাংলার প্যাটার্নের সাথে সঙ্গতিপূর্ণ করা হলো (৫৫ স্টেজ, একই ৬০টা মূল
     *  ক্যারেক্টার/চিহ্ন, শুধু গ্রুপিং বদলেছে — কোনো কী বাদ পড়েনি)।
     *  ⚠️ নোট: এই রিস্ট্রাকচারিংয়ে স্টেজ-নাম্বার শিফট হয়েছে (আগে স্টেজ ৬-এ যা ছিল
     *  এখন সেটা অন্য নম্বরে) — তাই কারো পুরনো English-track progress (persisted stage
     *  number) থাকলে সেটা নতুন গ্রুপিং অনুযায়ী পুনর্মূল্যায়ন হবে (নিচু দিকে শিফট হতে
     *  পারে, কিন্তু কোনো ডেটা করাপশন/ক্র্যাশ হবে না — CurriculumProvider stage সবসময়
     *  ENGLISH_STAGES.size দিয়ে coerce করে)। */
    val ENGLISH_STAGES: List<List<String>> = listOf(
        listOf("f", "j", "d", "k", "s", "l"),   // স্টেজ ১ — হোম-রো'র মূল ৬টা কী দিয়ে শুরু (বাংলার স্টেজ-১-এর মতোই)
        listOf("a"), listOf(";"), listOf("g"), listOf("h"),                 // হোম-রো সম্পূর্ণ হলো
        listOf("r"), listOf("u"), listOf("t"), listOf("y"), listOf("e"), listOf("i"),
        listOf("w"), listOf("o"), listOf("q"), listOf("p"),                 // টপ-রো
        listOf("v"), listOf("m"), listOf("c"), listOf(","), listOf("x"), listOf("."),
        listOf("z"), listOf("/"), listOf("b"), listOf("n"),                 // বটম-রো
        listOf("1"), listOf("0"), listOf("2"), listOf("9"), listOf("3"), listOf("8"),
        listOf("4"), listOf("7"), listOf("5"), listOf("6"),                 // সংখ্যা
        listOf("-"), listOf("="), listOf("["), listOf("]"), listOf("\\"), listOf("`"),
        listOf("'"), listOf("\""), listOf("!"), listOf("?"), listOf("@"), listOf("#"),
        listOf("$"), listOf("%"), listOf("^"), listOf("&"), listOf("*"), listOf("("), listOf(")"), listOf("_")   // চিহ্ন
    )

    fun stagesFor(track: String): List<List<String>> = if (track == "en") ENGLISH_STAGES else BANGLA_STAGES

    fun totalStages(track: String): Int = stagesFor(track).size

    /** stage পর্যন্ত (১-ভিত্তিক, inclusive) — এখন পর্যন্ত যত ক্যারেক্টার আনলক হয়েছে, সব ── */
    fun allowedCharsUpTo(track: String, stage: Int): Set<String> {
        val stages = stagesFor(track)
        val upTo = stage.coerceIn(1, stages.size)
        return stages.take(upTo).flatten().toSet()
    }

    /** এই নির্দিষ্ট স্টেজে নতুন যে ক্যারেক্টারগুলো যোগ হয়েছে (unlock-প্রগ্রেস চেক করতে লাগে) */
    fun newCharsAt(track: String, stage: Int): List<String> {
        val stages = stagesFor(track)
        val idx = stage - 1
        if (idx !in stages.indices) return emptyList()
        return stages[idx]
    }
}
