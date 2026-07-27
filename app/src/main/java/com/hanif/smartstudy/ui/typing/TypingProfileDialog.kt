package com.hanif.smartstudy.ui.typing

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hanif.smartstudy.data.remote.TypingCloudSyncService
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.SpeedRankUtil
import com.hanif.smartstudy.util.TypingHistoryEntry
import kotlinx.coroutines.launch

/**
 * Phase ৩: একক প্রোফাইল পেজ — Neonlipi-এর "প্রোফাইল, সব প্রগ্রেস ও Cloud Sync" ফিচারের
 * সমতুল্য। বেস্ট WPM, স্ট্রিক, সাম্প্রতিক হিস্ট্রি, achievements ব্যাজ, আর Cloud Sync
 * বাটন — সব একসাথে একটা ডায়ালগে। কোনো নতুন নেভিগেশন রুট লাগেনি (NavHost-এ হাত
 * দেওয়া হয়নি) — TypingPracticeScreen থেকে "👤 প্রোফাইল" বাটনে খোলে।
 */

private data class Achievement(val emoji: String, val label: String, val unlocked: Boolean)

private fun buildAchievements(bestWpm: Int, streak: Int): List<Achievement> = listOf(
    Achievement("🥉", "১৫+ WPM", bestWpm >= 15),
    Achievement("🥈", "৩০+ WPM", bestWpm >= 30),
    Achievement("🥇", "৫০+ WPM", bestWpm >= 50),
    Achievement("💎", "৭৫+ WPM", bestWpm >= 75),
    Achievement("🔥", "৩ দিনের স্ট্রিক", streak >= 3),
    Achievement("🔥🔥", "৭ দিনের স্ট্রিক", streak >= 7),
    Achievement("🔥🔥🔥", "৩০ দিনের স্ট্রিক", streak >= 30),
)

@Composable
fun TypingProfileDialog(context: Context, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val session = remember { SessionManager(context) }

    var bestWpm by remember { mutableStateOf(0) }
    var streak by remember { mutableStateOf(0) }
    var history by remember { mutableStateOf(listOf<TypingHistoryEntry>()) }
    var todaySeconds by remember { mutableStateOf(0) }
    var isSyncing by remember { mutableStateOf(false) }
    var lastSyncedAt by remember { mutableStateOf<Long?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val userName = session.getCurrentUser()?.displayName() ?: "ব্যবহারকারী"
    val phone = session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() }

    fun reloadLocal() {
        bestWpm = session.getTypingBestWpm()
        streak = session.getStreak()
        history = session.getTypingHistory()
        todaySeconds = session.getTypingTodaySeconds()
    }

    LaunchedEffect(Unit) { reloadLocal() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp,
            modifier = Modifier.fillMaxHeight(0.85f)
        ) {
            Column(Modifier.padding(18.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👤 প্রোফাইল", fontSize = 16.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("✕") }
                }
                Text(userName, fontSize = 13.sp, fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))

                // ── সংক্ষিপ্ত স্ট্যাট রো — বেস্ট WPM, স্ট্রিক, স্পিড-র‍্যাংক ──
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("বেস্ট WPM", "$bestWpm", Modifier.weight(1f))
                    StatBox("স্ট্রিক", "$streak দিন", Modifier.weight(1f))
                    StatBox("আজকের সময়", "${todaySeconds / 60} মিনিট", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                val rank = SpeedRankUtil.rankFor(bestWpm)
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Text(
                        "${rank.emoji} বর্তমান স্পিড-র‍্যাংক: ${rank.name}",
                        fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("☁️ Cloud Sync", fontSize = 13.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (phone == null) {
                    Text(
                        "Cloud Sync-এর জন্য ফোন নম্বর দিয়ে লগইন থাকা দরকার — এখন গেস্ট হিসেবে আছো, তাই প্রগ্রেস শুধু এই ডিভাইসেই থাকবে।",
                        fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Button(
                        onClick = {
                            isSyncing = true
                            syncMessage = null
                            scope.launch {
                                try {
                                    // ── প্রথমে cloud থেকে pull করে লোকালের সাথে merge —
                                    // তারপর merge করা (সম্ভবত বড়) ডেটাটাই আবার push করে,
                                    // যাতে অন্য ডিভাইসও এই ডিভাইসের সাম্প্রতিক ডেটা পায় ──
                                    val cloud = TypingCloudSyncService.pull(phone)
                                    if (cloud != null) {
                                        session.mergeTypingCloudSnapshot(cloud.bestWpm, cloud.history)
                                        reloadLocal()
                                    }
                                    TypingCloudSyncService.push(phone, session.getTypingBestWpm(), session.getRawTypingHistory())
                                    lastSyncedAt = System.currentTimeMillis()
                                    syncMessage = "✅ সফলভাবে sync হয়েছে"
                                } catch (e: Exception) {
                                    syncMessage = "❌ Sync ব্যর্থ হয়েছে, ইন্টারনেট চেক করুন"
                                } finally {
                                    isSyncing = false
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSyncing) "Sync হচ্ছে..." else "☁️ এখনই Sync করো",
                            fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                    syncMessage?.let {
                        Text(it, fontSize = 11.sp, fontFamily = NotoSansBengali,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("🏆 অর্জন (Achievements)", fontSize = 13.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                buildAchievements(bestWpm, streak).chunked(2).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { a ->
                            Surface(
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                                color = if (a.unlocked) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(if (a.unlocked) a.emoji else "🔒", fontSize = 16.sp)
                                    Text(a.label, fontSize = 11.sp, fontFamily = NotoSansBengali,
                                        color = if (a.unlocked) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("📜 সাম্প্রতিক সেশন", fontSize = 13.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (history.isEmpty()) {
                    Text("এখনো কোনো সেশন সম্পন্ন হয়নি", fontSize = 11.sp, fontFamily = NotoSansBengali,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    history.take(10).forEach { h ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(h.date, fontSize = 11.sp, fontFamily = NotoSansBengali,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${h.wpm} WPM · ${h.accuracy}%", fontSize = 11.sp, fontFamily = NotoSansBengali,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            Text(label, fontSize = 10.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
