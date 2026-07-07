package com.hanif.smartstudy.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════
//  FocusModePanel — Menu > Admin সেকশনে ফোকাস মোডের স্ট্যাটাস প্যানেল (Part ৫)
// ───────────────────────────────────────────────────────────────────
//  দেখায়: মোড চালু/বন্ধ, কোন সাবজেক্ট, কবে অটো বন্ধ হবে — আর একটা
//  "🔴 এখনই বন্ধ করো" বাটন (এক ট্যাপে সব রিসেট, background reminder সহ)।
//  সম্পূর্ণ self-contained — নিজের DataStore state নিজে collect করে,
//  তাই MenuScreen.kt-এ শুধু একটা লাইনের hook বসালেই চলে।
// ═══════════════════════════════════════════════════════════════════

private val FocusPrimary = Color(0xFF4F46E5)

@Composable
fun FocusModePanel() {
    if (!FocusModeConfig.ENABLED) return

    val context = LocalContext.current
    val store   = remember { FocusModeStore(context) }
    val scope   = rememberCoroutineScope()

    val focusState by store.stateFlow.collectAsState(initial = FocusModeState())
    val active = focusState.isEffectivelyActive()

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (active) FocusPrimary.copy(alpha = 0.10f)
                             else MaterialTheme.colorScheme.surface
        ),
        border   = BorderStroke(1.2.dp, FocusPrimary.copy(alpha = if (active) 0.5f else 0.25f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "ফোকাস মোড",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = NotoSansBengali,
                    color      = MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.weight(1f)
                )
                Text(
                    if (active) "চালু ✓" else "বন্ধ",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NotoSansBengali,
                    color      = if (active) FocusPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (active) {
                Text(
                    "সাবজেক্ট: ${focusState.subject}",
                    fontSize   = 12.sp,
                    fontFamily = NotoSansBengali,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
                focusState.autoOffDateMillis()?.let { millis ->
                    Text(
                        "অটো বন্ধ হবে: ${dateFmt.format(millis)}",
                        fontSize   = 12.sp,
                        fontFamily = NotoSansBengali,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "এখন কোনো সাবজেক্টে ফোকাস মোড চালু নেই। Study ট্যাবের \"🎯 আজ ফোকাস\" কার্ড থেকে চালু করা যাবে।",
                    fontSize   = 12.sp,
                    fontFamily = NotoSansBengali,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (focusState.enabled) {
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick  = { scope.launch { store.deactivate() } },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("🔴 এখনই বন্ধ করো", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, fontFamily = NotoSansBengali)
                }
            }
        }
    }
}
