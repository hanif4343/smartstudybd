package com.hanif.smartstudy.ui.components

// ═══════════════════════════════════════════════════════════════════
//  MediaViewer.kt  —  index.html এর parseLinksToImages() এর exact Kotlin port
//
//  পুরাতন HTML অ্যাপের লজিক (index.html থেকে):
//  1. PDF/Drive লিংক আগে detect → "📕 PDF দেখুন" বাটন
//  2. Image লিংক (.jpg/.png/.webp/ibb.co) → অটো ছবি + zoom
//  3. YouTube/Facebook লিংক → বাটন, ক্লিক করলে App এ সরাসরি খোলে
//  4. বাকি plain text → সাধারণ Text
//
//  ব্যবহার:
//    RichContentText(text = item.answer ?: "")
//    RichContentText(text = item.explanation ?: "")
//    RichContentText(text = item.technique ?: "")
//
//  index.html এ এটাই করে:
//    correctVal = parseLinksToImages(corRaw)
//    explanation = parseLinksToImages(expRaw || ansRaw)
//    tech = parseLinksToImages(techRaw)
// ═══════════════════════════════════════════════════════════════════

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.ui.shared.parseRichAnnotated
import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────
// রং — পুরাতন HTML অ্যাপের মতো (index.html style)
// ─────────────────────────────────────────────────────────────────
private val IndigoPrimary = Color(0xFF4338CA)
private val IndigoBorder  = Color(0xFFE2E8F0)
private val YtRed         = Color(0xFFBE123C)
private val YtBg          = Color(0xFFFFF1F2)
private val YtBorder      = Color(0xFFFECDD3)
private val FbBlue        = Color(0xFF0369A1)
private val FbBg          = Color(0xFFF0F9FF)
private val FbBorder      = Color(0xFFBAE6FD)

// ─────────────────────────────────────────────────────────────────
// MediaSegment — টেক্সট কে ভাগ করার জন্য
// ─────────────────────────────────────────────────────────────────
sealed class MediaSegment {
    data class PlainText(val text: String) : MediaSegment()
    data class ImageLink(val url: String)  : MediaSegment()
    data class VideoLink(val url: String, val isYoutube: Boolean, val title: String? = null) : MediaSegment()
    data class PdfLink(val url: String)    : MediaSegment()
    // ── Study Nav Phase 3: সাধারণ ওয়েবসাইট/আর্টিকেল লিংক — শুধু parse(allowWeb = true)
    // দিলে তৈরি হয় (Study রিডার), ডিফল্টে কখনো না → বাকি অ্যাপের আচরণ অপরিবর্তিত ──
    data class WebLink(val url: String)    : MediaSegment()
}

// ─────────────────────────────────────────────────────────────────
// MediaLinkParser — index.html এর parseLinksToImages() এর exact Kotlin port
//
// parse order (index এর মতো):
//   1. PDF/Drive লিংক আগে
//   2. Image লিংক (extension-based + ibb.co)
//   3. YouTube/Facebook ভিডিও লিংক
//   4. বাকি plain text
// ─────────────────────────────────────────────────────────────────
object MediaLinkParser {

    // ১. PDF regex — index এর: drive.google.com, docs.google.com, .pdf URL
    private val PDF_REGEX = Regex(
        """https?://(?:drive\.google\.com|docs\.google\.com|[^\s"'>]+?\.pdf)[^\s"'>]*""",
        RegexOption.IGNORE_CASE
    )

    // ২. ছবির লিংক — index এর imgRegex: extension-based
    private val IMAGE_EXT_REGEX = Regex(
        """https?://[^\s"'>]+?\.(?:jpg|jpeg|gif|png|webp|bmp)(?:\?[^\s"'>]*)?""",
        RegexOption.IGNORE_CASE
    )

    // ২b. ImgBB direct image URL — i.ibb.co বা ibb.co (index এ আলাদা imgbb regex)
    private val IMGBB_REGEX = Regex(
        """https?://(?:i\.ibb\.co|ibb\.co)/[^\s"'>]+""",
        RegexOption.IGNORE_CASE
    )

