package com.hanif.smartstudy.util

import android.content.Context
import android.util.Log
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.TypingSheetPassageEntity
import com.hanif.smartstudy.data.model.DataSourceMode
import com.hanif.smartstudy.data.remote.ContentFetchService
import com.hanif.smartstudy.data.remote.GasContentService
import com.hanif.smartstudy.ui.typing.PassageInfo

/**
 * টাইপিং প্র্যাকটিসের ডিফল্ট প্যাসেজ পুল — আগে TypingPracticeScreen.kt-এ হার্ডকোডেড
 * তালিকা ছিল, এখন Google Sheet-এর "Typing" ট্যাব (headers: id, language, content,
 * updatedAt) থেকে রানটাইমে লোড হয়। Admin App থেকে Typing ট্যাবে যোগ করা যেকোনো
 * কনটেন্ট এই একই পথে সরাসরি অ্যাপে চলে আসবে।
 *
 * Settings-এ "Data Source" ড্রপডাউন অনুযায়ী উৎস বদলায় — ঠিক Quiz/QBank/Study-এর
 * মতোই (দেখো ContentFetchService.kt/GasContentService.kt):
 *   - FIREBASE     → Firebase "Typing" node (GAS syncToFirebase() যেটা সিঙ্ক করে)
 *   - GOOGLE_SHEET → GAS getSheetRows action দিয়ে সরাসরি Google Sheet (Firebase বাইপাস)
 *
 * ক্রম: (১) এই app-process-এ একবার fetch হয়ে গেলে (একই mode-এর জন্য) RAM cache থেকেই সার্ভ হয়
 *       (২) নেট থাকলে ফ্রেশ fetch, সফল হলে Room-এ cache করে রাখে (অফলাইন fallback-এর জন্য)
 *       (৩) নেট না থাকলে/fetch ব্যর্থ হলে Room-এর আগের cache থেকে রিটার্ন করে
 *       (৪) ⚠️ আগে দুটোই খালি হলে (একদম প্রথমবার, ইন্টারনেট ছাড়াই প্রথম ওপেন) সম্পূর্ণ
 *           খালি লিস্ট রিটার্ন হতো — Normal/Smart Typing-এ কোনো প্যাসেজই দেখাত না।
 *           এখন এই ক্ষেত্রে APK-এর ভেতরেই বান্ডল-করা [bundledOfflinePassages] থেকে
 *           রিটার্ন হয় (কোনো নেটওয়ার্ক লাগে না), তাই একদম প্রথমবার অফলাইনেও টাইপিং
 *           প্র্যাকটিস কাজ করে — সেটাও সাথে সাথে Room-এ লিখে রাখা হয় (dao.replaceAll)
 *           যাতে অ্যাপ রিস্টার্ট হলেও (এখনো অফলাইন থাকলে) getAll() থেকেই পাওয়া যায় ──
 */
object TypingPassageProvider {
    private const val TAG = "TypingPassageProvider"

    @Volatile private var ramCache: List<PassageInfo>? = null
    @Volatile private var ramCacheMode: DataSourceMode? = null

    suspend fun getPassages(context: Context): List<PassageInfo> {
        val mode = SessionManager(context).getDataSourceMode()
        ramCache?.let { if (ramCacheMode == mode) return it }

        val dao = AppDatabase.getInstance(context).typingSheetPassageDao()

        val fresh = try {
            if (mode == DataSourceMode.GOOGLE_SHEET) GasContentService.fetchTypingPassages()
            else ContentFetchService.fetchTypingPassages()
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed (mode=$mode): ${e.message}")
            emptyList()
        }

        var result: List<PassageInfo> = if (fresh.isNotEmpty()) {
            try {
                dao.replaceAll(fresh.map {
                    TypingSheetPassageEntity(
                        id        = it.id,
                        language  = it.language,
                        content   = it.content,
                        updatedAt = it.updatedAt
                    )
                })
            } catch (e: Exception) {
                Log.w(TAG, "cache write failed: ${e.message}")
            }
            fresh.filter { it.content.isNotBlank() }
                .map { PassageInfo(it.content, classifyDifficulty(it.content, it.language)) }
        } else {
            // ── fetch ব্যর্থ/খালি হলে Room-এর পুরনো cache — এটা যেই mode-এই আগে fetch
            // হয়ে থাকুক না কেন (Firebase/Google Sheet), সম্পূর্ণ কিছু না-থাকার চেয়ে
            // পুরনো প্যাসেজ পুল দেখানো ভালো ──
            try {
                dao.getAll()
                    .filter { it.content.isNotBlank() }
                    .map { PassageInfo(it.content, classifyDifficulty(it.content, it.language)) }
            } catch (e: Exception) {
                Log.w(TAG, "cache read failed: ${e.message}")
                emptyList()
            }
        }

        // ── একদম প্রথমবার (নেটও নেই, Room cache-ও খালি) — বান্ডল-করা অফলাইন প্যাসেজে fallback,
        // আর সেটাও Room-এ লিখে রাখা হয় (dao) যাতে অ্যাপ রিস্টার্ট হলেও (এখনো অফলাইন থাকলে)
        // এই ফাংশন আবার না চালিয়েই getAll() থেকে সরাসরি এগুলো পাওয়া যায় ──
        if (result.isEmpty()) {
            Log.w(TAG, "no network + empty cache — falling back to bundled offline passages")
            result = bundledOfflinePassages()
            try {
                dao.replaceAll(result.mapIndexed { idx, p ->
                    TypingSheetPassageEntity(
                        id        = "bundled_$idx",
                        language  = if (isBengali(p.text)) "bn" else "en",
                        content   = p.text,
                        updatedAt = 0L
                    )
                })
            } catch (e: Exception) {
                Log.w(TAG, "bundled-cache write failed: ${e.message}")
            }
        }

        if (result.isNotEmpty()) { ramCache = result; ramCacheMode = mode }
        return result
    }

