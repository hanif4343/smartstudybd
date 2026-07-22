package com.hanif.smartstudy.ui.menu

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.viewmodel.MenuUiState
import com.hanif.smartstudy.viewmodel.MenuViewModel

// ─────────────────────────────────────────────────────────────
//  SettingsScreen — Dark mode, 4 themes, sound, reminder
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state  : MenuUiState,
    vm     : MenuViewModel,
    onBack : () -> Unit
) {
    val context = LocalContext.current

    // Exact-alarm permission না থাকলে reminder dialog খোলার বদলে settings এ পাঠাও —
    // এতে reminder কখনো "চালু আছে" দেখাবে অথচ আসলে বাজবে না, এমন হবে না।
    fun openReminderOrAskPermission(openDialog: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !vm.hasExactAlarmPermission()) {
            Toast.makeText(
                context,
                "⚠️ Settings থেকে \"Alarms & reminders\" Allow করো, তারপর আবার চেষ্টা করো",
                Toast.LENGTH_LONG
            ).show()
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            } catch (e: Exception) {
                // ignore — কিছু ডিভাইসে এই action সাপোর্ট নাও করতে পারে
            }
            return
        }
        openDialog()
    }

    var showTimePicker by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(
        initialHour   = state.reminderHour,
        initialMinute = state.reminderMinute
    )
    var morningRepeatDaily by remember { mutableStateOf(state.isMorningRepeat) }

    if (showTimePicker) {
        ReminderTimeDialog(
            title        = "সকালের রিমাইন্ডার সময়",
            timeState    = timeState,
            repeatDaily  = morningRepeatDaily,
            onRepeatChange = { morningRepeatDaily = it },
            onSave       = {
                vm.setMorningReminder(true, timeState.hour, timeState.minute, morningRepeatDaily)
                showTimePicker = false
            },
            onDismiss    = { showTimePicker = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ সেটিংস", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── অফলাইন মোড (Firebase disconnect) ──
            SettingsCard("🔌 অফলাইন মোড") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Firebase সংযোগ", fontFamily = NotoSansBengali, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (state.isOfflineMode) "বন্ধ আছে — সব লোকালি সেভ হচ্ছে" else "চালু আছে",
                                fontFamily = NotoSansBengali, fontSize = 11.sp,
                                color = if (state.isOfflineMode) Color(0xFFDC2626)
                                        else MaterialTheme.colorScheme.onSurface.copy(0.5f)
                            )
                        }
                        Switch(
                            checked         = state.isOfflineMode,
                            onCheckedChange = { vm.setOfflineMode(it) }
                        )
                    }
                    if (state.isOfflineMode) {
                        Text(
                            "⚠️ এই মোডে থাকা অবস্থায় নতুন কোনো ডাটা Firebase-এ read/write হবে না। " +
                            "যা কিছু করবেন সব ফোনেই (লোকাল স্টোরেজে) সেভ থাকবে। পরে বন্ধ করলে জমে থাকা " +
                            "পরিবর্তনগুলো আবার নিজে থেকেই Firebase-এ সিঙ্ক হয়ে যাবে।",
                            fontFamily = NotoSansBengali, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                    }
                }
            }

            // ── Dark mode ──
            SettingsCard("🌙 ডার্ক মোড") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ডার্ক মোড", fontFamily = NotoSansBengali, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (state.isDarkMode) "চালু আছে" else "বন্ধ আছে",
                            fontFamily = NotoSansBengali, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    }
                    Switch(
                        checked         = state.isDarkMode,
                        onCheckedChange = { vm.setDarkMode(it) }
                    )
                }
            }

            // ── Theme color ──
            SettingsCard("🎨 থিম রঙ") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("৫টি থিম থেকে বেছে নিন", fontFamily = NotoSansBengali, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    // ৫টি থিম একসারিতে সংকুচিত হয়ে যেত, তাই ৩ + ২ — দুই সারিতে ভাগ করা হয়েছে
                    val themeRows = AppTheme.entries.chunked(3)
                    themeRows.forEach { rowThemes ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowThemes.forEach { theme ->
                                ThemeChip(
                                    theme     = theme,
                                    selected  = state.appTheme == theme,
                                    onClick   = { vm.setTheme(theme) },
                                    modifier  = Modifier.weight(1f)
                                )
                            }
                            // শেষ সারিতে কম চিপ থাকলে খালি জায়গা রেখে alignment ঠিক রাখা হয়
                            repeat(3 - rowThemes.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ── Sound ──
            SettingsCard("🔊 সাউন্ড ইফেক্ট") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text("সাউন্ড ইফেক্ট", fontFamily = NotoSansBengali, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (state.isSoundOff) "বন্ধ আছে" else "চালু আছে",
                            fontFamily = NotoSansBengali, fontSize = 11.sp,
                            color      = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    }
                    Switch(
                        checked         = !state.isSoundOff,
                        onCheckedChange = { vm.setSoundOff(!it) }
                    )
                }
            }

            // ── Written উত্তর AI-অটো-চেক (Study ⌨️ রিকল-টাইপিং মোড) ──
            SettingsCard("🤖 AI দিয়ে Written উত্তর চেক") {
                AiApiKeysSection(state = state, vm = vm)
            }

            // ── Reminder ──
            SettingsCard("🔔 স্মার্ট নোটিফিকেশন") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Morning reminder
                    ReminderRow(
                        icon    = "🌅",
                        label   = "সকালের রিমাইন্ডার",
                        subLabel = if (state.isMorningOn)
                            "${if (state.isMorningRepeat) "প্রতিদিন" else "একবার"} ${state.morningHour}:${state.morningMinute.toString().padStart(2,'0')} এ"
                        else "বন্ধ আছে",
                        isOn    = state.isMorningOn,
                        onToggle = { on ->
                            if (on) { morningRepeatDaily = state.isMorningRepeat; openReminderOrAskPermission { showTimePicker = true } }
                            else vm.setMorningReminder(false)
                        },
                        onEdit  = { morningRepeatDaily = state.isMorningRepeat; openReminderOrAskPermission { showTimePicker = true } },
                        timeStr = "${state.morningHour}:${state.morningMinute.toString().padStart(2,'0')}"
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Midday progress check
                    var showMiddayPicker by remember { mutableStateOf(false) }
                    val middayTimeState = rememberTimePickerState(
                        initialHour   = state.middayHour,
                        initialMinute = state.middayMinute
                    )
                    var middayRepeatDaily by remember { mutableStateOf(state.isMiddayRepeat) }
                    ReminderRow(
                        icon    = "☀️",
                        label   = "দুপুরের প্রোগ্রেস চেক",
                        subLabel = if (state.isMiddayOn)
                            "${if (state.isMiddayRepeat) "প্রতিদিন" else "একবার"} ${state.middayHour}:${state.middayMinute.toString().padStart(2,'0')} এ"
                        else "বন্ধ আছে",
                        isOn    = state.isMiddayOn,
                        onToggle = { on ->
                            if (on) { middayRepeatDaily = state.isMiddayRepeat; openReminderOrAskPermission { showMiddayPicker = true } }
                            else vm.setMiddayReminder(false)
                        },
                        onEdit  = { middayRepeatDaily = state.isMiddayRepeat; openReminderOrAskPermission { showMiddayPicker = true } },
                        timeStr = "${state.middayHour}:${state.middayMinute.toString().padStart(2,'0')}"
                    )
                    if (showMiddayPicker) {
                        ReminderTimeDialog(
                            title       = "দুপুরের প্রোগ্রেস চেক সময়",
                            timeState   = middayTimeState,
                            repeatDaily = middayRepeatDaily,
                            onRepeatChange = { middayRepeatDaily = it },
                            onSave    = {
                                vm.setMiddayReminder(true, middayTimeState.hour, middayTimeState.minute, middayRepeatDaily)
                                showMiddayPicker = false
                            },
                            onDismiss = { showMiddayPicker = false }
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Evening urgency check
                    var showEveningPicker by remember { mutableStateOf(false) }
                    val eveningTimeState = rememberTimePickerState(
                        initialHour   = state.eveningHour,
                        initialMinute = state.eveningMinute
                    )
                    var eveningRepeatDaily by remember { mutableStateOf(state.isEveningRepeat) }
                    ReminderRow(
                        icon    = "🌆",
                        label   = "সন্ধ্যার আর্জেন্ট রিমাইন্ডার",
                        subLabel = if (state.isEveningOn)
                            "${if (state.isEveningRepeat) "প্রতিদিন" else "একবার"} ${state.eveningHour}:${state.eveningMinute.toString().padStart(2,'0')} এ"
                        else "বন্ধ আছে",
                        isOn    = state.isEveningOn,
                        onToggle = { on ->
                            if (on) { eveningRepeatDaily = state.isEveningRepeat; openReminderOrAskPermission { showEveningPicker = true } }
                            else vm.setEveningReminder(false)
                        },
                        onEdit  = { eveningRepeatDaily = state.isEveningRepeat; openReminderOrAskPermission { showEveningPicker = true } },
                        timeStr = "${state.eveningHour}:${state.eveningMinute.toString().padStart(2,'0')}"
                    )
                    if (showEveningPicker) {
                        ReminderTimeDialog(
                            title       = "সন্ধ্যার রিমাইন্ডার সময়",
                            timeState   = eveningTimeState,
                            repeatDaily = eveningRepeatDaily,
                            onRepeatChange = { eveningRepeatDaily = it },
                            onSave    = {
                                vm.setEveningReminder(true, eveningTimeState.hour, eveningTimeState.minute, eveningRepeatDaily)
                                showEveningPicker = false
                            },
                            onDismiss = { showEveningPicker = false }
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Night reminder
                    var showNightPicker by remember { mutableStateOf(false) }
                    val nightTimeState = rememberTimePickerState(
                        initialHour   = state.nightHour,
                        initialMinute = state.nightMinute
                    )
                    var nightRepeatDaily by remember { mutableStateOf(state.isNightRepeat) }

                    ReminderRow(
                        icon    = "🌙",
                        label   = "রাতের রিমাইন্ডার",
                        subLabel = if (state.isNightOn)
                            "${if (state.isNightRepeat) "প্রতিদিন" else "একবার"} ${state.nightHour}:${state.nightMinute.toString().padStart(2,'0')} এ"
                        else "বন্ধ আছে",
                        isOn    = state.isNightOn,
                        onToggle = { on ->
                            if (on) { nightRepeatDaily = state.isNightRepeat; openReminderOrAskPermission { showNightPicker = true } }
                            else vm.setNightReminder(false)
                        },
                        onEdit  = { nightRepeatDaily = state.isNightRepeat; openReminderOrAskPermission { showNightPicker = true } },
                        timeStr = "${state.nightHour}:${state.nightMinute.toString().padStart(2,'0')}"
                    )

                    if (showNightPicker) {
                        ReminderTimeDialog(
                            title       = "রাতের পড়ার রিমাইন্ডার সময়",
                            timeState   = nightTimeState,
                            repeatDaily = nightRepeatDaily,
                            onRepeatChange = { nightRepeatDaily = it },
                            onSave    = {
                                vm.setNightReminder(true, nightTimeState.hour, nightTimeState.minute, nightRepeatDaily)
                                showNightPicker = false
                            },
                            onDismiss = { showNightPicker = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── Reusable reminder time picker dialog ──────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    title: String,
    timeState: TimePickerState,
    repeatDaily: Boolean,
    onRepeatChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
        text  = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = timeState)
                Spacer(Modifier.height(12.dp))
                RepeatModeSelector(repeatDaily = repeatDaily, onChange = onRepeatChange)
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("সংরক্ষণ", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", fontFamily = NotoSansBengali)
            }
        }
    )
}

// ── Once / Daily repeat-mode selector ──────────────────────────

@Composable
private fun RepeatModeSelector(
    repeatDaily: Boolean,
    onChange: (Boolean) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "পুনরাবৃত্তি",
            fontFamily = NotoSansBengali, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            RepeatModeOption(
                label    = "একবার",
                selected = !repeatDaily,
                onClick  = { onChange(false) }
            )
            RepeatModeOption(
                label    = "প্রতিদিন",
                selected = repeatDaily,
                onClick  = { onChange(true) }
            )
        }
    }
}

@Composable
private fun RepeatModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(8.dp),
        color   = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Text(
            label,
            fontFamily = NotoSansBengali,
            fontSize   = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color      = if (selected) MaterialTheme.colorScheme.onPrimary
                         else MaterialTheme.colorScheme.onSurface.copy(0.7f),
            modifier   = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

// ── Reminder row ─────────────────────────────────────────────

@Composable
private fun ReminderRow(
    icon: String, label: String, subLabel: String,
    isOn: Boolean, onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit, timeStr: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(icon, fontSize = 22.sp)
                Column {
                    Text(label, fontFamily = NotoSansBengali, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(subLabel, fontFamily = NotoSansBengali, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            }
            Switch(checked = isOn, onCheckedChange = onToggle)
        }
        if (isOn) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)) {
                Text("⏰ সময় পরিবর্তন: $timeStr", fontFamily = NotoSansBengali, fontSize = 13.sp)
            }
        }
    }
}

// ── Settings card wrapper ─────────────────────────────────────

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            fontFamily = NotoSansBengali,
            modifier   = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Card(
            shape  = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(content = content)
        }
    }
}