    // ৩. ভিডিও লিংক — index এর urlRegex: youtube.com, youtu.be, facebook.com
    private val VIDEO_REGEX = Regex(
        """https?://(?:www\.)?(?:youtube\.com|youtu\.be|facebook\.com)[^\s]+""",
        RegexOption.IGNORE_CASE
    )

    // Study Nav Phase 3: PDF/ছবি/ভিডিও ছাড়া বাকি http(s) লিংক (allowWeb=true হলে)
    private val WEB_REGEX = Regex("""https?://[^\s"'>)\]]+""", RegexOption.IGNORE_CASE)

    fun parse(text: String, allowWeb: Boolean = false): List<MediaSegment> {
        if (text.isBlank()) return emptyList()

        data class Found(val start: Int, val end: Int, val segment: MediaSegment)
        val allFound = mutableListOf<Found>()

        // ── Step 1: PDF লিংক আগে detect করি (index এর মতো) ──
        PDF_REGEX.findAll(text).forEach { m ->
            val url = normalizePdfUrl(m.value.trim())
            allFound += Found(m.range.first, m.range.last + 1, MediaSegment.PdfLink(url))
        }

        // ── Step 2a: ImgBB লিংক ──
        IMGBB_REGEX.findAll(text).forEach { m ->
            val alreadyCovered = allFound.any { f -> m.range.first >= f.start && m.range.last < f.end }
            if (!alreadyCovered) {
                allFound += Found(m.range.first, m.range.last + 1, MediaSegment.ImageLink(m.value))
            }
        }

        // ── Step 2b: সাধারণ extension-based ছবির লিংক ──
        IMAGE_EXT_REGEX.findAll(text).forEach { m ->
            val alreadyCovered = allFound.any { f -> m.range.first >= f.start && m.range.last < f.end }
            if (!alreadyCovered) {
                allFound += Found(m.range.first, m.range.last + 1, MediaSegment.ImageLink(m.value))
            }
        }

        // ── Step 3: YouTube/Facebook ভিডিও লিংক ──
        VIDEO_REGEX.findAll(text).forEach { m ->
            val alreadyCovered = allFound.any { f -> m.range.first >= f.start && m.range.last < f.end }
            if (!alreadyCovered) {
                // trailing punctuation সরানো (index এ: .replace(/[,।]$/, ""))
                val cleanUrl = m.value.trimEnd(',', '।', '।')
                val isYt = cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be")
                allFound += Found(m.range.first, m.range.last + 1, MediaSegment.VideoLink(cleanUrl, isYt))
            }
        }

        // ── Step 4 (শুধু allowWeb): বাকি সাধারণ ওয়েব লিংক ──
        if (allowWeb) {
            WEB_REGEX.findAll(text).forEach { m ->
                val covered = allFound.any { f -> m.range.first >= f.start && m.range.first < f.end }
                if (!covered) {
                    val clean = m.value.trimEnd(',', '।', '.', ';')
                    allFound += Found(m.range.first, m.range.first + clean.length, MediaSegment.WebLink(clean))
                }
            }
        }

        allFound.sortBy { it.start }

        // ── Plain text gaps fill করি ──
        val segments = mutableListOf<MediaSegment>()
        var cursor = 0
        for (found in allFound) {
            if (found.start > cursor) {
                val plain = text.substring(cursor, found.start)
                if (plain.isNotBlank()) segments += MediaSegment.PlainText(plain.trim())
            }
            segments += found.segment
            cursor = found.end
        }
        if (cursor < text.length) {
            val plain = text.substring(cursor)
            if (plain.isNotBlank()) segments += MediaSegment.PlainText(plain.trim())
        }

        return segments
    }

