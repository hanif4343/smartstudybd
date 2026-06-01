package com.hanif.smartstudy.ui.menu

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
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
    var showTimePicker by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(
        initialHour   = state.reminderHour,
        initialMinute = state.reminderMinute
    )

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("পড়ার রিমাইন্ডার সময়", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
            text  = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setMorningReminder(true, timeState.hour, timeState.minute)
                    showTimePicker = false
                }) {
                    Text("সংরক্ষণ", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("বাতিল", fontFamily = NotoSansBengali)
                }
            }
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
                    Text("৪টি থিম থেকে বেছে নিন", fontFamily = NotoSansBengali, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppTheme.entries.forEach { theme ->
                            ThemeChip(
                                theme     = theme,
                                selected  = state.appTheme == theme,
                                onClick   = { vm.setTheme(theme) },
                                modifier  = Modifier.weight(1f)
                            )
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

            // ── Reminder ──
            SettingsCard("🔔 পড়ার রিমাইন্ডার") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Morning reminder
                    ReminderRow(
                        icon    = "🌅",
                        label   = "সকালের রিমাইন্ডার",
                        subLabel = if (state.isMorningOn)
                            "প্রতিদিন ${state.morningHour}:${state.morningMinute.toString().padStart(2,'0')} এ"
                        else "বন্ধ আছে",
                        isOn    = state.isMorningOn,
                        onToggle = { on ->
                            if (on) showTimePicker = true
                            else vm.setMorningReminder(false)
                        },
                        onEdit  = { showTimePicker = true },
                        timeStr = "${state.morningHour}:${state.morningMinute.toString().padStart(2,'0')}"
                    )

                    HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFF1F5F9))

                    // Night reminder
                    var showNightPicker by remember { mutableStateOf(false) }
                    val nightTimeState = rememberTimePickerState(
                        initialHour   = state.nightHour,
                        initialMinute = state.nightMinute
                    )

                    ReminderRow(
                        icon    = "🌙",
                        label   = "রাতের রিমাইন্ডার",
                        subLabel = if (state.isNightOn)
                            "প্রতিদিন ${state.nightHour}:${state.nightMinute.toString().padStart(2,'0')} এ"
                        else "বন্ধ আছে",
                        isOn    = state.isNightOn,
                        onToggle = { on ->
                            if (on) showNightPicker = true
                            else vm.setNightReminder(false)
                        },
                        onEdit  = { showNightPicker = true },
                        timeStr = "${state.nightHour}:${state.nightMinute.toString().padStart(2,'0')}"
                    )

                    if (showNightPicker) {
                        AlertDialog(
                            onDismissRequest = { showNightPicker = false },
                            title = { Text("রাতের পড়ার রিমাইন্ডার সময়", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                            text  = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    TimePicker(state = nightTimeState)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    vm.setNightReminder(true, nightTimeState.hour, nightTimeState.minute)
                                    showNightPicker = false
                                }) {
                                    Text("সংরক্ষণ", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showNightPicker = false }) {
                                    Text("বাতিল", fontFamily = NotoSansBengali)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
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
