package com.hanif.smartstudy.ui.quiz

// ─────────────────────────────────────────────────────────────────────────────
// Phase 3 v2 — Study internal tabs (NEW FILE, drop-in, no existing file changed)
//   Study ট্যাবের ভেতরে শুধু: বিষয় | কোর্স | ভিডিও
//   আলাদা নিচের (bottom) ট্যাব নয় — MainScreen-এর BottomTab অপরিবর্তিত থাকে।
//
// User decisions applied in v2 (2026-11-04):
//   • ভিডিও: ফেসবুক পেজ — ব্যবহারকারীর দেওয়া share লিংক হুবহু ব্যবহার করা
//     হয়েছে, পেজের নাম বলা হয়নি বলে বাটনের লেবেল সাধারণ ("ফেসবুকে দেখুন")।
//     ভিডিও শুধু দেখা যাবে (ACTION_VIEW) — ডাউনলোড দেওয়া হবে না, অ্যাপের
//     ভেতরে প্লেয়ার নেই, ফেসবুকের ভেতরে অগ্রগতি ট্র্যাক করা হয় না।
//   • কোর্সের ডেটা: "Study database" শিটে থাকবে, অ্যাডমিন অ্যাপ দিয়ে লেখা
//     হবে। সেই শিট-ট্যাব / ডেটা এখনো নেই, তাই কোর্স ট্যাব সত্যিকারের
//     খালি-স্টেট দেখায় — কোনো কোর্স, দাম বা টাইটেল বানানো হয়নি।
//   • "কেনা" চিপ: Phase 4 (ম্যানুয়াল পেমেন্ট + entitlement) পর্যন্ত HIDDEN —
//     v2-তে কোর্স ট্যাবে কোনো কেনা ফিল্টার নেই।
//   • নোট ট্যাব: বাদ দেওয়া হয়েছে (user decision) — StudyTab-এ NOTES নেই,
//     StudyNotesTab ফাংশনও নেই। টপিকের ভেতরের Quick Notes অপরিবর্তিত থাকে।
//   • শিক্ষক: Coming Soon, শুধু ধূসর ডিসপ্লে আইটেম। AudienceProfile-এ
//     Teacher যোগ করা হয়নি, সাইনআপেও Teacher সক্রিয় নয়।
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.ui.theme.NotoSansBengali

/** Study ট্যাবের ভেতরের ট্যাব — ক্রম ঠিক এইভাবেই থাকবে, মাত্র ৩টি। */
enum class StudyTab(val label: String) {
    SUBJECTS("📘 বিষয়"),
    COURSES("🎓 কোর্স"),
    VIDEOS("🎬 ভিডিও")
}

/** ফেসবুক পেজ — ব্যবহারকারীর দেওয়া share লিংক, হুবহু, পেজের নাম অনুমান করা হয়নি। */
object StudyLinks {
    const val FACEBOOK_PAGE_URL: String = "https://www.facebook.com/share/1Gd5ZWWqzL/"
}

// ── Tab row ──────────────────────────────────────────────────────────────────

@Composable
fun StudyTabRow(
    selected: StudyTab,
    onSelect: (StudyTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StudyTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) Color(0xFF059669) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .clickable { onSelect(tab) }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.label,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontFamily = NotoSansBengali,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Course tab — NO কেনা chip in v2 (hidden until Phase 4) ────────────────
// কোর্সের ডেটা সোর্স: Study database শিট, অ্যাডমিন অ্যাপ দিয়ে লেখা হবে।
// সেই শিট-ট্যাব এখনো নেই, তাই এখানে কোনো কোর্স-তালিকা হার্ডকোড করা হয়নি।

@Composable
fun StudyCourseTab(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StudyEmptyState("কোনো কোর্স এখনো যোগ হয়নি। কোর্স শিটে যোগ হলে এখানে কোর্সের তালিকা আসবে।")
        // ── Teacher: Coming Soon — শুধু ধূসর ডিসপ্লে আইটেম, ক্লিক করা যায় না। ──
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().alpha(0.6f)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "👩‍🏫 শিক্ষক",
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    fontFamily = NotoSansBengali
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "শীঘ্রই আসছে — এই অডিয়েন্সের কোর্স এখনো তৈরি হয়নি।",
                    fontSize = 12.5.sp, fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Video tab — Facebook page, view-only ────────────────────────────────────

@Composable
fun StudyVideoTab(
    facebookPageUrl: String = StudyLinks.FACEBOOK_PAGE_URL,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "ভিডিও ক্লাস ফেসবুক পেজে দেখা যাবে।",
            fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali
        )
        Text(
            "ফেসবুকে দেখুন — ভিডিও ডাউনলোড দেওয়া হয় না।",
            fontSize = 13.sp, fontFamily = NotoSansBengali,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = {
                // View-only: বাইরের ফেসবুক অ্যাপ/ব্রাউজারে খুলবে, অ্যাপে ডাউনলোড নয়।
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(facebookPageUrl))
                ctx.startActivity(intent)
            }
        ) {
            Text("ফেসবুকে দেখুন", fontFamily = NotoSansBengali)
        }
    }
}

// ── Shared empty-state helper ────────────────────────────────────────────────

@Composable
fun StudyEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(14.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 13.sp, fontFamily = NotoSansBengali,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
