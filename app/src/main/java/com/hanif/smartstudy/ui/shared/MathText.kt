package com.hanif.smartstudy.ui.shared

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.ui.theme.NotoSansBengali

// ── বীজগাণিতিক/গণিতীয় টেক্সট রেন্ডারার (অফলাইন, WebView/ইন্টারনেট ছাড়া) ──
// সমস্যা: ডেটাবেসে প্রশ্ন/অপশন "a^3+b^3+c^3", "x^2 + y^2", "x_1", "sqrt(x)", "\frac{a}{b}"
// ইত্যাদি কাঁচা আকারে থাকে, আর অ্যাপ সেগুলো ^ চিহ্নসহ হুবহু দেখাত (a^3 → "a^3")।
// সমাধান: ^ এর পরের অংশ সত্যিকারের সুপারস্ক্রিপ্ট (a³, x², 2ⁿ, (a+b)²), _ এর পরের অংশ
// সাবস্ক্রিপ্ট (x₁), এবং সাধারণ LaTeX (\frac, \sqrt, \times, \pi, $...$ ইত্যাদি) সরল গণিত-চিহ্নে
// রূপান্তর হয়। বাংলা ফন্টে ঠিকভাবে বসে কারণ Unicode সুপারস্ক্রিপ্ট নয়, BaselineShift স্টাইল ব্যবহার।
// গণিত না থাকলে hasMathMarkup() false দেয় — তখন কোনো টেক্সট বদলায় না।

// ── সহজ ডিটেকশন ──
private val MATH_HINT = Regex(
    """\^|\\[A-Za-z]+|\$|\bsqrt\s*\(|[A-Za-z0-9\)]_[\{0-9A-Za-z]|<\s*/?\s*(?:sup|sub|frac|sqrt|br)\b|\broot\s*[0-9০-৯(]"""
)

fun hasMathMarkup(text: String): Boolean = text.isNotEmpty() && MATH_HINT.containsMatchIn(text)

// WebView(MathJax)-ই লাগবে এমন জটিল LaTeX (ইন্টিগ্রাল, ম্যাট্রিক্স, লিমিট ইত্যাদি)
private val COMPLEX_LATEX = Regex(
    """\\(int|sum|prod|lim|begin|end|matrix|overline|underline|vec|hat|bar|binom|oint|iint)\b"""
)

fun needsHeavyMath(text: String): Boolean = COMPLEX_LATEX.containsMatchIn(text)

// ── LaTeX-lite → সরল টেক্সট (সুপার/সাবস্ক্রিপ্ট বাদে, ওগুলো নিচে স্প্যান হয়) ──
private val FRAC_RE = Regex("""\\[dt]?frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}""")
private val SQRT_BRACE_RE = Regex("""\\sqrt\s*\{([^{}]*)\}""")
private val SQRT_PAREN_RE = Regex("""\bsqrt\s*\(([^()]*)\)""")
private val SQRT_N_RE = Regex("""\\sqrt\s*\[([^\]]*)\]\s*\{([^{}]*)\}""")
private val TEXT_RE = Regex("""\\(?:text|mathrm|mathbf|textbf|mathit|operatorname)\s*\{([^{}]*)\}""")

private val SYMBOLS = listOf(
    "\\times" to "×", "\\div" to "÷", "\\cdot" to "·", "\\pm" to "±", "\\mp" to "∓",
    "\\leq" to "≤", "\\geq" to "≥", "\\neq" to "≠", "\\approx" to "≈", "\\equiv" to "≡",
    "\\le" to "≤", "\\ge" to "≥", "\\ne" to "≠", "\\sim" to "∼", "\\propto" to "∝",
    "\\therefore" to "∴", "\\because" to "∵", "\\infty" to "∞",
    "\\Rightarrow" to "⇒", "\\Leftrightarrow" to "⇔", "\\rightarrow" to "→", "\\leftarrow" to "←", "\\to" to "→",
    "\\angle" to "∠", "\\triangle" to "△", "\\perp" to "⊥", "\\parallel" to "∥",
    "\\circ" to "°", "\\degree" to "°", "\\%" to "%", "\\in" to "∈", "\\notin" to "∉",
    "\\subset" to "⊂", "\\cup" to "∪", "\\cap" to "∩", "\\emptyset" to "∅", "\\forall" to "∀", "\\exists" to "∃",
    "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\Delta" to "Δ",
    "\\theta" to "θ", "\\Theta" to "Θ", "\\lambda" to "λ", "\\mu" to "μ", "\\pi" to "π", "\\Pi" to "Π",
    "\\sigma" to "σ", "\\Sigma" to "Σ", "\\omega" to "ω", "\\Omega" to "Ω", "\\phi" to "φ", "\\epsilon" to "ε",
    "\\ldots" to "…", "\\cdots" to "⋯", "\\dots" to "…",
    "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan", "\\cot" to "cot", "\\sec" to "sec", "\\csc" to "csc",
    "\\log" to "log", "\\ln" to "ln", "\\exp" to "exp",
    "\\implies" to "⇒", "\\impliedby" to "⇐", "\\iff" to "⇔", "\\hline" to "", "\\newline" to "\n",
    "\\left" to "", "\\right" to "", "\\quad" to "  ", "\\qquad" to "   ",
    "\\," to " ", "\\;" to " ", "\\!" to "", "\\ " to " ", "\\{" to "{", "\\}" to "}", "\\'" to "'"
)

