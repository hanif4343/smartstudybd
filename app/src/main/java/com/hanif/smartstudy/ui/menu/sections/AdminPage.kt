package com.hanif.smartstudy.ui.menu.sections

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.MenuUiState
import com.hanif.smartstudy.viewmodel.MenuViewModel
import java.text.SimpleDateFormat
import java.util.*

private val Indigo600 = Color(0xFF4F46E5)
private val GreenOk   = Color(0xFF10B981)
private val RedWrong  = Color(0xFFEF4444)
// Theme-aware colors accessed via MaterialTheme.colorScheme inside composables
// Legacy constants below are kept only for non-composable contexts
private val DeepIndigo= Color(0xFF1E1B4B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPage(
    state  : MenuUiState,
    vm     : MenuViewModel,
    onBack : () -> Unit
) {
    var tab            by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🛡️ Admin Panel", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                        Text("${state.activeUsers.count { it.isOnline }} জন এখন অনলাইন",
                            fontSize = 10.sp, color = GreenOk, fontFamily = NotoSansBengali)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepIndigo,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Tab row — Phase 6 item 13: 👥ইউজার/📣Notify/🔑FCM/🚩Reports/🌐Bulk Tag/📋Logs
            // সম্পূর্ণ সরানো হয়েছে (Admin Web App-এ ডুপ্লিকেট ছিল) — এখন শুধু ৪টা যেগুলো
            // মোবাইল থেকে করা সত্যিই দরকারি (দ্রুত এক-প্রশ্ন যোগ, বাল্ক আপলোড, offline-edit
            // sync, রিলিজ-চেকলিস্ট)।
            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor   = DeepIndigo,
                contentColor     = Color.White,
                edgePadding      = 0.dp
            ) {
                listOf("⏳ Sync", "✅ চেকলিস্ট")
                    .forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i },
                            text = { Text(label, fontFamily = NotoSansBengali, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold) })
                    }
            }

            // Auto-load users when admin panel first opens
            LaunchedEffect(Unit) { vm.loadActiveUsers(); vm.loadPendingEdits() }

            // Sync ট্যাব (⏳ Sync, index 0) খোলার সময় প্রতিবার fresh করে নাও — যাতে
            // এইমাত্র করা offline edit-ও সাথে সাথে দেখা যায়
            LaunchedEffect(tab) { if (tab == 0) vm.loadPendingEdits() }

            when (tab) {
                0 -> PendingSyncTab(state, vm)
                1 -> ProductionChecklistTab()
            }
        }
    }

    // Messages
    val msg = state.successMsg ?: state.error
    if (msg != null) {
        val isSuccess = state.successMsg != null
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            vm.clearMsg()
        }
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                modifier          = Modifier.padding(16.dp),
                containerColor    = if (isSuccess) GreenOk else RedWrong,
                contentColor      = Color.White
            ) {
                Text(msg, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// (➕ নতুন প্রশ্ন ট্যাব Phase 1b-তে সরানো হয়েছে — স্টুডেন্ট অ্যাপ থেকে প্রশ্ন যোগ করা যাবে না)
// (Bulk Upload ট্যাব Phase 1-এ সরানো হয়েছে — স্টুডেন্ট অ্যাপ থেকে মাস্টার-প্রশ্ন বাল্ক আপলোড নেই)


// ── Pending Sync Tab ──
@Composable
private fun PendingSyncTab(state: MenuUiState, vm: MenuViewModel) {
    val pending  = state.pendingEdits
    val syncing  = state.isSyncingEdits
    val syncMsg  = state.syncEditsMsg
    val gson     = remember { com.google.gson.Gson() }
    val sdf      = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()) }

    LaunchedEffect(syncMsg) {
        if (syncMsg != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearSyncEditsMsg()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Header card
        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("⏳", fontSize = 28.sp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (pending.isEmpty()) "কোনো pending edit নেই" else "${pending.size}টি edit sync হয়নি",
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp, color = Color(0xFF1E1B4B)
                    )
                    Text(
                        "Offline এ করা edit গুলো এখানে জমা থাকে",
                        fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Badge
                if (pending.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFEF4444)) {
                        Text(
                            "${pending.size}",
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Sync Now button
        Button(
            onClick  = { vm.syncPendingEditsNow() },
            enabled  = !syncing,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (pending.isEmpty()) Color(0xFF94A3B8) else Color(0xFF4F46E5)
            )
        ) {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(20.dp), Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Sync হচ্ছে...", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (pending.isEmpty()) "✅ সব Sync হয়ে গেছে" else "☁ Sync Now (${pending.size}টি)",
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp
                )
            }
        }

        // Sync result message
        syncMsg?.let {
            val isOk = it.startsWith("✅")
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isOk) Color(0xFFF0FDF4) else Color(0xFFFFF7ED),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, Modifier.padding(12.dp), fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold,
                    color = if (isOk) Color(0xFF166534) else Color(0xFF92400E))
            }
        }

        // Pending list
        if (pending.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("সব edit sync হয়ে গেছে!", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold, color = GreenOk, fontSize = 15.sp)
                }
            }
        } else {
            Text(
                "pending edits:",
                fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pending, key = { it.id }) { action ->
                    val payload = try {
                        gson.fromJson(action.payload, Map::class.java)
                    } catch (e: Exception) { emptyMap<String, Any>() }

                    // ── admin_delete_subject_topic-এ "sheet" (singular) না, "sheets" (লিস্ট)
                    // থাকে আর questionPreview-এর বদলে subject/subTopic নাম দিয়ে preview বানাতে
                    // হয় — নাহলে এই কার্ডে sheet="?" আর preview খালি দেখাতো ──
                    val sheet = payload["sheet"]?.toString()
                        ?: (payload["sheets"] as? List<*>)?.joinToString("+") ?: "?"
                    val preview = payload["questionPreview"]?.toString()?.ifBlank { null }
                        ?: run {
                            when (action.type) {
                                "admin_move_questions" -> {
                                    val ids = (payload["ids"] as? List<*>)?.size ?: 0
                                    val ns = payload["newSubject"]?.toString().orEmpty()
                                    val nst = payload["newSubTopic"]?.toString().orEmpty()
                                    "${ids}টি প্রশ্ন → \"$ns\" › \"$nst\""
                                }
                                "admin_move_topic" -> {
                                    val nst = payload["newSubTopicName"]?.toString().orEmpty()
                                    val ns = payload["newSubjectName"]?.toString().orEmpty()
                                    val merge = payload["mergeTopicId"]?.toString()?.isNotBlank() == true
                                    "\"$nst\" অধ্যায় → \"$ns\"" + (if (merge) " (merge)" else "")
                                }
                                else -> {
                                    val subj = payload["subject"]?.toString().orEmpty()
                                    val subT = payload["subTopic"]?.toString().orEmpty()
                                    val delSub = payload["deleteSubTopic"]?.toString()?.toBoolean() ?: false
                                    if (subj.isNotBlank()) {
                                        if (delSub && subT.isNotBlank()) "\"$subj\" › \"$subT\" (পুরো অধ্যায়)" else "\"$subj\" (পুরো বিষয়)"
                                    } else ""
                                }
                            }
                        }
                    val retry    = action.retryCount

                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Status icon
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (retry > 0) Color(0xFFFFF7ED) else Color(0xFFEEF2FF)
                            ) {
                                Text(
                                    if (retry > 0) "⚠️" else "📴",
                                    Modifier.padding(6.dp), fontSize = 16.sp
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF4F46E5).copy(0.1f)) {
                                        Text(sheet, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF4F46E5), fontFamily = NotoSansBengali)
                                    }
                                    val (typeLabel, typeColor) = when (action.type) {
                                        "admin_add_question"         -> "➕ নতুন" to Color(0xFF16A34A)
                                        "admin_delete_question"      -> "🗑️ ডিলিট" to Color(0xFFDC2626)
                                        "admin_delete_subject_topic" -> "🗑️ বিষয়/অধ্যায় ডিলিট" to Color(0xFFDC2626)
                                        "admin_move_questions"       -> "📦 প্রশ্ন Move" to Color(0xFF0EA5E9)
                                        "admin_move_topic"           -> "📦 অধ্যায় Move" to Color(0xFF0EA5E9)
                                        else                          -> "✏️ এডিট" to Color(0xFF4F46E5)
                                    }
                                    Surface(shape = RoundedCornerShape(6.dp),
                                        color = typeColor.copy(0.1f)) {
                                        Text(typeLabel, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                            color = typeColor, fontFamily = NotoSansBengali)
                                    }
                                    Text(sdf.format(java.util.Date(action.createdAt)),
                                        fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                                    if (retry > 0) {
                                        Text("retry: $retry", fontSize = 9.sp,
                                            color = Color(0xFFEA580C), fontFamily = NotoSansBengali,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    preview.ifBlank { "প্রশ্ন preview নেই" },
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = NotoSansBengali,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// ✅ Production Checklist Tab
// Play Store এ ছাড়ার আগে এই সব জিনিস ঠিক করতে হবে
// ══════════════════════════════════════════════════════════════════
private data class CheckItem(
    val done    : Boolean,
    val critical: Boolean,
    val title   : String,
    val detail  : String,
    val file    : String
)

@Composable
private fun ProductionChecklistTab() {

    val checklist = listOf(
        // ── 🔴 CRITICAL ──────────────────────────────────────────────────
        CheckItem(false, true,
            "AdMob Test ID সরাও — App ID (Manifest)",
            "AndroidManifest.xml এ এখন Google-এর test App ID আছে:\n" +
            "ca-app-pub-3940256099942544~3347511713\n" +
            "→ নিজের AdMob অ্যাকাউন্ট থেকে আসল App ID বসাও।",
            "app/src/main/AndroidManifest.xml (line ~79)"
        ),
        CheckItem(false, true,
            "AdMob Test Ad Unit ID সরাও — AdManager.kt",
            "AdManager.kt-এ সব BANNER / INTERSTITIAL / REWARDED এখন Google test ID দিয়ে চলছে:\n" +
            "ca-app-pub-3940256099942544/...\n" +
            "→ Production-এ প্রতিটা val এর জায়গায় নিজের আসল ad unit ID বসাও।\n" +
            "স্থান: BANNER_HOME, BANNER_QUIZ_LIST, BANNER_QBANK_SUBJECT, BANNER_STUDY,\n" +
            "BANNER_WEEKEND, INTERSTITIAL_RESULT, INTERSTITIAL_CHALLENGE,\n" +
            "REWARDED_XP_BONUS, REWARDED_DAILY_LOGIN, NATIVE_HOME",
            "app/src/main/java/com/hanif/smartstudy/util/AdManager.kt (line ~42-50)"
        ),
        CheckItem(false, true,
            "REALTIME_DATA = false করো — build.gradle",
            "এখন build.gradle-এ REALTIME_DATA = true আছে।\n" +
            "এর মানে: প্রতিবার এপ খুললে সরাসরি Firebase থেকে data টানে — cache নেই।\n" +
            "→ Production-এ false করো, নইলে:\n" +
            "   • Firebase read বিল বাড়বে\n" +
            "   • অনেক user হলে Firebase throttle করবে\n" +
            "   • এপ খুলতে বেশি সময় লাগবে (offline-first না)",
            "app/build.gradle — buildConfigField \"boolean\", \"REALTIME_DATA\", \"true\""
        ),
        CheckItem(false, true,
            "minifyEnabled true করো — build.gradle (release)",
            "এখন release build-এ minifyEnabled false আছে।\n" +
            "→ true করলে:\n" +
            "   • APK ছোট হবে (~30-50%)\n" +
            "   • Code obfuscate হবে (reverse engineering কঠিন)\n" +
            "   • BuildConfig secrets গুলো decompile করা কঠিন হবে\n" +
            "⚠️ true করার পর proguard-rules.pro চেক করো — crash হলে rules যোগ করতে হবে।",
            "app/build.gradle — buildTypes > release > minifyEnabled false"
        ),
        CheckItem(true, true,
            "Firebase DB Secret → User-auth-only migration ✅",
            "FIREBASE_DB_SECRET সম্পূর্ণ সরানো হয়েছে (build.gradle, CI workflow,\n" +
            "FirebaseTokenProvider.kt সবখান থেকে)। এখন app চালু হওয়ার সাথে সাথেই\n" +
            "Firebase Anonymous Auth দিয়ে sign-in হয় (SmartStudyApp.onCreate), তাই\n" +
            "currentUser কখনো null থাকে না — কোনো master secret আর দরকার নেই।\n" +
            "⚠️ পুরনো secret আগের সব APK build-এ এমবেড হয়ে গেছে — Firebase Console এ\n" +
            "গিয়ে Database Secret rotate করো, আর GitHub repo থেকে\n" +
            "FIREBASE_DB_SECRET secret-টাও ডিলিট করে দাও (আমি এটা করতে পারবো না)।",
            "app/.../data/remote/FirebaseTokenProvider.kt"
        ),
        CheckItem(true, true,
            "Firebase Rules — Users node সবাই পড়তে পারছে ✅",
            "গভীরে গিয়ে দেখা গেল সমস্যাটা আরও বড় ছিল — isAdmin চেক\n" +
            "root.child('Users').child(auth.uid) দিয়ে হতো, কিন্তু Users node\n" +
            "phone দিয়ে key করা, Firebase Auth UID দিয়ে না! মানে এই isAdmin\n" +
            "চেক প্রোডাকশনে কখনোই সত্যি হতো না (Reports/ModelTests/Routine\n" +
            "সব জায়গায়) — সম্ভবত DB-secret fallback এটা আড়াল করে রাখতো।\n" +
            "→ Fix: নতুন UidToPhone/{uid}→phone ম্যাপিং (sign-in এর সময় লেখা হয়),\n" +
            "   Users/\$userId এখন শুধু owner + admin পড়তে/লিখতে পারবে,\n" +
            "   users (lowercase, FCM token) node ও একইভাবে fix হয়েছে।\n" +
            "⚠️ Firebase Console/CLI দিয়ে নতুন rules ডিপ্লয় করতে হবে, আর Google\n" +
            "   sign-in + admin login ভালোভাবে টেস্ট করে দেখো।",
            "firebase-database-rules.json"
        ),

        // ── 🟡 RECOMMENDED ────────────────────────────────────────────────
        CheckItem(false, false,
            "210+ Log statement সরাও বা disable করো",
            "সারা কোডজুড়ে ২১০টি Log.d/e/w আছে।\n" +
            "→ Production-এ sensitive info (phone, token, Firebase URL) log-এ দেখা যায়।\n" +
            "→ সহজ fix: proguard-rules.pro-তে যোগ করো:\n" +
            "   -assumenosideeffects class android.util.Log { *; }\n" +
            "   (minifyEnabled true হলে কাজ করবে)",
            "সব .kt ফাইল — grep: Log.d / Log.e / Log.w"
        ),
        CheckItem(false, false,
            "SyncWorker-এ DB Secret সরাসরি URL-এ যাচ্ছে",
            "SyncWorker.kt-এ ?auth=\$secret দিয়ে Firebase REST call হচ্ছে।\n" +
            "Worker background-এ চলে, তখন কোনো signed-in user নাও থাকতে পারে।\n" +
            "→ Worker-এ Firebase Auth token refresh করে ব্যবহার করো।",
            "app/.../worker/SyncWorker.kt"
        ),
        CheckItem(true, false,
            "RemoteLogger সম্পূর্ণ সরানো হয়েছে ✅",
            "RemoteLogger.kt (Firebase এ DB Secret দিয়ে debug log পাঠানো) পুরোপুরি " +
            "বাদ দেওয়া হয়েছে — ফাইল ডিলিট, আর init/flush/setUserPhone/d/e/i সব কল " +
            "সরিয়ে ফেলা হয়েছে (যেখানে দরকার ছিল সেখানে সাধারণ android.util.Log " +
            "রাখা হয়েছে, যা শুধু ডিভাইসের logcat-এই থাকে, বাইরে পাঠায় না)।",
            "app/.../util/RemoteLogger.kt — ডিলিট করা হয়েছে"
        ),
        CheckItem(false, false,
            "versionName এবং versionCode ঠিক করো",
            "এখন build.gradle-এ versionCode টা github run number দিয়ে auto-set হয়।\n" +
            "versionName \"1.3\" — Play Store-এর জন্য meaningful version দাও।\n" +
            "→ Semantic versioning: major.minor.patch (যেমন: 1.0.0)",
            "app/build.gradle — versionName"
        ),
        CheckItem(false, false,
            "Play Store listing-এ আসল App Icon দাও",
            "এখন build.yml-এ Python দিয়ে নীল রঙের 'SS' লেখা placeholder icon তৈরি হচ্ছে।\n" +
            "→ Figma বা Adobe দিয়ে proper icon বানাও:\n" +
            "   512×512 PNG (Play Store)\n" +
            "   মিপম্যাপ folder-এ (mdpi থেকে xxxhdpi)\n" +
            "   Adaptive icon (foreground + background আলাদা)",
            "app/src/main/res/mipmap-*/ + build.yml icon generation step"
        ),
        CheckItem(false, false,
            "Privacy Policy URL যাচাই করো",
            "Play Store-এ Privacy Policy আবশ্যক।\n" +
            "PrivacyPolicyScreen.kt এ কোনো URL hardcode আছে কিনা চেক করো।",
            "app/.../ui/menu/PrivacyPolicyScreen.kt"
        ),
    )

    val criticalCount  = checklist.count { it.critical && !it.done }
    val recommendCount = checklist.count { !it.critical && !it.done }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF4F46E5)))
                    )
                    .padding(16.dp)
            ) {
                Text(
                    "🚀 Production Checklist",
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp, color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Play Store এ ছাড়ার আগে এই সব ঠিক করতে হবে",
                    fontFamily = NotoSansBengali, fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("🔴 $criticalCount টা Critical বাকি",
                            fontFamily = NotoSansBengali, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("🟡 $recommendCount টা Recommended বাকি",
                            fontFamily = NotoSansBengali, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    }
                }
            }
        }

        // Critical section header
        item {
            Text(
                "🔴 অবশ্যই করতে হবে (Critical)",
                fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp, color = Color(0xFFEF4444),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Critical items
        items(checklist.filter { it.critical }) { item ->
            ChecklistCard(item.done, item.critical, item.title, item.detail, item.file)
        }

        // Recommended section header
        item {
            Text(
                "🟡 করা ভালো (Recommended)",
                fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp, color = Color(0xFFF59E0B),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Recommended items
        items(checklist.filter { !it.critical }) { item ->
            ChecklistCard(item.done, item.critical, item.title, item.detail, item.file)
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun ChecklistCard(
    done    : Boolean,
    critical: Boolean,
    title   : String,
    detail  : String,
    file    : String
) {
    val borderColor = if (critical) Color(0xFFEF4444) else Color(0xFFF59E0B)
    val bgColor     = if (critical) Color(0xFFEF4444).copy(alpha = 0.05f)
                      else          Color(0xFFF59E0B).copy(alpha = 0.05f)
    val icon        = if (done) "✅" else if (critical) "🔴" else "🟡"

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                title, fontFamily = NotoSansBengali,
                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = borderColor, modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                detail, fontFamily = NotoSansBengali, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Folder, null,
                    tint = borderColor, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(file, fontFamily = NotoSansBengali, fontSize = 10.sp,
                        color = borderColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
