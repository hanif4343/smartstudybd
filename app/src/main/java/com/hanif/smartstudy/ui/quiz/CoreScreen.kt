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
    // ── Phase 6: পদবী-মোডেও (আগে "পদ") ঠিক একই কারণে (navPath এখনো depth0-ই থাকে যখন
    // পদবী বাছাই করা হয়েছে কিন্তু প্রতিষ্ঠান এখনো না, দেখো selectQBankPost()) — নইলে
    // সিস্টেম ব্যাক সরাসরি HOME এ চলে যেত ──
    val isInsideQBankPostPicker = mode == StudyMode.QBANK &&
        (state.qbankFilterMode == QBankFilterMode.POST || state.qbankFilterMode == QBankFilterMode.DESIGNATION) &&
        state.qbankSelectedPost != null
    val isInsideNav = state.isMockZone ||
                      state.isModelTestZone ||
                      state.isModelTestSubjectPicker ||
                      state.showResult ||
                      state.navPath.depth() > 0 ||
                      isInsideQBankInstitutionPicker ||
                      isInsideQBankPostPicker
    BackHandler(enabled = isInsideNav) {
        // ── QBank-এর সবকটা ফিল্টার-মোডেই (পদবী/প্রতিষ্ঠান/সাল) এখন কাস্টম back লাগে —
        // generic navigateBack() শুধু plain Subject→SubTopic হায়ারার্কি বোঝে, এই
        // মোডগুলোর উল্টানো/flat হায়ারার্কি বোঝে না। Mock/Model Test/Result-এ আগের
        // মতোই generic navigateBack() ব্যবহার হয়। ──
        val useQBankFilterBack = mode == StudyMode.QBANK &&
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
                    } else if (mode == StudyMode.QBANK &&
                               (state.qbankFilterMode == QBankFilterMode.POST || state.qbankFilterMode == QBankFilterMode.DESIGNATION) &&
                               state.navPath.subject == "পদ") {
                        // পদবী-মোড (আগে "পদ") — navPath("পদ", institutionName) placeholder, আসল
                        // subject/subTopic pair না, তাই সরাসরি সেই institution আবার লোড করি
                        val institutionName = state.navPath.subTopic
                        viewModel.navigateBack()
                        if (institutionName != null) viewModel.selectQBankInstitutionUnderPost(institutionName)
                    } else if (mode == StudyMode.QBANK && state.qbankFilterMode == QBankFilterMode.INSTITUTION) {
                        // প্রতিষ্ঠান-মোড — navPath(institution, designation) আসল pair, কিন্তু এখন
                        // ID-ভিত্তিক ফ্ল্যাট-ফেচ (selectQBankDesignationUnderInstitution) —
                        // পুরনো raw navigateToSubTopic() আর কাজ করে না (সেই টেক্সট-কলাম নেই)
                        val designationName = state.navPath.subTopic
                        viewModel.navigateBack()
                        if (designationName != null) viewModel.selectQBankDesignationUnderInstitution(designationName)
                    } else {
                        // Same topic reload — বাকি সব জায়গায় (ডিফল্ট Quiz/Study) navPath এখন
                        // Phase 6-এর লেজি টপিক-নাম, তাই navigateToSubTopicLazy() ব্যবহার করতে
                        // হবে (topicId নাম দিয়ে state.subTopics থেকে resolve করে) — নাহলে পুরনো
                        // path Room-এ কিছু না পেয়ে আবার পুরো sheet fetch করতে যেত।
                        val subj = state.navPath.subject
                        val st   = state.navPath.subTopic
                        if (subj != null && st != null) {
                            viewModel.navigateBack()
                            viewModel.navigateToSubTopicLazy(st)
                        }
                    }
                },
                onHome  = {
                    // ── FIX ("অ্যাড বন্ধ হওয়ার পর সাদা স্ক্রিন, ব্যাক বাটনও কাজ করে না"):
                    // QBank পদবী/প্রতিষ্ঠান/সাল-মোডে navPath একটা synthetic/placeholder
                    // path (আসল Subject→SubTopic হায়ারার্কি না) — generic navigateBack()
                    // এটা বুঝতে পারে না, ফলে state এমন এক অবস্থায় চলে যেত যেটা CoreScreen-এর
                    // কোনো when-branch-ই ম্যাচ করতো না (স্ক্রিন সাদা), আর BackHandler-ও
                    // ইতিমধ্যে ভাঙা navPath নিয়ে বিভ্রান্ত হয়ে যেত। এখন BackHandler-এর মতোই
                    // এখানেও চেক করে সঠিক ফাংশন কল হচ্ছে। ──
                    val useQBankFilterBack = mode == StudyMode.QBANK &&
                        !state.isMockZone && !state.isModelTestZone &&
                        !state.isModelTestSubjectPicker
                    if (useQBankFilterBack) viewModel.qbankFilterBack() else viewModel.navigateBack()
                }
            )
        }

        // ── QBank পদবী/প্রতিষ্ঠান-মোডের ৩য় লেয়ার (প্রশ্নপত্র) — এক্সাম-পেপার স্টাইল
        // রিভিউ স্ক্রিন (উত্তর সরাসরি দেখানো, MCQ বাটন/টাইমার/সাবমিট নেই) — ব্যবহারকারীর
        // দেওয়া ডিজাইন অনুযায়ী। ── FIX: এই স্টাইল শুধু "written" প্রশ্নের জন্য — MCQ
        // প্রশ্ন থাকলে (এই institution/post-এর exam paper MCQ হলে) সাধারণ ইন্টারেক্টিভ
        // কুইজ স্ক্রিনই (নিচের depth==2 branch) দেখাবে, আগের মতোই। বাকি সব QBank/Quiz/
        // Study ফ্লো আগের মতোই অপরিবর্তিত। ──
        mode == StudyMode.QBANK &&
            (state.qbankFilterMode == QBankFilterMode.POST || state.qbankFilterMode == QBankFilterMode.DESIGNATION ||
             state.qbankFilterMode == QBankFilterMode.INSTITUTION) &&
            state.navPath.depth() == 2 &&
            state.questions.any { it.questionType.equals("written", ignoreCase = true) } -> {
            val institutionOrSubject = when (state.qbankFilterMode) {
                QBankFilterMode.INSTITUTION -> state.navPath.subject ?: ""   // প্রতিষ্ঠান-মোডে navPath = (institution, designation)
                else -> state.navPath.subTopic ?: ""                        // পদবী-মোডে navPath = ("পদ", institution) placeholder
            }
            val postOrDesignation = when (state.qbankFilterMode) {
                QBankFilterMode.INSTITUTION -> state.navPath.subTopic ?: ""
                else -> state.qbankSelectedPost ?: ""
            }
            QBankExamPaperScreen(
                institutionName = institutionOrSubject,
                postName        = postOrDesignation,
                // ── শুধু written অংশ — এই institution-এ MCQ+written মিশে থাকলেও
                // MCQ গুলো এই এক্সাম-পেপার ভিউতে দেখাবে না (কুইজ-স্ক্রিনে আলাদা দেখা যাবে না
                // এখানে যেহেতু একটাই স্ক্রিন-স্লট — বর্তমানে প্রতি institution সাধারণত
                // এক টাইপেরই হয়, তবে ভবিষ্যতে মিশ্র হলে MCQ অংশও এখানেই যোগ করা যাবে) ──
                questions       = state.questions.filter { it.questionType.equals("written", ignoreCase = true) },
                onBack          = {
                    val useQBankFilterBack = !state.isMockZone && !state.isModelTestZone && !state.isModelTestSubjectPicker
                    if (useQBankFilterBack) viewModel.qbankFilterBack() else viewModel.navigateBack()
                },
                onBookmark      = { qid -> viewModel.toggleBookmark(qid) },
                onReport        = { idx, issue -> viewModel.reportQuestion(idx, issue) }
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

        // ── SubTopic List (depth 1) — Phase 6: এখন লেজি (Topics reference-টেবিল
        // থেকে, প্রশ্ন ডাউনলোড ছাড়াই) — দেখো QuizViewModel.navigateToSubjectLazy ──
        state.navPath.depth() == 1 -> {
            SubTopicListScreen(
                subject     = state.navPath.subject ?: "",
                mode        = state.mode,
                subTopics   = state.subTopics,
                onSubTopic  = { viewModel.navigateToSubTopicLazy(it) },
                reviewProgress = state.reviewProgressTopics,
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

        // ── QBank পদবী-মোড (আগে নাম ছিল "পদ"), depth0, পদবী এখনো বাছাই করা হয়নি —
        // পদবীর তালিকা। নতুন schema-র Posts/Institutions/Exam_Appearances
        // reference-টেবিল থেকে (দেখো QuizViewModel.rebuildQBankPosts) —
        // INSTITUTION মোডের প্যাটার্নেই ──
        mode == StudyMode.QBANK &&
            (state.qbankFilterMode == QBankFilterMode.POST || state.qbankFilterMode == QBankFilterMode.DESIGNATION) &&
            state.navPath.depth() == 0 && state.qbankSelectedPost == null -> {
            SubjectListScreen(
                mode       = state.mode,
                subjects   = state.qbankPosts,
                weakTopics = emptyList(),
                isLoading  = state.isLoading,
                error      = state.error,
                onSubject  = { viewModel.selectQBankPost(it) },
                onMockZone = { viewModel.openMockZone() },
                onModelTestZone = { viewModel.openModelTestPicker() },
                // ── পদবী/প্রতিষ্ঠান লিস্ট synthetic (Subject/SubTopic নয়) — তাই Admin
                // rename/delete/reorder বন্ধ, ভুল ডেটার ওপর কাজ করে ফেলার ঝুঁকি এড়াতে ──
                isAdmin         = false,
                showQBankFilterBar      = true,
                qbankFilterMode         = state.qbankFilterMode,
                onQBankFilterModeChange = { viewModel.setQBankFilterMode(it) },
                qbankSearchQuery        = state.qbankSearchQuery,
                onQBankSearchQueryChange = { viewModel.setQBankSearchQuery(it) }
            )
        }

        // ── QBank পদবী-মোড, পদবী বাছাই করা হয়েছে — এর আন্ডারে প্রতিষ্ঠানের তালিকা
        // (navPath এখনো depth0-ই, ইচ্ছাকৃতভাবে — দেখো selectQBankPost()) ──
        mode == StudyMode.QBANK &&
            (state.qbankFilterMode == QBankFilterMode.POST || state.qbankFilterMode == QBankFilterMode.DESIGNATION) &&
            state.navPath.depth() == 0 && state.qbankSelectedPost != null -> {
            SubTopicListScreen(
                subject     = state.qbankSelectedPost ?: "",
                mode        = state.mode,
                subTopics   = state.qbankInstitutionsUnderPost,
                onSubTopic  = { viewModel.selectQBankInstitutionUnderPost(it) },
                onModelTest = { viewModel.openModelTestZone(it) },
                onBack      = { viewModel.qbankFilterBack() },
                isAdmin     = false
            )
        }

        // ── Subject List (depth 0, root) — Quiz, Study (QBank-এর ৩টা ফিল্টার-মোডই
        // (পদবী/প্রতিষ্ঠান/সাল) উপরের কেসগুলোতে ধরা পড়ে, তাই QBank এখানে আসে না) —
        // Phase 6: এখন লেজি (Subjects reference-টেবিল থেকে) ──
        else -> {
            SubjectListScreen(
                mode       = state.mode,
                subjects   = state.subjects,
                weakTopics = state.weakTopics,
                isLoading  = state.isLoading,
                error      = state.error,
                onSubject  = { viewModel.navigateToSubjectLazy(it) },
                reviewProgress = state.reviewProgressSubjects,
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
