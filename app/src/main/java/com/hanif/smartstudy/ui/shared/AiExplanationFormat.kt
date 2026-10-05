package com.hanif.smartstudy.ui.shared

// ── AI ব্যাখ্যার কাঁচা টেক্সট → পরিষ্কার, পড়ার উপযোগী লেখা ──
// AI প্রায়ই Markdown/LaTeX দেয় (\(x\), \[ ... \], ### শিরোনাম, - তালিকা, \frac, \times ...)।
// অ্যাপের টেক্সট রেন্ডারার শুধু **বোল্ড**/*ইটালিক* বোঝে, LaTeX বোঝে না — তাই সেগুলো
// এখানে সাধারণ গণিত-চিহ্নে (×, ÷, ², √, ভগ্নাংশ) রূপান্তর করা হয়। কোনো বিষয়বস্তু বদলানো হয় না,
// শুধু চেহারা ঠিক করা হয়। DB-র ব্যাখ্যায় এটা প্রযোজ্য নয় (শুধু AI-র জন্য)।

private val SUPERSCRIPT = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵',
    '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', 'n' to 'ⁿ'
)

private fun toSuperscript(s: String): String? {
    if (s.isEmpty()) return null
    val sb = StringBuilder()
    for (c in s) sb.append(SUPERSCRIPT[c] ?: return null)
    return sb.toString()
}

// \frac{a}{b} (নেস্টেড ছাড়া সাধারণ ক্ষেত্র) → (a)/(b), ছোট হলে a/b
private val FRAC = Regex("""\\d?frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}""")
private val SQRT = Regex("""\\sqrt\s*\{([^{}]*)\}""")
private val TEXT = Regex("""\\(?:text|mathrm|mathbf|textbf)\s*\{([^{}]*)\}""")
private val POW_BRACE = Regex("""\^\{([^{}]*)\}""")
private val POW_SINGLE = Regex("""\^([0-9n])""")
private val SUB_BRACE = Regex("""_\{([^{}]*)\}""")

fun formatAiExplanation(raw: String): String {
    var t = raw.replace("\r\n", "\n").trim()

    // ডিসপ্লে গণিত \[ ... \] এবং $$ ... $$ → আলাদা লাইনে; ইনলাইন \( ... \) এবং $ ... $ → সাধারণ লেখা
    t = t.replace(Regex("""\\\[\s*"""), "\n").replace(Regex("""\s*\\\]"""), "\n")
    t = t.replace(Regex("""\$\$\s*"""), "\n").replace(Regex("""\\\(\s*"""), "").replace(Regex("""\s*\\\)"""), "")
    t = t.replace(Regex("""(?<![\\\d])\$(?=\S)"""), "")

    // সাধারণ LaTeX চিহ্ন
    repeat(2) {
        t = FRAC.replace(t) { m ->
            val a = m.groupValues[1].trim(); val b = m.groupValues[2].trim()
            val simple = { s: String -> s.length <= 3 && s.none { it == ' ' || it == '+' || it == '-' } }
            if (simple(a) && simple(b)) "$a/$b" else "($a)/($b)"
        }
    }
    t = SQRT.replace(t) { "√(${it.groupValues[1]})" }
    t = TEXT.replace(t) { it.groupValues[1] }
    t = POW_BRACE.replace(t) { m -> toSuperscript(m.groupValues[1]) ?: "^(${m.groupValues[1]})" }
    t = POW_SINGLE.replace(t) { m -> toSuperscript(m.groupValues[1]) ?: m.value }
    t = SUB_BRACE.replace(t) { it.groupValues[1] }
    t = t.replace("\\left", "").replace("\\right", "")   // \le/\rightarrow-এর আগে, নইলে "\left" ভেঙে যেত
    val symbols = listOf(
        "\\times" to "×", "\\div" to "÷", "\\cdot" to "·", "\\pm" to "±", "\\leq" to "≤", "\\le" to "≤",
        "\\geq" to "≥", "\\ge" to "≥", "\\neq" to "≠", "\\approx" to "≈", "\\therefore" to "∴",
        "\\because" to "∵", "\\rightarrow" to "→", "\\Rightarrow" to "⇒", "\\to" to "→", "\\infty" to "∞",
        "\\pi" to "π", "\\%" to "%", "\\," to " ", "\\;" to " ", "\\ " to " ", "\\quad" to "  "
    )
    for ((k, v) in symbols) t = t.replace(k, v)

    // Markdown → রেন্ডারার-বান্ধব
    val out = ArrayList<String>()
    for (line0 in t.split("\n")) {
        var line = line0.trimEnd()
        val h = Regex("""^\s{0,3}#{1,6}\s*(.+)$""").find(line)
        line = when {
            h != null -> "**${h.groupValues[1].trim().trim('*')}**"
            Regex("""^\s*[-*•]\s+""").containsMatchIn(line) -> "• " + line.replace(Regex("""^\s*[-*•]\s+"""), "")
            else -> line
        }
        out.add(line)
    }
    t = out.joinToString("\n")

    // বাড়তি ফাঁকা লাইন কমানো (সর্বোচ্চ ১টা)
    t = t.replace(Regex("\n{3,}"), "\n\n").trim()
    return t
}
