package com.hanif.smartstudy.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.ui.theme.NotoSansBengali

// ═══════════════════════════════════════════════════════════════════
//  FocusNudgeSheet — Part ৩
// ───────────────────────────────────────────────────────────────────
//  ফোকাস মোড চালু থাকা অবস্থায় Home / চ্যালেঞ্জ / Menu ট্যাপ করলে সরাসরি
//  সেখানে না গিয়ে এই ছোট শিটটা দেখানো হয়। Study/QBank/Quiz/Wrong Review/
//  Model Test — এগুলোতে এই শিট কখনোই আটকায় না (কলার সাইটেই সেটা নিশ্চিত
//  করা হয়, এই composable শুধু UI)।
//
//  সম্পূর্ণ self-contained — কোনো state নিজে রাখে না, শুধু callback।
// ═══════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusNudgeSheet(
    subject   : String,
    onResume  : () -> Unit,   // "পড়ায় ফিরে যাই"
    onTurnOff : () -> Unit,   // "ফোকাস মোড বন্ধ করো"
    onDismiss : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isTyping   = subject == com.hanif.smartstudy.focus.FocusModeConfig.TYPING_FOCUS_SUBJECT
    val topLabel   = if (isTyping) "এখন প্র্যাকটিসের সময়" else "এখন পড়ার সময়"
    val finishVerb = if (isTyping) "শেষ করো আগে" else "শেষ করো আগে"
    val resumeIcon = if (isTyping) "⌨️" else "📖"
    val resumeVerb = if (isTyping) "প্র্যাকটিসে ফিরে যাই" else "পড়ায় ফিরে যাই"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⏳", fontSize = 40.sp)

            Text(
                topLabel,
                fontSize   = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = NotoSansBengali,
                textAlign  = TextAlign.Center
            )

            Text(
                "$subject $finishVerb",
                fontSize   = 13.sp,
                fontFamily = NotoSansBengali,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = onResume,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text("$resumeIcon $resumeVerb", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, fontFamily = NotoSansBengali)
            }

            OutlinedButton(
                onClick  = onTurnOff,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Text("🔴 ফোকাস মোড বন্ধ করো", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    fontFamily = NotoSansBengali)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
