package com.hanif.smartstudy.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.StudyMode
import com.hanif.smartstudy.ui.home.HomeScreen
import com.hanif.smartstudy.ui.menu.MenuScreen
import com.hanif.smartstudy.ui.quiz.CoreScreen
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.MenuViewModel
import com.hanif.smartstudy.viewmodel.QuizViewModel

enum class BottomTab(val icon: String, val label: String) {
    HOME(  "🏠", "Home"),
    QUIZ(  "🎯", "Quiz"),
    QBANK( "📚", "QBank"),
    STUDY( "📖", "Study"),
    MENU(  "👤", "Menu")
}

@Composable
fun MainScreen() {
    var currentTab    by remember { mutableStateOf(BottomTab.HOME) }
    val quizViewModel : QuizViewModel = viewModel()
    val menuViewModel : MenuViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick  = { currentTab = tab },
                        icon     = { Text(tab.icon, fontSize = 22.sp) },
                        label    = {
                            Text(tab.label, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NotoSansBengali)
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                BottomTab.HOME  -> HomeScreen()
                BottomTab.QUIZ  -> CoreScreen(mode = StudyMode.QUIZ,  viewModel = quizViewModel)
                BottomTab.QBANK -> CoreScreen(mode = StudyMode.QBANK, viewModel = quizViewModel)
                BottomTab.STUDY -> CoreScreen(mode = StudyMode.STUDY, viewModel = quizViewModel)
                BottomTab.MENU  -> MenuScreen(viewModel = menuViewModel)
            }
        }
    }
}