    // ── ভিডিওর শিরোনাম: [মৌলিক ও সাধিত শব্দ](https://youtu.be/xxxx) ──
    // শুধু YouTube/Facebook লিংকের জন্য; অন্য মার্কডাউন লিংক যেমন আছে তেমনই থাকে।
    // [শিরোনাম|verified](url) লিখলেও চলে (Study-র মতো) — এখানে |... অংশ উপেক্ষা হয়।
    private val TITLED_VIDEO = Regex("""\[([^\]\n|]{1,120})(?:\|[^\]\n]*)?]\((https?://[^)\s]+)\)""")

    /** `[শিরোনাম](ভিডিও-লিংক)` → শুধু লিংক (parse আগের মতো চলে) + লিংক→শিরোনাম ম্যাপ */
    fun extractVideoTitles(text: String): Pair<String, Map<String, String>> {
        if (!text.contains("](")) return text to emptyMap()
        val titles = LinkedHashMap<String, String>()
        val out = TITLED_VIDEO.replace(text) { m ->
            val url = m.groupValues[2].trim()
            if (VIDEO_REGEX.matchEntire(url) == null) m.value
            else { titles[url] = m.groupValues[1].trim(); " $url " }
        }
        return out to titles
    }

    // index এর normalizePdfUrl: /d/{fileId}/preview
    fun normalizePdfUrl(url: String): String {
        if (!url.contains("drive.google.com")) return url
        // already preview
        if (url.contains("/preview")) return url
        // /view → /preview
        if (url.contains("/view")) return url.replace(Regex("""/view.*$"""), "/preview")
        // /d/{fileId}/... → extract and rebuild
        val match = Regex("""/d/([^/]+)""").find(url)
        return if (match != null)
            "https://drive.google.com/file/d/${match.groupValues[1]}/preview"
        else url
    }
}

// ─────────────────────────────────────────────────────────────────
// RichContentText — মূল composable
//
// index.html এ যেখানে parseLinksToImages(text) ব্যবহার হয়:
//   - question field
//   - correct/answer field  ← FB+YT লিংক এখানেই থাকে
//   - explanation field     ← PDF/image লিংক এখানে থাকতে পারে
//   - technique field
//   - VisualURL field (আলাদা field হলে)
//
// এই composable সব ক্ষেত্রে ব্যবহার করো
// ─────────────────────────────────────────────────────────────────
@Composable
fun RichContentText(
    text: String,
    textColor: Color = Color(0xFF1E293B),
    fontSize: Int = 14,
    // ── Study Nav Phase 2: non-null হলে প্লেইন-টেক্সট অংশ ব্লক-রেন্ডারারে (হেডিং/লিস্ট/
    // টেবিল...) + রিডার সেটিংসে (ফন্ট/স্পেসিং) দেখায়। null = আগের আচরণ হুবহু। ──
    reader: com.hanif.smartstudy.ui.shared.StudyReaderSettings? = null
) {
    // ── Study Nav Phase 3: Study রিডারে নতুন Resource-কার্ড রেন্ডারার (StudyMediaCards.kt) ──
    if (reader != null) {
        StudyRichContent(text = text, textColor = textColor, fontSize = fontSize, reader = reader)
        return
    }

    val videoPrepared = remember(text) { MediaLinkParser.extractVideoTitles(text) }
    val segments = remember(videoPrepared.first) { MediaLinkParser.parse(videoPrepared.first) }

    var zoomImageUrl by remember { mutableStateOf<String?>(null) }

    // কিছু না থাকলে render করার দরকার নেই
    if (segments.isEmpty()) return

    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        segments.forEach { seg ->
            when (seg) {
                is MediaSegment.PlainText -> {
                    if (seg.text.isNotBlank()) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            if (reader != null) {
                                com.hanif.smartstudy.ui.shared.StudyRichBlocks(
                                    text       = seg.text,
                                    settings   = reader,
                                    baseSizeSp = fontSize,
                                    textColor  = textColor
                                )
                            } else {
                                Text(
                                    text       = parseRichAnnotated(seg.text, fontSize.toFloat()),
                                    color      = textColor,
                                    fontSize   = fontSize.sp,
                                    fontFamily = NotoSansBengali,
                                    lineHeight  = (fontSize + 6).sp
                                )
                            }
                        }
                    }
                }
                is MediaSegment.ImageLink -> {
                    AutoImage(
                        url     = seg.url,
                        onClick = { zoomImageUrl = seg.url }
                    )
                }
                is MediaSegment.VideoLink -> {
                    VideoButton(
                        url = seg.url, isYoutube = seg.isYoutube,
                        title = seg.title ?: videoPrepared.second[seg.url]
                    )
                }
                is MediaSegment.PdfLink -> {
                    val ctx = LocalContext.current
                    PdfButton(url = seg.url, onClick = { openPdfExternal(ctx, seg.url) })
                }
                // WebLink শুধু parse(allowWeb=true)-তে আসে (Study রিডার, উপরে early-return) — এই পথে কখনো না
                is MediaSegment.WebLink -> Unit
            }
        }
    }

    // ── Image Zoom Overlay (index এর #image-zoom-overlay এর মতো) ──
    zoomImageUrl?.let { url ->
        ImageZoomOverlay(imageUrl = url, onClose = { zoomImageUrl = null })
    }
}

