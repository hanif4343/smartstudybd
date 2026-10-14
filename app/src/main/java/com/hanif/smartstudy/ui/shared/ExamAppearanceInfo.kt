package com.hanif.smartstudy.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Phase 6: "এই প্রশ্নটি কোন কোন পরীক্ষায় এসেছে" ──
// একই প্রশ্ন ডুপ্লিকেট না করে Exam_Appearances (Room: exam_appearances) থেকে প্রশ্নের আইডি
// ধরে পরীক্ষার তালিকা দেখায়। কোনো appearance না থাকলে কিছুই দেখায় না (UI খালি থাকে)।
//
// ── নতুন নিয়ম (চিপে Post + Year): ইউজার এখন যে প্রশ্নপত্রে (Post/Institution/Year) আছে,
// প্রশ্নের সেই appearance বাদ দিয়ে বাকিগুলো "Post — Year" চিপে দেখানো হয়। যেমন ৩৫তম বিসিএস
// প্রশ্নপত্রে থাকলে ৪৫তমে আসা প্রশ্নে শুধু "৪৫তম বিসিএস" দেখাবে (৩৫তমের কথা না — ইউজার
// নিজেই ওখানে), আর ৪৫তমের প্রশ্নপত্রে সেই প্রশ্নেই দেখাবে "৩৫তম বিসিএস"। বর্তমান প্রশ্নপত্র
// জানা না থাকলে (সাবজেক্ট-ভিত্তিক QBank) আগের মতো ২+ appearance হলে সবগুলো দেখায়। ──
// শুধু পড়ে (read-only) — কোনো ডেটা লেখে না, ডাটাবেস স্কিমা বদলায় না।

/** ইউজার এখন QBank-এর কোন প্রশ্নপত্রে আছে — CoreScreen থেকে provide করা হয় */
class QBankAppearanceCtx(val post: String? = null, val institution: String? = null, val year: String? = null)

val LocalQBankAppearanceCtx = compositionLocalOf<QBankAppearanceCtx?> { null }

private data class AppearanceLine(val institution: String, val post: String, val year: String, val isCurrent: Boolean)

// post/institution নামের ম্যাপ প্রতি কার্ডে আবার না আনতে একবার লোড করে রাখা হয় (খালি হলে ক্যাশ হয় না)
private object AppearanceNameCache {
    @Volatile var posts: Map<String, String> = emptyMap()
    @Volatile var institutions: Map<String, String> = emptyMap()
}

private fun bnToLatin(s: String): String = buildString {
    for (c in s) append(if (c in '\u09E6'..'\u09EF') ('0' + (c - '\u09E6')) else c)
}

/** সাল ডাটাবেসে যেভাবেই থাকুক — UI-তে সবসময় বাংলা অঙ্কে (দেখো util/YearFormat) */
private fun cleanYear(s: String): String = com.hanif.smartstudy.util.YearFormat.bn(s)

