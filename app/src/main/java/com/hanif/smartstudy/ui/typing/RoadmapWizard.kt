package com.hanif.smartstudy.ui.typing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.RoadmapPlan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Phase ৩: Roadmap Wizard — Neonlipi-এর ৫-ধাপ প্রশ্নমালার সমতুল্য। প্রশ্নের উত্তর
 * থেকে একটা মোটামুটি (heuristic) সময়সীমা বের করে, TypingPracticeScreen-এ
 * "তোমার Roadmap" কার্ড হিসেবে দেখানো হয়। এটা কোনো নতুন নেভিগেশন রুট লাগে না —
 * পুরোটাই একটা full-screen Dialog হিসেবে, তাই MainScreen-এর NavHost-এ হাত দিতে হয়নি।
 */

/** মোটামুটি হিউরিস্টিক — এটা কোনো নির্ভুল বিজ্ঞান না, শুধু ব্যবহারকারীকে একটা বাস্তবসম্মত
 *  লক্ষ্য-তারিখ দেওয়ার জন্য। টার্গেট WPM যত বেশি, তত বেশি অনুশীলন-সময় দরকার; আগে থেকে
 *  কিছুটা অভিজ্ঞতা থাকলে কম সময় লাগবে; একাধিক ট্র্যাক (বাংলা+ইংরেজি) একসাথে শিখলে বেশি। */
private fun estimateTotalMinutesNeeded(plan: RoadmapPlan): Double {
    val baseMinutesPerWpm = 15.0
    val experienceMultiplier = if (plan.experience == "some") 0.7 else 1.0
    val trackMultiplier = if (plan.tracks.size > 1) 1.6 else 1.0
    return plan.targetWpm * baseMinutesPerWpm * experienceMultiplier * trackMultiplier
}

private fun estimateCompletionMillis(plan: RoadmapPlan): Long {
    val totalMinutes = estimateTotalMinutesNeeded(plan)
    val days = ceil(totalMinutes / plan.dailyMinutes.coerceAtLeast(5)).toLong().coerceAtLeast(1)
    return System.currentTimeMillis() + days * 24L * 60 * 60 * 1000
}

private val dateFmt = SimpleDateFormat("d MMMM, yyyy", Locale("bn"))

