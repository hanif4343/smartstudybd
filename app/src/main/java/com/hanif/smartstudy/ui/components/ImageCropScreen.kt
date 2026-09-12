package com.hanif.smartstudy.ui.components

// ═══════════════════════════════════════════════════════════════════
//  ImageCropScreen.kt — প্রোফাইল ছবির জন্য Compose-নেটিভ গোল-ফ্রেম ক্রপ স্ক্রিন
//
//  কেন লাগলো (Image/CDN Hosting Phase): আগে গ্যালারি থেকে ছবি বাছাই করলেই
//  সরাসরি resize+upload হয়ে যেত — ইউজার কোন অংশ রাখতে চায় সেটা বেছে নিতে
//  পারত না (ImgBbService.kt-এর পুরনো ফ্লো দেখুন)। এখন ছবি বাছাইয়ের পর এই
//  স্ক্রিন দেখাবে — আঙুল দিয়ে টেনে/জুম করে গোল ফ্রেমের ভেতর ঠিক জায়গামতো
//  বসিয়ে "✅ সেভ করুন" চাপলে সেই ফ্রেমের ভেতরের অংশটাই স্কয়ার বিটম্যাপ হয়ে
//  বেরোবে (আসল ফাইল সবসময় স্কয়ার-ই সেভ হয়, গোলটা শুধু ভিউফাইন্ডার —
//  অ্যাভাটার এমনিতেই CircleShape দিয়ে গোল দেখানো হয় বাকি স্ক্রিনগুলোতে)।
//
//  পুরনো Activity-based crop লাইব্রেরি (uCrop ইত্যাদি) ব্যবহার না করে এখানে
//  সম্পূর্ণ Compose-নেটিভ বানানো হয়েছে — যাতে বাকি অ্যাপের সাথে সামঞ্জস্যপূর্ণ
//  থাকে (কোনো নতুন Activity/Manifest এন্ট্রি লাগবে না)।
// ═══════════════════════════════════════════════════════════════════

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * @param imageUri যে ছবিটা ক্রপ করা হবে (গ্যালারি থেকে বাছাই করা)
 * @param onCancel বাতিল করলে
 * @param onCropped ক্রপ করা স্কয়ার Bitmap নিয়ে কল হয় (caller এটা compress করে আপলোড করবে)
 */
