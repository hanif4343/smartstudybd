package com.hanif.smartstudy.ui.menu

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hanif.smartstudy.ui.theme.NotoSansBengali

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError  by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("গোপনীয়তা নীতি", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (hasError) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🌐", fontSize = 48.sp)
                    Text("লোড হয়নি", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("ইন্টারনেট সংযোগ চেক করো", fontFamily = NotoSansBengali,
                        fontSize = 13.sp, color = Color(0xFF64748B))
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory  = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }
                                override fun onReceivedError(
                                    view: WebView?, request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false; hasError = true
                                    }
                                }
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            loadUrl("https://hanif4343.github.io/smart-study/privacy-policy.html")
                        }
                    }
                )
            }
            if (isLoading && !hasError) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color    = Color(0xFF4F46E5)
                )
            }
        }
    }
}
