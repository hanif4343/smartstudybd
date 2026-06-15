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
import com.hanif.smartstudy.ui.challenge.ChallengeZone
import com.hanif.smartstudy.ui.quiz.CoreScreen
import com.hanif.smartstudy.ui.search.GlobalSearchScreen
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.ui.typing.TypingPracticeScreen
import com.hanif.smartstudy.util.DeepLinkAction
import com.hanif.smartstudy.viewmodel.ChallengeViewModel
import com.hanif.smartstudy.viewmodel.MenuViewModel
import com.hanif.smartstudy.viewmodel.QuizViewModel

enum class BottomTab(val icon: String, val label: String) {
    HOME(      "🏠", "Home"),
    QUIZ(      "🎯", "Quiz"),
    QBANK(     "📚", "QBank"),
    STUDY(     "📖", "Study"),
    CHALLENGE( "⚔️", "চ্যালেঞ্জ"),
    MENU(      "👤", "Menu")
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
    var currentTab     by remember { mutableStateOf(BottomTab.HOME) }
    var showSearch     by remember { mutableStateOf(false) }
    var showTyping     by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title   = { Text("অ্যাপ বন্ধ করবেন?", fontFamily = NotoSansBengali) },
            text    = { Text("আপনি কি SmartStudy বন্ধ করতে চান?", fontFamily = NotoSansBengali) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    (context as? androidx.activity.ComponentActivity)?.finish()
                }) { Text("হ্যাঁ, বন্ধ করুন", fontFamily = NotoSansBengali) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("না", fontFamily = NotoSansBengali)
                }
            }
        )
    }

    // Back button:
    // 1) search/typing খোলা থাকলে বন্ধ করো
    // 2) অন্য tab এ থাকলে Home এ যাও (Quiz ভেতরের depth CoreScreen BackHandler আগেই consume করে)
    // 3) Home এ থাকলে exit dialog দেখাও
    BackHandler(enabled = true) {
        when {
            showSearch                   -> showSearch = false
            showTyping                   -> showTyping = false
            // Menu tab এ থাকলে MenuScreen এর নিজস্ব BackHandler handle করবে
            currentTab == BottomTab.MENU -> { /* MenuScreen BackHandler consume করবে */ }
            currentTab != BottomTab.HOME -> currentTab = BottomTab.HOME
            else                         -> showExitDialog = true
        }
    }

    // FIX: তিনটা আলাদা ViewModel — একটাতে mode switch করলে অন্যটার data নষ্ট হবে না
    val quizViewModel  : QuizViewModel = viewModel(key = "quiz_vm")
    val qbankViewModel : QuizViewModel = viewModel(key = "qbank_vm")
    val studyViewModel : QuizViewModel = viewModel(key = "study_vm")
    val menuViewModel       : MenuViewModel       = viewModel()
    val challengeViewModel  : ChallengeViewModel  = viewModel()
    val battleViewModel     : com.hanif.smartstudy.viewmodel.WeekendBattleViewModel = viewModel()
    val challengeState by challengeViewModel.state.collectAsState()
    val pendingInvites = challengeState.pendingInvites.size

    // ViewModel init-এ mode set করো
    LaunchedEffect(Unit) {
        quizViewModel.setMode(StudyMode.QUIZ)
        qbankViewModel.setMode(StudyMode.QBANK)
        studyViewModel.setMode(StudyMode.STUDY)
    }

    // Admin audience tag পরিবর্তন হলে সব ViewModel reload
    val menuState by menuViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(menuState.adminViewingTag) {
        if (menuState.isAdmin) {
            quizViewModel.adminRefreshContent()
            qbankViewModel.adminRefreshContent()
            studyViewModel.adminRefreshContent()
        }
    }

    // Admin question edit হলে সব ViewModel fresh reload করো
    LaunchedEffect(menuState.contentEditVersion) {
        if (menuState.contentEditVersion > 0) {
            quizViewModel.adminRefreshContent()
            qbankViewModel.adminRefreshContent()
            studyViewModel.adminRefreshContent()
        }
    }

    LaunchedEffect(deepLink.type) {
        when (deepLink.type) {
            DeepLinkAction.Type.QUIZ       -> {
                currentTab = BottomTab.QUIZ
                deepLink.questionId?.let { quizViewModel.navigateToQuestion(it) }
            }
            DeepLinkAction.Type.QBANK      -> {
                currentTab = BottomTab.QBANK
                deepLink.questionId?.let { qbankViewModel.navigateToQuestion(it) }
            }
            DeepLinkAction.Type.STUDY      -> {
                currentTab = BottomTab.STUDY
                deepLink.questionId?.let { studyViewModel.navigateToQuestion(it) }
            }
            DeepLinkAction.Type.SEARCH     -> { showSearch = true }
            DeepLinkAction.Type.REPORTS    -> {
                val qid = deepLink.questionId
                if (!qid.isNullOrBlank()) {
                    when (deepLink.tab?.lowercase()) {
                        "qbank" -> { currentTab = BottomTab.QBANK; qbankViewModel.navigateToQuestion(qid) }
                        "study" -> { currentTab = BottomTab.STUDY; studyViewModel.navigateToQuestion(qid) }
                        else    -> { currentTab = BottomTab.QUIZ;  quizViewModel.navigateToQuestion(qid) }
                    }
                } else {
                    currentTab = BottomTab.MENU
                }
            }
            DeepLinkAction.Type.TECHNIQUES -> { currentTab = BottomTab.MENU  }
            DeepLinkAction.Type.MENU       -> { currentTab = BottomTab.MENU  }
            DeepLinkAction.Type.CHALLENGE  -> { currentTab = BottomTab.CHALLENGE }
            DeepLinkAction.Type.NONE       -> {}
        }
    }

    // deepLink থেকে menuPage বের করো
    val menuInitialPage = remember(deepLink) {
        when (deepLink.type) {
            DeepLinkAction.Type.REPORTS    -> if (deepLink.questionId.isNullOrBlank()) "admin" else null
            DeepLinkAction.Type.TECHNIQUES -> "admin"
            DeepLinkAction.Type.MENU       -> deepLink.menuPage
            else                           -> null
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
                        icon     = {
                            BadgedBox(badge = {
                                if (tab == BottomTab.CHALLENGE && pendingInvites > 0) {
                                    Badge { Text(pendingInvites.toString()) }
                                }
                            }) { Text(tab.icon, fontSize = 22.sp) }
                        },
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
                    quizViewModel = quizViewModel,
                    onSearchClick = { showSearch = true },
                    onTypingClick = { showTyping = true },
                    onOpenStudy = { subject, subTopic ->
                        currentTab = BottomTab.STUDY
                        studyViewModel.navigateToSubject(subject)
                        if (subTopic.isNotBlank()) studyViewModel.navigateToSubTopic(subTopic)
                    },
                    onOpenInstantTest = { subject, subTopic ->
                        currentTab = BottomTab.QUIZ
                        quizViewModel.startInstantTestFor(subject, subTopic)
                    },
                    onOpenWeeklyTest = {
                        currentTab = BottomTab.CHALLENGE
                    }
                )
                BottomTab.QUIZ  -> CoreScreen(
                    mode      = StudyMode.QUIZ,
                    viewModel = quizViewModel,
                    onAchievementUnlocked = onAchievementUnlocked,
                    onStreakUpdated       = onStreakUpdated,
                    onAdminEdit = { sheet, rowKey, fields, preview ->
                        menuViewModel.adminEditQuestion(sheet, rowKey, fields, preview)
                    }
                )
                BottomTab.QBANK -> CoreScreen(
                    mode      = StudyMode.QBANK,
                    viewModel = qbankViewModel,
                    onAchievementUnlocked = onAchievementUnlocked,
                    onStreakUpdated       = onStreakUpdated,
                    onAdminEdit = { sheet, rowKey, fields, preview ->
                        menuViewModel.adminEditQuestion(sheet, rowKey, fields, preview)
                    }
                )
                BottomTab.STUDY -> CoreScreen(
                    mode      = StudyMode.STUDY,
                    viewModel = studyViewModel,
                    onAchievementUnlocked = onAchievementUnlocked,
                    onStreakUpdated       = onStreakUpdated,
                    onAdminEdit = { sheet, rowKey, fields, preview ->
                        menuViewModel.adminEditQuestion(sheet, rowKey, fields, preview)
                    }
                )
                BottomTab.CHALLENGE -> ChallengeZone(vm = challengeViewModel, battleVm = battleViewModel)
                BottomTab.MENU  -> MenuScreen(
                    vm                   = menuViewModel,
                    onLogout             = onLogout,
                    onSearchClick        = { showSearch = true },
                    onTypingClick        = { showTyping = true },
                    initialPage          = menuInitialPage
                )
            }
            } // Box
        } // Column
    }
}
