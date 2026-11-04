package com.hanif.smartstudy.ui.ink

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hanif.smartstudy.ui.theme.LocalDarkMode
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/* ══════════════════════════════════════════════════════════════════════════
   ✍ হাতে লেখা — দেখা (InkViewer), লেখা (InkPadDialog) ও প্রশ্নের নিচে বসানোর অংশ (InkSection)
   ══════════════════════════════════════════════════════════════════════════ */

private const val INK_W = 1000f              // লজিক্যাল চওড়া
private const val INK_MAX_H = 10000f         // পাতার সর্বোচ্চ উচ্চতা (লজিক্যাল একক)
private const val INK_GROW_STEP = 800f       // নিচে লিখতে লিখতে পাতা এতটুকু করে বাড়ে
private const val INK_GROW_EDGE = 450f       // নিচের কিনারার এত কাছে কলম এলে পাতা বাড়ে

/** true হলে প্রশ্নের নিচে ইনলাইন "✍ হাতে লেখো" বাটন দেখায়। কুইজ/QBank তালিকায় ভাসমান বাটনের
 *  ভেতরে ✍ থাকে, তাই সেখানে এটা false করে দেওয়া হয় — ফলে দুইবার বাটন আসে না। */
val LocalInkInlineButtons = androidx.compose.runtime.compositionLocalOf { true }
private const val INK_GAP_MS = 250f          // প্লে-তে দুই স্ট্রোকের মাঝের বিরতি
private const val INK_DEFAULT = 0xFF111111.toInt()   // ডিফল্ট কালো — ডার্ক মোডে সাদাটে দেখায়
private val INK_COLORS = listOf(INK_DEFAULT, 0xFF1D4ED8.toInt(), 0xFFDC2626.toInt())
private val INK_WIDTHS = listOf(2.5f, 4.5f, 8f)

private fun inkColor(c: Int, dark: Boolean): Color =
    if (dark && c == INK_DEFAULT) Color(0xFFE5E7EB) else Color(c)

private fun inkPaper(dark: Boolean) = if (dark) Color(0xFF141B22) else Color(0xFFFFFFFF)
private fun inkLine(dark: Boolean)  = if (dark) Color(0xFF26323B) else Color(0xFFE2E8F0)

/** upToMs পর্যন্ত স্ট্রোক আঁকা (প্লে-র জন্য); Float.MAX_VALUE = সব */
private fun DrawScope.drawInk(strokes: List<InkStroke>, scale: Float, dark: Boolean, upToMs: Float) {
    var clock = 0f
    for (st in strokes) {
        val dur = (st.pts.lastOrNull()?.t ?: 0).toFloat()
        val localEnd = upToMs - clock
        if (localEnd <= 0f) break
        val limit = if (localEnd >= dur) Float.MAX_VALUE else localEnd
        drawStroke(st.pts, st.width, inkColor(st.color, dark), scale, limit)
        clock += dur + INK_GAP_MS
    }
}

