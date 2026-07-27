package com.hanif.smartstudy.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.viewmodel.QuizViewModel

/**
 * CoreScreen — Quiz / Study / QBank এর master orchestrator.
 * NavPath depth অনুযায়ী সঠিক screen দেখায়:
 *   depth 0 → Subject list
 *   depth 1 → SubTopic list
 *   depth 2 → Question list
 *   isMockZone → Mock selection
 *   showResult → Result modal
 */
@Composable
fun CoreScreen(
    mode                  : StudyMode,
    viewModel             : QuizViewModel = viewModel(),
    onAchievementUnlocked : (com.hanif.smartstudy.data.model.Achievement) -> Unit = {},
    onStreakUpdated       : (Int) -> Unit = {},
    onAdminEdit           : ((sheet: String, rowKey: String, fields: Map<String, String>, preview: String) -> Unit)? = null,
    onAdminDelete         : ((sheet: String, rowKey: String, preview: String) -> Unit)? = null,
    // ── Subject/SubTopic-লেভেল Rename/Delete — SubjectListScreen/SubTopicListScreen-এর
    // "Admin" মেনু থেকে ট্রিগার হয়, বর্তমান sheet (নিচে sheetKey দেখো)-এর ওপরই কাজ করে ──
    onAdminRenameSubject  : ((sheet: String, oldName: String, newName: String) -> Unit)? = null,
    onAdminDeleteSubject  : ((sheet: String, name: String) -> Unit)? = null,
    onAdminRenameSubTopic : ((sheet: String, subject: String, oldName: String, newName: String) -> Unit)? = null,
    onAdminDeleteSubTopic : ((sheet: String, subject: String, name: String) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val ctx   = LocalContext.current
    val currentUser = remember { SessionManager(ctx).getCurrentUser() }

    // ── FirebaseDataService/GasContentService-এ ব্যবহৃত sheet key — capitalized,
    // ঠিক QuizViewModel.reportQuestion()-এ ব্যবহৃত mapping-এর মতোই ──
    val sheetKey = when (mode) {
        StudyMode.QUIZ  -> "Quiz"
        StudyMode.QBANK -> "QBank"
        StudyMode.STUDY -> "Study"
    }

    // Collect and forward achievement/streak events
    val achievement by viewModel.pendingAchievement.collectAsState()
    val streak      by viewModel.pendingStreak.collectAsState()

    LaunchedEffect(achievement) {
        achievement?.let { onAchievementUnlocked(it); viewModel.consumeAchievement() }
    }
    LaunchedEffect(streak) {
        if (streak > 0) { onStreakUpdated(streak); viewModel.consumeStreak() }
    }

    // Mode init — শুধু প্রথমবার, পরে MainScreen থেকে আলাদা VM তাই দরকার নেই
    LaunchedEffect(Unit) {
        if (state.mode != mode) viewModel.setMode(mode)
    }

    // Back handler: depth > 0 বা isMockZone বা showResult হলে ভেতরে handle করো
    // depth 0 (subject list) হলে consume করি না — MainScreen এর BackHandler HOME এ নেবে
    // ── QBank প্রতিষ্ঠান-মোডে "প্রতিষ্ঠান বাছাই করা হয়েছে" অবস্থাতেও navPath এখনো
    // depth0-ই থাকে (ইচ্ছাকৃতভাবে — দেখো selectQBankInstitution()), তাই সেটাও এখানে
    // আলাদাভাবে ধরতে হচ্ছে, নইলে সিস্টেম ব্যাক সরাসরি HOME এ চলে যেত। ──
    val isInsideQBankInstitutionPicker = mode == StudyMode.QBANK &&
        state.qbankFilterMode == QBankFilterMode.INSTITUTION &&
        state.qbankSelectedInstitution != null
    val isInsideNav = state.isMockZone ||
                      state.isModelTestZone ||
                      state.isModelTestSubjectPicker ||
                      state.showResult ||
                      state.navPath.depth() > 0 ||
                      isInsideQBankInstitutionPicker
    BackHandler(enabled = isInsideNav) {
        // ── QBank প্রতিষ্ঠান/সাল ফিল্টার-মোডে থাকা অবস্থায় কাস্টম back — generic
        // navigateBack() এই দুই মোডের উল্টানো/flat হায়ারার্কি বোঝে না। পদবী(ডিফল্ট)
        // মোডে ও Mock/Model Test/Result-এ আগের মতোই generic navigateBack() ব্যবহার হয়। ──
        val useQBankFilterBack = mode == StudyMode.QBANK &&
            state.qbankFilterMode != QBankFilterMode.DESIGNATION &&
            !state.isMockZone && !state.isModelTestZone &&
            !state.isModelTestSubjectPicker && !state.showResult
        if (useQBankFilterBack) viewModel.qbankFilterBack() else viewModel.navigateBack()
    }

    when {
        // ── Mock Zone ──
        state.isMockZone -> {
            MockSelectionScreen(
                subjects       = state.subjects,
                mockConfig     = state.mockConfig,
                onToggleTopic  = { viewModel.toggleMockTopic(it) },
                onSetLimit     = { viewModel.setMockLimit(it) },
                onStart        = { viewModel.startMock() },
                onBack         = { viewModel.navigateBack() }
            )
        }

        // ── Model Test — Subject Picker (Mock Test-এর মতো গ্লোবাল এন্ট্রি) ──
        state.isModelTestSubjectPicker -> {
            ModelTestSubjectPickerScreen(
                subjects = state.modelTestSubjectList,
                onSelect = { viewModel.openModelTestZone(it) },
                onBack   = { viewModel.navigateBack() }
            )
        }

        // ── Model Test Zone — ইউজার নিজে জেনারেট করা টেস্ট লিস্ট (লোকাল স্টোরেজ) ──
        state.isModelTestZone -> {
            ModelTestListScreen(
                subject       = state.modelTests.firstOrNull()?.subject
                    ?: if (state.isModelTestJobUser)
                           com.hanif.smartstudy.data.local.LocalModelTestStore.JOB_ALL_LABEL
                       else state.modelTestSubject,
                tests         = state.modelTests,
                warning       = state.modelTestGenWarning,
                onSelect      = { viewModel.selectModelTest(it) },
                onGenerateNew = { viewModel.openModelTestGenerateSheet() },
                onBack        = { viewModel.navigateBack() }
            )
            if (state.pendingModelTestType != null) {
                ModelTestTypeSheet(
                    test      = state.pendingModelTestType!!,
                    onPick    = { type -> viewModel.startModelTest(state.pendingModelTestType!!, type) },
                    onDismiss = { viewModel.dismissModelTestTypePicker() }
                )
            }
            if (state.showModelTestGenerateSheet) {
                ModelTestGenerateSheet(
                    isGenerating = state.isGeneratingModelTest,
                    onGenerate   = { type, perTest, count -> viewModel.generateLocalModelTests(type, perTest, count) },
                    onDismiss    = { viewModel.dismissModelTestGenerateSheet() }
                )
            }
        }

        // ── Result Modal ──
        state.showResult && state.result != null -> {
            // Show question list behind + result on top
            QuestionListScreen(
                viewModel      = viewModel,
                mode           = state.mode,
                subject        = state.navPath.subject ?: "",
                subTopic       = state.navPath.subTopic ?: "",
                questions      = state.questions,
                timerSec       = 0,
                totalTime      = state.totalTimeSec,
                answered       = state.answeredCount,
                currentPage    = state.currentPage,
                totalQuestions = state.totalQuestions,
                onBack         = { viewModel.navigateBack() },
                onSubmit       = {},
                currentUser    = currentUser,
                onAdminEdit    = onAdminEdit,
                onAdminDelete  = onAdminDelete
            )
            ResultModal(
                result  = state.result!!,
                onRetry = {
                    if (state.activeModelTest != null) {
                        viewModel.retryModelTest()
                    } else if (state.navPath.subject == "Mock Test") {
                        // Mock Test: navPath.subTopic আসল কোনো subTopic না (শুধু নেভিগেশন
                        // depth ঠিক রাখতে বসানো), তাই navigateToSubTopic() না করে আগের
                        // mockConfig দিয়েই নতুন র‍্যান্ডম সেট শুরু করি।
                        viewModel.retryMock()
                    } else if (state.navPath.subject == "সাল" && state.qbankSelectedYear != null) {
                        // QBank সাল-মোড: navPath আসল কোনো subject/subTopic pair না (শুধু
                        // ডেপথ ঠিক রাখতে বসানো placeholder), তাই সরাসরি ওই সালটাই আবার লোড করি।
                        viewModel.selectQBankYear(state.qbankSelectedYear!!)
                    } else {
                        // Same topic reload — QBank প্রতিষ্ঠান-মোডেও navPath আসল
                        // (designation, institution) pair-ই থাকে, তাই এটাই কাজ করে।
                        val subj = state.navPath.subject
                        val st   = state.navPath.subTopic
                        if (subj != null && st != null) {
                            viewModel.navigateBack()
                            viewModel.navigateToSubTopic(st)
                        }
                    }
                },
                onHome  = { viewModel.navigateBack() }
            )
        }

        // ── Question List (depth 2) ──
        state.navPath.depth() == 2 -> {
            QuestionListScreen(
                viewModel           = viewModel,
                mode                = state.mode,
                subject             = state.navPath.subject ?: "",
                subTopic            = state.navPath.subTopic ?: "",
                questions           = state.questions,
                timerSec            = state.timerSec,
                totalTime           = state.totalTimeSec,
                answered            = state.answeredCount,
                currentPage         = state.currentPage,
                totalQuestions      = state.totalQuestions,
                onBack              = { viewModel.navigateBack() },
                onSubmit            = { viewModel.submitQuiz() },
                currentUser         = currentUser,
                highlightQuestionId = state.highlightQuestionId,
                onHighlightConsumed = { viewModel.consumeHighlight() },
                onAdminEdit         = onAdminEdit,
                onAdminDelete       = onAdminDelete
            )
        }

        // ── SubTopic List (depth 1) ──
        state.navPath.depth() == 1 -> {
            SubTopicListScreen(
                subject     = state.navPath.subject ?: "",
                mode        = state.mode,
                subTopics   = state.subTopics,
                onSubTopic  = { viewModel.navigateToSubTopic(it) },
                onModelTest = { viewModel.openModelTestZone(it) },
                onBack      = { viewModel.navigateBack() },
                isAdmin         = state.isAdmin,
                isReorderMode   = state.isReorderMode,
                isSavingOrder   = state.isSavingOrder,
                orderSavedMsg   = state.orderSavedMsg,
                onToggleReorder = { viewModel.toggleReorderMode() },
                onMoveSubTopic  = { from, to -> viewModel.moveSubTopic(from, to) },
                onRenameSubTopic = { old, new ->
                    onAdminRenameSubTopic?.invoke(sheetKey, state.navPath.subject ?: "", old, new)
                },
                onDeleteSubTopic = { name ->
                    onAdminDeleteSubTopic?.invoke(sheetKey, state.navPath.subject ?: "", name)
                }
            )
        }

        // ── QBank প্রতিষ্ঠান-মোড, depth0, প্রতিষ্ঠান এখনো বাছাই করা হয়নি — প্রতিষ্ঠানের তালিকা ──
        mode == StudyMode.QBANK && state.qbankFilterMode == QBankFilterMode.INSTITUTION &&
            state.navPath.depth() == 0 && state.qbankSelectedInstitution == null -> {
            SubjectListScreen(
                mode       = state.mode,
                subjects   = state.qbankInstitutions,
                weakTopics = emptyList(),
                isLoading  = state.isLoading,
                error      = state.error,
                onSubject  = { viewModel.selectQBankInstitution(it) },
                onMockZone = { viewModel.openMockZone() },
                onModelTestZone = { viewModel.openModelTestPicker() },
                // ── প্রতিষ্ঠান/সাল ফিল্টার লিস্ট synthetic (Subject/SubTopic নয়) —
                // তাই এখানে Admin rename/delete/reorder বন্ধ রাখা হলো, ভুল ডেটার
                // ওপর কাজ করে ফেলার ঝুঁকি এড়াতে ──
                isAdmin         = false,
                showQBankFilterBar      = true,
                qbankFilterMode         = state.qbankFilterMode,
                onQBankFilterModeChange = { viewModel.setQBankFilterMode(it) },
                qbankSearchQuery        = state.qbankSearchQuery,
                onQBankSearchQueryChange = { viewModel.setQBankSearchQuery(it) }
            )
        }

        // ── QBank প্রতিষ্ঠান-মোড, প্রতিষ্ঠান বাছাই করা হয়েছে — এর আন্ডারে পদবীর তালিকা
        // (navPath এখনো depth0-ই, ইচ্ছাকৃতভাবে — দেখো selectQBankInstitution()) ──
        mode == StudyMode.QBANK && state.qbankFilterMode == QBankFilterMode.INSTITUTION &&
            state.navPath.depth() == 0 && state.qbankSelectedInstitution != null -> {
            SubTopicListScreen(
                subject     = state.qbankSelectedInstitution ?: "",
                mode        = state.mode,
                subTopics   = state.qbankDesignationsUnderInstitution,
                onSubTopic  = { viewModel.selectQBankDesignationUnderInstitution(it) },
                onModelTest = { viewModel.openModelTestZone(it) },
                onBack      = { viewModel.qbankFilterBack() },
                isAdmin     = false
            )
        }

        // ── QBank সাল-মোড, depth0 — সালের তালিকা (সাল বাছাই করলেই navPath সরাসরি
        // depth2-এ চলে যায় flat প্রশ্ন-লিস্ট নিয়ে, তাই এখানে দ্বিতীয় কোনো ধাপ নেই) ──
        mode == StudyMode.QBANK && state.qbankFilterMode == QBankFilterMode.YEAR &&
            state.navPath.depth() == 0 -> {
            SubjectListScreen(
                mode       = state.mode,
                subjects   = state.qbankYears,
                weakTopics = emptyList(),
                isLoading  = state.isLoading,
                error      = state.error,
                onSubject  = { viewModel.selectQBankYear(it) },
                onMockZone = { viewModel.openMockZone() },
                onModelTestZone = { viewModel.openModelTestPicker() },
                isAdmin         = false,
                showQBankFilterBar      = true,
                qbankFilterMode         = state.qbankFilterMode,
                onQBankFilterModeChange = { viewModel.setQBankFilterMode(it) },
                qbankSearchQuery        = state.qbankSearchQuery,
                onQBankSearchQueryChange = { viewModel.setQBankSearchQuery(it) }
            )
        }

        // ── Subject List (depth 0, root) — পদবী(ডিফল্ট) ফিল্টার-মোড, Quiz, Study ──
        else -> {
            SubjectListScreen(
                mode       = state.mode,
                subjects   = state.subjects,
                weakTopics = state.weakTopics,
                isLoading  = state.isLoading,
                error      = state.error,
                onSubject  = { viewModel.navigateToSubject(it) },
                onMockZone = { viewModel.openMockZone() },
                onModelTestZone = { viewModel.openModelTestPicker() },
                isAdmin         = state.isAdmin,
                isReorderMode   = state.isReorderMode,
                isSavingOrder   = state.isSavingOrder,
                orderSavedMsg   = state.orderSavedMsg,
                onToggleReorder = { viewModel.toggleReorderMode() },
                onMoveSubject   = { from, to -> viewModel.moveSubject(from, to) },
                onRenameSubject = { old, new -> onAdminRenameSubject?.invoke(sheetKey, old, new) },
                onDeleteSubject = { name -> onAdminDeleteSubject?.invoke(sheetKey, name) },
                // ── QBank-এ থাকলে ফিল্টার বার দেখাও (ডিফল্ট পদবী-চিপ সিলেক্টেড), Quiz/Study-তে না ──
                showQBankFilterBar      = mode == StudyMode.QBANK,
                qbankFilterMode         = state.qbankFilterMode,
                onQBankFilterModeChange = { viewModel.setQBankFilterMode(it) },
                qbankSearchQuery        = state.qbankSearchQuery,
                onQBankSearchQueryChange = { viewModel.setQBankSearchQuery(it) }
            )
        }
    }
}
