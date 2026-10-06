package com.hanif.smartstudy.ui.shared

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hanif.smartstudy.ui.theme.NotoSansBengali

/**
 * ── Study Nav "Phase 2 — Rich Content + PDF-quality Reader" ──
 *
 *  • StudyReaderSettings / StudyReaderStore — ফন্ট সাইজ, লাইন স্পেসিং, রিডিং উইডথ,
 *    থিম (Auto/Light/Dark/Sepia), ফুলস্ক্রিন। SharedPreferences-এ সেভ থাকে।
 *  • StudyRichBlocks — ব্লক-লেভেল রেন্ডারার: হেডিং, বুলেট, নাম্বার লিস্ট, কোট,
 *    ডিভাইডার, টেবিল, ইম্পরট্যান্ট বক্স, অ্যালাইনমেন্ট, সুপার/সাবস্ক্রিপ্ট।
 *    ইনলাইন **বোল্ড** / *ইটালিক* / __আন্ডারলাইন__ / <mark> হাইলাইট আগের
 *    parseRichAnnotated() দিয়েই হয় (তাই আগের কনটেন্ট হুবহু একই থাকে)।
 *  • StudyReaderSettingsSheet — "Aa" বাটনে খোলা সেটিংস শীট।
 *  • StudyFullscreenEffect — সিস্টেম বার লুকিয়ে ফুলস্ক্রিন রিডিং।
 *
 * কনটেন্টে লেখার সিনট্যাক্স (Sheet/Admin App-এর যেকোনো টেক্সট ঘরে):
 *   # বড় হেডিং        ## মাঝারি হেডিং      ### ছোট হেডিং
 *   - বুলেট            1. নাম্বার লিস্ট      > কোট
 *   !! পরীক্ষার জন্য ইম্পরট্যান্ট (হলুদ বক্স)
 *   ---  (ডিভাইডার)    | কলাম | কলাম |  (টেবিল; প্রথম সারি হেডার)
 *   <center>লেখা</center>   <right>লেখা</right>
 *   H<sub>2</sub>O   x<sup>2</sup>
 */

// ─────────────────────────────────────────────────────────────────────────────
// Settings model + store
// ─────────────────────────────────────────────────────────────────────────────
enum class StudyReaderTheme(val label: String) {
    AUTO("Auto"), LIGHT("Light"), DARK("Dark"), SEPIA("Sepia")
}

enum class StudyReadingWidth(val label: String, val extraPadding: Dp) {
    NARROW("সরু", 18.dp), NORMAL("সাধারণ", 4.dp), WIDE("চওড়া", 0.dp)
}

data class StudyReaderSettings(
    val fontScale   : Float             = 1.0f,    // 0.85 .. 1.6
    val lineSpacing : Float             = 1.45f,   // 1.2 .. 2.0 (ফন্ট-সাইজের গুণিতক)
    val width       : StudyReadingWidth = StudyReadingWidth.NORMAL,
    val theme       : StudyReaderTheme  = StudyReaderTheme.AUTO,
    val fullscreen  : Boolean           = false,
    // ── Phase 3: মিডিয়া দেখানো/লুকানো (বন্ধ থাকলে "দেখান" চিপ আসে, ট্যাপে খোলে) ──
    val showImages  : Boolean           = true,
    val showVideos  : Boolean           = true
) {
    /** Auto হলে null — মানে অ্যাপের নিজস্ব রঙই থাকবে */
    fun bgColor(): Color? = when (theme) {
        StudyReaderTheme.AUTO  -> null
        StudyReaderTheme.LIGHT -> Color(0xFFFFFFFF)
        StudyReaderTheme.DARK  -> Color(0xFF14181F)
        StudyReaderTheme.SEPIA -> Color(0xFFF4ECD8)
    }

    fun textColor(): Color? = when (theme) {
        StudyReaderTheme.AUTO  -> null
        StudyReaderTheme.LIGHT -> Color(0xFF1E293B)
        StudyReaderTheme.DARK  -> Color(0xFFE5E7EB)
        StudyReaderTheme.SEPIA -> Color(0xFF433422)
    }

    fun scaledSp(base: Int): Int = (base * fontScale).toInt().coerceAtLeast(10)
}

object StudyReaderStore {
    private const val PREFS = "study_reader_prefs"
    private var loaded = false

    /** Compose state — এটা বদলালে যেসব কার্ড পড়ছে সব নিজে থেকেই রিকম্পোজ হয় */
    var settings by mutableStateOf(StudyReaderSettings())
        private set

