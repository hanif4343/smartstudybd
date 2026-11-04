package com.hanif.smartstudy.util

import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.data.model.StudyMode
import com.hanif.smartstudy.data.model.User
import com.hanif.smartstudy.util.AudienceFilter.forUser

/**
 * 🧪 টেস্ট-প্রবাহের কেন্দ্রীয় মডেল (পরীক্ষা মার্জ — ধাপ M2)।
 *
 * আগে মক, কুইজ-মডেল, QBank-মডেল ও লিখিত-মডেল টেস্ট চারটা আলাদা ছোট-বড় ফ্ল্যাগে চলত
 * (isMockZone, isModelTestZone, isModelTestSubjectPicker, isQBankMtZone, activeQBankMt, activeModelTest)
 * এবং ফলাফলের ইতিহাস তিন জায়গায় আলাদা লেখা হতো। এখন:
 *   • [TestKind]   — কোন ধরনের টেস্ট, ইতিহাসে কোন `source` লাগবে, গ্রেড হয় কি না
 *   • [TestStage]  — প্রবাহের কোন ধাপে আছি (একটাই মান)
 *   • [ActiveTest] — চলমান/সদ্য-শেষ টেস্টের পরিচয় (একটাই জায়গা থেকে ইতিহাস লেখা)
 *   • [TestSpec] / [TestPlan] / [TestPlanner] — "কী ধরনের টেস্ট চাই" → "কোন প্রশ্নগুলো"
 *
 * ডাটা ফরম্যাট অপরিবর্তিত: ইতিহাসের source = "mock_test" | "model_test" আগের মতোই।
 * এই ধাপে UI একই; পিকারের নতুন UI আসবে M3-এ।
 */
enum class TestKind(val historySource: String, val graded: Boolean) {
    MOCK("mock_test", true),
    QUIZ_MODEL("model_test", true),
    QBANK_MODEL("model_test", true),
    WRITTEN_MODEL("model_test", false)   // গ্রেড হয় না — শুধু জমা (recorded)
}

enum class TestStage {
    NONE,                    // টেস্ট-প্রবাহের বাইরে
    MOCK_PICKER,             // মক: বিষয়/টপিক ও সংখ্যা বাছাই
    MODEL_SUBJECT_PICKER,    // কুইজ-মডেল: বিষয় বাছাই (শিক্ষার্থী)
    MODEL_LIST,              // কুইজ-মডেল: টেস্ট-তালিকা
    QBANK_SETUP,             // QBank-মডেল: ক্যাটাগরি ও সংখ্যার সেটআপ
    RUNNING                  // টেস্ট চলছে বা ফলাফল দেখাচ্ছে
}

/** চলমান বা সদ্য-শেষ টেস্টের পরিচয় — ফলাফলের ইতিহাস লেখার জন্য যা যা লাগে */
data class ActiveTest(
    val kind   : TestKind,
    val title  : String,
    val mode   : StudyMode,          // ইতিহাসে যে মোডের নামে জমা হবে
    val topics : List<String>        // ইতিহাসের "বিষয়" লেবেল
)

/** "কী ধরনের টেস্ট চাই" — পিকার/প্রিসেট এখান থেকেই আসবে (M3) */
data class TestSpec(
    val mode           : StudyMode,
    val selectedTopics : List<String> = emptyList(),   // "Subject||SubTopic" — ফাঁকা = সব
    val count          : Int          = 25,
    val kind           : TestKind     = TestKind.MOCK
)

/** প্ল্যানারের ফল: স্পেক + বাছাই হওয়া প্রশ্ন */
data class TestPlan(val spec: TestSpec, val questions: List<QuestionItem>)

object TestPlanner {

    /** "Subject||SubTopic" কী-গুলো থেকে ইতিহাসের লেবেল ("সব বিষয় (র‍্যান্ডম)" বা "বিষয় - টপিক") */
    fun topicLabels(selectedTopics: List<String>): List<String> =
        if (selectedTopics.isEmpty()) listOf("সব বিষয় (র‍্যান্ডম)")
        else selectedTopics.map { key ->
            val parts = key.split("||")
            val subj  = parts.getOrNull(0) ?: ""
            val sub   = parts.getOrNull(1)
            if (!sub.isNullOrBlank()) "$subj - $sub" else subj
        }

    /**
     * মক টেস্টের প্রশ্ন বাছাই। বিশুদ্ধ ফাংশন (ViewModel/Android ছাড়া) — তাই সহজে টেস্ট করা যায়।
     *  1) পুল সবসময় অডিয়েন্স-ছাঁকা (forUser) — অন্য অডিয়েন্সের প্রশ্ন ঢোকে না
     *  2) selectedTopics ফাঁকা হলে ছাঁকা পুলের সবটা, নইলে শুধু বাছাই করা বিষয়/টপিক
     *  3) shuffle → count পর্যন্ত
     */
    fun planMock(
        content  : AppContent,
        user     : User?,
        adminTag : String,
        spec     : TestSpec,
        shuffle  : (List<QuestionItem>) -> List<QuestionItem> = { it.shuffled() }
    ): TestPlan {
        val visible = content.forUser(user, adminTag)
        val pool = when (spec.mode) {
            StudyMode.QUIZ  -> visible.quiz.map  { QuestionItem.fromQuizItem(it) }
            StudyMode.QBANK -> visible.qbank.map { QuestionItem.fromQBankItem(it) }
            StudyMode.STUDY -> visible.study.map { QuestionItem.fromStudyItem(it) }
        }
        val picked = if (spec.selectedTopics.isEmpty()) pool
        else pool.filter { q ->
            spec.selectedTopics.any { key ->
                val parts = key.split("||")
                q.subject == parts.getOrNull(0) && q.subTopic == parts.getOrNull(1)
            }
        }
        return TestPlan(spec, shuffle(picked).take(spec.count))
    }
}
