@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.hanif.smartstudy.officeapp.excel

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
import androidx.compose.material.icons.filled.GridOn
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

/**
 * ══════════════════════════════════════════════════════════════════
 *  ExcelFileListScreen — WordFileListScreen-এর একই প্যাটার্ন। "New" এ
 *  ট্যাপ করলে টেমপ্লেট বাছাই (Salary/GPA/Inventory/Bill/Blank), সেই
 *  cells ম্যাপকে JS sheet-state JSON-এ রূপান্তর করে OfficeFile.content-এ
 *  বসিয়ে দেয়।
 * ══════════════════════════════════════════════════════════════════
 */
@Composable
fun ExcelFileListScreen(
    onBack: () -> Unit,
    onOpenFile: (OfficeFile) -> Unit
) {
    val context = LocalContext.current
    val store = remember { OfficeFileStore(context) }
    var files by remember { mutableStateOf(store.list(OfficeModule.EXCEL)) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<OfficeFile?>(null) }

    BackHandler { onBack() }

    if (showTemplatePicker) {
        ExcelTemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onPick = { template ->
                val newFile = store.createNew(
                    module = OfficeModule.EXCEL,
                    title = if (template.id == "blank") "নতুন স্প্রেডশীট" else template.title,
                    initialContent = templateToSheetStateJson(template)
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
                    store.delete(OfficeModule.EXCEL, f.id)
                    files = store.list(OfficeModule.EXCEL)
                    fileToDelete = null
                }) { Text("ডিলিট করুন", color = Color(0xFFDC2626), fontFamily = NotoSansBengali) }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("বাতিল", fontFamily = NotoSansBengali) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Excel ফাইল", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showTemplatePicker = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("নতুন ফাইল", fontFamily = NotoSansBengali) },
                containerColor = Color(0xFF16A34A),
                contentColor = Color.White
            )
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", fontSize = 48.sp)
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
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFDCFCE7)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.GridOn, contentDescription = null, tint = Color(0xFF16A34A))
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
private fun ExcelTemplatePickerDialog(onDismiss: () -> Unit, onPick: (ExcelTemplate) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)) {
            Text("টেমপ্লেট বাছাই করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            ExcelTemplates.all.forEach { t ->
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

/** ExcelTemplate.cells (raw string map) -> JS sheet-state JSON (setSheetState এ যা লাগবে) */
private fun templateToSheetStateJson(template: ExcelTemplate): String {
    val gson = Gson()
    val cellsObj = template.cells.mapValues { (_, raw) ->
        mapOf("raw" to raw, "bold" to false, "italic" to false, "border" to false, "bg" to "")
    }
    val state = mapOf(
        "numCols" to template.numCols,
        "numRows" to template.numRows,
        "cells" to cellsObj
    )
    return gson.toJson(state)
}
