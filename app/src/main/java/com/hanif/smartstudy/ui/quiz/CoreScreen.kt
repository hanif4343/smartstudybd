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
    onAdminDelete         : ((sheet: String, rowKey: String, preview: String) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val ctx   = LocalContext.current
    val currentUser = remember { SessionManager(ctx).getCurrentUser() }

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
    val isInsideNav = state.isMockZone ||
                      state.isModelTestZone ||
                      state.isModelTestSubjectPicker ||
                      state.showResult ||
                      state.navPath.depth() > 0
    BackHandler(enabled = isInsideNav) {
        viewModel.navigateBack()
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
                    } else {
                        // Same topic reload
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
                onMoveSubTopic  = { from, to -> viewModel.moveSubTopic(from, to) }
            )
        }

        // ── Subject List (depth 0, root) ──
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
                onMoveSubject   = { from, to -> viewModel.moveSubject(from, to) }
            )
        }
    }
}
