package com.hanif.smartstudy.ui.menu

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.util.AppUsageTracker
import com.hanif.smartstudy.util.DeviceUsageStats
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  StudyTimeScreen — "আমার সময়": SmartStudy-তে কতক্ষণ (সবসময় পাওয়া যায়, লোকাল)
//  + ফোনে বাকি অ্যাপে কতক্ষণ (ঐচ্ছিক, Usage Access পারমিশন লাগে)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTimeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var summary by remember { mutableStateOf<AppUsageTracker.Summary?>(null) }
    var hasPerm by remember { mutableStateOf(DeviceUsageStats.hasPermission(context)) }
    var otherUsage by remember { mutableStateOf<DeviceUsageStats.TodayUsage?>(null) }

    fun reload() {
        scope.launch {
            summary = AppUsageTracker.summary(context)
            hasPerm = DeviceUsageStats.hasPermission(context)
            if (hasPerm) otherUsage = DeviceUsageStats.getTodayOtherAppsUsage(context)
        }
    }

    LaunchedEffect(Unit) { reload() }
    // Settings থেকে permission দিয়ে ফিরলে (এই Composable আবার visible হলে) রিফ্রেশ
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) reload()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⏱ আমার সময়", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── SmartStudy-তে আজ/এই সপ্তাহে কতক্ষণ ──
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📚 SmartStudy-তে সময়", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TimeStatCol("আজ", formatMin(summary?.todayMin ?: 0), Indigo600)
                        TimeStatCol("এই সপ্তাহে", formatMin(summary?.weekMin ?: 0), Teal600)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("শেষ ৭ দিন", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontFamily = NotoSansBengali)
                    MinuteBarChart(summary?.last7Days ?: emptyList())
                }
            }

            // ── ফোনের বাকি অ্যাপে সময় (ঐচ্ছিক) ──
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📱 ফোনের অন্যান্য অ্যাপ (আজ)", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)

                    if (!hasPerm) {
                        Text(
                            "ফোনে বাকি কোন অ্যাপে কতক্ষণ গেল দেখতে হলে একটা বিশেষ অনুমতি (Usage Access) " +
                                "লাগবে। এই তথ্য শুধু আপনার ফোনেই থাকে, কোথাও পাঠানো হয় না।",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                            fontFamily = NotoSansBengali
                        )
                        Button(onClick = { DeviceUsageStats.openUsageAccessSettings(context) }) {
                            Text("অনুমতি দিন", fontFamily = NotoSansBengali)
                        }
                    } else {
                        val usage = otherUsage
                        if (usage == null) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        } else if (usage.apps.isEmpty()) {
                            Text("আজ এখনো তেমন কোনো তথ্য নেই।", fontSize = 12.sp, fontFamily = NotoSansBengali,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        } else {
                            val smartStudyMin = summary?.todayMin ?: 0
                            val otherMin = (usage.totalMillis / 60000).toInt()
                            val totalMin = (smartStudyMin + otherMin).coerceAtLeast(1)
                            val pct = (smartStudyMin * 100) / totalMin
                            Text(
                                "আজ ফোনের মোট ব্যবহারের প্রায় $pct% SmartStudy-তে",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Indigo600, fontFamily = NotoSansBengali
                            )
                            Spacer(Modifier.height(4.dp))
                            usage.apps.forEach { app ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(app.label, fontSize = 13.sp, fontFamily = NotoSansBengali, maxLines = 1,
                                        modifier = Modifier.weight(1f))
                                    Text(formatMin((app.millis / 60000).toInt()), fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontFamily = NotoSansBengali)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── StatsScreen.kt-এর TimeStatCol হুবহু একই দেখতে — কিন্তু Kotlin-এ top-level `private`
// মানে file-private (একই package হলেও অন্য ফাইল থেকে দেখা যায় না), তাই এখানে আলাদা করে
// রাখা হলো, StatsScreen.kt স্পর্শ করতে হলো না ──
@Composable
private fun TimeStatCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = NotoSansBengali)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontFamily = NotoSansBengali)
    }
}

private fun formatMin(min: Int): String {
    if (min < 60) return "${min} মিনিট"
    val h = min / 60; val m = min % 60
    return if (m == 0) "${h} ঘণ্টা" else "${h} ঘণ্টা ${m} মিনিট"
}

@Composable
private fun MinuteBarChart(days: List<Pair<String, Int>>) {
    val maxMin = days.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Row(
        Modifier.fillMaxWidth().height(90.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { (date, min) ->
            val fraction = min.toFloat() / maxMin
            val animH by androidx.compose.animation.core.animateFloatAsState(fraction, label = "usage_$date")
            val dayLabel = date.split("-").lastOrNull() ?: ""
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (min > 0) Text("$min", fontSize = 7.sp, color = Teal600, fontFamily = NotoSansBengali)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animH.coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (min > 0) Teal600 else MaterialTheme.colorScheme.outline.copy(0.2f))
                )
                Text(dayLabel, fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), fontFamily = NotoSansBengali)
            }
        }
    }
}