@Composable
fun RoadmapWizardDialog(onDismiss: () -> Unit, onComplete: (RoadmapPlan) -> Unit) {
    var step by remember { mutableStateOf(1) }
    var tracks by remember { mutableStateOf(setOf("bn")) }
    var experience by remember { mutableStateOf("new") }
    var targetWpm by remember { mutableStateOf(20) }
    var planMode by remember { mutableStateOf("daily") }   // "daily" | "deadline"
    var dailyMinutes by remember { mutableStateOf(30) }
    var deadlineDays by remember { mutableStateOf(30) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ধাপ $step/৫", fontSize = 12.sp, fontFamily = NotoSansBengali,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("✕") }
                }
                Spacer(Modifier.height(12.dp))

                when (step) {
                    1 -> {
                        Text("কোনটা আগে শিখতে চাও?", fontSize = 16.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold)
                        Text("একাধিক বেছে নিতে পারো।", fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        listOf("bn" to "বাংলা (Bijoy)", "en" to "ইংরেজি").forEach { (value, label) ->
                            val selected = value in tracks
                            OptionRow(label, selected) {
                                tracks = if (selected) tracks - value else tracks + value
                            }
                        }
                    }
                    2 -> {
                        Text("তোমার অভিজ্ঞতা কেমন?", fontSize = 16.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        OptionRow("একদম নতুন — কখনো টাইপ করিনি বা সবে শুরু করেছি", experience == "new") { experience = "new" }
                        OptionRow("কিছুটা অভিজ্ঞতা আছে — আগে কিছুটা প্র্যাকটিস করেছি", experience == "some") { experience = "some" }
                    }
                    3 -> {
                        Text("টার্গেট স্পিড কত চাও?", fontSize = 16.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        OptionRow("সরকারি চাকরি (প্রায় ২০-৩০ WPM)", targetWpm == 25) { targetWpm = 25 }
                        OptionRow("ফ্ল্যাগশিপ ডাটা এন্ট্রি (৩৫+ WPM)", targetWpm == 35) { targetWpm = 35 }
                        OptionRow("ব্যক্তিগত/সাধারণ (২০ WPM)", targetWpm == 20) { targetWpm = 20 }
                        Spacer(Modifier.height(8.dp))
                        Text("নিজে ঠিক করো: $targetWpm WPM", fontSize = 12.sp, fontFamily = NotoSansBengali)
                        Slider(
                            value = targetWpm.toFloat(),
                            onValueChange = { targetWpm = it.toInt() },
                            valueRange = 10f..100f, steps = 17
                        )
                    }
                    4 -> {
                        Text("কীভাবে প্ল্যান করব?", fontSize = 16.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold)
                        Text("তারিখ ঠিক করো, নাকি প্রতিদিনের সময়।", fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("deadline" to "Deadline", "daily" to "Daily Time").forEach { (value, label) ->
                                val selected = planMode == value
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = { planMode = value }
                                ) {
                                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (planMode == "daily") {
                            Text("দৈনিক সময় দাও, আমি তারিখ বের করব", fontSize = 12.sp, fontFamily = NotoSansBengali)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30, 60, 90).forEach { m ->
                                    OptionChip("$m মিনিট", dailyMinutes == m) { dailyMinutes = m }
                                }
                            }
                        } else {
                            Text("কত দিনের মধ্যে শেষ করতে চাও?", fontSize = 12.sp, fontFamily = NotoSansBengali)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30, 60, 90).forEach { d ->
                                    OptionChip("$d দিন", deadlineDays == d) { deadlineDays = d }
                                }
                            }
                        }
                    }
                    5 -> {
                        val plan = RoadmapPlan(
                            tracks = tracks.toList().ifEmpty { listOf("bn") },
                            experience = experience,
                            targetWpm = targetWpm,
                            planMode = planMode,
                            dailyMinutes = if (planMode == "daily") dailyMinutes else run {
                                val totalMin = estimateTotalMinutesNeeded(
                                    RoadmapPlan(tracks = tracks.toList(), experience = experience, targetWpm = targetWpm)
                                )
                                ceil(totalMin / deadlineDays.coerceAtLeast(1)).toInt().coerceAtLeast(5)
                            },
                            deadlineMillis = if (planMode == "deadline")
                                System.currentTimeMillis() + deadlineDays * 24L * 60 * 60 * 1000 else 0L,
                            createdAt = System.currentTimeMillis()
                        )
                        val finalPlan = plan.copy(estimatedDoneMillis = estimateCompletionMillis(plan))
                        Text("তোমার Roadmap তৈরি", fontSize = 16.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Card(
                            Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "দৈনিক ${finalPlan.dailyMinutes} মিনিট দিলে তুমি আনুমানিক ${dateFmt.format(Date(finalPlan.estimatedDoneMillis))}-এর মধ্যে শেষ করবে।",
                                    fontSize = 13.sp, fontFamily = NotoSansBengali
                                )
                                Text(
                                    "ট্র্যাক: ${finalPlan.tracks.joinToString(", ") { if (it == "bn") "বাংলা (Bijoy)" else "ইংরেজি" }}",
                                    fontSize = 11.sp, fontFamily = NotoSansBengali,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "টার্গেট WPM: ${finalPlan.targetWpm}",
                                    fontSize = 11.sp, fontFamily = NotoSansBengali,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "প্রাথমিক অনুমান — তোমার প্র্যাকটিস শুরু হলে এটা আরও নির্ভুল হবে।",
                            fontSize = 10.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onComplete(finalPlan) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Roadmap শুরু করো", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                        }
                        return@Column
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { if (step > 1) step-- }, enabled = step > 1) {
                        Text("← আগে")
                    }
                    Button(onClick = { if (step < 5) step++ }) {
                        Text("পরের ধাপ →")
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, sub: String? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 13.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, fontSize = 10.sp, fontFamily = NotoSansBengali,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Text(label, fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

/** পুরো prepare-session স্ক্রিনের উপরের দিকে সবসময় দেখানোর জন্য — বর্তমান Roadmap-এর
 *  সংক্ষিপ্তসার + "on track" স্ট্যাটাস। plan থাকলেই দেখায়, না থাকলে null-check caller-এর দায়িত্ব। */
@Composable
fun RoadmapSummaryCard(plan: RoadmapPlan, onRebuild: () -> Unit) {
    val now = System.currentTimeMillis()
    val daysLeft = ((plan.estimatedDoneMillis - now) / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
    val isOverdue = now > plan.estimatedDoneMillis

    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🗺️ তোমার Roadmap", fontSize = 13.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isOverdue) Color(0xFFB91C1C).copy(alpha = 0.15f) else Color(0xFF15803D).copy(alpha = 0.15f)
                ) {
                    Text(
                        if (isOverdue) "পিছিয়ে আছো" else "on track",
                        fontSize = 10.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        color = if (isOverdue) Color(0xFFB91C1C) else Color(0xFF15803D),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                "লক্ষ্য তারিখ: ${dateFmt.format(Date(plan.estimatedDoneMillis))} ($daysLeft দিন বাকি)",
                fontSize = 12.sp, fontFamily = NotoSansBengali
            )
            Text(
                "দৈনিক ${plan.dailyMinutes} মিনিট · টার্গেট ${plan.targetWpm} WPM · ${plan.tracks.joinToString("+") { if (it == "bn") "বাংলা" else "ইংরেজি" }}",
                fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onRebuild, modifier = Modifier.align(Alignment.End)) {
                Text("Roadmap আবার বানাও", fontSize = 11.sp, fontFamily = NotoSansBengali)
            }
        }
    }
}