    /** Settings-এ Data Source বদলালে (Firebase ↔ Google Sheet) MenuViewModel এটা কল করে,
     *  যাতে পরের getPassages() পুরনো mode-এর RAM cache না ব্যবহার করে নতুন সোর্স থেকে
     *  আবার fetch করে। */
    fun forceRefreshNextTime() {
        ramCache = null
        ramCacheMode = null
    }

    // ── সহজ/মাঝারি/কঠিন — Sheet-এ আলাদা "level" কলাম নেই (আগে ছিল, বাদ দেওয়া হয়েছে),
    // তাই কনটেন্ট থেকেই আন্দাজ করা হয়: শব্দ-সংখ্যা + গড় শব্দ-দৈর্ঘ্য + (বাংলার জন্য)
    // যুক্তাক্ষর/হসন্ত-ক্লাস্টারের ঘনত্ব — এগুলো বেশি মানেই টাইপ করা কঠিন। এটা একটা
    // heuristic, নিখুঁত না, কিন্তু "সহজ/মাঝারি/কঠিন" ট্যাবে অন্তত প্যাসেজ দেখাবে
    // (আগে সব প্যাসেজ শুধু "all"-এ পড়ে থাকত, easy/medium/hard সবসময় খালি ছিল)। */
    private fun classifyDifficulty(content: String, language: String): String {
        val words = content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return "medium"
        val avgWordLen = words.sumOf { it.length } / words.size.toDouble()
        val conjunctRatio = if (language == "bn") {
            // '্' (হসন্ত) থাকা মানে যুক্তাক্ষর — বাংলায় টাইপ করা তুলনামূলক কঠিন
            val hasanta = content.count { it == '্' }
            hasanta / content.length.coerceAtLeast(1).toDouble()
        } else 0.0
        // দৈর্ঘ্য-ভিত্তিক স্কোর: ছোট প্যাসেজ + ছোট শব্দ + কম যুক্তাক্ষর = সহজ
        val score = avgWordLen + conjunctRatio * 20 + (words.size / 20.0)
        return when {
            score < 4.5 -> "easy"
            score < 7.0 -> "medium"
            else -> "hard"
        }
    }

    private fun isBengali(text: String): Boolean = text.any { it.code in 0x0980..0x09FF }