    /** একবারই লোড হয় (বারবার কল করা নিরাপদ) */
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        settings = StudyReaderSettings(
            fontScale   = p.getFloat("font_scale", 1.0f).coerceIn(0.85f, 1.6f),
            lineSpacing = p.getFloat("line_spacing", 1.45f).coerceIn(1.2f, 2.0f),
            width       = runCatching { StudyReadingWidth.valueOf(p.getString("width", "NORMAL") ?: "NORMAL") }
                            .getOrDefault(StudyReadingWidth.NORMAL),
            theme       = runCatching { StudyReaderTheme.valueOf(p.getString("theme", "AUTO") ?: "AUTO") }
                            .getOrDefault(StudyReaderTheme.AUTO),
            fullscreen  = p.getBoolean("fullscreen", false),
            showImages  = p.getBoolean("show_images", true),
            showVideos  = p.getBoolean("show_videos", true)
        )
    }

    fun update(context: Context, change: (StudyReaderSettings) -> StudyReaderSettings) {
        init(context)
        val s = change(settings)
        settings = s
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("font_scale", s.fontScale)
            .putFloat("line_spacing", s.lineSpacing)
            .putString("width", s.width.name)
            .putString("theme", s.theme.name)
            .putBoolean("fullscreen", s.fullscreen)
            .putBoolean("show_images", s.showImages)
            .putBoolean("show_videos", s.showVideos)
            .apply()
    }

    fun reset(context: Context) = update(context) { StudyReaderSettings() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Block parser + renderer
// ─────────────────────────────────────────────────────────────────────────────
private sealed class RBlock {
    data class Heading(val level: Int, val text: String) : RBlock()
    data class Bullet(val text: String) : RBlock()
    data class Numbered(val num: String, val text: String) : RBlock()
    data class Quote(val text: String) : RBlock()
    data class Important(val text: String) : RBlock()
    data class Mistake(val text: String) : RBlock()
    data class Para(val text: String, val align: TextAlign) : RBlock()
    data class Table(val rows: List<List<String>>) : RBlock()
    object Divider : RBlock()
    object Gap : RBlock()
}

private val numberedRegex = Regex("^([0-9০-৯]{1,3})[.)]\\s+(.*)$")

private fun isTableLine(l: String) = l.startsWith("|") && l.count { it == '|' } >= 2
private fun isTableSeparator(cells: List<String>) =
    cells.isNotEmpty() && cells.all { c -> c.isNotBlank() && c.all { it == '-' || it == ':' || it == ' ' } }

private fun splitCells(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

private fun lineKind(raw: String): String {
    val l = raw.trim()
    return when {
        l.isEmpty() -> "gap"
        l == "---" || l == "___" -> "divider"
        l.startsWith("### ") || l.startsWith("## ") || l.startsWith("# ") -> "heading"
        l.startsWith("- ") || l.startsWith("• ") || l.startsWith("* ") -> "bullet"
        numberedRegex.matches(l) -> "numbered"
        l.startsWith("> ") -> "quote"
        l.startsWith("!! ") -> "important"
        l.startsWith("?? ") -> "mistake"
        isTableLine(l) -> "table"
        l.startsWith("<center>") || l.startsWith("<right>") -> "align"
        l.contains("<sup>") || l.contains("<sub>") -> "script"
        else -> "para"
    }
}

/** দ্রুত চেক — টেক্সটে আসলেই ব্লক-মার্কআপ আছে কিনা (নাহলে পুরনো রেন্ডারিং-ই থাকে) */
fun hasStudyBlockMarkup(text: String): Boolean {
    if (text.length < 3) return false
    return text.lineSequence().any {
        when (lineKind(it)) { "gap", "para" -> false else -> true }
    }
}

private fun parseBlocks(text: String): List<RBlock> {
    val lines = text.replace("\r\n", "\n").split('\n')
    val out = ArrayList<RBlock>()
    var i = 0
    while (i < lines.size) {
        val l = lines[i].trim()
        when (lineKind(l)) {
            "gap" -> { if (out.isNotEmpty() && out.last() !is RBlock.Gap) out.add(RBlock.Gap); i++ }
            "divider" -> { out.add(RBlock.Divider); i++ }
            "heading" -> {
                val level = when { l.startsWith("### ") -> 3; l.startsWith("## ") -> 2; else -> 1 }
                out.add(RBlock.Heading(level, l.dropWhile { it == '#' }.trim())); i++
            }
            "bullet" -> { out.add(RBlock.Bullet(l.substring(2).trim())); i++ }
            "numbered" -> {
                val m = numberedRegex.find(l)!!
                out.add(RBlock.Numbered(m.groupValues[1], m.groupValues[2])); i++
            }
            "quote" -> { out.add(RBlock.Quote(l.substring(2).trim())); i++ }
            "important" -> { out.add(RBlock.Important(l.substring(3).trim())); i++ }
            "mistake" -> { out.add(RBlock.Mistake(l.substring(3).trim())); i++ }
            "table" -> {
                val rows = ArrayList<List<String>>()
                while (i < lines.size && isTableLine(lines[i].trim())) {
                    val cells = splitCells(lines[i])
                    if (!isTableSeparator(cells)) rows.add(cells)
                    i++
                }
                if (rows.isNotEmpty()) out.add(RBlock.Table(rows))
            }
            "align" -> {
                val center = l.startsWith("<center>")
                val inner = l.removePrefix("<center>").removePrefix("<right>")
                    .removeSuffix("</center>").removeSuffix("</right>").trim()
                out.add(RBlock.Para(inner, if (center) TextAlign.Center else TextAlign.End)); i++
            }
            else -> { out.add(RBlock.Para(l, TextAlign.Start)); i++ }
        }
    }
    while (out.isNotEmpty() && out.last() is RBlock.Gap) out.removeAt(out.size - 1)
    return out
}

/** ইনলাইন মার্কআপ + <sup>/<sub> */
private fun inlineAnnotated(text: String, sizeSp: Float): androidx.compose.ui.text.AnnotatedString {
    if (!text.contains("<sup>") && !text.contains("<sub>")) return parseRichAnnotated(text, sizeSp)
    val tag = Regex("<(sup|sub)>(.*?)</\\1>")
    return buildAnnotatedString {
        var last = 0
        for (m in tag.findAll(text)) {
            if (m.range.first > last) append(parseRichAnnotated(text.substring(last, m.range.first), sizeSp))
            val shift = if (m.groupValues[1] == "sup") BaselineShift.Superscript else BaselineShift.Subscript
            pushStyle(SpanStyle(baselineShift = shift, fontSize = (sizeSp * 0.75f).sp))
            append(parseRichAnnotated(m.groupValues[2], sizeSp))
            pop()
            last = m.range.last + 1
        }
        if (last < text.length) append(parseRichAnnotated(text.substring(last), sizeSp))
    }
}

@Composable
fun StudyRichBlocks(
    text      : String,
    settings  : StudyReaderSettings,
    baseSizeSp: Int,
    textColor : Color,
    modifier  : Modifier = Modifier
) {
    val blocks = remember(text) { parseBlocks(text) }
    val size   = baseSizeSp * settings.fontScale
    val lh     = (size * settings.lineSpacing).sp
    val accent = Color(0xFF059669)
    val muted  = textColor.copy(alpha = 0.55f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { b ->
            when (b) {
                is RBlock.Gap -> Spacer(Modifier.height((size * 0.5f).dp))
                is RBlock.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp), color = muted.copy(alpha = 0.4f)
                )
                is RBlock.Heading -> {
                    val hs = size * when (b.level) { 1 -> 1.45f; 2 -> 1.25f; else -> 1.1f }
                    Text(
                        text = inlineAnnotated(b.text, hs),
                        color = if (b.level == 1) accent else textColor,
                        fontSize = hs.sp, lineHeight = (hs * settings.lineSpacing).sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
                        modifier = Modifier.padding(top = if (b.level == 1) 6.dp else 3.dp)
                    )
                }
                is RBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text("•", color = accent, fontSize = size.sp, lineHeight = lh,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
                    Text(inlineAnnotated(b.text, size), color = textColor, fontSize = size.sp,
                        lineHeight = lh, fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                }
                is RBlock.Numbered -> Row(Modifier.fillMaxWidth()) {
                    Text("${b.num}.", color = accent, fontSize = size.sp, lineHeight = lh,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
                    Text(inlineAnnotated(b.text, size), color = textColor, fontSize = size.sp,
                        lineHeight = lh, fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                }
                is RBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(accent.copy(alpha = 0.7f)))
                    Text(inlineAnnotated(b.text, size), color = textColor.copy(alpha = 0.85f),
                        fontSize = size.sp, lineHeight = lh, fontFamily = NotoSansBengali,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp))
                }
                is RBlock.Important -> Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF3C4),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Row(Modifier.padding(10.dp)) {
                        Text("💡", fontSize = size.sp, modifier = Modifier.padding(end = 8.dp))
                        Text(inlineAnnotated(b.text, size), color = Color(0xFF3B2A00),
                            fontSize = size.sp, lineHeight = lh, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                }
                is RBlock.Mistake -> Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEE2E2),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Row(Modifier.padding(10.dp)) {
                        Text("⚠️", fontSize = size.sp, modifier = Modifier.padding(end = 8.dp))
                        Text(inlineAnnotated(b.text, size), color = Color(0xFF7F1D1D),
                            fontSize = size.sp, lineHeight = lh, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                }
                is RBlock.Para -> Text(
                    inlineAnnotated(b.text, size), color = textColor, fontSize = size.sp,
                    lineHeight = lh, fontFamily = NotoSansBengali, textAlign = b.align,
                    modifier = Modifier.fillMaxWidth()
                )
                is RBlock.Table -> {
                    val cols = b.rows.maxOf { it.size }
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(muted.copy(alpha = 0.06f))
                    ) {
                        b.rows.forEachIndexed { r, row ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (r == 0) accent.copy(alpha = 0.16f) else Color.Transparent)
                            ) {
                                for (c in 0 until cols) {
                                    Text(
                                        inlineAnnotated(row.getOrElse(c) { "" }, size * 0.95f),
                                        color = textColor, fontSize = (size * 0.95f).sp,
                                        lineHeight = (size * 0.95f * settings.lineSpacing).sp,
                                        fontFamily = NotoSansBengali,
                                        fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            if (r < b.rows.size - 1) HorizontalDivider(color = muted.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings sheet
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyReaderSettingsSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val s   = StudyReaderStore.settings
    val green = Color(0xFF059669)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aa  পড়ার সেটিংস", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                    fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                Text("রিসেট", fontSize = 13.sp, color = green, fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { StudyReaderStore.reset(ctx) }.padding(8.dp))
            }

            SettingSlider("Font Size", "${(s.fontScale * 100).toInt()}%", s.fontScale, 0.85f..1.6f) { v ->
                StudyReaderStore.update(ctx) { it.copy(fontScale = v) }
            }
            SettingSlider("Line Spacing", String.format("%.1f", s.lineSpacing), s.lineSpacing, 1.2f..2.0f) { v ->
                StudyReaderStore.update(ctx) { it.copy(lineSpacing = v) }
            }

            Text("Reading Width", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudyReadingWidth.values().forEach { w ->
                    ChoiceChip(w.label, s.width == w) { StudyReaderStore.update(ctx) { it.copy(width = w) } }
                }
            }

            Text("Theme", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudyReaderTheme.values().forEach { t ->
                    ChoiceChip(t.label, s.theme == t) { StudyReaderStore.update(ctx) { it.copy(theme = t) } }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fullscreen Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        fontFamily = NotoSansBengali)
                    Text("স্ট্যাটাস/নেভিগেশন বার লুকিয়ে পড়া", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                }
                Switch(checked = s.fullscreen,
                    onCheckedChange = { v -> StudyReaderStore.update(ctx) { it.copy(fullscreen = v) } })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show Images", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                Switch(checked = s.showImages,
                    onCheckedChange = { v -> StudyReaderStore.update(ctx) { it.copy(showImages = v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show YouTube / Facebook", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                Switch(checked = s.showVideos,
                    onCheckedChange = { v -> StudyReaderStore.update(ctx) { it.copy(showVideos = v) } })
            }

            // লাইভ প্রিভিউ — পরিবর্তন সাথে সাথে দেখা যায়
            val bg = s.bgColor() ?: MaterialTheme.colorScheme.surfaceVariant
            val fg = s.textColor() ?: MaterialTheme.colorScheme.onSurface
            Surface(shape = RoundedCornerShape(12.dp), color = bg,
                border = BorderStroke(1.dp, fg.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(horizontal = 12.dp + s.width.extraPadding, vertical = 12.dp)) {
                    StudyRichBlocks(
                        text = "## তৎপুরুষ সমাস\nযে সমাসে **পূর্বপদ** প্রধান নয়, __পরপদই__ প্রধান।\n- রাজার পুত্র = রাজপুত্র",
                        settings = s, baseSizeSp = 14, textColor = fg
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String, valueText: String, value: Float,
    range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit
) {
    Column {
        Row {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 12.sp, color = Color(0xFF059669),
                fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val green = Color(0xFF059669)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) green else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) green else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, fontSize = 13.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fullscreen reading
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StudyFullscreenEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            // স্ক্রিন থেকে বেরোলে/বন্ধ করলে সিস্টেম বার ফিরিয়ে দেওয়া হয়
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
