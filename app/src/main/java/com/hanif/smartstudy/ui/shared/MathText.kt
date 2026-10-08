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
    """\^|\\[A-Za-z]+|\$|\bsqrt\s*\(|[A-Za-z0-9\)]_[\{0-9A-Za-z]"""
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
    "\\left" to "", "\\right" to "", "\\quad" to "  ", "\\qquad" to "   ",
    "\\," to " ", "\\;" to " ", "\\!" to "", "\\ " to " ", "\\{" to "{", "\\}" to "}"
)

private val SYMBOL_REGEX: List<Pair<Regex, String>> = SYMBOLS.map { (k, v) ->
    val pat = Regex.escape(k) + (if (k.last().isLetter()) "(?![A-Za-z])" else "")
    Regex(pat) to v
}

private fun simplify(raw: String): String {
    var t = raw
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
    val s = simplify(raw)
    val sup = SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.72.em)
    val sub = SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.72.em)
    return buildAnnotatedString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
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
