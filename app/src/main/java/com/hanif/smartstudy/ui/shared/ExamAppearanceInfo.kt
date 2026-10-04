package com.hanif.smartstudy.ui.shared

import androidx.compose.foundation.clickable
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
// শুধু পড়ে (read-only) — কোনো ডেটা লেখে না, ডাটাবেস স্কিমা বদলায় না।

private data class AppearanceLine(val institution: String, val post: String, val year: String)

// post/institution নামের ম্যাপ প্রতি কার্ডে আবার না আনতে একবার লোড করে রাখা হয় (খালি হলে ক্যাশ হয় না)
private object AppearanceNameCache {
    @Volatile var posts: Map<String, String> = emptyMap()
    @Volatile var institutions: Map<String, String> = emptyMap()
}

private fun bnToLatin(s: String): String = buildString {
    for (c in s) append(if (c in '০'..'৯') ('0' + (c - '০')) else c)
}

@Composable
fun ExamAppearanceInfo(questionId: String, modifier: Modifier = Modifier) {
    if (questionId.isBlank()) return
    val context = LocalContext.current
    var lines by remember(questionId) { mutableStateOf<List<AppearanceLine>>(emptyList()) }
    var expanded by remember(questionId) { mutableStateOf(false) }

    LaunchedEffect(questionId) {
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
                rows.sortedByDescending { bnToLatin(it.year).toIntOrNull() ?: 0 }
                    .map {
                        AppearanceLine(
                            institution = AppearanceNameCache.institutions[it.institutionId] ?: it.institutionId,
                            post        = AppearanceNameCache.posts[it.postId] ?: it.postId,
                            year        = it.year
                        )
                    }
            }
        } catch (e: Exception) {
            emptyList()   // কোনো সমস্যায় কার্ড ভাঙবে না — শুধু তালিকা দেখাবে না
        }
    }

    if (lines.isEmpty()) return

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Text(
            text = (if (expanded) "▾ " else "▸ ") + "📅 এই প্রশ্ন পরীক্ষায় এসেছে (${lines.size})",
            fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp)
        )
        if (expanded) {
            lines.forEach { l ->
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
}
