package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.QBankCategory
import com.hanif.smartstudy.util.QBankModelTestEngine as Engine

private val MtGreen = Color(0xFF059669)
private val MtBlue  = Color(0xFF3157D5)

private fun bn(n: Int): String =
    n.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

/**
 * QBank → "🏆 মডেল টেস্ট": ক্যাটাগরি (বিসিএস/প্রাথমিক/…) → প্রশ্ন সংখ্যা (৫০/১০০/১৫০/২০০) →
 * preview (৬ বিষয়ে সমান ভাগ + কেন বদলেছে তার রিপোর্ট) → শুরু। রেজাল্ট হিস্ট্রিতে সেভ হয়।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QBankModelTestScreen(
    category   : String,
    count      : Int,
    isLoading  : Boolean,
    error      : String?,
    plan       : Engine.Plan?,
    // ── লিখিত মডেল টেস্ট (MCQ | লিখিত ট্যাব): ডিফল্ট থাকায় পুরনো কল ভাঙে না ──
    kind       : Int = 0,
    wPlan      : com.hanif.smartstudy.util.WrittenModelTestEngine.Plan? = null,
    wCount     : Int = 40,
    onKind     : (Int) -> Unit = {},
    onWCount   : (Int) -> Unit = {},
    onCategory : (String) -> Unit,
    onCount    : (Int) -> Unit,
    onRegenerate: () -> Unit,
    onStart    : () -> Unit,
    onBack     : () -> Unit
) {
    // বর্তমান ট্যাবের প্রশ্ন-তালিকা (শুরু বাটনের জন্য)
    val curQs = if (kind == 1) wPlan?.questions else plan?.questions
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏆 মডেল টেস্ট", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRegenerate, enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    ) { Text("🔄 আবার বানান", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Button(
                        onClick = onStart,
                        enabled = !isLoading && !curQs.isNullOrEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MtGreen),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text(
                            "▶ শুরু করুন" + (curQs?.size?.let { " (${bn(it)})" } ?: ""),
                            fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── ১. পরীক্ষা ──
            // ── MCQ | লিখিত ট্যাব ──
            MtKindTabs(kind, onKind)
            if (kind == 1) {
                WrittenMtContent(wCount, isLoading, error, wPlan, onWCount)
            } else {
            SectionLabel("১. পরীক্ষা বেছে নিন")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QBankCategory.ALL.forEach { cat ->
                    Chip(cat, cat == category) { onCategory(cat) }
                }
            }

            // ── ২. প্রশ্ন সংখ্যা ──
            SectionLabel("২. প্রশ্ন সংখ্যা")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Engine.COUNT_OPTIONS.forEach { n ->
                    Chip(bn(n), n == count) { onCount(n) }
                }
            }

            // ── ৩. Preview ──
            SectionLabel("৩. টেস্টের বিন্যাস")
            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MtGreen)
                            Spacer(Modifier.height(10.dp))
                            Text("প্রশ্ন সাজানো হচ্ছে…", fontFamily = NotoSansBengali, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                error != null -> {
                    Text("⚠️ $error", color = Color(0xFFE53935), fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                plan != null -> PlanPreview(plan)
            }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(t: String) =
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
        color = MaterialTheme.colorScheme.onSurface)

@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(if (active) MtBlue else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (active) MtBlue else Color(0xFFE0E4ED), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PlanPreview(plan: Engine.Plan) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MtGreen.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${plan.category} মডেল টেস্ট — ${bn(plan.questions.size)} প্রশ্ন",
                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, color = MtGreen
            )
            Text(
                "পুলে মোট ${bn(plan.poolSize)} টি প্রশ্ন, যোগ্য ${bn(plan.eligible)} টি · একই topic থেকে সর্বোচ্চ ${bn(Engine.TOPIC_CAP)}টি",
                fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            plan.stats.forEach { st ->
                val frac = if (plan.questions.isNotEmpty()) st.actual.toFloat() / plan.questions.size else 0f
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${st.bucket.emoji} ${st.bucket.label}", fontSize = 13.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${bn(st.actual)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                            fontFamily = NotoSansBengali,
                            color = if (st.actual != st.quota) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurface)
                        Text("  · ${bn(st.topics)} টপিক", fontSize = 10.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { frac.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = MtGreen, trackColor = MtGreen.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }

    if (plan.notes.isNotEmpty()) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                plan.notes.forEach {
                    Text(it, fontSize = 11.5.sp, fontFamily = NotoSansBengali, color = Color(0xFF7A4B00), lineHeight = 16.sp)
                }
            }
        }
    }
    Text(
        "📜 টেস্ট শেষে রেজাল্ট ও রিভিউ আপনার হিস্ট্রিতে সেভ হবে। কখনো না-দেওয়া ও আগে ভুল-করা প্রশ্ন আগে আসে।",
        fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start
    )
}


@Composable
private fun MtKindTabs(kind: Int, onKind: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0 to "🔘 MCQ", 1 to "✍️ লিখিত").forEach { (k, label) ->
            val on = kind == k
            val accent = if (k == 1) Color(0xFF6D28D9) else MtBlue
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) accent else Color.Transparent)
                    .border(1.5.dp, if (on) accent else accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable { onKind(k) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
                    color = if (on) Color.White else accent)
            }
        }
    }
}

/** লিখিত মডেল টেস্ট: সব বিষয় মিলিয়ে একটাই পেপার — প্রতিটা সেকশনে ৫টা প্রশ্ন, আলাদা আলাদা উৎস-গ্রুপ থেকে */
@Composable
private fun WrittenMtContent(
    count: Int, isLoading: Boolean, error: String?,
    plan: com.hanif.smartstudy.util.WrittenModelTestEngine.Plan?, onCount: (Int) -> Unit
) {
    val purple = Color(0xFF6D28D9)
    SectionLabel("১. প্রশ্ন সংখ্যা (৫টা করে সেকশন)")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        com.hanif.smartstudy.util.WrittenModelTestEngine.SIZE_OPTIONS.forEach { n ->
            Chip("${bn(n)} (${bn(n / 5)} সেকশন)", n == count) { onCount(n) }
        }
    }
    SectionLabel("২. পেপারের প্রিভিউ")
    when {
        isLoading -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = purple)
                Spacer(Modifier.height(10.dp))
                Text("লিখিত প্রশ্ন সাজানো হচ্ছে…", fontFamily = NotoSansBengali, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error != null -> Text("⚠ $error", color = Color(0xFFE53935), fontFamily = NotoSansBengali,
            fontWeight = FontWeight.Bold, fontSize = 13.sp)
        plan != null && plan.questions.isEmpty() -> Text(
            "লিখিত প্রশ্ন পাওয়া যায়নি। ইন্টারনেট চালু আছে কিনা দেখে \"🔄 আবার বানান\" চাপুন।",
            fontFamily = NotoSansBengali, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        plan != null -> {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = purple.copy(alpha = 0.07f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("লিখিত মডেল টেস্ট · ${bn(plan.questions.size)}টি প্রশ্ন · ${bn(plan.sections.size)}টি সেকশন",
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, color = purple)
                    Text("যোগ্য লিখিত প্রশ্ন ${bn(plan.eligible)}টি · প্রতি সেকশনে প্রশ্ন আলাদা আলাদা গ্রুপ থেকে",
                        fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    plan.stats.forEach { st ->
                        Text("${st.subject} — ${bn(st.sections)} সেকশন · ${bn(st.questions)}টি প্রশ্ন",
                            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                        plan.sections.filter { it.subject == st.subject }.forEach { sec ->
                            Text("   • ${sec.title.trimEnd(':', ' ')}  (${bn(sec.questions.size)})",
                                fontSize = 12.sp, fontFamily = NotoSansBengali,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
                        }
                    }
                }
            }
            if (plan.notes.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        plan.notes.forEach {
                            Text(it, fontSize = 11.5.sp, fontFamily = NotoSansBengali, color = Color(0xFF7A4B00), lineHeight = 16.sp)
                        }
                    }
                }
            }
            Text("পেপারে উত্তর প্রথমে লুকানো থাকবে — নিচে ডানের বাটনে ট্যাপ করলে দেখা যাবে। প্রতিবার \"🔄 আবার বানান\" চাপলে নতুন র‍্যান্ডম পেপার হবে।",
                fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