private fun DrawScope.drawStroke(pts: List<InkPt>, width: Float, color: Color, scale: Float, limitMs: Float) {
    if (pts.isEmpty()) return
    val path = Path()
    var started = false
    var prev: InkPt? = null
    var count = 0
    for (p in pts) {
        if (p.t > limitMs) break
        val x = p.x * scale
        val y = p.y * scale
        if (!started) { path.moveTo(x, y); started = true }
        else {
            val pp = prev!!
            path.quadraticBezierTo(pp.x * scale, pp.y * scale, (pp.x + p.x) / 2f * scale, (pp.y + p.y) / 2f * scale)
        }
        prev = p
        count++
    }
    if (count == 0) return
    if (count == 1) {
        drawCircle(color, radius = width * scale / 2f, center = Offset(pts[0].x * scale, pts[0].y * scale))
    } else {
        drawPath(path, color, style = Stroke(width = width * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** লেখা দেখানো — পুরো সমাধান একসাথে (কোনো ধাপ বা প্লে-ব্যাক নেই) */
@Composable
fun InkViewer(doc: InkDoc, modifier: Modifier = Modifier) {
    val dark = LocalDarkMode.current.value
    val ratio = (doc.w.toFloat() / doc.h.coerceAtLeast(1).toFloat()).coerceIn(0.1f, 6f)
    Box(
        modifier.fillMaxWidth().aspectRatio(ratio)
            .clip(RoundedCornerShape(10.dp)).background(inkPaper(dark))
            .border(1.dp, inkLine(dark), RoundedCornerShape(10.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawInk(doc.strokes, size.width / doc.w.toFloat(), dark, Float.MAX_VALUE)
        }
    }
}

/** প্রশ্নের নিচে বসানোর অংশ: লেখা থাকলে দেখায়; অ্যাডমিনকে "✍ হাতে লেখো / এডিট" বাটন দেয় */
@Composable
fun InkSection(questionId: String, field: String, isAdmin: Boolean, modifier: Modifier = Modifier) {
    if (questionId.isBlank()) return
    val ctx = LocalContext.current.applicationContext
    val inlineButtons = LocalInkInlineButtons.current
    LaunchedEffect(Unit) { InkRepository.ensureIndex() }
    val ver = InkRepository.version(questionId, field)
    var doc by remember(questionId, field, ver) { mutableStateOf<InkDoc?>(null) }
    var loading by remember(questionId, field, ver) { mutableStateOf(ver != null) }
    var retry by remember(questionId, field) { mutableStateOf(0) }
    var editing by remember(questionId, field) { mutableStateOf(false) }
    var showInk by remember(questionId, field, ver) { mutableStateOf(false) }
    LaunchedEffect(questionId, field, ver, retry) {
        if (ver != null) {
            loading = true
            doc = InkRepository.load(ctx, questionId, field)
            loading = false
        } else { doc = null; loading = false }
    }
    val canEditHere = isAdmin && inlineButtons
    if (ver == null && !canEditHere) return
    val title = if (field == "technique") "✍ হাতে লেখা কৌশল" else "✍ হাতে লেখা সমাধান"
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (ver != null) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            val d = doc
            when {
                loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                d != null && d.text.isNotBlank() -> {
                    // পরিষ্কার টাইপ-করা সমাধান আগে; আসল হাতের লেখা বাটনের পেছনে
                    SelectionContainer {
                        com.hanif.smartstudy.ui.shared.MathText(
                            text = d.text, fontSize = 15.sp, lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(onClick = { showInk = !showInk }, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text(if (showInk) "হাতের লেখা লুকাও" else "✍ আসল হাতের লেখা দেখো", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                    if (showInk) InkViewer(d)
                }
                d != null -> InkViewer(d)
                else -> TextButton(onClick = { retry++ }) { Text("লোড করা যায়নি — আবার চেষ্টা", fontFamily = NotoSansBengali) }
            }
        }
        if (canEditHere) {
            OutlinedButton(
                onClick = { editing = true },
                modifier = Modifier.heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (ver != null) "✍ হাতের লেখা এডিট" else "✍ হাতে লেখো (টাচপেন)", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (editing) {
        InkPadDialog(
            title = title,
            initial = doc,
            onDismiss = { editing = false },
            onSave = { newDoc, newText ->
                val err = InkRepository.save(questionId, field, newDoc, text = newText)
                if (err == null) { editing = false; retry++ }
                err
            }
        )
    }
}

/** পূর্ণ-পর্দা লেখার প্যাড (টাচপেন/আঙুল)। onSave সফল হলে null, নইলে ত্রুটি-বার্তা ফেরত দেয় */
@Composable
fun InkPadDialog(
    title: String,
    initial: InkDoc?,
    onDismiss: () -> Unit,
    onSave: suspend (InkDoc?, String?) -> String?,
    // ব্যাখ্যা | কৌশল বদলানোর চিপ (null হলে দেখায় না)
    fieldOptions: List<Pair<String, String>>? = null,
    currentField: String = "",
    onFieldChange: (String) -> Unit = {}
) {
    val dark = LocalDarkMode.current.value
    var strokes by remember { mutableStateOf(initial?.strokes ?: emptyList()) }
    val undo = remember { mutableStateListOf<List<InkStroke>>() }
    val redo = remember { mutableStateListOf<List<InkStroke>>() }
    var eraser by remember { mutableStateOf(false) }
    var colorIdx by remember { mutableStateOf(0) }
    var widthIdx by remember { mutableStateOf(1) }
    var grid by remember { mutableStateOf(true) }
    // আঙুলেও লেখা: বন্ধ (ডিফল্ট) = শুধু স্টাইলাস লেখে, আঙুল/তালু শুধু পাতা ওপর-নিচ টানে
    var fingerDraw by remember { mutableStateOf(false) }
    var docH by remember { mutableStateOf(max(initial?.h ?: 0, 1200).toFloat().coerceAtMost(INK_MAX_H)) }
    var dirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askDiscard by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var textChanged by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }
    var converting by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf((initial?.text ?: "").isNotBlank()) }
    val liveList = remember { ArrayList<InkPt>() }
    var liveTick by remember { mutableStateOf(0) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun pushUndo() { undo.add(strokes); redo.clear(); dirty = true }
    fun requestClose() { if (dirty) askDiscard = true else onDismiss() }
    BackHandler { requestClose() }

    Dialog(onDismissRequest = { requestClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // ── উপরের বার ──
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { requestClose() }, modifier = Modifier.heightIn(min = 44.dp)) { Text("✕ বন্ধ", fontFamily = NotoSansBengali) }
                    Text(title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, maxLines = 1)
                    Button(
                        enabled = !saving,
                        onClick = {
                            val problem = when {
                                strokes.isEmpty() && text.isNotBlank() -> "টেক্সট সংরক্ষণ করতে আগে হাতে লিখতে হবে"
                                textChanged && text.isNotBlank() && !verified -> "টেক্সট যাচাই করে \"আমি যাচাই করেছি\" টিক দাও — অথবা টেক্সট খালি করো"
                                else -> null
                            }
                            if (problem != null) { error = problem } else {
                            saving = true; error = null
                            val maxY = strokes.maxOfOrNull { st -> st.pts.maxOfOrNull { it.y } ?: 0f } ?: 0f
                            val cropH = minOf(docH, max(maxY + 60f, 200f)).toInt()
                            val doc = if (strokes.isEmpty()) null else InkDoc(INK_W.toInt(), cropH, strokes)
                            scope.launch {
                                val err = onSave(doc, if (textChanged) text.trim() else null)
                                saving = false
                                if (err != null) error = err
                            }
                            }
                        },
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) { Text(if (saving) "সেভ হচ্ছে…" else "সংরক্ষণ", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp), fontFamily = NotoSansBengali) }

                // ── টুলবার ──
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    fieldOptions?.forEach { (key, label) ->
                        FilterChip(selected = currentField == key, onClick = {
                            if (key == currentField) return@FilterChip
                            if (dirty) error = "আগে সংরক্ষণ করুন, তারপর ব্যাখ্যা/কৌশল বদলান" else onFieldChange(key)
                        }, label = { Text("✍ $label", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) })
                    }
                    FilterChip(selected = fingerDraw, onClick = { fingerDraw = !fingerDraw },
                        label = { Text(if (fingerDraw) "☝ আঙুলে লিখছি" else "🖊 শুধু স্টাইলাস", fontFamily = NotoSansBengali) })
                    OutlinedButton(enabled = undo.isNotEmpty(), onClick = {
                        redo.add(strokes); strokes = undo.removeAt(undo.size - 1); dirty = true
                    }, modifier = Modifier.heightIn(min = 40.dp)) { Text("↶ ফেরাও", fontFamily = NotoSansBengali) }
                    OutlinedButton(enabled = redo.isNotEmpty(), onClick = {
                        undo.add(strokes); strokes = redo.removeAt(redo.size - 1); dirty = true
                    }, modifier = Modifier.heightIn(min = 40.dp)) { Text("↷ আবার", fontFamily = NotoSansBengali) }
                    FilterChip(selected = !eraser, onClick = { eraser = false }, label = { Text("কলম", fontFamily = NotoSansBengali) })
                    FilterChip(selected = eraser, onClick = { eraser = true }, label = { Text("রাবার", fontFamily = NotoSansBengali) })
                    INK_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(inkColor(c, dark))
                                .border(if (!eraser && colorIdx == i) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Gray), CircleShape)
                                .clickable { colorIdx = i; eraser = false }
                        )
                    }
                    listOf("সরু", "মাঝারি", "মোটা").forEachIndexed { i, l ->
                        FilterChip(selected = widthIdx == i, onClick = { widthIdx = i }, label = { Text(l, fontFamily = NotoSansBengali) })
                    }
                    FilterChip(selected = showText, onClick = { showText = !showText }, label = { Text("Aa টেক্সট", fontFamily = NotoSansBengali) })
                    FilterChip(selected = grid, onClick = { grid = !grid }, label = { Text("গ্রিড", fontFamily = NotoSansBengali) })
                    OutlinedButton(enabled = docH < INK_MAX_H, onClick = { docH = (docH + 800f).coerceAtMost(INK_MAX_H); dirty = true }, modifier = Modifier.heightIn(min = 40.dp)) { Text("＋ জায়গা", fontFamily = NotoSansBengali) }
                    OutlinedButton(enabled = strokes.isNotEmpty(), onClick = { askClear = true }, modifier = Modifier.heightIn(min = 40.dp)) { Text("সব মোছো", fontFamily = NotoSansBengali) }
                }
                Spacer(Modifier.height(6.dp))

                // ── লেখার জায়গা ──
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp)).background(inkPaper(dark)).border(1.dp, inkLine(dark), RoundedCornerShape(10.dp))) {
                    val wPx = constraints.maxWidth.toFloat()
                    val scale = wPx / INK_W
                    // পাতা সবসময় পুরো পর্দা ভরে থাকে (লেখার জায়গা কম পড়ে না)
                    val viewportUnits = constraints.maxHeight.toFloat() / scale
                    LaunchedEffect(viewportUnits) { if (docH < viewportUnits) docH = viewportUnits.coerceAtMost(INK_MAX_H) }
                    val canvasH = with(LocalDensity.current) { (docH * scale).toDp() }.coerceAtLeast(maxHeight)
                    Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        Canvas(
                            Modifier.fillMaxWidth().height(canvasH)
                                .pointerInput(eraser, colorIdx, widthIdx, scale, fingerDraw) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val isPen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                                        // আঙুল/তালু (স্টাইলাস-অনলি মোডে) → কিছুই না করে স্ক্রলকে ছেড়ে দাও:
                                        // আঙুলে টেনে পাতা নিচে আনা/ওপরে তোলা যায়, তালু লেখায় বাধা দেয় না
                                        if (!isPen && !fingerDraw) return@awaitEachGesture
                                        val useEraser = eraser || down.type == PointerType.Eraser
                                        val t0 = System.currentTimeMillis()
                                        if (useEraser) {
                                            var pushed = false
                                            fun eraseAt(o: Offset) {
                                                val lx = o.x / scale
                                                val ly = o.y / scale
                                                val keep = strokes.filter { st -> st.pts.none { hypot(it.x - lx, it.y - ly) < 28f } }
                                                if (keep.size != strokes.size) {
                                                    if (!pushed) { pushUndo(); pushed = true }
                                                    strokes = keep
                                                }
                                            }
                                            eraseAt(down.position); down.consume()
                                            do {
                                                val ev = awaitPointerEvent()
                                                ev.changes.forEach { c ->
                                                    if (c.id == down.id && c.pressed) eraseAt(c.position)
                                                    c.consume()   // অন্য পয়েন্টার (তালু) স্ক্রল/লেখা কিছুই ঘটাবে না
                                                }
                                            } while (ev.changes.any { it.id == down.id && it.pressed })
                                        } else {
                                            liveList.clear()
                                            fun add(o: Offset) {
                                                val lx = o.x / scale
                                                val ly = o.y / scale
                                                val last = liveList.lastOrNull()
                                                if (last == null || hypot(lx - last.x, ly - last.y) >= 1.5f) {
                                                    liveList.add(InkPt(lx, ly, (System.currentTimeMillis() - t0).toInt()))
                                                    liveTick++
                                                    // নিচের কিনারার কাছে লিখলে পাতা নিজে থেকে বাড়ে — টেনে নামানোর জায়গা সবসময় থাকে
                                                    if (ly > docH - INK_GROW_EDGE && docH < INK_MAX_H) docH = (docH + INK_GROW_STEP).coerceAtMost(INK_MAX_H)
                                                }
                                            }
                                            add(down.position); down.consume()
                                            do {
                                                val ev = awaitPointerEvent()
                                                ev.changes.forEach { c ->
                                                    if (c.id == down.id && c.pressed) add(c.position)
                                                    c.consume()   // তালুর স্পর্শ গিলে ফেলা হয় — পাতা নড়ে না, দাগও পড়ে না
                                                }
                                            } while (ev.changes.any { it.id == down.id && it.pressed })
                                            if (liveList.isNotEmpty()) {
                                                pushUndo()
                                                strokes = strokes + InkStroke(INK_COLORS[colorIdx], INK_WIDTHS[widthIdx], liveList.toList())
                                            }
                                            liveList.clear()
                                            liveTick++
                                        }
                                    }
                                }
                        ) {
                            if (grid) {
                                var gy = 60f * scale
                                while (gy < size.height) {
                                    drawLine(inkLine(dark), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                                    gy += 60f * scale
                                }
                            }
                            drawInk(strokes, scale, dark, Float.MAX_VALUE)
                            if (liveTick >= 0 && liveList.isNotEmpty()) {
                                drawStroke(liveList.toList(), INK_WIDTHS[widthIdx], inkColor(INK_COLORS[colorIdx], dark), scale, Float.MAX_VALUE)
                            }
                        }
                    }
                    // প্রথম দাগ না পড়া পর্যন্ত ছোট নির্দেশ (স্পর্শ আটকায় না)
                    if (strokes.isEmpty()) {
                        Text(
                            if (fingerDraw) "আঙুল দিয়ে লিখুন। ওপর-নিচ করতে ওপরের \"আঙুলে লিখছি\" বন্ধ করুন।"
                            else "স্টাইলাস দিয়ে লিখুন · আঙুল দিয়ে টেনে পাতা ওপর-নিচ করুন (তালু বাধা দেবে না)",
                            Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 16.dp, end = 16.dp),
                            fontSize = 12.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                if (showText) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 270.dp).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                enabled = !converting && strokes.isNotEmpty(),
                                onClick = {
                                    converting = true; error = null
                                    val maxY = strokes.maxOfOrNull { st -> st.pts.maxOfOrNull { it.y } ?: 0f } ?: 0f
                                    val d = InkDoc(INK_W.toInt(), minOf(docH, max(maxY + 60f, 200f)).toInt(), strokes)
                                    scope.launch {
                                        val (t, err) = InkRepository.toText(d)
                                        converting = false
                                        if (err != null) error = err
                                        else { text = t ?: ""; textChanged = true; verified = false; dirty = true }
                                    }
                                },
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) { Text(if (converting) "রূপান্তর হচ্ছে…" else "Aa লেখা থেকে রূপান্তর", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.weight(1f))
                            if (textChanged && text.isNotBlank()) {
                                Checkbox(checked = verified, onCheckedChange = { verified = it })
                                Text("আমি যাচাই করেছি", fontSize = 12.sp, fontFamily = NotoSansBengali)
                            }
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it; textChanged = true; verified = false; dirty = true },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            label = { Text("টেক্সট (ঘাত x^2, ভগ্নাংশ \\frac{a}{b}, মূল sqrt(x))", fontFamily = NotoSansBengali, fontSize = 12.sp) }
                        )
                        if (text.isNotBlank()) {
                            Text("প্রিভিউ — স্টুডেন্ট এভাবে দেখবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 6.dp))
                            com.hanif.smartstudy.ui.shared.MathText(text = text, fontSize = 15.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("সংরক্ষণ না করে বের হবেন?", fontFamily = NotoSansBengali) },
            text = { Text("নতুন লেখা হারিয়ে যাবে।", fontFamily = NotoSansBengali) },
            confirmButton = { TextButton(onClick = { askDiscard = false; onDismiss() }) { Text("হ্যাঁ, বের হব", fontFamily = NotoSansBengali) } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("থাকি", fontFamily = NotoSansBengali) } }
        )
    }
    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("সব লেখা মুছবেন?", fontFamily = NotoSansBengali) },
            text = { Text("\"ফেরাও\" দিয়ে আবার ফিরিয়ে আনা যাবে (সংরক্ষণের আগে)।", fontFamily = NotoSansBengali) },
            confirmButton = { TextButton(onClick = { pushUndo(); strokes = emptyList(); askClear = false }) { Text("মুছুন", fontFamily = NotoSansBengali) } },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("না", fontFamily = NotoSansBengali) } }
        )
    }
}


