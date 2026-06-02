package com.hanif.smartstudy.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.hanif.smartstudy.ui.shared.OfflineBanner
import com.hanif.smartstudy.util.ConnectivityObserver
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
    val context    = LocalContext.current
    val isOnline   by ConnectivityObserver.observe(context)
                        .collectAsStateWithLifecycle(initialValue = true)
    var currentTab by remember { mutableStateOf(BottomTab.HOME) }
    var showSearch by remember { mutableStateOf(false) }
    var showTyping by remember { mutableStateOf(false) }

    // Back button: search/typing বন্ধ করো অথবা Home এ যাও
    // Quiz/QBank/Study এর ভেতরের navigation CoreScreen এর BackHandler handle করে
    BackHandler(enabled = showSearch || showTyping || currentTab != BottomTab.HOME) {
        when {
            showSearch -> showSearch = false
            showTyping -> showTyping = false
            // Quiz/QBank/Study tab এ back দিলে শুধু Home এ যাবে যদি CoreScreen এর back consume না হয়
            currentTab != BottomTab.HOME -> currentTab = BottomTab.HOME
        }
    }

    // FIX: তিনটা আলাদা ViewModel — একটাতে mode switch করলে অন্যটার data নষ্ট হবে না
    val quizViewModel  : QuizViewModel = viewModel(key = "quiz_vm")
    val qbankViewModel : QuizViewModel = viewModel(key = "qbank_vm")
    val studyViewModel : QuizViewModel = viewModel(key = "study_vm")
    val menuViewModel  : MenuViewModel = viewModel()

    // ViewModel init-এ mode set করো
    LaunchedEffect(Unit) {
        quizViewModel.setMode(StudyMode.QUIZ)
        qbankViewModel.setMode(StudyMode.QBANK)
        studyViewModel.setMode(StudyMode.STUDY)
    }

    LaunchedEffect(deepLink.type) {
        when (deepLink.type) {
            DeepLinkAction.Type.QUIZ   -> { currentTab = BottomTab.QUIZ  }
            DeepLinkAction.Type.QBANK  -> { currentTab = BottomTab.QBANK }
            DeepLinkAction.Type.STUDY  -> { currentTab = BottomTab.STUDY }
            DeepLinkAction.Type.SEARCH -> { showSearch = true }
            DeepLinkAction.Type.NONE   -> {}
        }
    }

    if (showSearch) {
        val activeVm = when (currentTab) {
            BottomTab.QUIZ  -> quizViewModel
            BottomTab.QBANK -> qbankViewModel
            BottomTab.STUDY -> studyViewModel
            else            -> quizViewModel
        }
        val activeState by activeVm.state.collectAsState()
        GlobalSearchScreen(
            allQuestions = activeState.questions,
            onBack       = { showSearch = false }
        )
        return
    }
    if (showTyping) {
        TypingPracticeScreen(
            onBack   = { showTyping = false },
            onResult = {}
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
            Column(Modifier.fillMaxSize()) {
                OfflineBanner(visible = !isOnline)
            when (currentTab) {
                BottomTab.HOME  -> HomeScreen(
                    onSearchClick = { showSearch = true },
                    onTypingClick = { showTyping = true }
                )
                BottomTab.QUIZ  -> CoreScreen(
                    mode      = StudyMode.QUIZ,
                    viewModel = quizViewModel,
                    onAchievementUnlocked = onAchievementUnlocked,
                    onStreakUpdated       = onStreakUpdated
                )
                BottomTab.QBANK -> CoreScreen(
                    mode      = StudyMode.QBANK,
                    viewModel = qbankViewModel,
                    onAchievementUnlocked = onAchievementUnlocked,
                    onStreakUpdated       = onStreakUpdated
                )
                BottomTab.STUDY -> CoreScreen(
                    mode      = StudyMode.STUDY,
                    viewModel = studyViewModel,
                    onAchievementUnlocked = onAchievementUnlocked,
                    onStreakUpdated       = onStreakUpdated
                )
                BottomTab.MENU  -> MenuScreen(
                    vm                   = menuViewModel,
                    onLogout             = onLogout,
                    onSearchClick        = { showSearch = true },
                    onTypingClick        = { showTyping = true }
                )
            }
            } // Box
        } // Column
    }
}
