package com.hanif.smartstudy.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.ui.home.HomeScreen
import com.hanif.smartstudy.ui.theme.NotoSansBengali

enum class BottomTab(val icon: String, val label: String) {
    HOME(  "🏠", "Home"),
    QUIZ(  "🎯", "Quiz"),
    QBANK( "📚", "QBank"),
    STUDY( "📖", "Study"),
    MENU(  "👤", "Menu")
}

@Composable
fun MainScreen() {
    var currentTab by remember { mutableStateOf(BottomTab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick  = { currentTab = tab },
                        icon     = { Text(tab.icon, fontSize = 22.sp) },
                        label    = {
                            Text(
                                tab.label,
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NotoSansBengali
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                BottomTab.HOME  -> HomeScreen()                          // ✅ Phase 3
                BottomTab.QUIZ  -> PlaceholderTab("🎯 Quiz\n(Phase 4 এ আসবে)")
                BottomTab.QBANK -> PlaceholderTab("📚 QBank\n(Phase 4 এ আসবে)")
                BottomTab.STUDY -> PlaceholderTab("📖 Study\n(Phase 4 এ আসবে)")
                BottomTab.MENU  -> PlaceholderTab("👤 Menu\n(Phase 5 এ আসবে)")
            }
        }
    }
}

@Composable
fun PlaceholderTab(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text       = label,
            fontSize   = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = NotoSansBengali,
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
