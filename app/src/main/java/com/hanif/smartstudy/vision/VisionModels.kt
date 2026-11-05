package com.hanif.smartstudy.vision

data class VisionMcq(
    val id: String,
    val topicId: String,
    val question: String,
    val options: List<String>,
    val answerIndex: Int,
    val shortExplain: String,
    val deepExplain: String,
    val trapNote: String,
    val source: String,
    val difficulty: String
)

data class VisionTopic(
    val id: String,
    val name: String,
    val group: String,
    val description: String
)

data class VisionWrittenPrompt(
    val id: String,
    val topicId: String,
    val title: String,
    val prompt: String,
    val marks: Int,
    val modelPoints: List<String>,
    val modelAnswer: String
)

data class VisionPaperSection(
    val heading: String,
    val items: List<VisionMcq>
)

data class VisionPaper(
    val id: String,
    val title: String,
    val year: String,
    val sections: List<VisionPaperSection>
)

data class VisionProfile(
    val exam: String,
    val examDateMs: Long,
    val dailyMinutes: Int,
    val baselineDone: Boolean,
    val baselineCorrect: Int,
    val baselineTotal: Int
)

data class VisionTopicScore(
    val topicId: String,
    val correct: Int,
    val total: Int
)

data class VisionMockAttempt(
    val dateMs: Long,
    val total: Int,
    val correct: Int,
    val perTopic: List<VisionTopicScore>,
    val guessedCount: Int,
    val avgSeconds: Int
)

data class VisionWrittenCheck(
    val promptId: String,
    val checkedPoints: List<Int>,
    val note: String,
    val dateMs: Long
)
