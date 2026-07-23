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
import com.hanif.smartstudy.util.toDeepLinkAction
import com.hanif.smartstudy.viewmodel.ChallengeViewModel
import com.hanif.smartstudy.viewmodel.MenuViewModel
import com.hanif.smartstudy.viewmodel.QuizViewModel
import kotlinx.coroutines.launch

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
    var showTypingRace by remember { mutableStateOf(false) }
    var showFocusModeInfo by remember { mutableStateOf(false) }
    var showAiChat     by remember { mutableStateOf(false) }
    var showExitDialog        by remember { mutableStateOf(false) }
    var pendingRoutineItemId  by remember { mutableStateOf<String?>(null) }

    // ── Typing/Search achievement unlock এর জন্য ──
    // এই দুটো screen এর নিজস্ব ViewModel নেই (শুধু stateless composable),
    // তাই QuizViewModel.checkAndUnlock() এর মতো একই কাজ এখানে সরাসরি
    // SessionManager দিয়ে করা হলো (CoreScreen/MenuScreen এও একই pattern
    // ব্যবহৃত হয়েছে)।
    val session = remember { com.hanif.smartstudy.util.SessionManager(context) }
    val scope   = rememberCoroutineScope()
    fun unlockAchievement(id: String) {
        scope.launch {
            if (!session.hasAchievement(id)) {
                session.unlockAchievement(id)
                com.hanif.smartstudy.data.model.Achievements.findById(id)?.let { onAchievementUnlocked(it) }
            }
        }
    }

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
    // 1) search/typing/focus-mode-info খোলা থাকলে বন্ধ করো
    // 2) Menu ট্যাবের সাবপেজে থাকলে MenuScreen এর নিজস্ব (nested) BackHandler
    //    আগেই handle করে ফেলবে (Home থেকে শর্টকাটে আসা সাবপেজ হলে সরাসরি Home এ
    //    ফিরিয়ে দেয়, নাহলে Menu-র MAIN লিস্টে ফেরত যায়) — তাই এখানে আলাদা কিছু
    //    করার দরকার নেই।
    // 3) অন্য যেকোনো tab এ (Menu-র MAIN root সহ) থাকলে Home এ যাও
    // 4) Home এ থাকলে exit dialog দেখাও
    BackHandler(enabled = true) {
        when {
            showSearch                    -> showSearch = false
            showTyping                    -> showTyping = false
            showTypingRace                -> showTypingRace = false
            showFocusModeInfo             -> showFocusModeInfo = false
            currentTab != BottomTab.HOME  -> currentTab = BottomTab.HOME
            else                          -> showExitDialog = true
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

    // deepLink থেকে menuPage বের করো (এখন mutable — Home এর grid card থেকেও, বা
    // 🔔 notification tap থেকেও applyDeepLink() এর মাধ্যমে set করা যায়)
    var menuInitialPage by remember { mutableStateOf<String?>(null) }

    fun applyDeepLink(action: DeepLinkAction) {
        when (action.type) {
            DeepLinkAction.Type.QUIZ       -> {
                currentTab = BottomTab.QUIZ
                action.questionId?.let { quizViewModel.navigateToQuestion(it) }
            }
            DeepLinkAction.Type.QBANK      -> {
                currentTab = BottomTab.QBANK
                action.questionId?.let { qbankViewModel.navigateToQuestion(it) }
            }
            DeepLinkAction.Type.STUDY      -> {
                currentTab = BottomTab.STUDY
                action.questionId?.let { studyViewModel.navigateToQuestion(it) }
            }
            DeepLinkAction.Type.SEARCH     -> { showSearch = true }
            DeepLinkAction.Type.REPORTS    -> {
                val qid = action.questionId
                if (!qid.isNullOrBlank()) {
                    when (action.tab?.lowercase()) {
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
            DeepLinkAction.Type.ROUTINE    -> {
                currentTab = BottomTab.MENU
                pendingRoutineItemId = action.routineItemId
            }
            DeepLinkAction.Type.FOCUS      -> {
                currentTab = BottomTab.STUDY
                action.subject?.let { studyViewModel.navigateToSubject(it) }
            }
            DeepLinkAction.Type.NONE       -> {}
        }
        // ── Menu ট্যাবের কোন সাব-পেজে যেতে হবে সেটাও এখানেই সেট করা হলো —
        // আগে এটা আলাদা LaunchedEffect(deepLink) এ ছিল যেটা শুধু top-level
        // deepLink prop বদলালেই চলত, in-app notification tap থেকে চললে চলত না ──
        menuInitialPage = when (action.type) {
            DeepLinkAction.Type.REPORTS    -> if (action.questionId.isNullOrBlank()) "admin" else null
            DeepLinkAction.Type.TECHNIQUES -> "admin"
            DeepLinkAction.Type.MENU       -> action.menuPage
            else                            -> null
        }
    }

    LaunchedEffect(deepLink.type) {
        applyDeepLink(deepLink)
    }

    // ── ফোকাস মোড: অ্যাপ খোলার সাথে সাথে (একবারই, cold-open এ) ফোকাস মোড চালু
    // থাকলে warning overlay দেখাও। সম্পূর্ণ hook — মূল লজিক focus প্যাকেজে। ──
    val focusStore = remember { com.hanif.smartstudy.focus.FocusModeStore(context) }
    var focusWarning by remember { mutableStateOf<com.hanif.smartstudy.focus.FocusModeState?>(null) }
    LaunchedEffect(Unit) {
        if (com.hanif.smartstudy.focus.FocusModeConfig.ENABLED) {
            val fs = focusStore.getState()
            if (fs.isEffectivelyActive()) focusWarning = fs
        }
    }

    // Part ৩ — লাইভ ফোকাস স্টেট, Home/চ্যালেঞ্জ/Menu ট্যাপ ইন্টারসেপ্ট করার জন্য।
    // Study/QBank/Quiz/Wrong Review/Model Test — এদের ক্ষেত্রে কখনোই ব্যবহৃত হয় না।
    val focusState by focusStore.stateFlow.collectAsStateWithLifecycle(
        initialValue = com.hanif.smartstudy.focus.FocusModeState()
    )
    var pendingFocusNudgeTab by remember { mutableStateOf<BottomTab?>(null) }
    val focusNudgeTabs = remember { setOf(BottomTab.HOME, BottomTab.CHALLENGE, BottomTab.MENU) }

    // ── Home-এর "Typing" টাইল বা Menu-এর "Typing Practice" রো থেকে সরাসরি
    // showTyping=true হয়ে যেত — bottom-tab নাজ (nudge) সিস্টেম বাইপাস হয়ে
    // যাচ্ছিল। এখন ফোকাস মোড অন্য কোনো সাবজেক্টে সক্রিয় থাকলে (টাইপিং নিজেই
    // ফোকাস সাবজেক্ট না হলে), একই FocusNudgeSheet/নোটিফিকেশন পাইপলাইন দিয়েই
    // ইন্টারসেপ্ট হবে — বাকি সব জায়গার মতোই সেম আচরণ। ──
    var pendingTypingNudge by remember { mutableStateOf(false) }
    fun openTypingWithFocusCheck() {
        if (focusState.isEffectivelyActive() &&
            focusState.subject != com.hanif.smartstudy.focus.FocusModeConfig.TYPING_FOCUS_SUBJECT) {
            pendingTypingNudge = true
        } else {
            showTyping = true
        }
    }

    if (showSearch) {
        LaunchedEffect(Unit) { unlockAchievement("search_used") }
        GlobalSearchScreen(
            onBack = { showSearch = false }
        )
        return
    }
    // ── Study সাবজেক্টের তালিকা — এখন টাইপিং স্ক্রিনের নিজস্ব "🎯 আজ ফোকাস" কার্ডেও লাগে,
    // তাই showFocusModeInfo ব্লকের বদলে এখানে একবারই collect করা হচ্ছে ──
    val studyStateForFocus by studyViewModel.state.collectAsStateWithLifecycle()
    if (showTyping) {
        TypingPracticeScreen(
            onBack     = { showTyping = false },
            onResult   = { r -> if (r.wpm >= 40) unlockAchievement("typing_40wpm") },
            onOpenRace = { showTyping = false; showTypingRace = true },
            focusStudySubjects = studyStateForFocus.subjects.map { it.name }
        )
        return
    }
    if (showTypingRace) {
        com.hanif.smartstudy.ui.typing.TypingRaceScreen(onBack = { showTypingRace = false })
        return
    }
    if (showFocusModeInfo) {
        com.hanif.smartstudy.focus.FocusModeInfoScreen(
            subjects = listOf(com.hanif.smartstudy.focus.FocusModeConfig.TYPING_FOCUS_SUBJECT) + studyStateForFocus.subjects.map { it.name },
            onBack   = { showFocusModeInfo = false }
        )
        return
    }
    if (showAiChat) {
        com.hanif.smartstudy.ui.aichat.AiChatScreen(
            onBack = { showAiChat = false }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick  = {
                            if (tab in focusNudgeTabs && focusState.isEffectivelyActive()) {
                                pendingFocusNudgeTab = tab
                            } else {
                                currentTab = tab
                            }
                        },
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
                    isAdmin        = menuState.isAdmin,
                    onSearchClick  = { showSearch = true },
                    onOpenMenu     = { menuInitialPage = null; currentTab = BottomTab.MENU },
                    onOpenMenuPage = { page -> menuInitialPage = page; currentTab = BottomTab.MENU },
                    onOpenQuizTab  = { currentTab = BottomTab.QUIZ },
                    onOpenQBankTab = { currentTab = BottomTab.QBANK },
                    onOpenStudyTab = { currentTab = BottomTab.STUDY },
                    onOpenTyping   = { openTypingWithFocusCheck() },
                    onOpenMockTest = { isQBank ->
                        if (isQBank) {
                            currentTab = BottomTab.QBANK
                            qbankViewModel.openMockZone(StudyMode.QBANK)
                        } else {
                            currentTab = BottomTab.QUIZ
                            quizViewModel.openMockZone(StudyMode.QUIZ)
                        }
                    },
                    onOpenFocusMode = { showFocusModeInfo = true },
                    onOpenAiChat    = { showAiChat = true },
                    onNotificationClick = { notif -> applyDeepLink(notif.toDeepLinkAction()) }
                )
                BottomTab.QUIZ  -> CoreScreen(
                    mode      = StudyMode.QUIZ,
                    viewModel = quizViewModel,
                    onAchievementUnlocked = onAchievementUnlocked,
                    onStreakUpdated       = onStreakUpdated,
                    onAdminEdit = { sheet, rowKey, fields, preview ->
                        menuViewModel.adminEditQuestion(sheet, rowKey, fields, preview)
                    },
                    onAdminDelete = { sheet, rowKey, preview ->
                        menuViewModel.adminDeleteQuestion(sheet, rowKey, preview)
                    }
                )
                BottomTab.QBANK -> CoreScreen(
                    mode      = StudyMode.QBANK,
                    viewModel = qbankViewModel,
                    onAchievementUnlocked = onAchievementUnlocked,
                    onStreakUpdated       = onStreakUpdated,
                    onAdminEdit = { sheet, rowKey, fields, preview ->
                        menuViewModel.adminEditQuestion(sheet, rowKey, fields, preview)
                    },
                    onAdminDelete = { sheet, rowKey, preview ->
                        menuViewModel.adminDeleteQuestion(sheet, rowKey, preview)
                    }
                )
                BottomTab.STUDY -> CoreScreen(
                    mode      = StudyMode.STUDY,
                    viewModel = studyViewModel,
                    onAchievementUnlocked = onAchievementUnlocked,
                    onStreakUpdated       = onStreakUpdated,
                    onAdminEdit = { sheet, rowKey, fields, preview ->
                        menuViewModel.adminEditQuestion(sheet, rowKey, fields, preview)
                    },
                    onAdminDelete = { sheet, rowKey, preview ->
                        menuViewModel.adminDeleteQuestion(sheet, rowKey, preview)
                    }
                )
                BottomTab.CHALLENGE -> ChallengeZone(vm = challengeViewModel, battleVm = battleViewModel)
                BottomTab.MENU  -> MenuScreen(
                    vm                   = menuViewModel,
                    onLogout             = onLogout,
                    onSearchClick        = { showSearch = true },
                    onTypingClick        = { openTypingWithFocusCheck() },
                    initialPage          = menuInitialPage,
                    quizViewModel        = quizViewModel,
                    highlightRoutineItemId   = pendingRoutineItemId,
                    onRoutineItemHighlighted = { pendingRoutineItemId = null },
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
                    },
                    onBackToHome = { menuInitialPage = null; currentTab = BottomTab.HOME }
                )
            }
            } // Box
        } // Column
    }

    focusWarning?.let { fs ->
        com.hanif.smartstudy.focus.FocusWarningOverlay(
            subject  = fs.subject,
            daysLeft = fs.daysUntilExam(),
            onStart  = {
                if (fs.subject == com.hanif.smartstudy.focus.FocusModeConfig.TYPING_FOCUS_SUBJECT) {
                    showTyping = true
                } else {
                    currentTab = BottomTab.STUDY
                    studyViewModel.navigateToSubject(fs.subject)
                }
                focusWarning = null
            },
            onDismiss = { focusWarning = null }
        )
    }

    pendingFocusNudgeTab?.let { targetTab ->
        com.hanif.smartstudy.focus.FocusNudgeSheet(
            subject   = focusState.subject,
            onResume  = {
                if (focusState.subject == com.hanif.smartstudy.focus.FocusModeConfig.TYPING_FOCUS_SUBJECT) {
                    showTyping = true
                } else {
                    currentTab = BottomTab.STUDY
                    studyViewModel.navigateToSubject(focusState.subject)
                }
                pendingFocusNudgeTab = null
            },
            onTurnOff = {
                scope.launch { focusStore.deactivate() }
                currentTab = targetTab
                pendingFocusNudgeTab = null
            },
            onDismiss = { pendingFocusNudgeTab = null }
        )
    }

    // ── Home/Menu থেকে Typing Practice খুলতে চাইলে, কিন্তু ফোকাস মোড অন্য
    // সাবজেক্টে সক্রিয় থাকলে — বাকি সব জায়গার মতোই সেম FocusNudgeSheet ──
    if (pendingTypingNudge) {
        com.hanif.smartstudy.focus.FocusNudgeSheet(
            subject   = focusState.subject,
            onResume  = {
                currentTab = BottomTab.STUDY
                studyViewModel.navigateToSubject(focusState.subject)
                pendingTypingNudge = false
            },
            onTurnOff = {
                scope.launch { focusStore.deactivate() }
                showTyping = true
                pendingTypingNudge = false
            },
            onDismiss = { pendingTypingNudge = false }
        )
    }
    } // Box (focus overlay wrapper)
}
