package com.hanif.smartstudy.ui.quiz

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.viewmodel.QuizViewModel

@Composable
fun CoreScreen(
    mode                  : StudyMode,
    viewModel             : QuizViewModel = viewModel(),
    onAchievementUnlocked : (Achievement) -> Unit = {},
    onStreakUpdated       : (Int) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    val achievement by viewModel.pendingAchievement.collectAsState()
    val streak      by viewModel.pendingStreak.collectAsState()

    LaunchedEffect(achievement) {
        achievement?.let { onAchievementUnlocked(it); viewModel.consumeAchievement() }
    }
    LaunchedEffect(streak) {
        if (streak > 0) { onStreakUpdated(streak); viewModel.consumeStreak() }
    }

    // CoreScreen নিজে setMode call করে না — MainScreen থেকে সঠিক VM আসে

    when {
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

        state.showResult && state.result != null -> {
            QuestionListScreen(
                viewModel = viewModel,
                mode      = state.mode,
                subject   = state.navPath.subject ?: "",
                subTopic  = state.navPath.subTopic ?: "",
                questions = state.questions,
                timerSec  = 0,
                totalTime = state.totalTimeSec,
                answered  = state.answeredCount,
                onBack    = { viewModel.navigateBack() },
                onSubmit  = {}
            )
            ResultModal(
                result  = state.result!!,
                onRetry = {
                    val subj = state.navPath.subject ?: return@ResultModal
                    val st   = state.navPath.subTopic ?: return@ResultModal
                    viewModel.navigateBack()
                    viewModel.navigateToSubTopic(st)
                },
                onHome  = { viewModel.navigateBack() }
            )
        }

        state.navPath.depth() == 2 -> {
            QuestionListScreen(
                viewModel = viewModel,
                mode      = state.mode,
                subject   = state.navPath.subject ?: "",
                subTopic  = state.navPath.subTopic ?: "",
                questions = state.questions,
                timerSec  = state.timerSec,
                totalTime = state.totalTimeSec,
                answered  = state.answeredCount,
                onBack    = { viewModel.navigateBack() },
                onSubmit  = { viewModel.submitQuiz() }
            )
        }

        state.navPath.depth() == 1 -> {
            SubTopicListScreen(
                subject    = state.navPath.subject ?: "",
                mode       = state.mode,
                subTopics  = state.subTopics,
                onSubTopic = { viewModel.navigateToSubTopic(it) },
                onBack     = { viewModel.navigateBack() }
            )
        }

        else -> {
            SubjectListScreen(
                mode       = state.mode,
                subjects   = state.subjects,
                weakTopics = state.weakTopics,
                isLoading  = state.isLoading,
                onSubject  = { viewModel.navigateToSubject(it) },
                onMockZone = { viewModel.openMockZone() }
            )
        }
    }
}
