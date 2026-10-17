package com.hanif.smartstudy.ui.quiz

import com.hanif.smartstudy.data.model.QuestionItem

/* ══════════════════════════════════════════════════════════════════════════
   QBank Written "পেপার" লেআউট (Admin App-এর PaperComposer / paperTemplates.js-এর আয়না)
   ──────────────────────────────────────────────────────────────────────────
   Admin App প্রতিটা কার্ডের শিরোনাম (group_heading) থেকেই লেআউট চেনে (resolveTpl) — এখানেও ঠিক একই নিয়ম:
   শিরোনাম মিললে সেই টেমপ্লেটের লেআউট, না মিললে format_style/অপশন দেখে অনুমান।
   kind: pair (ক) বাম = ডান) · tbl (দুই কলামের টেবিল) · stack (প্রশ্ন + উত্তর লাইন) · mcq (Options + Answer)
         · cloze (শব্দ-বাক্স + অনুচ্ছেদ + Answer) · fill (ফাঁকা পূরণ) · plain (আগের মতো ক) খ) তালিকা)
   ══════════════════════════════════════════════════════════════════════════ */
internal data class PaperLayout(
    val kind: String,
    val sep: String = "",
    val pre: String = "",
    val cols: List<String> = emptyList(),
    val thirdPre: String? = null,
    val thirdSuf: String = "",
    val highlight: Boolean = false
)

private val PAPER_TPL_ROWS: List<Pair<String, PaperLayout>> = listOf(
    "বাগধারা:" to PaperLayout("pair", sep = "=", thirdPre = "বাক্য:"),
    "সন্ধি বিচ্ছেদ করুন:" to PaperLayout("tbl", cols = listOf("প্রদত্ত শব্দ", "সন্ধি বিচ্ছেদ")),
    "বিপরীত শব্দ:" to PaperLayout("tbl", cols = listOf("প্রদত্ত শব্দ", "বিপরীত শব্দ")),
    "সমার্থক শব্দ লিখুন:" to PaperLayout("tbl", cols = listOf("প্রদত্ত শব্দ", "সমার্থক শব্দ")),
    "লিঙ্গ পরিবর্তন করুন:" to PaperLayout("tbl", cols = listOf("প্রদত্ত শব্দ", "লিঙ্গ পরিবর্তন")),
    "এক কথায় প্রকাশ:" to PaperLayout("pair", sep = "="),
    "বানান শুদ্ধিকরণ:" to PaperLayout("tbl", cols = listOf("ভুল বানান", "শুদ্ধ বানান")),
    "কারক ও বিভক্তি নির্ণয় করুন:" to PaperLayout("pair", sep = "-", highlight = true),
    "বাক্য শুদ্ধ করে লিখুন:" to PaperLayout("stack", pre = "সঠিক-"),
    "ব্যাসবাক্যসহ সমাস নির্ণয় কর:" to PaperLayout("pair", sep = "-", thirdPre = "(", thirdSuf = ")", highlight = true),
    "সংক্ষিপ্ত প্রশ্নের উত্তর দাও:" to PaperLayout("stack", pre = "উত্তর:"),
    "Fill in the blanks with appropriate prepositions:" to PaperLayout("fill"),
    "Fill in the blanks with articles:" to PaperLayout("fill"),
    "Replace the bracketed wrong word from the options:" to PaperLayout("mcq"),
    "Choose the correct word from the options:" to PaperLayout("mcq"),
    "Translate into English:" to PaperLayout("stack", pre = "Ans:"),
    "Change the Gender:" to PaperLayout("tbl", cols = listOf("Given Word", "Opposite Gender")),
    "Write the plural number:" to PaperLayout("tbl", cols = listOf("Singular", "Plural")),
    "Word Meaning:" to PaperLayout("tbl", cols = listOf("Word", "Meaning")),
    "Write the synonym words:" to PaperLayout("tbl", cols = listOf("Given Word", "Synonym")),
    "Word Spelling:" to PaperLayout("tbl", cols = listOf("Wrong Spelling", "Correct Spelling")),
    "Make a sentence with the following words:" to PaperLayout("pair", sep = "=", thirdPre = "Sentence:"),
    "Idioms and Phrases:" to PaperLayout("pair", sep = "=", thirdPre = "Sentence:"),
    "Change the sentence as per direction:" to PaperLayout("stack", pre = "Ans:"),
    "Change the voice:" to PaperLayout("stack", pre = "Ans:"),
    "Correct the following sentences:" to PaperLayout("stack", pre = "Ans:"),
    "Write paragraph:" to PaperLayout("stack", pre = "Ans:"),
    "Fill in the blanks:" to PaperLayout("fill"),
    "Fill in the blanks with appropriate word given below:" to PaperLayout("cloze"),
)