// ── Written উত্তর AI-অটো-চেক: Groq/Mistral/Cerebras/Gemini — ৪টা API key ──
// একবার সেভ করলে DataStore-এ থেকে যায়, বারবার বসাতে হয় না। শুধু Study-তে
// ⌨️ (রিকল-টাইপিং) মোড চালু থাকলেই ব্যবহার হয়। চেষ্টার ক্রম:
// Groq → Mistral → Cerebras → Gemini (Gemini সবার শেষে — এটা প্রায়ই ফেইল করে)।
// কোনো key না দিলে বা AI ব্যর্থ হলে আগের মতোই ম্যানুয়াল ঠিক/ভুল বাটনে চলে।
@Composable
private fun AiApiKeysSection(state: MenuUiState, vm: MenuViewModel) {
    var groqKey     by remember(state.groqApiKey)     { mutableStateOf(state.groqApiKey) }
    var mistralKey  by remember(state.mistralApiKey)  { mutableStateOf(state.mistralApiKey) }
    var cerebrasKey by remember(state.cerebrasApiKey) { mutableStateOf(state.cerebrasApiKey) }
    var geminiKey   by remember(state.geminiApiKey)   { mutableStateOf(state.geminiApiKey) }

    val context = LocalContext.current
    LaunchedEffect(state.aiKeysSavedMsg) {
        state.aiKeysSavedMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearAiKeysSavedMsg()
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "স্টাডিতে ⌨️ (টাইপ করে উত্তর মেলানো) মোড চালু থাকলে, নিজের লেখা Written " +
            "উত্তর জমা দেওয়ার সাথে সাথে এই key গুলো দিয়ে AI সঠিক/ভুল ধরে দেবে। " +
            "চেষ্টার ক্রম: Groq → Mistral → Cerebras → Gemini। একটা ফেইল করলে পরেরটা " +
            "চেষ্টা হয়, সব ব্যর্থ হলে বা key না দিলে আগের মতোই নিজে ঠিক/ভুল বেছে নেওয়া যাবে।",
            fontFamily = NotoSansBengali, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
        )

        AiApiKeyField(label = "Groq API Key", value = groqKey, onChange = { groqKey = it })
        AiApiKeyField(label = "Mistral API Key", value = mistralKey, onChange = { mistralKey = it })
        AiApiKeyField(label = "Cerebras API Key", value = cerebrasKey, onChange = { cerebrasKey = it })
        AiApiKeyField(label = "Gemini API Key", value = geminiKey, onChange = { geminiKey = it })

        Button(
            onClick  = { vm.saveAiApiKeys(groqKey, mistralKey, cerebrasKey, geminiKey) },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
        ) {
            Text("সংরক্ষণ করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun AiApiKeyField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label, fontSize = 12.sp) },
        singleLine    = true,
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(12.dp),
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None
                               else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        trailingIcon  = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "লুকাও" else "দেখাও"
                )
            }
        },
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Indigo600,
            unfocusedBorderColor = Color(0xFFE2E8F0)
        )
    )
}

// ── Theme color chip ──────────────────────────────────────────

@Composable
fun ThemeChip(
    theme    : AppTheme,
    selected : Boolean,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    val color = when (theme) {
        AppTheme.INDIGO -> Indigo600
        AppTheme.TEAL   -> Teal600
        AppTheme.ROSE   -> Rose600
        AppTheme.AMBER  -> Amber500
        AppTheme.NORDIC -> NordicSage
    }

    Surface(
        onClick   = onClick,
        shape     = RoundedCornerShape(10.dp),
        color     = if (selected) color.copy(0.15f) else MaterialTheme.colorScheme.surface,
        border    = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) color else MaterialTheme.colorScheme.outline.copy(0.2f)
        ),
        modifier  = modifier
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(theme.emoji, fontSize = 20.sp)
            Text(
                theme.label,
                fontSize   = 10.sp,
                fontFamily = NotoSansBengali,
                color      = if (selected) color else MaterialTheme.colorScheme.onSurface.copy(0.6f),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
