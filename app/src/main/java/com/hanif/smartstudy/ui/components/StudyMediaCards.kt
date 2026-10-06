package com.hanif.smartstudy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hanif.smartstudy.ui.shared.StudyReaderSettings
import com.hanif.smartstudy.ui.shared.StudyRichBlocks
import com.hanif.smartstudy.ui.theme.NotoSansBengali

/**
 * ── Study Nav "Phase 3 — Media" ──
 *
 * Study মোডে (RichContentText-এর `reader != null` পথে) লিংকগুলো সাধারণ বাটনের বদলে
 * সুন্দর Resource কার্ড হয়ে দেখায়:
 *   🎥 YouTube  — থাম্বনেইল + শিরোনাম + ▶ Watch
 *   🔵 Facebook — শিরোনাম + ▶ Watch
 *   📕 PDF      — শিরোনাম + Open
 *   🌐 Website  — শিরোনাম + ডোমেইন + Open
 *   🖼️ ছবি     — ক্যাপশন সহ, ট্যাপে জুম; পরপর একাধিক ছবি হলে স্ক্রল-যোগ্য Gallery
 *   "📎 Lesson Resources" হেডার (২+ রিসোর্স থাকলে), "✓ Verified" ব্যাজ।
 *   Reader সেটিংসের "Show Images"/"Show YouTube/Facebook" বন্ধ থাকলে ছোট
 *   "দেখান" চিপ — ট্যাপ করলে সেই আইটেম খোলে (কিছু হারায় না)।
 *
 * কোনো নতুন Sheet কলাম লাগে না — শিরোনাম/Verified টেক্সটের ভেতরেই লেখা যায়:
 *     [শিরোনাম](https://লিংক)              ← শিরোনামসহ
 *     [শিরোনাম|verified](https://লিংক)      ← ✓ Verified ব্যাজসহ
 * শিরোনাম ছাড়া সাধারণ লিংক আগের মতোই কাজ করে (ডিফল্ট শিরোনাম বসে)।
 */

private data class LinkMeta(val title: String, val verified: Boolean)

private val titledLinkRegex =
    Regex("""\[([^\]\n|]{1,120})(?:\|([^\]\n]*))?]\((https?://[^)\s]+)\)""")

/** `[Title|verified](url)` → শুধু `url` (বাকি পার্সার আগের মতোই কাজ করে) + url→meta ম্যাপ */
private fun extractTitledLinks(text: String): Pair<String, Map<String, LinkMeta>> {
    if (!text.contains("](")) return text to emptyMap()
    val meta = LinkedHashMap<String, LinkMeta>()
    val replaced = titledLinkRegex.replace(text) { m ->
        val title = m.groupValues[1].trim()
        val flag  = m.groupValues[2].trim().lowercase()
        val url   = m.groupValues[3].trim()
        val verified = flag == "verified" || flag == "official" || flag == "✓"
        val info = LinkMeta(title, verified)
        meta[url] = info
        meta[MediaLinkParser.normalizePdfUrl(url)] = info
        " $url "
    }
    return replaced to meta
}

private fun youtubeId(url: String): String = when {
    url.contains("v=")         -> url.substringAfter("v=").substringBefore("&")
    url.contains("youtu.be/")  -> url.substringAfter("youtu.be/").substringBefore("?")
    url.contains("shorts/")    -> url.substringAfter("shorts/").substringBefore("?")
    url.contains("embed/")     -> url.substringAfter("embed/").substringBefore("?")
    else                       -> url.substringAfterLast("/").substringBefore("?")
}

private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore("/").removePrefix("www.")

private fun fileNameOf(url: String): String =
    url.substringBefore("?").substringAfterLast("/").ifBlank { "PDF" }

private sealed class RItem {
    data class Seg(val seg: MediaSegment) : RItem()
    data class Strip(val urls: List<String>) : RItem()
}