/** কুইজ/QBank তালিকার ভাসমান বাটনের ✍ থেকে খোলে: একটা প্রশ্নের ব্যাখ্যা বা কৌশল হাতে লেখার পূর্ণ-পাতা প্যাড */
@Composable
fun InkPadForQuestion(questionId: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    var field by remember(questionId) { mutableStateOf("explanation") }
    var doc by remember(questionId, field) { mutableStateOf<InkDoc?>(null) }
    var loaded by remember(questionId, field) { mutableStateOf(false) }
    LaunchedEffect(questionId, field) {
        InkRepository.ensureIndex()
        doc = if (InkRepository.version(questionId, field) != null) InkRepository.load(ctx, questionId, field) else null
        loaded = true
    }
    if (!loaded) return
    key(questionId, field) {
        InkPadDialog(
            title = if (field == "technique") "✍ হাতে লেখা কৌশল" else "✍ হাতে লেখা সমাধান",
            initial = doc,
            onDismiss = onDismiss,
            onSave = { d, t ->
                val err = InkRepository.save(questionId, field, d, text = t)
                if (err == null) onDismiss()
                err
            },
            fieldOptions = listOf("explanation" to "ব্যাখ্যা", "technique" to "কৌশল"),
            currentField = field,
            onFieldChange = { field = it }
        )
    }
}