// ─────────────────────────────────────────────────────────────────
// 1. AutoImage — index এর <img class="preview-img" onclick="openImageZoom(...)">
// ─────────────────────────────────────────────────────────────────
@Composable
fun AutoImage(url: String, onClick: () -> Unit) {
    var state by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Loading(null)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = "ছবি",
            contentScale       = ContentScale.FillWidth,
            modifier           = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            onState = { state = it }
        )

        // Loading shimmer
        if (state is AsyncImagePainter.State.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(28.dp),
                    color       = IndigoPrimary,
                    strokeWidth = 2.dp
                )
            }
        }

        // Error — index এ: onerror="this.style.display='none'" — আমরা error message দেখাই
        if (state is AsyncImagePainter.State.Error) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFEF2F2)),
                contentAlignment = Alignment.Center
            ) {
                Text("🖼️ ছবি লোড হয়নি", color = Color(0xFF991B1B), fontSize = 12.sp,
                    fontFamily = NotoSansBengali)
            }
        }

        // ছবিতে ট্যাপ করলেই সরাসরি জুম হয় — আলাদা বাটন দরকার নেই
    }
}

// ─────────────────────────────────────────────────────────────────
// 2. VideoButton — index এর YT/FB button style হুবহু
//
// YT button style (index):
//   background:#fff1f2; color:#be123c; border:1px solid #fecdd3
//   "🎬 ইউটিউব অ্যাপে দেখুন"
//
// FB button style (index):
//   background:#f0f9ff; color:#0369a1; border:1px solid #bae6fd
//   "🔵 ফেইসবুক অ্যাপে দেখুন"
// ─────────────────────────────────────────────────────────────────
@Composable
fun VideoButton(url: String, isYoutube: Boolean, title: String? = null) {
    val ctx = LocalContext.current
    val accent = if (isYoutube) YtRed else FbBlue
    var showPlayer by remember { mutableStateOf(false) }   // ▶ অ্যাপের ভিতরেই চালানো

    if (showPlayer) VideoPlayerDialog(url, isYoutube, title, onClose = { showPlayer = false })

    Surface(
        onClick  = { showPlayer = true },
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = if (isYoutube) YtBg else FbBg,
        border   = BorderStroke(1.dp, if (isYoutube) YtBorder else FbBorder)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            VideoLogo(isYoutube, 22.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (!title.isNullOrBlank()) {
                    // শিরোনাম থাকলে: উপরে শিরোনাম (বোল্ড), নিচে ছোট করে কোথায় দেখবেন
                    Text(
                        text = title, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = accent, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isYoutube) "ইউটিউবে দেখুন" else "ফেসবুকে দেখুন",
                        fontFamily = NotoSansBengali, fontSize = 11.sp, color = accent.copy(alpha = 0.75f)
                    )
                } else {
                    Text(
                        text = if (isYoutube) "ইউটিউব অ্যাপে দেখুন" else "ফেইসবুক অ্যাপে দেখুন",
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = accent
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("▶", fontSize = 14.sp, color = accent)
            // ↗ বাইরের ইউটিউব/ফেসবুক অ্যাপে খুলুন (ব্যাক চাপলে এই অ্যাপেই ফেরে)
            Text(
                "↗", fontSize = 16.sp, color = accent, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { openVideoApp(ctx, url) }.padding(start = 6.dp, top = 4.dp, bottom = 4.dp, end = 2.dp)
            )
        }
    }
}

/** ছোট YouTube/Facebook লোগো — ছবি ছাড়া, Compose-এ আঁকা; রঙ আগের Yt/Fb রঙই */
@Composable
fun VideoLogo(isYoutube: Boolean, s: androidx.compose.ui.unit.Dp = 22.dp) {
    if (isYoutube) {
        Box(
            Modifier.size(width = s * 1.4f, height = s).clip(RoundedCornerShape(s * 0.28f)).background(YtRed),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(s * 0.4f)) {
                val tri = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.12f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.12f, size.height)
                    close()
                }
                drawPath(tri, Color.White)
            }
        }
    } else {
        Box(Modifier.size(s).clip(CircleShape).background(FbBlue), contentAlignment = Alignment.Center) {
            Text("f", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (s.value * 0.78f).sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 🎬 VideoPlayerDialog — অ্যাপের ভিতরেই ভিডিও চালায় (WebView + অফিসিয়াল embed)।
//   YouTube  → youtube.com/embed/{id}   (সব ভিডিও না-ও চলতে পারে: মালিক embed বন্ধ রাখলে)
//   Facebook → facebook.com/plugins/video.php (শুধু পাবলিক ভিডিও; প্রাইভেট/গ্রুপ চলে না)
// না চললে উপরের "↗ অ্যাপে খুলুন" বাটন বাইরের অ্যাপে নিয়ে যায়। ব্যাক চাপলে প্লেয়ার বন্ধ হয়ে
// একই প্রশ্নে থাকে; ফুল-স্ক্রিন বাটন চাপলে আড়াআড়ি হয়, ব্যাকে আগের অবস্থায়।
// ─────────────────────────────────────────────────────────────────
private fun ytVideoId(url: String): String? {
    val raw = when {
        url.contains("v=")        -> url.substringAfter("v=").substringBefore("&")
        url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
        url.contains("shorts/")   -> url.substringAfter("shorts/").substringBefore("?")
        url.contains("embed/")    -> url.substringAfter("embed/").substringBefore("?")
        url.contains("live/")     -> url.substringAfter("live/").substringBefore("?")
        else -> ""
    }
    // শুধু বৈধ ID — প্লেলিস্ট/চ্যানেল লিংকে null (তখন বাইরের অ্যাপে যায়)
    return raw.takeIf { Regex("^[A-Za-z0-9_-]{6,20}$").matches(it) }
}

/** (baseUrl, html) — প্লেয়ার চালানো না গেলে (ID নেই) null */
private fun playerPage(url: String, isYoutube: Boolean): Pair<String, String>? {
    val src: String
    val base: String
    if (isYoutube) {
        val id = ytVideoId(url) ?: return null
        src  = "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=1"
        base = "https://www.youtube.com"
    } else {
        src  = "https://www.facebook.com/plugins/video.php?href=${Uri.encode(url)}&show_text=false&autoplay=true&allowfullscreen=true"
        base = "https://www.facebook.com"
    }
    val html = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
        "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}" +
        "iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style></head><body>" +
        "<iframe src=\"$src\" allow=\"autoplay; encrypted-media; picture-in-picture; fullscreen\" allowfullscreen></iframe>" +
        "</body></html>"
    return base to html
}

@Composable
fun VideoPlayerDialog(url: String, isYoutube: Boolean, title: String?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val page = remember(url) { playerPage(url, isYoutube) }

    // embed করা যায় না (যেমন ইউটিউব প্লেলিস্ট) → সরাসরি বাইরের অ্যাপে
    if (page == null) {
        LaunchedEffect(Unit) { openVideoApp(ctx, url); onClose() }
        return
    }

    val activity = remember(ctx) { ctx.findHostActivity() }
    var fsCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var loading by remember { mutableStateOf(true) }
    val webHolder = remember { arrayOfNulls<WebView>(1) }
    var rotated by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (rotated) activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            webHolder[0]?.let { w -> try { w.stopLoading(); w.loadUrl("about:blank"); w.destroy() } catch (_: Exception) { } }
            webHolder[0] = null
        }
    }

    Dialog(
        onDismissRequest = {
            // ফুল-স্ক্রিনে থাকলে আগে সেটা থেকে বের হও, নইলে প্লেয়ার বন্ধ
            val cb = fsCallback
            if (cb != null) cb.onCustomViewHidden() else onClose()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                if (fsCallback == null) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.hanif.smartstudy.ui.components.VideoLogo(isYoutube, 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            title?.takeIf { it.isNotBlank() } ?: if (isYoutube) "ইউটিউব ভিডিও" else "ফেসবুক ভিডিও",
                            color = Color.White, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { openVideoApp(ctx, url); onClose() }) {
                            Text("↗ অ্যাপে খুলুন", color = Color.White, fontFamily = NotoSansBengali, fontSize = 12.sp)
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "বন্ধ", tint = Color.White)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AndroidView(
                        modifier = if (isYoutube && fsCallback == null) Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                   else Modifier.fillMaxSize(),
                        factory = { c ->
                            val root = android.widget.FrameLayout(c)
                            val wv = WebView(c)
                            wv.setBackgroundColor(android.graphics.Color.BLACK)
                            wv.settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                setSupportMultipleWindows(false)
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            wv.webViewClient = object : WebViewClient() {
                                // প্লেয়ারের ভিতরের লিংক ট্যাপে পুরো পেজ বদলে যেতে দেওয়া হয় না (iframe-এর ভেতরের লোড চলে)
                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean =
                                    request?.isForMainFrame == true
                                override fun onPageFinished(view: WebView?, u: String?) { loading = false }
                            }
                            var customView: android.view.View? = null
                            wv.webChromeClient = object : WebChromeClient() {
                                override fun onShowCustomView(view: android.view.View?, callback: WebChromeClient.CustomViewCallback?) {
                                    if (view == null) { callback?.onCustomViewHidden(); return }
                                    customView = view
                                    wv.visibility = android.view.View.GONE
                                    root.addView(view, android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT))
                                    fsCallback = callback
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    rotated = true
                                }
                                override fun onHideCustomView() {
                                    customView?.let { root.removeView(it) }
                                    customView = null
                                    wv.visibility = android.view.View.VISIBLE
                                    fsCallback = null
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            }
                            root.addView(wv, android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT))
                            webHolder[0] = wv
                            wv.loadDataWithBaseURL(page.first, page.second, "text/html", "utf-8", null)
                            root
                        }
                    )
                    if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                }
                if (fsCallback == null) {
                    Text(
                        "ভিডিও না চললে ওপরের ↗ অ্যাপে খুলুন চাপুন। কিছু ভিডিও (প্রাইভেট/embed বন্ধ) অ্যাপের ভিতরে চলে না।",
                        color = Color.White.copy(alpha = 0.65f), fontFamily = NotoSansBengali, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

// openVideoApp — YT → vnd.youtube:{videoId} (অ্যাপ) → না থাকলে ব্রাউজার;
//                FB → fb://facewebmodal/f?href={encoded} (অ্যাপ) → না থাকলে ব্রাউজার।
//
// ── FIX ("ইউটিউব/ফেসবুকে গিয়ে ব্যাক চাপলে অ্যাপে ফেরে না"): আগে সব intent-এ
// FLAG_ACTIVITY_NEW_TASK দেওয়া ছিল — তাতে YouTube/Facebook নিজের আলাদা টাস্কে খুলত
// (আগে থেকে ব্যাকগ্রাউন্ডে থাকলে তার পুরনো স্ক্রিনই সামনে আসত), তাই ব্যাক চাপলে সে
// তার আগের পেজে যেত, আমাদের অ্যাপে না। এখন Activity থেকে খোলার সময় NEW_TASK ছাড়াই
// খোলা হয় — বাইরের অ্যাপ আমাদের টাস্কের উপরে বসে, ব্যাক চাপলে একই প্রশ্নে ফিরে আসে।
// (Activity না পেলে — খুব বিরল — আগের মতো NEW_TASK লাগে।) ──
private fun Context.findHostActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private fun startInSameTask(ctx: Context, intent: Intent) {
    val act = ctx.findHostActivity()
    if (act != null) act.startActivity(intent)
    else ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun openVideoApp(ctx: Context, url: String) {
    try {
        when {
            url.contains("youtube.com") || url.contains("youtu.be") -> {
                val videoId = when {
                    url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                    url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
                    url.contains("shorts/") -> url.substringAfter("shorts/").substringBefore("?")
                    else -> url.substringAfterLast("/").substringBefore("?")
                }
                try {
                    startInSameTask(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")))
                } catch (e: Exception) {
                    // YT অ্যাপ নেই → ব্রাউজার (এটাও একই টাস্কে, তাই ব্যাকে ফেরা যায়)
                    startInSameTask(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            url.contains("facebook.com") -> {
                try {
                    startInSameTask(ctx, Intent(Intent.ACTION_VIEW,
                        Uri.parse("fb://facewebmodal/f?href=${Uri.encode(url)}")))
                } catch (e: Exception) {
                    // FB অ্যাপ নেই → ব্রাউজার
                    startInSameTask(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(ctx.applicationContext, "ভিডিও খোলা যায়নি", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────────────────────────────
// openPdfExternal — সরাসরি system এ PDF open করে
// স্ক্রিনশটে তীর চিহ্ন (↗) বাটনে ক্লিক করলে যেটা হয়, এখন PDF বাটনেই সেটা হবে
// Drive লিংক → browser এ preview
// Direct PDF → system PDF viewer / browser
// ─────────────────────────────────────────────────────────────────
// ── FIX: ভিডিওর মতোই — NEW_TASK ছাড়া (একই টাস্কে) খোলে, তাই PDF/ব্রাউজার থেকে ব্যাক
// চাপলে এই অ্যাপের একই স্ক্রিনে ফেরা যায় (দেখো openVideoApp-এর নোট) ──
fun openPdfExternal(ctx: Context, url: String) {
    try {
        startInSameTask(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(ctx.applicationContext, "PDF খোলা যায়নি", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────────────────────────────
// 3. PdfButton — index এর .pdf-btn-style হুবহু
//    "📕 PDF দেখুন"
// ─────────────────────────────────────────────────────────────────
@Composable
fun PdfButton(url: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape  = RoundedCornerShape(50.dp),   // index: border-radius: 50px (pill shape)
        border = BorderStroke(1.dp, IndigoBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor   = IndigoPrimary,
            containerColor = Color.Transparent
        )
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier              = Modifier.padding(vertical = 6.dp)
        ) {
            Text("📕", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text       = "PDF দেখুন",
                fontFamily = NotoSansBengali,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = IndigoPrimary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// PDF Viewer Modal — index এর #pdf-modal (iframe → WebView)
//
// index structure:
//   <div id="pdf-modal" class="fixed inset-0 bg-black/80 z-[6000]">
//     <div class="flex justify-between items-center p-4 bg-white shadow-md">
//       <h3>PDF Viewer</h3>
//       <button onclick="closePdfModal()">✕</button>   ← bg-red-500 rounded-full
//     </div>
//     <iframe src="" class="w-full h-full border-none"></iframe>
//   </div>
// ─────────────────────────────────────────────────────────────────
@Composable
fun PdfViewerModal(url: String, onClose: () -> Unit) {

    // Back press হ্যান্ডেল করি (index এ history.pushState দিয়ে করে)
    BackHandler(enabled = true) { onClose() }

    Dialog(
        onDismissRequest = onClose,
        properties       = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header ── (index: flex justify-between items-center p-4 bg-white shadow-md)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "📕 PDF Viewer",
                        fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 16.sp,
                        color      = IndigoPrimary
                    )
                    // Close button (index: bg-red-500 rounded-full w-10 h-10)
                    IconButton(
                        onClick  = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Close,
                            contentDescription = "বন্ধ করুন",
                            tint               = Color.White,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }

                // ── WebView — index এর iframe এর পরিবর্তে ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White)
                ) {
                    var isLoading by remember { mutableStateOf(true) }

                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory  = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    javaScriptEnabled      = true
                                    domStorageEnabled      = true
                                    loadWithOverviewMode   = true
                                    useWideViewPort        = true
                                    builtInZoomControls    = true
                                    displayZoomControls    = false
                                    setSupportZoom(true)
                                    mixedContentMode       = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                loadUrl(url)
                            }
                        }
                    )

                    if (isLoading) {
                        Box(
                            modifier         = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color    = IndigoPrimary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "PDF লোড হচ্ছে...",
                                    fontFamily = NotoSansBengali,
                                    fontSize   = 13.sp,
                                    color      = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Image Zoom Overlay — index এর #image-zoom-overlay হুবহু
//
// index features:
//   - position fixed, inset 0, background rgba(0,0,0,0.98)
//   - pinch zoom (2-finger touch): scale 1–5
//   - drag to pan (1-finger, scale > 1)
//   - ✕ বাটন (top-right)
//   - Back button → closeImageZoom()
//   - history.pushState ব্যবহার করে (Android: BackHandler দিয়ে handle)
// ─────────────────────────────────────────────────────────────────
@Composable
fun ImageZoomOverlay(imageUrl: String, onClose: () -> Unit) {
    var scale  by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Back press (index এর history.pushState + popstate listener এর মতো)
    BackHandler(enabled = true) {
        if (scale > 1f) {
            scale  = 1f
            offset = Offset.Zero
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFA000000))  // rgba(0,0,0,0.98) এর মতো
            .pointerInput(Unit) {
                // index এর handleTouchMove: 2-finger pinch zoom + 1-finger drag
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = max(1f, min(5f, scale * zoom))
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        // ── ছবি (centered) ──
        AsyncImage(
            model              = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(false)
                .build(),
            contentDescription = "বড় ছবি",
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX          = scale,
                    scaleY          = scale,
                    translationX    = offset.x,
                    translationY    = offset.y,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                )
        )

        // ── Top gradient (index এর .zoom-controls এর bg) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(0.7f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )

        // ── Close button (top-right) — index এর .zoom-close-btn ──
        IconButton(
            onClick  = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .border(1.dp, Color.White.copy(0.3f), CircleShape)
        ) {
            Icon(
                imageVector        = Icons.Default.Close,
                contentDescription = "বন্ধ করুন",
                tint               = Color.White,
                modifier           = Modifier.size(22.dp)
            )
        }

        // ── Hint text (bottom center) ──
        if (scale == 1f) {
            Text(
                text     = "চিমটি দিয়ে জুম করুন",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color      = Color.White.copy(0.8f),
                fontSize   = 12.sp,
                fontFamily = NotoSansBengali
            )
        }

        // ── Reset zoom ──
        if (scale > 1f) {
            TextButton(
                onClick  = { scale = 1f; offset = Offset.Zero },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(0.5f))
            ) {
                Text(
                    "রিসেট করুন",
                    color      = Color.White,
                    fontFamily = NotoSansBengali,
                    fontSize   = 13.sp
                )
            }
        }
    }
}