/** পরপর ২+ ছবি → একটা Strip (Gallery) */
private fun groupImages(segs: List<MediaSegment>): List<RItem> {
    val out = ArrayList<RItem>()
    var i = 0
    while (i < segs.size) {
        val s = segs[i]
        if (s is MediaSegment.ImageLink) {
            val run = ArrayList<String>()
            var j = i
            while (j < segs.size && segs[j] is MediaSegment.ImageLink) {
                run.add((segs[j] as MediaSegment.ImageLink).url); j++
            }
            if (run.size >= 2) out.add(RItem.Strip(run)) else out.add(RItem.Seg(s))
            i = j
        } else { out.add(RItem.Seg(s)); i++ }
    }
    return out
}

private val Green = Color(0xFF059669)

@Composable
fun StudyRichContent(
    text      : String,
    textColor : Color,
    fontSize  : Int,
    reader    : StudyReaderSettings
) {
    val prepared = remember(text) { extractTitledLinks(text) }
    val metaMap  = prepared.second
    val segments = remember(prepared.first) { MediaLinkParser.parse(prepared.first, allowWeb = true) }
    if (segments.isEmpty()) return

    val items = remember(segments) { groupImages(segments) }
    var zoomUrl by remember { mutableStateOf<String?>(null) }
    val firstResourceIdx = items.indexOfFirst {
        it is RItem.Seg && (it.seg is MediaSegment.VideoLink || it.seg is MediaSegment.PdfLink || it.seg is MediaSegment.WebLink)
    }
    val resourceCount = items.count {
        it is RItem.Seg && (it.seg is MediaSegment.VideoLink || it.seg is MediaSegment.PdfLink || it.seg is MediaSegment.WebLink)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { idx, item ->
            if (idx == firstResourceIdx && resourceCount >= 2) {
                Text("📎 Lesson Resources", fontSize = (fontSize * reader.fontScale * 0.95f).sp,
                    fontWeight = FontWeight.ExtraBold, color = textColor, fontFamily = NotoSansBengali,
                    modifier = Modifier.padding(top = 4.dp))
            }
            when (item) {
                is RItem.Strip -> {
                    if (reader.showImages) {
                        Text("🖼️ Images & Diagrams", fontSize = (fontSize * reader.fontScale * 0.9f).sp,
                            fontWeight = FontWeight.Bold, color = textColor, fontFamily = NotoSansBengali)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.urls.forEach { u ->
                                AsyncImage(
                                    model = u, contentDescription = metaMap[u]?.title ?: "ছবি",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(width = 150.dp, height = 112.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(textColor.copy(alpha = 0.08f))
                                        .clickable { zoomUrl = u }
                                )
                            }
                        }
                        Text("ট্যাপ করলে বড় করে দেখা যাবে", fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.55f), fontFamily = NotoSansBengali)
                    } else {
                        HiddenChip("🖼️ ${item.urls.size}টি ছবি লুকানো — দেখান", textColor) {
                            // সেটিংস বন্ধ থাকলেও ট্যাপে প্রথম ছবি জুমে খোলে
                            zoomUrl = item.urls.first()
                        }
                    }
                }
                is RItem.Seg -> when (val seg = item.seg) {
                    is MediaSegment.PlainText -> if (seg.text.isNotBlank()) {
                        StudyRichBlocks(seg.text, reader, fontSize, textColor)
                    }
                    is MediaSegment.ImageLink -> StudyImageBlock(
                        url = seg.url, caption = metaMap[seg.url]?.title, verified = metaMap[seg.url]?.verified == true,
                        show = reader.showImages, textColor = textColor, fontSize = fontSize, reader = reader,
                        onZoom = { zoomUrl = seg.url }
                    )
                    is MediaSegment.VideoLink -> StudyVideoCard(
                        url = seg.url, isYoutube = seg.isYoutube, meta = metaMap[seg.url],
                        show = reader.showVideos, textColor = textColor
                    )
                    is MediaSegment.PdfLink -> {
                        val ctx = LocalContext.current
                        val m = metaMap[seg.url]
                        ResourceCard(
                            icon = "📕", title = m?.title ?: fileNameOf(seg.url), source = "PDF",
                            verified = m?.verified == true, action = "Open", accent = Color(0xFF4338CA),
                            textColor = textColor, onClick = { openPdfExternal(ctx, seg.url) }
                        )
                    }
                    is MediaSegment.WebLink -> {
                        val ctx = LocalContext.current
                        val m = metaMap[seg.url]
                        ResourceCard(
                            icon = "🌐", title = m?.title ?: hostOf(seg.url), source = hostOf(seg.url),
                            verified = m?.verified == true, action = "Open", accent = Color(0xFF0F766E),
                            textColor = textColor, onClick = { openPdfExternal(ctx, seg.url) }
                        )
                    }
                }
            }
        }
    }

    zoomUrl?.let { u -> ImageZoomOverlay(imageUrl = u, onClose = { zoomUrl = null }) }
}