@Composable
fun ImageCropScreen(
    imageUri: Uri,
    onCancel: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        sourceBitmap = withContext(Dispatchers.IO) { decodeBitmapForCrop(context, imageUri) }
        if (sourceBitmap == null) loadError = true
    }

    val viewportDp = 280.dp
    val viewportPx = with(density) { viewportDp.toPx() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bmp = sourceBitmap
        when {
            loadError -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ছবি লোড করা যায়নি 😔", color = Color.White, fontFamily = NotoSansBengali)
                }
            }
            bmp == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            else -> {
                // ── baseScale: ছবির ছোট পাশ (width/height যেটা ছোট) যেন কমপক্ষে
                // viewport-এর সমান হয় — এতে জুম-আউট করলেও viewport-এর বাইরে কোনো
                // ফাঁকা জায়গা (blank gap) কখনো দেখা যাবে না। ──
                val baseScale = remember(bmp) {
                    viewportPx / min(bmp.width, bmp.height).toFloat()
                }
                var scale by remember(bmp) { mutableStateOf(1f) }   // baseScale-এর ওপর মাল্টিপ্লায়ার
                var offset by remember(bmp) { mutableStateOf(Offset.Zero) }

                val displayedW = bmp.width * baseScale * scale
                val displayedH = bmp.height * baseScale * scale

                fun clampOffset(o: Offset): Offset {
                    val maxX = max(0f, (displayedW - viewportPx) / 2f)
                    val maxY = max(0f, (displayedH - viewportPx) / 2f)
                    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                }

                Column(Modifier.fillMaxSize()) {
                    // ── টপ বার ──
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "বাতিল", tint = Color.White)
                        }
                        Text("ছবি ঠিক করুন", color = Color.White, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.width(48.dp))
                    }

                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // ── পুরো viewport-সাইজ বক্স — এর বাইরে ছবি রেন্ডার clip হয়ে যাবে ──
                        Box(
                            Modifier
                                .size(viewportDp)
                                .pointerInput(bmp) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        offset = clampOffset(offset + pan)
                                    }
                                }
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(
                                        with(density) { (bmp.width * baseScale).toDp() },
                                        with(density) { (bmp.height * baseScale).toDp() }
                                    )
                                    .graphicsLayer(
                                        scaleX = scale, scaleY = scale,
                                        translationX = offset.x, translationY = offset.y
                                    )
                            )
                        }

                        // ── ডার্ক ওভারলে + গোল কাটআউট (spotlight প্যাটার্ন — BlendMode.Clear
                        // ঠিকভাবে কাজ করার জন্য graphicsLayer(alpha=0.99f) দরকার) ──
                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer(alpha = 0.99f)
                        ) {
                            drawRect(color = Color.Black.copy(alpha = 0.6f))
                            drawCircle(
                                color = Color.Transparent,
                                radius = viewportPx / 2f,
                                blendMode = BlendMode.Clear
                            )
                        }
                        // ── ফ্রেমের সাদা বর্ডার (গাইডলাইন) ──
                        Canvas(modifier = Modifier.size(viewportDp)) {
                            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = viewportPx / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                        }
                    }

                    Text(
                        "টেনে সরান বা চিমটি কেটে জুম করুন",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = NotoSansBengali,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)
                    )

                    Button(
                        onClick = {
                            val cropped = cropCircularRegion(bmp, baseScale, scale, offset, viewportPx)
                            onCropped(cropped)
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("সেভ করুন", color = Color.White, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** বড় ছবি মেমরিতে পুরোপুরি না এনে ~1500px-এর মধ্যে ডাউনস্যাম্পল করে লোড করে (OOM এড়াতে)। */
private fun decodeBitmapForCrop(context: Context, uri: Uri): Bitmap? = try {
    val opts1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts1) }
    var sample = 1
    val maxDim = 1500
    while ((opts1.outWidth / sample) > maxDim || (opts1.outHeight / sample) > maxDim) sample *= 2
    val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts2) }
} catch (e: Exception) {
    null
}

/**
 * বর্তমান pan/zoom স্টেট থেকে আসল bitmap-এর সঠিক pixel-region বের করে ক্রপ করে —
 * viewport-এ যেটুকু দেখা যাচ্ছে ঠিক ততটুকুই (স্কয়ার) কাটা হবে।
 */
private fun cropCircularRegion(
    bmp: Bitmap,
    baseScale: Float,
    scale: Float,
    offset: Offset,
    viewportPx: Float
): Bitmap {
    val effectiveScale = baseScale * scale
    val displayedW = bmp.width * effectiveScale
    val displayedH = bmp.height * effectiveScale

    // viewport-এর কেন্দ্র, ছবির (unscaled/untranslated) কো-অর্ডিনেটে
    val centerXDisplayed = displayedW / 2f - offset.x
    val centerYDisplayed = displayedH / 2f - offset.y
    val centerXOrig = centerXDisplayed / effectiveScale
    val centerYOrig = centerYDisplayed / effectiveScale
    val cropSizeOrig = (viewportPx / effectiveScale).coerceAtMost(min(bmp.width, bmp.height).toFloat())

    var left = (centerXOrig - cropSizeOrig / 2f).coerceIn(0f, (bmp.width - cropSizeOrig).coerceAtLeast(0f))
    var top  = (centerYOrig - cropSizeOrig / 2f).coerceIn(0f, (bmp.height - cropSizeOrig).coerceAtLeast(0f))
    val size = cropSizeOrig.toInt().coerceAtLeast(1)

    return Bitmap.createBitmap(bmp, left.toInt(), top.toInt(), size.coerceAtMost(bmp.width - left.toInt()), size.coerceAtMost(bmp.height - top.toInt()))
}
