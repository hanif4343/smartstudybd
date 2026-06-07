package com.hanif.smartstudy.ui.components

// ═══════════════════════════════════════════════════════════════════
//  MediaViewer.kt
//  পুরাতন HTML অ্যাপের index.html থেকে ধারণা নিয়ে Kotlin/Compose এ তৈরি
//
//  ৩টি কাজ:
//  1. ImgBB/যেকোনো ছবির লিংক → AutoImage (অটো দেখায়) + ক্লিক করলে ZoomOverlay
//  2. YouTube/Facebook ভিডিও লিংক → বাটন দেখায়, ক্লিক করলে সরাসরি App এ খোলে
//  3. PDF/Google Drive লিংক → "📕 PDF দেখুন" বাটন, ক্লিক করলে WebView modal
//
//  ব্যবহার:
//    RichContentText(text = studyItem.answer ?: "")
//    বা আলাদাভাবে:
//    MediaLinkParser(rawText = text) → MediaSegment list → MediaRow()
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
import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────
// রং — পুরাতন অ্যাপের মতো
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
// MediaLinkParser — পুরাতন parseLinksToImages() এর Kotlin version
// ─────────────────────────────────────────────────────────────────
object MediaLinkParser {

    private val IMAGE_REGEX = Regex(
        """https?://[^\s"'>]+?\.(?:jpg|jpeg|gif|png|webp|bmp)(?:\?[^\s"'>]*)?""",
        RegexOption.IGNORE_CASE
    )
    private val PDF_REGEX = Regex(
        """https?://(?:drive\.google\.com|docs\.google\.com|[^\s"'>]+?\.pdf)[^\s"'>]*""",
        RegexOption.IGNORE_CASE
    )
    private val VIDEO_REGEX = Regex(
        """https?://(?:www\.)?(?:youtube\.com|youtu\.be|facebook\.com)[^\s]+""",
        RegexOption.IGNORE_CASE
    )
    // ImgBB direct image URL pattern (i.ibb.co বা ibb.co/...)
    private val IMGBB_REGEX = Regex(
        """https?://(?:i\.ibb\.co|ibb\.co)/[^\s"'>]+""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): List<MediaSegment> {
        if (text.isBlank()) return emptyList()

        val segments = mutableListOf<MediaSegment>()
        var remaining = text

        // সব লিংক খোঁজা
        data class Found(val start: Int, val end: Int, val segment: MediaSegment)
        val allFound = mutableListOf<Found>()

        // ImgBB লিংক
        IMGBB_REGEX.findAll(remaining).forEach {
            allFound += Found(it.range.first, it.range.last + 1, MediaSegment.ImageLink(it.value))
        }
        // সাধারণ ছবির লিংক
        IMAGE_REGEX.findAll(remaining).forEach { m ->
            val alreadyCovered = allFound.any { f -> m.range.first >= f.start && m.range.last < f.end }
            if (!alreadyCovered) {
                allFound += Found(m.range.first, m.range.last + 1, MediaSegment.ImageLink(m.value))
            }
        }
        // PDF লিংক
        PDF_REGEX.findAll(remaining).forEach { m ->
            val alreadyCovered = allFound.any { f -> m.range.first >= f.start && m.range.last < f.end }
            if (!alreadyCovered) {
                val url = normalizePdfUrl(m.value)
                allFound += Found(m.range.first, m.range.last + 1, MediaSegment.PdfLink(url))
            }
        }
        // ভিডিও লিংক
        VIDEO_REGEX.findAll(remaining).forEach { m ->
            val alreadyCovered = allFound.any { f -> m.range.first >= f.start && m.range.last < f.end }
            if (!alreadyCovered) {
                val isYt = m.value.contains("youtube.com") || m.value.contains("youtu.be")
                allFound += Found(m.range.first, m.range.last + 1, MediaSegment.VideoLink(m.value.trimEnd(',','।'), isYt))
            }
        }

        allFound.sortBy { it.start }

        var cursor = 0
        for (found in allFound) {
            if (found.start > cursor) {
                val plain = remaining.substring(cursor, found.start)
                if (plain.isNotBlank()) segments += MediaSegment.PlainText(plain)
            }
            segments += found.segment
            cursor = found.end
        }
        if (cursor < remaining.length) {
            val plain = remaining.substring(cursor)
            if (plain.isNotBlank()) segments += MediaSegment.PlainText(plain)
        }

        return segments
    }

    private fun normalizePdfUrl(url: String): String {
        if (!url.contains("drive.google.com")) return url
        val match = Regex("""/d/([^/]+)""").find(url)
        return if (match != null)
            "https://drive.google.com/file/d/${match.groupValues[1]}/preview"
        else url
    }
}

// ─────────────────────────────────────────────────────────────────
// RichContentText — মূল composable: টেক্সট পার্স করে সব রেন্ডার করে
//  ব্যবহার: RichContentText(text = item.explanation ?: "")
// ─────────────────────────────────────────────────────────────────
@Composable
fun RichContentText(
    text: String,
    textColor: Color = Color(0xFF1E293B),
    fontSize: Int = 14
) {
    val segments = remember(text) { MediaLinkParser.parse(text) }

    // Image zoom state — এই screen level এ manage করতে হবে
    var zoomImageUrl by remember { mutableStateOf<String?>(null) }
    // PDF modal state
    var pdfUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        segments.forEach { seg ->
            when (seg) {
                is MediaSegment.PlainText -> {
                    if (seg.text.isNotBlank()) {
                        Text(
                            text       = seg.text.trim(),
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
                    VideoButton(
                        url       = seg.url,
                        isYoutube = seg.isYoutube
                    )
                }
                is MediaSegment.PdfLink -> {
                    PdfButton(
                        url     = seg.url,
                        onClick = { pdfUrl = seg.url }
                    )
                }
            }
        }
    }

    // ── Image Zoom Overlay ──
    zoomImageUrl?.let { url ->
        ImageZoomOverlay(
            imageUrl = url,
            onClose  = { zoomImageUrl = null }
        )
    }

    // ── PDF Modal ──
    pdfUrl?.let { url ->
        PdfViewerModal(
            url     = url,
            onClose = { pdfUrl = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// 1. AutoImage — ছবি অটো দেখায়, ক্লিক করলে জুম
// ─────────────────────────────────────────────────────────────────
@Composable
fun AutoImage(
    url     : String,
    onClick : () -> Unit
) {
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
                    modifier = Modifier.size(28.dp),
                    color    = IndigoPrimary,
                    strokeWidth = 2.dp
                )
            }
        }

        // Error state
        if (state is AsyncImagePainter.State.Error) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFEF2F2)),
                contentAlignment = Alignment.Center
            ) {
                Text("🖼️ ছবি লোড হয়নি", color = Color(0xFF991B1B), fontSize = 12.sp,
                    fontFamily = NotoSansBengali)
            }
        }

        // Zoom hint overlay (ছবি লোড হলে দেখায়)
        if (state is AsyncImagePainter.State.Success) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🔍", fontSize = 14.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 2. VideoButton — YT বা FB বাটন
//    পুরাতন অ্যাপের openVideoApp() এর মতো
// ─────────────────────────────────────────────────────────────────
@Composable
fun VideoButton(url: String, isYoutube: Boolean) {
    val ctx = LocalContext.current

    Button(
        onClick = { openVideoApp(ctx, url) },
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = if (isYoutube) YtBg else FbBg,
            contentColor   = if (isYoutube) YtRed else FbBlue
        ),
        border   = BorderStroke(1.dp, if (isYoutube) YtBorder else FbBorder),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier              = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text       = if (isYoutube) "🎬" else "🔵",
                fontSize   = 18.sp
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

// পুরাতন অ্যাপের openVideoApp() — YouTube App Intent, FB App fallback
private fun openVideoApp(ctx: Context, url: String) {
    try {
        when {
            url.contains("youtube.com") || url.contains("youtu.be") -> {
                // YouTube App এ সরাসরি খোলে
                val videoId = when {
                    url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                    else               -> url.substringAfterLast("/").substringBefore("?")
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    ctx.startActivity(intent)
                } catch (e: Exception) {
                    // YouTube App নেই → Browser এ খোলো
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
            url.contains("facebook.com") -> {
                // FB App Intent
                val fbIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("fb://facewebmodal/f?href=${Uri.encode(url)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    ctx.startActivity(fbIntent)
                } catch (e: Exception) {
                    // FB App নেই → Browser
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
// 3. PdfButton — "📕 PDF দেখুন" বাটন
//    পুরাতন অ্যাপের pdf-btn-style এর মতো
// ─────────────────────────────────────────────────────────────────
@Composable
fun PdfButton(url: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape    = RoundedCornerShape(50.dp),  // পুরাতনে border-radius: 50px
        border   = BorderStroke(1.dp, IndigoBorder),
        colors   = ButtonDefaults.outlinedButtonColors(
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
// PDF Viewer Modal — পুরাতন #pdf-modal এর মতো (iframe → WebView)
// ─────────────────────────────────────────────────────────────────
@Composable
fun PdfViewerModal(url: String, onClose: () -> Unit) {
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

                // ── Header ── (পুরাতনে: justify-between items-center p-4 bg-white)
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
                    // Close Button — পুরাতনে bg-red-500 rounded-full
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

                // ── WebView (iframe এর পরিবর্তে) ──
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
                                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                        isLoading = false
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                loadUrl(url)
                            }
                        }
                    )

                    // Loading indicator
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
// Image Zoom Overlay — পুরাতন #image-zoom-overlay এর মতো
//  - Pinch-to-zoom (multi-touch)
//  - Drag to pan (zoom > 1 এর সময়)
//  - ✕ বাটন বা Back press এ বন্ধ
//  - পুরাতনে: position fixed, inset 0, background rgba(0,0,0,0.98)
// ─────────────────────────────────────────────────────────────────
@Composable
fun ImageZoomOverlay(imageUrl: String, onClose: () -> Unit) {
    var scale  by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Back press handle
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
            .background(Color(0xFA000000))   // rgba(0,0,0,0.98) এর মতো
            .pointerInput(Unit) {
                // Pinch zoom + drag
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
        // ── ছবি (center) ──
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
                    scaleX           = scale,
                    scaleY           = scale,
                    translationX     = offset.x,
                    translationY     = offset.y,
                    transformOrigin  = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                )
        )

        // ── Close Button (top right) — পুরাতন .zoom-close-btn ──
        // gradient overlay (পুরাতনে: linear-gradient to bottom, rgba(0,0,0,0.7), transparent)
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

        IconButton(
            onClick  = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(45.dp)
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

        // ── Zoom hint (শুধু scale == 1 এর সময়) ──
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

        // ── Reset zoom button (scale > 1 এর সময়) ──
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