@Composable
private fun HiddenChip(label: String, textColor: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = textColor.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.15f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, fontSize = 12.sp, color = textColor.copy(alpha = 0.8f), fontFamily = NotoSansBengali,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun VerifiedBadge() {
    Text("✓ Verified", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Green,
        fontFamily = NotoSansBengali,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Green.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
private fun StudyImageBlock(
    url: String, caption: String?, verified: Boolean, show: Boolean,
    textColor: Color, fontSize: Int, reader: StudyReaderSettings, onZoom: () -> Unit
) {
    var revealed by remember(url) { mutableStateOf(false) }
    if (!show && !revealed) {
        HiddenChip("🖼️ ছবি লুকানো — দেখান", textColor) { revealed = true }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AutoImage(url = url, onClick = onZoom)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!caption.isNullOrBlank()) {
                Text(caption, fontSize = (fontSize * reader.fontScale * 0.9f).sp, color = textColor.copy(alpha = 0.85f),
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
            }
            if (verified) VerifiedBadge()
            Text("🔍 জুম", fontSize = 10.sp, color = textColor.copy(alpha = 0.55f), fontFamily = NotoSansBengali)
        }
    }
}

@Composable
private fun StudyVideoCard(url: String, isYoutube: Boolean, meta: LinkMeta?, show: Boolean, textColor: Color) {
    val ctx = LocalContext.current
    var revealed by remember(url) { mutableStateOf(false) }
    val accent = if (isYoutube) Color(0xFFBE123C) else Color(0xFF0369A1)
    if (!show && !revealed) {
        HiddenChip((if (isYoutube) "🎥 YouTube" else "🔵 Facebook") + " ভিডিও লুকানো — দেখান", textColor) { revealed = true }
        return
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = textColor.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().clickable { openVideoApp(ctx, url) }
    ) {
        Column {
            if (isYoutube) {
                Box(Modifier.fillMaxWidth().aspectRatioCompat()) {
                    AsyncImage(
                        model = "https://img.youtube.com/vi/${youtubeId(url)}/hqdefault.jpg",
                        contentDescription = meta?.title ?: "YouTube",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(Color(0xFF111827))
                    )
                    Box(Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                        Text("▶", color = Color.White, fontSize = 20.sp)
                    }
                }
            }
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isYoutube) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) { Text("🔵", fontSize = 20.sp) }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(meta?.title ?: if (isYoutube) "YouTube ভিডিও" else "Facebook ভিডিও",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor,
                        fontFamily = NotoSansBengali, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (isYoutube) "YouTube" else "Facebook", fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.6f), fontFamily = NotoSansBengali)
                        if (meta?.verified == true) VerifiedBadge()
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("▶ Watch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    fontFamily = NotoSansBengali,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(accent)
                        .padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

/** 16:9 — aspectRatio modifier import না বাড়িয়ে লোকাল হেল্পার */
private fun Modifier.aspectRatioCompat(): Modifier = this.then(Modifier.aspectRatio(16f / 9f))

@Composable
private fun ResourceCard(
    icon: String, title: String, source: String, verified: Boolean,
    action: String, accent: Color, textColor: Color, onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = textColor.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor,
                    fontFamily = NotoSansBengali, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(source, fontSize = 11.sp, color = textColor.copy(alpha = 0.6f),
                        fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (verified) VerifiedBadge()
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(action, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                fontFamily = NotoSansBengali,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(accent)
                    .padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}