private val PAPER_NORM_TRAIL = Regex("[:：।\\s]+$")
private val PAPER_NORM_SPACES = Regex("\\s+")

internal fun paperNormHeading(s: String): String =
    s.replace(PAPER_NORM_TRAIL, "").replace(PAPER_NORM_SPACES, " ").trim().lowercase()

private val PAPER_TPL_MAP: Map<String, PaperLayout> by lazy {
    PAPER_TPL_ROWS.associate { paperNormHeading(it.first) to it.second }
}

/** শিরোনামের প্রথম লাইন (cloze-এ বাকি লাইনে শব্দ-বাক্স ও অনুচ্ছেদ থাকে) */
internal fun paperHeadingBase(heading: String): String =
    heading.lineSequence().firstOrNull()?.trim().orEmpty()

internal fun resolvePaperLayout(heading: String, formatStyle: String, hasOptions: Boolean, english: Boolean): PaperLayout {
    PAPER_TPL_MAP[paperNormHeading(paperHeadingBase(heading))]?.let { return it }
    return when {
        formatStyle == "table" -> PaperLayout(
            "tbl",
            cols = if (english) listOf("Word", "Meaning") else listOf("শব্দ", "ব্যাখ্যা")
        )
        hasOptions -> PaperLayout("mcq")
        else -> PaperLayout("plain")
    }
}

/** Replace/Choose-এর মতো MCQ-লেআউটের শিরোনাম কিনা (গ্রুপ-করা MCQ সারি Written পেপারে আনার জন্য) */
internal fun paperIsMcqHeading(heading: String): Boolean =
    PAPER_TPL_MAP[paperNormHeading(paperHeadingBase(heading))]?.kind == "mcq"

internal fun paperIsPaperHeading(heading: String): Boolean =
    PAPER_TPL_MAP.containsKey(paperNormHeading(paperHeadingBase(heading)))

internal data class PaperClozeParts(val words: List<String>, val passage: String)

/** cloze-এর শিরোনাম: লাইন১ = হেডিং, লাইন২ = শব্দ-বাক্স ("a  |  b  |  c"), বাকি = অনুচ্ছেদ (Admin: cardHeadingText) */
internal fun paperClozeParts(heading: String): PaperClozeParts {
    val lines = heading.split("\n")
    val second = lines.getOrNull(1).orEmpty()
    return if (second.contains("|")) {
        PaperClozeParts(
            words = second.split("|").map { it.trim() }.filter { it.isNotEmpty() },
            passage = lines.drop(2).joinToString("\n").trim()
        )
    } else {
        PaperClozeParts(words = emptyList(), passage = lines.drop(1).joinToString("\n").trim())
    }
}

private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

/** ১ → "০১", ১২ → "১২" (ছাপা পেপারের "০১।" ধরনের ক্রমিক নম্বর) */
internal fun paperBn(n: Int, width: Int = 2): String =
    n.toString().padStart(width, '0').map { if (it in '0'..'9') BN_DIGITS[it - '0'] else it }.joinToString("")

internal fun paperOptionsLine(q: QuestionItem): String? {
    val opts = listOf(q.optionA, q.optionB, q.optionC, q.optionD).map { it.trim() }.filter { it.isNotEmpty() }
    return if (opts.isEmpty()) null else opts.joinToString("/")
}
