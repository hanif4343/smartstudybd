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
    data class VideoLink(val url: String, val isYoutube: Boolean) : MediaSegment()
    data class PdfLink(val url: String)    : MediaSegment()
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

    fun parse(text: String): List<MediaSegment> {
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
    fontSize: Int = 14
) {
    val segments = remember(text) { MediaLinkParser.parse(text) }

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
                        Text(
                            text       = parseRichAnnotated(seg.text, fontSize.toFloat()),
                            color      = textColor,
                            fontSize   = fontSize.sp,
                            fontFamily = NotoSansBengali,
                            lineHeight  = (fontSize + 6).sp
                        )
                    }
                }
                is MediaSegment.ImageLink -> {
                    AutoImage(
                        url     = seg.url,
                        onClick = { zoomImageUrl = seg.url }
                    )
                }
                is MediaSegment.VideoLink -> {
                    VideoButton(url = seg.url, isYoutube = seg.isYoutube)
                }
                is MediaSegment.PdfLink -> {
                    val ctx = LocalContext.current
                    PdfButton(url = seg.url, onClick = { openPdfExternal(ctx, seg.url) })
                }
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
fun VideoButton(url: String, isYoutube: Boolean) {
    val ctx = LocalContext.current

    Button(
        onClick  = { openVideoApp(ctx, url) },
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = if (isYoutube) YtBg else FbBg,
            contentColor   = if (isYoutube) YtRed else FbBlue
        ),
        border    = BorderStroke(1.dp, if (isYoutube) YtBorder else FbBorder),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier              = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text     = if (isYoutube) "🎬" else "🔵",
                fontSize = 18.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = if (isYoutube) "ইউটিউব অ্যাপে দেখুন" else "ফেইসবুক অ্যাপে দেখুন",
                fontFamily = NotoSansBengali,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = if (isYoutube) YtRed else FbBlue
            )
        }
    }
}

// index এর openVideoApp() হুবহু Kotlin port:
//   YT → vnd.youtube:{videoId} intent (app) → fallback browser
//   FB → fb://facewebmodal/f?href={encoded} intent → fallback browser (1500ms delay)
private fun openVideoApp(ctx: Context, url: String) {
    try {
        when {
            url.contains("youtube.com") || url.contains("youtu.be") -> {
                // index: if (url.includes("v=")) videoId = url.split("v=")[1].split("&")[0]
                //        else videoId = url.substring(url.lastIndexOf("/") + 1)
                val videoId = when {
                    url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                    url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
                    url.contains("shorts/") -> url.substringAfter("shorts/").substringBefore("?")
                    else -> url.substringAfterLast("/").substringBefore("?")
                }
                val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    ctx.startActivity(ytIntent)
                } catch (e: Exception) {
                    // YT App নেই → Browser
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
            url.contains("facebook.com") -> {
                // index: fb://facewebmodal/f?href={encodedUrl}
                val fbIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("fb://facewebmodal/f?href=${Uri.encode(url)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    ctx.startActivity(fbIntent)
                } catch (e: Exception) {
                    // FB App নেই → Browser (index এ 1500ms timeout, Kotlin এ সরাসরি)
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ─────────────────────────────────────────────────────────────────
// openPdfExternal — সরাসরি system এ PDF open করে
// স্ক্রিনশটে তীর চিহ্ন (↗) বাটনে ক্লিক করলে যেটা হয়, এখন PDF বাটনেই সেটা হবে
// Drive লিংক → browser এ preview
// Direct PDF → system PDF viewer / browser
// ─────────────────────────────────────────────────────────────────
fun openPdfExternal(ctx: Context, url: String) {
    try {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
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