    // ── APK-এর ভেতরেই বান্ডল-করা offline প্যাসেজ ব্যাংক — কোনো নেটওয়ার্ক/সার্ভার লাগে
    // না, একদম প্রথমবার অ্যাপ ওপেন করার সময় ইন্টারনেট না থাকলে এখান থেকেই টাইপিং
    // প্র্যাকটিস শুরু করা যায় (দেখো getPassages()-এর ধাপ ৪)। easy/medium/hard —
    // তিন লেভেলেই কয়েকটা করে বাংলা বাক্য, আর কয়েকটা ইংরেজিও (bn/en টগলের জন্য)।
    // ইচ্ছাকৃতভাবে ছোট রাখা হয়েছে (২৪টা) — শুধু "অফলাইন গ্যাপ" পূরণের জন্য, আসল
    // কনটেন্ট-পুল এখনো Google Sheet/Firebase থেকেই আসবে নেট থাকলে ──
    private val BUNDLED_OFFLINE_RAW: List<String> = listOf(
        // ── সহজ (ছোট শব্দ, কম যুক্তাক্ষর) ──
        "আমাদের দেশের নাম বাংলাদেশ। এর রাজধানীর নাম ঢাকা।",
        "সূর্য পূর্ব দিকে ওঠে এবং পশ্চিম দিকে অস্ত যায়।",
        "বই আমাদের সবচেয়ে ভালো বন্ধু। প্রতিদিন বই পড়ার অভ্যাস করা উচিত।",
        "গ্রামের মাঠে সবুজ ধানের ক্ষেত দেখতে অনেক সুন্দর লাগে।",
        "শিক্ষার্থীদের প্রতিদিন নিয়ম করে টাইপিং প্র্যাকটিস করা উচিত।",
        "সকালে ঘুম থেকে উঠে এক গ্লাস পানি খাওয়া স্বাস্থ্যের জন্য ভালো।",
        // ── মাঝারি ──
        "বাংলাদেশ একটি নদীমাতৃক দেশ। এখানে অসংখ্য ছোট-বড় নদী প্রবাহিত হয়েছে, যা কৃষি ও যোগাযোগের ক্ষেত্রে গুরুত্বপূর্ণ ভূমিকা পালন করে।",
        "প্রযুক্তির উন্নয়নের ফলে আজকাল ঘরে বসেই অনলাইনে পড়াশোনা করা সম্ভব হচ্ছে, যা শিক্ষার্থীদের জন্য একটি বড় সুবিধা।",
        "কম্পিউটারে দ্রুত ও নির্ভুলভাবে টাইপ করতে পারা একটি গুরুত্বপূর্ণ দক্ষতা, বিশেষ করে সরকারি চাকরির পরীক্ষায় এর প্রয়োজন অনেক বেশি।",
        "নিয়মিত অনুশীলনের মাধ্যমে যেকোনো কঠিন কাজও ধীরে ধীরে সহজ হয়ে ওঠে, টাইপিং দক্ষতা অর্জনের ক্ষেত্রেও এই কথাটি সমান সত্য।",
        "স্বাস্থ্যকর খাদ্যাভ্যাস ও নিয়মিত ব্যায়াম শরীর ও মনকে সতেজ রাখতে সাহায্য করে, যা পড়াশোনায় মনোযোগ বাড়াতেও কার্যকর।",
        "সময়ের সঠিক ব্যবহার একজন শিক্ষার্থীর সাফল্যের অন্যতম প্রধান চাবিকাঠি, তাই প্রতিদিনের রুটিন মেনে চলা উচিত।",
        // ── কঠিন (বেশি যুক্তাক্ষর/লম্বা বাক্য) ──
        "বাংলাদেশের সংবিধান রাষ্ট্র পরিচালনার মূলনীতি নির্ধারণপূর্বক স্বাধীনতা ঘোষণাপত্রের আলোকে প্রণীত হয়েছে, যা জাতির ঐতিহাসিক সংগ্রামের প্রতিচ্ছবি।",
        "শিল্পায়ন, নগরায়ন ও বিশ্বায়নের এই যুগে বিজ্ঞান ও প্রযুক্তির উৎকর্ষ সাধন একটি উন্নয়নশীল দেশের জন্য অপরিহার্য হয়ে দাঁড়িয়েছে।",
        "মুক্তিযুদ্ধের চেতনা ও ঐতিহাসিক ঐতিহ্যকে সমুন্নত রেখে জাতি গঠনের ক্ষেত্রে শিক্ষা ব্যবস্থার আমূল সংস্কার একান্ত প্রয়োজনীয়।",
        "অর্থনৈতিক উন্নয়নের পাশাপাশি পরিবেশ সংরক্ষণ ও প্রাকৃতিক সম্পদের যথাযথ ব্যবহার নিশ্চিত করা টেকসই উন্নয়নের অন্যতম পূর্বশর্ত।",
        "বিজ্ঞান ও প্রযুক্তিগত উদ্ভাবনের মাধ্যমে কৃষি, স্বাস্থ্য ও যোগাযোগ ক্ষেত্রে বৈপ্লবিক পরিবর্তন সাধিত হয়েছে, যা জাতীয় অগ্রগতিকে ত্বরান্বিত করেছে।",
        "সুশাসন প্রতিষ্ঠা ও দুর্নীতি দমনের মাধ্যমেই একটি রাষ্ট্র প্রকৃত অর্থে জনকল্যাণমুখী ও গণতান্ত্রিক ব্যবস্থার দিকে এগিয়ে যেতে পারে।",
        // ── English (easy/medium/hard mix) ──
        "The sun rises in the east and sets in the west every single day.",
        "Reading books daily helps improve vocabulary, focus, and imagination over time.",
        "Regular typing practice builds both speed and accuracy, which are essential for exams.",
        "Technology has made it possible for students to learn from home using online resources.",
        "A healthy lifestyle, including proper diet and exercise, greatly improves concentration and productivity.",
        "Good governance, transparency, and accountability are essential pillars of sustainable national development."
    )

    private fun bundledOfflinePassages(): List<PassageInfo> =
        BUNDLED_OFFLINE_RAW.map { text ->
            val lang = if (isBengali(text)) "bn" else "en"
            PassageInfo(text, classifyDifficulty(text, lang))
        }
}
