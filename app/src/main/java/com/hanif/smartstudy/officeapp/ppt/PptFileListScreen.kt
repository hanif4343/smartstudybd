package com.hanif.smartstudy.officeapp.ppt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.gson.Gson
import com.hanif.smartstudy.officeapp.common.OfficeFile
import com.hanif.smartstudy.officeapp.common.OfficeFileStore
import com.hanif.smartstudy.officeapp.common.OfficeModule
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PptFileListScreen(
    onBack: () -> Unit,
    onOpenFile: (OfficeFile) -> Unit
) {
    val context = LocalContext.current
    val store = remember { OfficeFileStore(context) }
    var files by remember { mutableStateOf(store.list(OfficeModule.PPT)) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<OfficeFile?>(null) }

    BackHandler { onBack() }

    if (showTemplatePicker) {
        PptTemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onPick = { template ->
                val newFile = store.createNew(
                    module = OfficeModule.PPT,
                    title = if (template.id == "blank") "নতুন প্রেজেন্টেশন" else template.title,
                    initialContent = templateToPresentationJson(template)
                )
                showTemplatePicker = false
                onOpenFile(newFile)
            }
        )
    }

    fileToDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("ফাইল ডিলিট করবেন?", fontFamily = NotoSansBengali) },
            text = { Text("\"${f.title}\" স্থায়ীভাবে মুছে যাবে।", fontFamily = NotoSansBengali) },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(OfficeModule.PPT, f.id)
                    files = store.list(OfficeModule.PPT)
                    fileToDelete = null
                }) { Text("ডিলিট করুন", color = Color(0xFFDC2626), fontFamily = NotoSansBengali) }
            },
            dismissButton = { TextButton(onClick = { fileToDelete = null }) { Text("বাতিল", fontFamily = NotoSansBengali) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PowerPoint ফাইল", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showTemplatePicker = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("নতুন ফাইল", fontFamily = NotoSansBengali) },
                containerColor = Color(0xFFEA580C),
                contentColor = Color.White
            )
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📽️", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("এখনো কোনো ফাইল নেই", fontFamily = NotoSansBengali, color = Color(0xFF64748B))
                    Spacer(Modifier.height(4.dp))
                    Text("নিচের \"নতুন ফাইল\" বাটনে চাপুন", fontFamily = NotoSansBengali, color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF8FAFC))
                            .clickable { onOpenFile(file) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFFEDD5)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Slideshow, contentDescription = null, tint = Color(0xFFEA580C))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.title, fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                remember { SimpleDateFormat("dd MMM, hh:mm a", Locale("bn","BD")) }.format(Date(file.updatedAt)),
                                fontFamily = NotoSansBengali, fontSize = 11.sp, color = Color(0xFF94A3B8)
                            )
                        }
                        IconButton(onClick = { fileToDelete = file }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFCBD5E1))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PptTemplatePickerDialog(onDismiss: () -> Unit, onPick: (PptTemplate) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)) {
            Text("টেমপ্লেট বাছাই করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            PptTemplates.all.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(t) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, fontFamily = NotoSansBengali, fontSize = 14.sp)
                }
            }
        }
    }
}

/** PptTemplate -> JS presentation-state JSON (setPresentationState এ যা লাগবে) */
private fun templateToPresentationJson(template: PptTemplate): String {
    val gson = Gson()
    val slidesList = template.slides.map { s ->
        mapOf("bg" to s.bg, "transition" to "none", "elements" to s.elements)
    }
    return gson.toJson(mapOf("slides" to slidesList))
}
