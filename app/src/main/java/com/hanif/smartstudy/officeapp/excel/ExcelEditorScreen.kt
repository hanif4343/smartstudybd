package com.hanif.smartstudy.officeapp.excel

import android.annotation.SuppressLint
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hanif.smartstudy.officeapp.common.OfficeFile
import com.hanif.smartstudy.officeapp.common.OfficeFileStore
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import org.json.JSONObject.quote as jsonQuote

/**
 * ══════════════════════════════════════════════════════════════════
 *  ExcelEditorScreen — WebView-এ assets/officeapp/excel/index.html
 *  লোড করে, JS bridge দিয়ে সম্পূর্ণ sheet state (grid + formulas +
 *  formatting) JSON হিসেবে get/set করে OfficeFileStore-এ সেভ করে।
 * ══════════════════════════════════════════════════════════════════
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ExcelEditorScreen(
    initialFile: OfficeFile,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { OfficeFileStore(context) }
    var file by remember { mutableStateOf(initialFile) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isDirty by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    fun saveNow(showToast: Boolean = true) {
        val wv = webViewRef ?: return
        wv.evaluateJavascript("(function(){return window.getSheetState();})()") { raw ->
            val json = try {
                org.json.JSONTokener(raw).nextValue() as? String ?: ""
            } catch (e: Exception) { "" }
            if (json.isNotBlank()) {
                file = file.copy(content = json)
                store.save(file)
                isDirty = false
                if (showToast) Toast.makeText(context, "সেভ হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun printSheet(landscape: Boolean = true) {
        val wv = webViewRef ?: return
        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
        val jobName = "${file.title} - SmartStudy Excel"
        val adapter = wv.createPrintDocumentAdapter(jobName)
        val baseSize = PrintAttributes.MediaSize.ISO_A4
        val attrs = PrintAttributes.Builder()
            .setMediaSize(if (landscape) baseSize.asLandscape() else baseSize)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        printManager.print(jobName, adapter, attrs)
    }

    BackHandler {
        saveNow(showToast = false)
        onBack()
    }

    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(file.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("ফাইলের নাম পরিবর্তন", fontFamily = NotoSansBengali) },
            text = {
                OutlinedTextField(
                    value = newTitle, onValueChange = { newTitle = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    file = file.copy(title = newTitle.ifBlank { file.title })
                    store.save(file)
                    showRenameDialog = false
                }) { Text("ঠিক আছে", fontFamily = NotoSansBengali) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("বাতিল", fontFamily = NotoSansBengali) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.title, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        if (isDirty) Text("সেভ হয়নি", fontSize = 10.sp, color = Color(0xFFF59E0B), fontFamily = NotoSansBengali)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { saveNow(showToast = false); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { saveNow() }) { Icon(Icons.Default.Save, contentDescription = "Save") }
                    IconButton(onClick = { printSheet(landscape = true) }) { Icon(Icons.Default.Print, contentDescription = "Print / Export PDF") }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("নাম পরিবর্তন (Rename)", fontFamily = NotoSansBengali) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { showMenu = false; showRenameDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("প্রিন্ট (Landscape) / PDF", fontFamily = NotoSansBengali) },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = { showMenu = false; printSheet(landscape = true) }
                            )
                            DropdownMenuItem(
                                text = { Text("প্রিন্ট (Portrait) / PDF", fontFamily = NotoSansBengali) },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = { showMenu = false; printSheet(landscape = false) }
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
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        val webViewSelf = this@apply
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onEditorReady() {
                                webViewSelf.post {
                                    val escaped = jsonQuote(file.content)
                                    webViewSelf.evaluateJavascript("window.setSheetState($escaped)", null)
                                }
                            }
                            @JavascriptInterface
                            fun onContentChanged() { webViewSelf.post { isDirty = true } }
                        }, "AndroidBridge")

                        webViewClient = object : WebViewClient() {}
                        loadUrl("file:///android_asset/officeapp/excel/index.html")
                        webViewRef = this
                    }
                }
            )
        }
    }

    LaunchedEffect(isDirty) {
        if (isDirty) {
            kotlinx.coroutines.delay(15_000)
            if (isDirty) saveNow(showToast = false)
        }
    }
}
