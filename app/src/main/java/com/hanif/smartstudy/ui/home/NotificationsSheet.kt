package com.hanif.smartstudy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.AppNotification
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ── 🔔 নোটিফিকেশন ইনবক্স — Home-এর বেল আইকনে চাপলে খোলে। এতদিন এই আইকনে
 * কোনো ফাংশনই যুক্ত ছিল না (UI placeholder), এই শীট দিয়ে সেটা এখন বাস্তব
 * ফিচার হলো — Firebase "Notifications/{phone}" node থেকে পড়া হয় (এই একই
 * node আগে থেকেই NotificationPollWorker.kt system-tray notification
 * দেখানোর জন্য পোল করে), তাই সিস্টেম নোটিফিকেশন মিস করলেও এখানে এসে
 * পুরোনোগুলো দেখা যাবে। ──
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
    notifications : List<AppNotification>,
    isLoading     : Boolean,
    onDismiss     : () -> Unit,
    onItemClick   : (AppNotification) -> Unit,
    onMarkAllRead : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 560.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🔔 নোটিফিকেশন",
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                if (notifications.any { !it.read }) {
                    TextButton(onClick = onMarkAllRead) {
                        Text("সব পড়া হয়েছে", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFF4F46E5))
                    }
                }
            }

            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = Color(0xFF4F46E5))
                    }
                }
                notifications.isEmpty() -> {
                    Column(
                        Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null,
                            tint = Color(0xFFCBD5E1), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("এখনো কোনো নোটিফিকেশন নেই", fontFamily = NotoSansBengali, fontSize = 13.sp, color = Color(0xFF94A3B8))
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(notifications, key = { it.key }) { notif ->
                            NotificationRow(notif = notif, onClick = { onItemClick(notif) })
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notif: AppNotification, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (notif.read) Color.Transparent else Color(0xFF4F46E5).copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // অপঠিত হলে বাম পাশে একটা ছোট নীল ডট
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(if (notif.read) Color.Transparent else Color(0xFF4F46E5))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                notif.title,
                fontFamily = NotoSansBengali,
                fontWeight = if (notif.read) FontWeight.Medium else FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF1E293B)
            )
            if (notif.body.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    notif.body,
                    fontFamily = NotoSansBengali, fontSize = 12.sp,
                    color = Color(0xFF64748B), maxLines = 3
                )
            }
            if (notif.time > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    relativeTime(notif.time),
                    fontFamily = NotoSansBengali, fontSize = 10.sp, color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

/** "৩ ঘণ্টা আগে" স্টাইলে আপেক্ষিক সময় — নাহলে তারিখ দেখায় */
private fun relativeTime(timeMs: Long): String {
    val diff = System.currentTimeMillis() - timeMs
    val minutes = diff / 60000
    return when {
        minutes < 1  -> "এইমাত্র"
        minutes < 60 -> "$minutes মিনিট আগে"
        minutes < 24 * 60 -> "${minutes / 60} ঘণ্টা আগে"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} দিন আগে"
        else -> SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(timeMs))
    }
}