private fun norm(s: String?): String = (s ?: "").trim().lowercase()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamAppearanceInfo(questionId: String, modifier: Modifier = Modifier) {
    if (questionId.isBlank()) return
    val context = LocalContext.current
    // "পদবী, সাল" চিপের নিয়ম শুধু "প্রাথমিক" ও "ব্যাংক" ক্যাটাগরিতে; বাকি চিপে আগের আচরণ
    // (সাল যেমন ডাটাবেসে আছে — বাংলা বা ইংরেজি — তেমনই দেখানো হয়)
    val isPrimary = com.hanif.smartstudy.util.QBankCategory.selected.let { it == "প্রাথমিক" || it == "ব্যাংক" }
    val cur = if (isPrimary) LocalQBankAppearanceCtx.current else null
    var expanded by remember(questionId) { mutableStateOf(false) }
    val curPost = cur?.post; val curInst = cur?.institution; val curYear = cur?.year
    var lines by remember(questionId, curPost, curInst, curYear) { mutableStateOf<List<AppearanceLine>>(emptyList()) }

    LaunchedEffect(questionId, curPost, curInst, curYear) {
        lines = try {
            withContext(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(context).referenceDao()
                val rows = dao.getAppearancesForQuestion(questionId)
                if (rows.isEmpty()) return@withContext emptyList<AppearanceLine>()
                if (AppearanceNameCache.posts.isEmpty()) {
                    AppearanceNameCache.posts = dao.getAllPosts().associate { it.postId to it.name }
                }
                if (AppearanceNameCache.institutions.isEmpty()) {
                    AppearanceNameCache.institutions = dao.getAllInstitutions().associate { it.institutionId to it.name }
                }
                rows.sortedByDescending { com.hanif.smartstudy.util.YearFormat.sortKey(it.year) }
                    .map {
                        val instName = AppearanceNameCache.institutions[it.institutionId] ?: it.institutionId
                        val postName = AppearanceNameCache.posts[it.postId] ?: it.postId
                        val isCurrent = when {
                            // সাল-মোড: এই সালের appearance-ই বর্তমান
                            !curYear.isNullOrBlank() -> com.hanif.smartstudy.util.YearFormat.latin(it.year) == com.hanif.smartstudy.util.YearFormat.latin(curYear)
                            // পদবী/প্রতিষ্ঠান-মোড: Post (+ Institution) মিললে বর্তমান
                            !curPost.isNullOrBlank() -> {
                                val postOk = norm(postName) == norm(curPost) || norm("$postName, $instName") == norm(curPost)
                                val instOk = curInst.isNullOrBlank() || norm(instName) == norm(curInst) || norm("$postName, $instName") == norm(curPost)
                                postOk && instOk
                            }
                            else -> false
                        }
                        AppearanceLine(instName, postName, cleanYear(it.year), isCurrent)
                    }
            }
        } catch (e: Exception) {
            emptyList()   // কোনো সমস্যায় কার্ড ভাঙবে না — শুধু তালিকা দেখাবে না
        }
    }

    val hasCtx = !curPost.isNullOrBlank() || !curYear.isNullOrBlank()
    // বর্তমান প্রশ্নপত্র জানা থাকলে: সেটা বাদ দিয়ে বাকি (অন্তত ১টা) দেখাও।
    // না জানা থাকলে আগের মতো — ২+ appearance হলেই সবগুলো।
    val shown = if (hasCtx) lines.filter { !it.isCurrent } else lines
    if (shown.isEmpty() || (!hasCtx && shown.size < 2)) return
    if (!isPrimary) {
        // ── আগের আচরণ (বিসিএস/নিবন্ধন/ব্যাংক/১৬-২০ গ্রেড): ২+ হলে ট্যাপ করে খোলা তালিকা ──
        Column(modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                text = (if (expanded) "▾ " else "▸ ") + "\uD83D\uDCC5 এই প্রশ্ন পরীক্ষায় এসেছে (${shown.size})",
                fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                    .clickable { expanded = !expanded }.padding(vertical = 8.dp)
            )
            if (expanded) {
                shown.forEach { l ->
                    val parts = listOf(l.institution, l.post, l.year).filter { it.isNotBlank() }
                    Text(
                        text = "• " + parts.joinToString(" — "),
                        fontFamily = NotoSansBengali, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                    )
                }
            }
        }
        return
    }
    val showInst = false
    val isPrimaryCat = com.hanif.smartstudy.util.QBankCategory.selected == "প্রাথমিক"

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(
            text = if (hasCtx) "\uD83D\uDCC5 এই প্রশ্ন আরও এসেছে" else "\uD83D\uDCC5 এই প্রশ্ন পরীক্ষায় এসেছে (${shown.size})",
            fontFamily = NotoSansBengali, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // প্রাথমিক: "সহকারী শিক্ষক (পদ্মা), ২০২৪" · ব্যাংক: "সিনিয়র অফিসার, ২০২৪"
            // (প্রতিষ্ঠান বাদ; একই পদবী+সাল একাধিক প্রতিষ্ঠান থেকে এলে একবারই দেখায়)
            shown.map { l ->
                // প্রাথমিক: "সহকারী শিক্ষক (পদ্মা), ২০২৪" · ব্যাংক: "সিনিয়র অফিসার, ২০২৪"
                val postLabel = if (isPrimaryCat && l.institution.isNotBlank() &&
                    !l.post.contains(l.institution) && !l.post.contains("(")
                ) "${l.post} (${l.institution})" else l.post
                listOf(postLabel, l.year).filter { it.isNotBlank() }.joinToString(", ")
            }.distinct().forEach { chipText ->
                Text(
                    text = chipText,
                    fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
