package com.hanif.smartstudy.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.Achievement
import com.hanif.smartstudy.data.model.StudyMode
import com.hanif.smartstudy.ui.home.HomeScreen
import com.hanif.smartstudy.ui.menu.MenuScreen
import com.hanif.smartstudy.ui.quiz.CoreScreen
import com.hanif.smartstudy.ui.search.GlobalSearchScreen
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.ui.typing.TypingPracticeScreen
import com.hanif.smartstudy.util.DeepLinkAction
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
fun MainScreen(
    deepLink              : DeepLinkAction = DeepLinkAction(DeepLinkAction.Type.NONE),
    onLogout              : () -> Unit     = {},
    onAchievementUnlocked : (Achievement) -> Unit = {},
    onStreakUpdated       : (Int) -> Unit  = {}
) {
    var currentTab      by remember { mutableStateOf(BottomTab.HOME) }
    var showSearch      by remember { mutableStateOf(false) }
    var showTyping      by remember { mutableStateOf(false) }

    val quizViewModel : QuizViewModel = viewModel()
    val menuViewModel : MenuViewModel = viewModel()
    val quizState by quizViewModel.state.collectAsState()

    // Handle deep link on first composition
    LaunchedEffect(deepLink.type) {
        when (deepLink.type) {
            DeepLinkAction.Type.QUIZ   -> { currentTab = BottomTab.QUIZ  }
            DeepLinkAction.Type.QBANK  -> { currentTab = BottomTab.QBANK }
            DeepLinkAction.Type.STUDY  -> { currentTab = BottomTab.STUDY }
            DeepLinkAction.Type.SEARCH -> { showSearch = true }
            DeepLinkAction.Type.NONE   -> {}
        }
    }

    // Overlay screens (full-screen, slide over bottom nav)
    if (showSearch) {
        GlobalSearchScreen(
            allQuestions = quizState.questions,
            onBack       = { showSearch = false }
        )
        return
    }
    if (showTyping) {
        TypingPracticeScreen(
            onBack   = { showTyping = false },
            onResult = { result ->
                if (result.wpm >= 40) {
                    // Achievement for typing speed
                }
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick  = { currentTab = tab },
                        icon     = { Text(tab.icon, fontSize = 22.sp) },
                        label    = {
                            Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                fontFamily = NotoSansBengali)
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                BottomTab.HOME  -> HomeScreen(
                    onSearchClick = { showSearch = true },
                    onTypingClick = { showTyping = true }
                )
                BottomTab.QUIZ  -> CoreScreen(mode = StudyMode.QUIZ,  viewModel = quizViewModel,
                    onAchievementUnlocked = onAchievementUnlocked, onStreakUpdated = onStreakUpdated)
                BottomTab.QBANK -> CoreScreen(mode = StudyMode.QBANK, viewModel = quizViewModel,
                    onAchievementUnlocked = onAchievementUnlocked, onStreakUpdated = onStreakUpdated)
                BottomTab.STUDY -> CoreScreen(mode = StudyMode.STUDY, viewModel = quizViewModel,
                    onAchievementUnlocked = onAchievementUnlocked, onStreakUpdated = onStreakUpdated)
                BottomTab.MENU  -> MenuScreen(
                    vm                   = menuViewModel,
                    onLogout             = onLogout,
                    onSearchClick        = { showSearch = true },
                    onTypingClick        = { showTyping = true }
                )
            }
        }
    }
}
