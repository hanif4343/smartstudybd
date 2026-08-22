package com.hanif.smartstudy.officeapp.word

import android.annotation.SuppressLint
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.hanif.smartstudy.officeapp.common.OfficeFile
import com.hanif.smartstudy.officeapp.common.OfficeFileStore
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import org.json.JSONObject.quote as jsonQuote

/**
 * ══════════════════════════════════════════════════════════════════
 *  WordEditorScreen — আসল Word এডিটর। WebView-এ
 *  assets/officeapp/word/index.html লোড করে, Kotlin ↔ JS ব্রিজের
 *  মাধ্যমে কনটেন্ট get/set করে এবং OfficeFileStore-এ সেভ করে।
 *
 *  File মেনু: New (আগের স্ক্রিনে হয়), Save, Page Setup, Print/Export PDF
 * ══════════════════════════════════════════════════════════════════
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WordEditorScreen(
    initialFile: OfficeFile,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { OfficeFileStore(context) }
    var file by remember { mutableStateOf(initialFile) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var editorReady by remember { mutableStateOf(false) }
    var isDirty by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showPageSetup by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    fun applyPageSettings(wv: WebView, f: OfficeFile) {
        wv.evaluateJavascript(
            "window.setPageSetup(${f.pageMarginTop},${f.pageMarginRight},${f.pageMarginBottom},${f.pageMarginLeft},'${f.pageSize}')",
            null
        )
        wv.evaluateJavascript(
            "window.setPageBorder(${if (f.pageBorder) "'3px double #1F2937'" else "''"})",
            null
        )
    }

    fun saveNow(showToast: Boolean = true) {
        val wv = webViewRef ?: return
        wv.evaluateJavascript("(function(){return window.getContent();})()") { raw ->
            // evaluateJavascript returns a JSON-encoded string; strip surrounding quotes & unescape
            val html = try {
                org.json.JSONTokener(raw).nextValue() as? String ?: ""
            } catch (e: Exception) { "" }
            file = file.copy(content = html)
            store.save(file)
            isDirty = false
            if (showToast) Toast.makeText(context, "সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    fun printOrExportPdf() {
        val wv = webViewRef ?: return
        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
        val jobName = "${file.title} - SmartStudy Word"
        val adapter = wv.createPrintDocumentAdapter(jobName)
        val attrs = PrintAttributes.Builder()
            .setMediaSize(
                when (file.pageSize) {
                    "legal" -> PrintAttributes.MediaSize.NA_LEGAL
                    "a5" -> PrintAttributes.MediaSize.ISO_A5
                    else -> PrintAttributes.MediaSize.ISO_A4
                }
            )
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        printManager.print(jobName, adapter, attrs)
    }

    // পিছনে গেলে auto-save
    BackHandler {
        saveNow(showToast = false)
        onBack()
    }

    if (showPageSetup) {
        PageSetupDialog(
            file = file,
            onDismiss = { showPageSetup = false },
            onApply = { updated ->
                file = updated
                webViewRef?.let { applyPageSettings(it, updated) }
                store.save(updated)
                showPageSetup = false
            }
        )
    }

    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(file.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("ফাইলের নাম পরিবর্তন", fontFamily = NotoSansBengali) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    file = file.copy(title = newTitle.ifBlank { file.title })
                    store.save(file)
                    showRenameDialog = false
                }) { Text("ঠিক আছে", fontFamily = NotoSansBengali) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("বাতিল", fontFamily = NotoSansBengali) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            file.title,
                            fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        if (isDirty) Text("সেভ হয়নি", fontSize = 10.sp, color = Color(0xFFF59E0B), fontFamily = NotoSansBengali)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        saveNow(showToast = false)
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { saveNow() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                    IconButton(onClick = { printOrExportPdf() }) {
                        Icon(Icons.Default.Print, contentDescription = "Print / Export PDF")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("নাম পরিবর্তন (Rename)", fontFamily = NotoSansBengali) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { showMenu = false; showRenameDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("পেজ সেটআপ (Page Setup)", fontFamily = NotoSansBengali) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = { showMenu = false; showPageSetup = true }
                            )
                            DropdownMenuItem(
                                text = { Text("প্রিন্ট / PDF Export", fontFamily = NotoSansBengali) },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = { showMenu = false; printOrExportPdf() }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        val webViewSelf = this@apply
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onEditorReady() {
                                webViewSelf.post {
                                    editorReady = true
                                    applyPageSettings(webViewSelf, file)
                                    val escaped = jsonQuote(file.content)
                                    webViewSelf.evaluateJavascript("window.setContent($escaped)", null)
                                }
                            }

                            @JavascriptInterface
                            fun onContentChanged() {
                                webViewSelf.post { isDirty = true }
                            }

                            @JavascriptInterface
                            fun openPageSetup() {
                                webViewSelf.post { showPageSetup = true }
                            }
                        }, "AndroidBridge")

                        webViewClient = object : WebViewClient() {}
                        loadUrl("file:///android_asset/officeapp/word/index.html")
                        webViewRef = this
                    }
                }
            )
        }
    }

    // অটো-সেভ: প্রতি ১৫ সেকেন্ডে যদি dirty থাকে
    LaunchedEffect(isDirty) {
        if (isDirty) {
            kotlinx.coroutines.delay(15_000)
            if (isDirty) saveNow(showToast = false)
        }
    }
}

@Composable
private fun PageSetupDialog(
    file: OfficeFile,
    onDismiss: () -> Unit,
    onApply: (OfficeFile) -> Unit
) {
    var top by remember { mutableStateOf(file.pageMarginTop.toFloat()) }
    var right by remember { mutableStateOf(file.pageMarginRight.toFloat()) }
    var bottom by remember { mutableStateOf(file.pageMarginBottom.toFloat()) }
    var left by remember { mutableStateOf(file.pageMarginLeft.toFloat()) }
    var pageSize by remember { mutableStateOf(file.pageSize) }
    var border by remember { mutableStateOf(file.pageBorder) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            Text("পেজ সেটআপ", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))

            Text("পেজ সাইজ", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFF64748B))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("a4" to "A4", "legal" to "Legal", "a5" to "A5").forEach { (id, label) ->
                    FilterChip(
                        selected = pageSize == id,
                        onClick = { pageSize = id },
                        label = { Text(label, fontFamily = NotoSansBengali) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            MarginSlider("উপরে (Top) mm", top) { top = it }
            MarginSlider("নিচে (Bottom) mm", bottom) { bottom = it }
            MarginSlider("বামে (Left) mm", left) { left = it }
            MarginSlider("ডানে (Right) mm", right) { right = it }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = border, onCheckedChange = { border = it })
                Text("পেজ বর্ডার (Page Border)", fontFamily = NotoSansBengali, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    file.pageMarginTop = top.toInt()
                    file.pageMarginRight = right.toInt()
                    file.pageMarginBottom = bottom.toInt()
                    file.pageMarginLeft = left.toInt()
                    file.pageSize = pageSize
                    file.pageBorder = border
                    onApply(file)
                }) { Text("প্রয়োগ করুন", fontFamily = NotoSansBengali) }
            }
        }
    }
}

@Composable
private fun MarginSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${value.toInt()}mm", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFF334155))
        Slider(value = value, onValueChange = onChange, valueRange = 5f..50f, steps = 44)
    }
}
