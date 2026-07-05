package com.hanif.smartstudy.ui.shared

import android.app.SearchManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextToolbar
import androidx.compose.foundation.text.selection.TextToolbarStatus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.TtsManager
import kotlin.math.roundToInt

/**
 * ════════════════════════════════════════════════════════════════
 *  SmartTextToolbar — টেক্সট সিলেকশন টুলবার, ওয়েব অ্যাপের মতো
 * ════════════════════════════════════════════════════════════════
 * Jetpack Compose এর ডিফল্ট SelectionContainer টুলবারে শুধু
 * "Copy" আর "Select all" থাকে — নরমাল Android TextView এর মতো
 * Share / Web Search / Read Aloud এমনিতে আসে না, যেটা এই অ্যাপে
 * অনেকগুলো জায়গায় (Quiz/QBank/Study/Admin — SelectionContainer
 * যেখানেই ব্যবহার হয়েছে) আশা করা হয়েছিল কিন্তু আসেনি।
 *
 * এই ফাইলটা একটা কাস্টম TextToolbar implement করে যেটা
 * CompositionLocalProvider(LocalTextToolbar provides ...) দিয়ে
 * অ্যাপের রুটে (MainActivity) একবার বসালেই — অ্যাপের প্রতিটা
 * SelectionContainer (আলাদা আলাদা করে এডিট না করেই) — এই ৫টা
 * অপশন দেখাবে: Copy, Select all, Share, Web Search, Read Aloud।
 *
 * ── কেন Clipboard দিয়ে সিলেক্টেড টেক্সট পড়া হয় ──
 * Compose এর TextToolbar API সরাসরি সিলেক্ট করা টেক্সট স্ট্রিং
 * দেয় না — শুধু onCopyRequested() লাম্বডা দেয় (যেটা internally
 * clipboard এ কপি করে)। তাই Share/Web Search/Read Aloud চাপলে
 * প্রথমে copy অ্যাকশন চালিয়ে, তারপর clipboard থেকে টেক্সট পড়ে
 * আনা হয় — এটাই Compose এ এই ফিচার বানানোর প্রচলিত উপায়।
 */
private class SmartTextToolbarImpl(private val context: Context) : TextToolbar {

    var toolbarRect by mutableStateOf(Rect.Zero)
        private set
    var copyAction by mutableStateOf<(() -> Unit)?>(null)
        private set
    var selectAllAction by mutableStateOf<(() -> Unit)?>(null)
        private set

    override var status: TextToolbarStatus by mutableStateOf(TextToolbarStatus.Hidden)
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        toolbarRect = rect
        copyAction = onCopyRequested
        selectAllAction = onSelectAllRequested
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
    }

    /** copy ট্রিগার করে clipboard থেকে সিলেক্ট করা টেক্সট বের করে আনে */
    fun readSelectedText(): String {
        copyAction?.invoke()
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
            } else ""
        } catch (_: Exception) { "" }
    }
}

/**
 * পুরো অ্যাপের রুটে (MainActivity এর setContent এর ভেতরে, সবার উপরে)
 * এটা দিয়ে একবার wrap করলেই সব SelectionContainer এ Copy/Select all
 * এর পাশাপাশি Share, Web Search আর Read Aloud বাটনও পাওয়া যাবে —
 * প্রতিটা স্ক্রিন আলাদাভাবে এডিট করার দরকার নেই।
 */
@Composable
fun ProvideSmartTextToolbar(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val toolbar = remember { SmartTextToolbarImpl(context) }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()
    }

    if (toolbar.status == TextToolbarStatus.Shown) {
        SmartToolbarPopup(toolbar)
    }
}

@Composable
private fun SmartToolbarPopup(toolbar: SmartTextToolbarImpl) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val rect = toolbar.toolbarRect

    // টুলবারটা সিলেকশনের ঠিক উপরে বসানো হয় — screen edge এ গেলে 0 এর নিচে না যায়
    val toolbarHeightPx = with(density) { 52.dp.toPx() }
    val xPx = rect.left.roundToInt().coerceAtLeast(0)
    val yPx = (rect.top - toolbarHeightPx).roundToInt().coerceAtLeast(0)

    Popup(
        offset = IntOffset(xPx, yPx),
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1F2937),
            shadowElevation = 6.dp
        ) {
            Row(Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                toolbar.copyAction?.let { action ->
                    ToolbarActionButton(Icons.Default.ContentCopy, "কপি") {
                        action()
                        toolbar.hide()
                    }
                }
                toolbar.selectAllAction?.let { action ->
                    ToolbarActionButton(Icons.Default.SelectAll, "সব সিলেক্ট") {
                        action()
                    }
                }
                ToolbarActionButton(Icons.Default.Share, "শেয়ার") {
                    val text = toolbar.readSelectedText()
                    toolbar.hide()
                    if (text.isNotBlank()) {
                        try {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            val chooser = Intent.createChooser(send, "শেয়ার করুন")
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        } catch (_: Exception) { }
                    }
                }
                ToolbarActionButton(Icons.Default.Search, "ওয়েব সার্চ") {
                    val text = toolbar.readSelectedText()
                    toolbar.hide()
                    if (text.isNotBlank()) {
                        try {
                            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                                putExtra(SearchManager.QUERY, text)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    }
                }
                ToolbarActionButton(Icons.Default.RecordVoiceOver, "পড়ে শোনাও") {
                    val text = toolbar.readSelectedText()
                    toolbar.hide()
                    if (text.isNotBlank()) {
                        TtsManager.speak(text, "selection_readaloud")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = NotoSansBengali
        )
    }
}