private val SYMBOL_REGEX: List<Pair<Regex, String>> = SYMBOLS.map { (k, v) ->
    val pat = Regex.escape(k) + (if (k.last().isLetter()) "(?![A-Za-z])" else "")
    Regex(pat) to v
}

// ── ডাটাবেসে যেসব মার্কআপ আসে (বোল্ড/আন্ডারলাইন/বর্গ/মূল/ভগ্নাংশ/∴ ইত্যাদির টেক্সট-ভার্সন) ──
private val BR_RE         = Regex("""<\s*br\s*/?\s*>""", RegexOption.IGNORE_CASE)
private val FRAC_TAG_RE   = Regex("""<\s*frac\s*>(.*?)<\s*/\s*frac\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val SQRT_TAG_RE   = Regex("""<\s*sqrt\s*>(.*?)<\s*/\s*sqrt\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val ROOT_PAREN_RE = Regex("""\broot\s*\(([^()]{1,24})\)""", RegexOption.IGNORE_CASE)
private val ROOT_NUM_RE   = Regex("""\broot\s*([0-9০-৯]+(?:\.[0-9০-৯]+)?)""", RegexOption.IGNORE_CASE)
private val MULTI_BS_RE   = Regex("""\\{2,}(?=[A-Za-z])""")
private val ENTITIES = listOf("&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&amp;" to "&")

// ডাটায় অনেক জায়গায় শেষ "}" হারিয়ে গেছে (যেমন \\frac{২}{৫ ; x^{3}+\\frac{1}{x^{3}= কত?)। না-বন্ধ "{" টা তার
// আসল শেষে (প্রথম ফাঁকা/=/,/;/।/? এ, না থাকলে লেখার শেষে) বন্ধ করে দেয় — শুধু গণিত-মার্কআপ থাকলে।
private fun repairBraces(t: String): String {
    if (t.indexOf('{') < 0 || !(t.contains("\\frac") || t.contains("\\sqrt") || t.contains("^{") || t.contains("_{"))) return t
    var s = t
    repeat(4) {
        val stack = ArrayList<Int>()
        for (i in s.indices) {
            if (s[i] == '{') stack.add(i)
            else if (s[i] == '}' && stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        }
        if (stack.isEmpty()) return s
        val start = stack.last()
        var depth = 0
        var j = start + 1
        while (j < s.length) {
            val c = s[j]
            if (c == '{') depth++
            else if (c == '}') { if (depth == 0) break else depth-- }
            else if (depth == 0 && j > start + 1 && (c.isWhitespace() || c == '=' || c == ',' || c == ';' || c == '।' || c == '?')) break
            j++
        }
        s = s.substring(0, j) + "}" + s.substring(j)
    }
    return s
}

private fun normalizeMarkup(raw: String): String {
    var t = raw
    // ডাটায় ডবল ব্যাকস্ল্যাশ ("\\frac", "\\therefore") থাকে — একটা করে নিলে আগের LaTeX-লাইট কনভার্টার ঠিক চলে
    if (t.indexOf('\\') >= 0) t = MULTI_BS_RE.replace(t) { "\\" }
    t = repairBraces(t)
    t = BR_RE.replace(t) { "\n" }
    // <frac>১৫|৫</frac> → ১৫/৫, <sqrt>3</sqrt> → √3, root7 → √7, root(x+1) → √(x+1)
    t = FRAC_TAG_RE.replace(t) { m ->
        val p = m.groupValues[1].split('|', limit = 2)
        if (p.size == 2) "\\frac{" + p[0].trim() + "}{" + p[1].trim() + "}" else m.groupValues[1]
    }
    t = SQRT_TAG_RE.replace(t) { m -> "\\sqrt{" + m.groupValues[1].trim() + "}" }
    t = ROOT_PAREN_RE.replace(t) { m -> "\\sqrt{" + m.groupValues[1].trim() + "}" }
    t = ROOT_NUM_RE.replace(t) { m -> "\\sqrt{" + m.groupValues[1] + "}" }
    if (t.indexOf('&') >= 0) for ((k, v) in ENTITIES) t = t.replace(k, v)
    return t
}

// <b> </b> <u> <i> <sup> <sub> <mark> <li> … — শুধু হুবহু ট্যাগ (বড়/ছোট হাতের চলে; ভেতরে ফাঁকা বা অ্যাট্রিবিউট নয়)।
// তাই "a < b এবং c > d" এর মতো অসমতার লেখা কখনো ট্যাগ ভেবে ভুল হয় না।
private val TAG_RE = Regex("""<(/?)(b|strong|u|i|em|sup|sub|mark|hb|li)>""", RegexOption.IGNORE_CASE)

// ^{2} → ^(2): ভগ্নাংশ/মূলের ভেতরে {} থাকলে পুরনো \\frac/\\sqrt নিয়ম মিলত না (যেমন \\frac{1}{x^{2}})
private val FRAC_ONE_RE = Regex("""\\[dt]?frac\s*\{([^{}]*)\}""")
private val SCRIPT_BRACE_RE = Regex("""\^\{([^{}()]{1,12})\}""")

private fun simplify(raw: String): String {
    var t = SCRIPT_BRACE_RE.replace(raw) { "^(" + it.groupValues[1] + ")" }
    // $$..$$, $..$, \( \), \[ \] ডিলিমিটার সরানো (টাকার চিহ্ন "$5" এ হাত না দিয়ে — শুধু জোড়া-$)
    t = t.replace("\\[", "").replace("\\]", "").replace("\\(", "").replace("\\)", "")
    t = t.replace("$$", "")
    t = Regex("""\$([^$\n]+)\$""").replace(t) { it.groupValues[1] }
    t = TEXT_RE.replace(t) { it.groupValues[1] }
    repeat(3) {
        t = FRAC_RE.replace(t) { m ->
            val a = m.groupValues[1].trim(); val b = m.groupValues[2].trim()
            val simple = { s: String -> s.length <= 3 && s.none { it == ' ' || it == '+' || it == '-' || it == '*' } }
            if (simple(a) && simple(b)) "$a/$b" else "($a)/($b)"
        }
    }
    t = FRAC_ONE_RE.replace(t) { it.groupValues[1] }
    t = SQRT_N_RE.replace(t) { m ->
        val n = m.groupValues[1].trim()
        val sup = when (n) { "3" -> "∛"; "4" -> "∜"; else -> "√" }
        "$sup(${m.groupValues[2]})"
    }
    t = SQRT_BRACE_RE.replace(t) { m ->
        val inner = m.groupValues[1].trim()
        if (inner.length <= 1 || inner.all { it.isLetterOrDigit() }) "√$inner" else "√($inner)"
    }
    t = SQRT_PAREN_RE.replace(t) { m ->
        val inner = m.groupValues[1].trim()
        if (inner.isNotEmpty() && inner.all { it.isLetterOrDigit() }) "√$inner" else "√($inner)"
    }
    // শব্দ-সীমানা সহ রিপ্লেস — নইলে "\\le" আগে বসে "\\leftarrow"/"\\left" ভেঙে দিত
    for ((k, v) in SYMBOL_REGEX) t = t.replace(k, v)
    return t
}

// ^ এর পরের "এক্সপোনেন্ট" কতটুকু — ^{...} / ^(...) / ^-n / ^+n / ^n(অঙ্কের সারি) / ^x(একটা অক্ষর)
private fun readScript(s: String, start: Int): Pair<String, Int>? {
    if (start >= s.length) return null
    val c = s[start]
    if (c == '{') {
        val end = s.indexOf('}', start + 1)
        if (end < 0) return null
        return s.substring(start + 1, end) to end + 1
    }
    if (c == '(') {
        val end = s.indexOf(')', start + 1)
        if (end < 0 || end - start > 14) return null
        return s.substring(start + 1, end) to end + 1
    }
    var i = start
    if (s[i] == '-' || s[i] == '+' || s[i] == '−') i++
    if (i < s.length && s[i].isDigit()) {
        while (i < s.length && s[i].isDigit()) i++
        return s.substring(start, i) to i
    }
    if (i < s.length && (s[i].isLetter() && s[i].code < 128)) {   // x^n, x^a (শুধু ইংরেজি ১টা অক্ষর)
        return s.substring(start, i + 1) to i + 1
    }
    return null
}

private val SUB_OK = Regex("""[A-Za-z0-9)\]}]""")

fun buildMathAnnotated(raw: String): AnnotatedString {
    val s = simplify(normalizeMarkup(raw))
    val sup = SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.72.em)
    val sub = SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.72.em)
    val boldSt = SpanStyle(fontWeight = FontWeight.ExtraBold)
    val underSt = SpanStyle(textDecoration = TextDecoration.Underline)
    val italicSt = SpanStyle(fontStyle = FontStyle.Italic)
    val markSt = SpanStyle(fontWeight = FontWeight.ExtraBold, background = Color(0x33F59E0B))
    return buildAnnotatedString {
        // খোলা ট্যাগের স্ট্যাক — ভুল-নেস্টিং হলেও (<b><u>..</b></u>) ভাঙে না; শেষে না-বন্ধ ট্যাগ আপনিই বন্ধ হয়
        val open = ArrayList<Pair<String, SpanStyle>>()
        fun openTag(name: String, st: SpanStyle) { open.add(name to st); pushStyle(st) }
        fun closeTag(name: String) {
            val idx = open.indexOfLast { it.first == name }
            if (idx < 0) return
            val above = open.subList(idx + 1, open.size).toList()
            repeat(open.size - idx) { pop() }
            while (open.size > idx) open.removeAt(open.size - 1)
            for (a in above) { open.add(a); pushStyle(a.second) }
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '<') {
                val m = TAG_RE.find(s, i)
                if (m != null && m.range.first == i) {
                    val closing = m.groupValues[1] == "/"
                    when (m.groupValues[2].lowercase()) {
                        "b", "strong" -> if (closing) closeTag("b") else openTag("b", boldSt)
                        "u"           -> if (closing) closeTag("u") else openTag("u", underSt)
                        "i", "em"     -> if (closing) closeTag("i") else openTag("i", italicSt)
                        "sup"         -> if (closing) closeTag("sup") else openTag("sup", sup)
                        "sub"         -> if (closing) closeTag("sub") else openTag("sub", sub)
                        "mark", "hb"  -> if (closing) closeTag("mark") else openTag("mark", markSt)
                        "li"          -> if (closing) append("\n") else append("• ")
                        else          -> Unit
                    }
                    i = m.range.last + 1
                    continue
                }
            }
            if (c == '*' && s.startsWith("**", i)) {
                // **বোল্ড** — জোড়া থাকলেই (একা ** সাধারণ অক্ষর)
                if (open.any { it.first == "**" }) { closeTag("**"); i += 2; continue }
                if (s.indexOf("**", i + 2) > 0) { openTag("**", boldSt); i += 2; continue }
            }
            if (c == '^' && (s.getOrNull(i + 1) == '°' || s.getOrNull(i + 1) == '∘')) {
                append('°'); i += 2; continue          // 90^° → 90°
            }
            if (c == '^') {
                val r = readScript(s, i + 1)
                if (r != null) {
                    withStyle(sup) { append(r.first.replace('-', '−')) }
                    i = r.second
                    continue
                }
            } else if (c == '_' && i > 0 && SUB_OK.matches(s[i - 1].toString())) {
                // সাবস্ক্রিপ্ট শুধু x_1 / x_{12} / a_n ধরনে; "__" বা "_word_" (ফিল-ইন-ব্ল্যাংক) এ হাত দেয় না
                val next = s.getOrNull(i + 1)
                if (next == '{' || (next != null && next.isDigit())) {
                    val r = if (next == '{') readScript(s, i + 1)
                            else {
                                var j = i + 1
                                while (j < s.length && s[j].isDigit()) j++
                                s.substring(i + 1, j) to j
                            }
                    if (r != null) {
                        withStyle(sub) { append(r.first) }
                        i = r.second
                        continue
                    }
                }
            }
            append(c)
            i++
        }
    }
}

@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    selectable: Boolean = false
) {
    val annotated = remember(text) { buildMathAnnotated(text) }
    // সুপারস্ক্রিপ্ট লাইনের উপরে উঠে কাটা পড়ে না যাতে — lineHeight একটু বেশি রাখা ভালো
    val lh = if (lineHeight == TextUnit.Unspecified) fontSize * 1.5f else lineHeight
    val body: @Composable () -> Unit = {
        Text(
            text = annotated,
            modifier = modifier,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
            fontFamily = NotoSansBengali,
            lineHeight = lh
        )
    }
    if (selectable) SelectionContainer { body() } else body()
}
