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
    onAdminEdit           : ((sheet: String, rowKey: String, fields: Map<String, String>, preview: String) -> Unit)? = null
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

        // ── Result Modal ──
        state.showResult && state.result != null -> {
            // Show question list behind + result on top
            QuestionListScreen(
                viewModel   = viewModel,
                mode        = state.mode,
                subject     = state.navPath.subject ?: "",
                subTopic    = state.navPath.subTopic ?: "",
                questions   = state.questions,
                timerSec    = 0,
                totalTime   = state.totalTimeSec,
                answered    = state.answeredCount,
                onBack      = { viewModel.navigateBack() },
                onSubmit    = {},
                currentUser = currentUser,
                onAdminEdit = onAdminEdit
            )
            ResultModal(
                result  = state.result!!,
                onRetry = {
                    // Same topic reload
                    val subj = state.navPath.subject ?: return@ResultModal
                    val st   = state.navPath.subTopic ?: return@ResultModal
                    viewModel.navigateBack()
                    viewModel.navigateToSubTopic(st)
                },
                onHome  = { viewModel.navigateBack() }
            )
        }

        // ── Question List (depth 2) ──
        state.navPath.depth() == 2 -> {
            QuestionListScreen(
                viewModel   = viewModel,
                mode        = state.mode,
                subject     = state.navPath.subject ?: "",
                subTopic    = state.navPath.subTopic ?: "",
                questions   = state.questions,
                timerSec    = state.timerSec,
                totalTime   = state.totalTimeSec,
                answered    = state.answeredCount,
                onBack      = { viewModel.navigateBack() },
                onSubmit    = { viewModel.submitQuiz() },
                currentUser = currentUser,
                highlightQuestionId = state.highlightQuestionId,
                onHighlightConsumed = { viewModel.consumeHighlight() },
                onAdminEdit = onAdminEdit
            )
        }

        // ── SubTopic List (depth 1) ──
        state.navPath.depth() == 1 -> {
            SubTopicListScreen(
                subject    = state.navPath.subject ?: "",
                mode       = state.mode,
                subTopics  = state.subTopics,
                onSubTopic = { viewModel.navigateToSubTopic(it) },
                onBack     = { viewModel.navigateBack() }
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
                onMockZone = { viewModel.openMockZone() }
            )
        }
    }
}
