package com.hanif.smartstudy.ui.menu

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.viewmodel.MenuUiState
import com.hanif.smartstudy.viewmodel.MenuViewModel

// ─────────────────────────────────────────────────────────────
//  StatsScreen — Total stats + XP bar chart
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state  : MenuUiState,
    vm     : MenuViewModel,
    onBack : () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📈 পরিসংখ্যান", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
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

            // ── Accuracy ring ──
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    AccuracyRing(pct = state.accuracyPct)
                    Column {
                        Text("মোট উত্তর", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontFamily = NotoSansBengali)
                        Text("${state.correctCount + state.wrongCount}টি", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatBadge("✅ সঠিক", "${state.correctCount}", Green500)
                            StatBadge("❌ ভুল",  "${state.wrongCount}",  Red500)
                        }
                    }
                }
            }

            // ── Study time ──
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⏱ পড়ার সময়", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TimeStatCol("পড়াশোনা", "${state.totalStudyMin} মিনিট", Indigo600)
                        TimeStatCol("অ্যাপ সময়", "${state.totalAppMin} মিনিট", Teal600)
                        TimeStatCol("XP মোট", "${state.user?.xp ?: 0}", Amber500)
                    }
                }
            }

            // ── XP History bar chart ──
            if (state.xpHistory.isNotEmpty()) {
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("⭐ XP ইতিহাস (শেষ ৩০ দিন)", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                        Spacer(Modifier.height(12.dp))
                        XpBarChart(history = state.xpHistory)
                    }
                }
            }
        }
    }
}

// ── Accuracy ring ──────────────────────────────────────────────

@Composable
private fun AccuracyRing(pct: Int) {
    val animPct by androidx.compose.animation.core.animateFloatAsState(
        pct / 100f, androidx.compose.animation.core.tween(1000), label = "acc"
    )
    val color = when { pct >= 70 -> Green500; pct >= 40 -> Amber500; else -> Red500 }

    Box(Modifier.size(90.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress     = { animPct },
            modifier     = Modifier.fillMaxSize(),
            color        = color,
            trackColor   = color.copy(0.12f),
            strokeWidth  = 9.dp,
            strokeCap    = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color, fontFamily = NotoSansBengali)
            Text("accuracy", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), fontFamily = NotoSansBengali)
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = NotoSansBengali)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontFamily = NotoSansBengali)
    }
}

@Composable
private fun TimeStatCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = NotoSansBengali)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontFamily = NotoSansBengali)
    }
}

// ── Simple Compose bar chart for XP ───────────────────────────

@Composable
private fun XpBarChart(history: List<Pair<String, Int>>) {
    val maxXp = history.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val last14 = history.takeLast(14)

    Row(
        Modifier
            .fillMaxWidth()
            .height(100.dp),
        verticalAlignment    = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        last14.forEach { (date, xp) ->
            val fraction = xp.toFloat() / maxXp
            val animH by androidx.compose.animation.core.animateFloatAsState(fraction, label = "bar_$date")
            val dayLabel = date.split("-").lastOrNull() ?: ""

            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (xp > 0) {
                    Text("$xp", fontSize = 7.sp, color = Indigo600, fontFamily = NotoSansBengali)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animH.coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            if (xp > 0) Indigo600 else MaterialTheme.colorScheme.outline.copy(0.2f)
                        )
                )
                Text(dayLabel, fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), fontFamily = NotoSansBengali)
            }
        }
    }
}
