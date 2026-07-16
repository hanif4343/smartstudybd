package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.data.model.ModelTestMeta
import com.hanif.smartstudy.ui.theme.NotoSansBengali

private val ModelGreen = Color(0xFF059669)
private val ModelGreenDark = Color(0xFF065F46)

// ─────────────────────────────────────────────────────────
// Model Test — Subject Picker (Mock Test-এর মতো গ্লোবাল এন্ট্রি পয়েন্ট)
// Study/Quiz/QBank subject list-এর নিচে "🏆 মডেল টেস্ট" বাটনে ট্যাপ করলে এটা খোলে —
// শুধু সেই subject-গুলো দেখায় যেখানে অন্তত ১টা Model Test আছে (audience tag-ভিত্তিক
// ফিল্টারিং ইতিমধ্যে ViewModel-এ হয়ে গেছে, তাই এখানে যা আসে তাই দেখানো হয়)।
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelTestSubjectPickerScreen(
    subjects : List<Pair<String, Int>>,   // subject -> কতগুলো টেস্ট
    onSelect : (String) -> Unit,
    onBack   : () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("🏆 মডেল টেস্ট", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        fontFamily = NotoSansBengali)
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (subjects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("এখনো কোনো বিষয়ে মডেল টেস্ট যোগ করা হয়নি", fontFamily = NotoSansBengali,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "যে বিষয়ে মডেল টেস্ট দিতে চান সেটা বেছে নিন",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali, modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(subjects, key = { it.first }) { (subject, count) ->
                Card(
                    modifier  = Modifier.fillMaxWidth().clickable { onSelect(subject) },
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(ModelGreen, ModelGreenDark))),
                            contentAlignment = Alignment.Center
                        ) { Text("🏆", fontSize = 18.sp) }
                        Column(Modifier.weight(1f)) {
                            Text(subject, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                            Text("${count}টি মডেল টেস্ট", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                        }
                        Icon(Icons.Default.ArrowForwardIos, null, tint = ModelGreen, modifier = Modifier.size(14.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────
// Model Test — টেস্ট লিস্ট (1, 2, 3 ... N)
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelTestListScreen(
    subject      : String,
    tests        : List<ModelTestMeta>,
    warning      : String? = null,
    onSelect     : (ModelTestMeta) -> Unit,
    onGenerateNew: () -> Unit = {},
    onBack       : () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🏆 মডেল টেস্ট", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                            fontFamily = NotoSansBengali)
                        Text(subject, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (tests.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("এখনো কোনো মডেল টেস্ট বানানো হয়নি", fontFamily = NotoSansBengali,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onGenerateNew,
                        shape   = RoundedCornerShape(14.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = ModelGreen)
                    ) {
                        Text("+ নতুন মডেল টেস্ট বানান", fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedButton(
                    onClick  = onGenerateNew,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = ModelGreen),
                    border   = BorderStroke(1.2.dp, ModelGreen)
                ) {
                    Text("+ নতুন মডেল টেস্ট বানান", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
            if (warning != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF7ED)
                    ) {
                        Text(warning, fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = Color(0xFF9A3412), modifier = Modifier.padding(10.dp))
                    }
                }
            }
            item {
                Text(
                    "প্রতিটা টেস্ট পূর্ণমান — শুরু করলে পুরো সেট একসাথে দিতে হবে",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali, modifier = Modifier.padding(bottom = 4.dp, top = 4.dp)
                )
            }
            items(tests) { test ->
                ModelTestCard(test = test, onClick = { onSelect(test) })
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun ModelTestCard(test: ModelTestMeta, onClick: () -> Unit) {
    val typeLabel = when (test.type) {
        "mcq"     -> "MCQ"
        "written" -> "Written"
        else      -> "MCQ / Written"
    }
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ModelGreen, ModelGreenDark))),
                contentAlignment = Alignment.Center
            ) {
                Text("${test.testNumber}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(test.displayTitle(), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                Text("পূর্ণমান ${test.totalMarks}  ·  $typeLabel", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
            }
            Icon(Icons.Default.PlayCircleFilled, null, tint = ModelGreen, modifier = Modifier.size(28.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────
// Model Test — "+ নতুন মডেল টেস্ট বানান" ফর্ম (bottom sheet)
// প্রশ্নের ধরন, প্রতি টেস্টে প্রশ্ন সংখ্যা (=পূর্ণমান), কয়টা টেস্ট — এই তিনটা নিয়ে জেনারেট করে
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelTestGenerateSheet(
    isGenerating : Boolean,
    onGenerate   : (type: String, perTest: Int, count: Int) -> Unit,
    onDismiss    : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type    by remember { mutableStateOf("written") }
    var perTest by remember { mutableStateOf(25) }
    var count   by remember { mutableStateOf(5) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 30.dp)
        ) {
            Text("🏆 নতুন মডেল টেস্ট বানান", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(18.dp))

            Text("প্রশ্নের ধরন", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("MCQ", type == "mcq", Modifier.weight(1f)) { type = "mcq" }
                TypeChip("Written", type == "written", Modifier.weight(1f)) { type = "written" }
                TypeChip("উভয়", type == "both", Modifier.weight(1f)) { type = "both" }
            }

            Spacer(Modifier.height(20.dp))
            StepperRow(
                label = "প্রতি টেস্টে প্রশ্ন (=পূর্ণমান)", value = perTest,
                onDec = { perTest = (perTest - 5).coerceIn(10, 100) },
                onInc = { perTest = (perTest + 5).coerceIn(10, 100) }
            )

            Spacer(Modifier.height(14.dp))
            StepperRow(
                label = "কয়টা টেস্ট বানাবে", value = count,
                onDec = { count = (count - 1).coerceIn(1, 15) },
                onInc = { count = (count + 1).coerceIn(1, 15) }
            )

            Spacer(Modifier.height(22.dp))
            Button(
                onClick  = { onGenerate(type, perTest, count) },
                enabled  = !isGenerating,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = ModelGreen)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("জেনারেট করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape    = RoundedCornerShape(12.dp),
        color    = if (selected) ModelGreen else MaterialTheme.colorScheme.surfaceVariant,
        border   = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            label, fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        )
    }
}

@Composable
private fun StepperRow(label: String, value: Int, onDec: () -> Unit, onInc: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, fontFamily = NotoSansBengali,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onDec, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.RemoveCircle, null, tint = ModelGreen)
            }
            Text("$value", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = NotoSansBengali, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            IconButton(onClick = onInc, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.AddCircle, null, tint = ModelGreen)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// MCQ / Written টাইপ সিলেক্টর — type=="both" হলে দেখানো হয়
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelTestTypeSheet(
    test      : ModelTestMeta,
    onPick    : (String) -> Unit,
    onDismiss : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🏆", fontSize = 32.sp)
            Text(test.displayTitle(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center)
            Text("কোন ধরনের প্রশ্নে দেবেন?", fontSize = 12.sp, fontFamily = NotoSansBengali,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick  = { onPick("mcq") },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ModelGreen)
                ) {
                    Text("📝 MCQ", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
                Button(
                    onClick  = { onPick("written") },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                ) {
                    Text("✍️ Written", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
