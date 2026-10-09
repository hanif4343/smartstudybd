package com.hanif.smartstudy.ui.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.launch

private val QpIndigo = Color(0xFF4F46E5)

private fun qpBn(n: Int): String =
    n.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

/**
 * QBank "মূল প্রশ্নপত্র" — একাধিক ছবি থাকলে সহজে দেখার প্যানেল:
 *  • ডানে-বাঁয়ে swipe করে পাতা বদল (আগে সব ছবি লম্বা করে নিচে নিচে ছিল)
 *  • ওপরে "১ / ৩" কাউন্টার, নিচে থাম্বনেইল-স্ট্রিপ (ট্যাপে সরাসরি ওই ছবিতে)
 *  • ছবিতে ট্যাপ বা ⛶ → ফুল-স্ক্রিন ভিউয়ার (QuestionPaperViewer)
 */
@Composable
fun QuestionPaperPager(
    urls    : List<String>,
    modifier: Modifier = Modifier,
    onOpen  : (Int) -> Unit
) {
    if (urls.isEmpty()) return
    val pager = rememberPagerState(pageCount = { urls.size })
    val scope = rememberCoroutineScope()
    val thumbs = rememberLazyListState()
    LaunchedEffect(pager.currentPage) { if (urls.size > 1) thumbs.animateScrollToItem(pager.currentPage) }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(340.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1F5F9))
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                AsyncImage(
                    model = urls[i], contentDescription = "প্রশ্নপত্র ${qpBn(i + 1)}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable { onOpen(i) }
                )
            }
            if (urls.size > 1) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text("${qpBn(pager.currentPage + 1)} / ${qpBn(urls.size)}", color = Color.White,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                    .clickable { onOpen(pager.currentPage) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.OpenInFull, "বড় করে দেখুন", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }

        if (urls.size > 1) {
            Spacer(Modifier.height(6.dp))
            LazyRow(state = thumbs, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(urls) { i, u ->
                    val active = i == pager.currentPage
                    Box(
                        Modifier.size(width = 46.dp, height = 58.dp).clip(RoundedCornerShape(8.dp))
                            .border(if (active) 2.dp else 1.dp, if (active) QpIndigo else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                    ) {
                        AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                        Text(qpBn(i + 1), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * ফুল-স্ক্রিন ভিউয়ার: swipe / ◀ ▶ বাটনে ছবি বদল, দুই আঙুলে জুম, ডাবল-ট্যাপে ২.৫× জুম (আবার ডাবল-ট্যাপে ফিরে),
 * জুম থাকা অবস্থায় এক আঙুলে ছবি সরানো। নিচে থাম্বনেইল-স্ট্রিপ।
 */
@Composable
fun QuestionPaperViewer(
    urls      : List<String>,
    startIndex: Int = 0,
    onDismiss : () -> Unit
) {
    if (urls.isEmpty()) { onDismiss(); return }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, urls.lastIndex), pageCount = { urls.size })
    val scope = rememberCoroutineScope()
    val thumbs = rememberLazyListState()
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false; if (urls.size > 1) thumbs.animateScrollToItem(pager.currentPage) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pager, modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed
            ) { i ->
                QpZoomPage(url = urls[i], onZoomChanged = { if (i == pager.currentPage) zoomed = it })
            }

            // ── ওপরের বার: বন্ধ + কাউন্টার ──
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Close, "বন্ধ", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("মূল প্রশ্নপত্র  ${qpBn(pager.currentPage + 1)} / ${qpBn(urls.size)}", color = Color.White,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                }
            }

            // ── আগের/পরের ছবির বাটন ──
            if (urls.size > 1) {
                if (pager.currentPage > 0) QpNavBtn(Modifier.align(Alignment.CenterStart).padding(6.dp), left = true) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                }
                if (pager.currentPage < urls.lastIndex) QpNavBtn(Modifier.align(Alignment.CenterEnd).padding(6.dp), left = false) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }

            // ── নিচে: থাম্বনেইল + ইঙ্গিত ──
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (urls.size > 1) {
                    LazyRow(
                        state = thumbs, contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(urls) { i, u ->
                            val active = i == pager.currentPage
                            Box(
                                Modifier.size(width = 44.dp, height = 56.dp).clip(RoundedCornerShape(8.dp))
                                    .border(if (active) 2.dp else 1.dp, if (active) Color.White else Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { scope.launch { pager.animateScrollToPage(i) } }
                            ) {
                                AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text("দুই আঙুলে জুম · ডাবল ট্যাপে বড়/ছোট", color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp, fontFamily = NotoSansBengali)
            }
        }
    }
}

@Composable
private fun QpNavBtn(modifier: Modifier, left: Boolean, onClick: () -> Unit) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(if (left) Icons.Default.ChevronLeft else Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
}

/** একটা পাতা — পিঞ্চ-জুম + ডাবল-ট্যাপ। এক আঙুল (জুম ছাড়া) pager-কে ছেড়ে দেয়, তাই swipe বাধা পায় না। */
@Composable
private fun QpZoomPage(url: String, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.01f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan  = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale <= 1.01f) { scale = 1f; offset = Offset.Zero }
                            else {
                                val maxX = box.width  * (scale - 1f) / 2f
                                val maxY = box.height * (scale - 1f) / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y
            )
        )
    }
}
