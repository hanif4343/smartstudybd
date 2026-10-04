package com.hanif.smartstudy.data.local

import com.hanif.smartstudy.data.model.QBankItem
import com.hanif.smartstudy.data.model.QuizItem
import com.hanif.smartstudy.data.model.StudyItem
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.data.model.SubjectRef
import com.hanif.smartstudy.data.model.TopicRef
import com.hanif.smartstudy.data.model.SubTopicRef
import com.hanif.smartstudy.data.model.TagRef
import com.hanif.smartstudy.data.model.PostRef
import com.hanif.smartstudy.data.model.InstitutionRef
import com.hanif.smartstudy.data.model.ExamAppearanceRef

// ── Firebase model → Room Entity ─────────────────────────────────────────────

fun QuizItem.toEntity(syncedAt: Long = System.currentTimeMillis()) = QuestionEntity(
    sheet        = "QUIZ",
    fbKey        = id ?: "",
    subject      = subject ?: "",
    subTopic     = subTopic ?: "",
    question     = question ?: "",
    optionA      = optionA ?: "",
    optionB      = optionB ?: "",
    optionC      = optionC ?: "",
    optionD      = optionD ?: "",
    answer       = answer ?: "",
    explanation  = explanation ?: "",
    explanationIsPublic = (explanationVisibility?.lowercase()?.trim() != "private"),
    technique    = technique ?: "",
    questionType = questionType ?: "mcq",
    audienceTags = audienceTags ?: "",
    imageUrl     = imageUrl ?: "",
    visualUrl    = visualUrl ?: "",
    subjectId    = subjectId ?: "",
    topicId      = topicId ?: "",
    groupId      = groupId ?: "",
    subIndex     = subIndex?.toIntOrNull() ?: 0,
    groupHeading = groupHeading ?: "",
    formatStyle  = formatStyle ?: "",
    important    = important == true,
    reviewed     = reviewed?.lowercase()?.trim() == "true",
    reviewedAt   = reviewedAt?.toLongOrNull() ?: 0L,
    syncedAt     = syncedAt
)

// ── Phase 10: নষ্ট/ফাঁকা সারি থেকে অ্যাপ বাঁচানো ──
// CDN থেকে আসা কোনো সারি স্পষ্টভাবে ব্যবহার-অযোগ্য হলে (আইডি নেই, বা Quiz/QBank-এ প্রশ্ন/ছবি
// কিছুই নেই, বা শুধু placeholder "প্রশ্ন") সেটা Room-এ লেখা হয় না। এটা কোনো প্রশ্ন "ঠিক করে" না,
// আন্দাজও করে না — শুধু অকেজো সারি এড়ায়। Unidentified subject/topic-এর প্রশ্ন বাদ যায় না।
// STUDY শীটে ফাঁকা question বৈধ (শুধু ব্যাখ্যা/নোট থাকতে পারে), তাই সেখানে শুধু আইডি চেক হয়।
private val PLACEHOLDER_QUESTIONS = setOf("প্রশ্ন", "প্রশ্ন:", "Question", "question")

fun QuestionEntity.isUsable(): Boolean {
    if (fbKey.isBlank()) return false
    if (sheet.equals("STUDY", ignoreCase = true)) return true
    val hasMedia = imageUrl.isNotBlank() || visualUrl.isNotBlank() || questionPaperUrls.isNotBlank()
    val q = question.trim()
    if (q.isBlank()) return hasMedia
    if (q in PLACEHOLDER_QUESTIONS && !hasMedia) return false
    return true
}

fun QBankItem.toEntity(syncedAt: Long = System.currentTimeMillis()) = QuestionEntity(
    sheet        = "QBANK",
    fbKey        = id ?: "",
    subject      = subject ?: "",
    subTopic     = subTopic ?: "",
    question     = question ?: "",
    optionA      = optionA ?: "",
    optionB      = optionB ?: "",
    optionC      = optionC ?: "",
    optionD      = optionD ?: "",
    answer       = answer ?: "",
    explanation  = explanation ?: "",
    explanationIsPublic = (explanationVisibility?.lowercase()?.trim() != "private"),
    technique    = technique ?: "",
    questionType = questionType ?: "mcq",
    audienceTags = audienceTags ?: "",
    year         = year ?: "",
    examName     = examName ?: "",
    imageUrl     = imageUrl ?: "",
    visualUrl    = visualUrl ?: "",
    questionPaperUrls = questionPaperUrls ?: "",
    subjectId    = subjectId ?: "",
    topicId      = topicId ?: "",
    subtopicId   = subtopicId ?: "",
    groupId      = groupId ?: "",
    subIndex     = subIndex?.toIntOrNull() ?: 0,
    groupHeading = groupHeading ?: "",
    formatStyle  = formatStyle ?: "",
    important    = important == true,
    reviewed     = reviewed?.lowercase()?.trim() == "true",
    reviewedAt   = reviewedAt?.toLongOrNull() ?: 0L,
    syncedAt     = syncedAt
)

fun StudyItem.toEntity(syncedAt: Long = System.currentTimeMillis()) = QuestionEntity(
    sheet        = "STUDY",
    fbKey        = id ?: "",
    subject      = subject ?: "",
    subTopic     = subTopic ?: "",
    question     = question ?: "",
    answer       = answer ?: correct ?: "",
    explanation  = explanation ?: "",
    explanationIsPublic = (explanationVisibility?.lowercase()?.trim() != "private"),
    technique    = technique ?: "",
    questionType = questionType ?: "study",
    audienceTags = audienceTags ?: "",
    visualUrl    = visualUrl ?: "",
    subjectId    = subjectId ?: "",
    topicId      = topicId ?: "",
    groupId      = groupId ?: "",
    subIndex     = subIndex?.toIntOrNull() ?: 0,
    groupHeading = groupHeading ?: "",   // StudyItem-এ formatStyle/important নেই (দেখো StudyContent.kt)
    reviewed     = reviewed?.lowercase()?.trim() == "true",
    reviewedAt   = reviewedAt?.toLongOrNull() ?: 0L,
    syncedAt     = syncedAt
)

// ── GAS getReferenceData model → Room Entity (Phase 6) ───────────────────────

fun SubjectRef.toEntity() = SubjectEntity(
    subjectId = subjectId ?: "",
    name      = name ?: "",
    sheet     = sheet ?: "",
    tagId     = tagId ?: ""
)

fun TopicRef.toEntity() = TopicEntity(
    topicId   = topicId ?: "",
    subjectId = subjectId ?: "",
    name      = name ?: "",
    // rowStart/rowCount এখন String? (দেখো ReferenceModels.kt-এর কমেন্ট) — "" বা অন্য
    // অ-সংখ্যা মান এলে toIntOrNull() null দেয়, তখন ডিফল্ট 0 (মানে "এই topic-এ এখনো
    // index/প্রশ্ন নেই", crash না করে)
    sortOrder     = sortOrder?.trim()?.toDoubleOrNull()?.toInt() ?: 0,
    rowStart      = rowStart?.toIntOrNull() ?: 0,
    rowCount      = rowCount?.toIntOrNull() ?: 0,      // legacy — fallback-only
    rowCountQuiz  = rowCountQuiz?.toIntOrNull()  ?: 0,
    rowCountQbank = rowCountQbank?.toIntOrNull() ?: 0,
    rowCountStudy = rowCountStudy?.toIntOrNull() ?: 0
)

fun SubTopicRef.toEntity() = SubTopicEntity(
    subtopicId = subtopicId ?: "",
    topicId    = topicId ?: "",
    name       = name ?: ""
)

fun TagRef.toEntity() = TagEntity(
    tagId = tagId ?: "",
    name  = name ?: ""
)

fun PostRef.toEntity() = PostEntity(
    postId = postId ?: "",
    name   = name ?: ""
)

fun InstitutionRef.toEntity() = InstitutionEntity(
    institutionId = institutionId ?: "",
    name          = name ?: ""
)

fun ExamAppearanceRef.toEntity() = ExamAppearanceEntity(
    appearanceId  = appearanceId ?: "",
    questionId    = questionId ?: "",
    postId        = postId ?: "",
    institutionId = institutionId ?: "",
    year          = year ?: ""
)

// ── Room Entity → QuizItem/QBankItem/StudyItem (Speed Plan Task 3.5) ──────────
// ── FIX ("Sheet er group heading nai"): groupHeading/formatStyle/important এখন
// QuestionEntity-তে persist হয় (দেখো QuestionEntity.kt/AppDatabase.kt v16 কমেন্ট),
// তাই এই তিনটা reverse-conversion-এও সঠিকভাবে ফেরত বসানো হলো। updatedAt/newId
// এখনো নেই (delta-sync/admin-edit key resolve ছাড়া অন্য কোথাও দরকার হয় না বলে
// রাখা হয়নি) — fbKey-ই newId হিসেবে ব্যবহার হয়, যা আগেও ছিল। ──

fun QuestionEntity.toQuizItem() = QuizItem(
    id = fbKey, newId = fbKey, subject = subject, subTopic = subTopic, question = question,
    optionA = optionA, optionB = optionB, optionC = optionC, optionD = optionD,
    answer = answer, explanation = explanation,
    explanationVisibility = if (explanationIsPublic) "public" else "private",
    questionType = questionType, technique = technique, audienceTags = audienceTags,
    imageUrl = imageUrl, visualUrl = visualUrl, important = important,
    subjectId = subjectId, topicId = topicId, groupId = groupId,
    subIndex = if (subIndex != 0) subIndex.toString() else "",
    groupHeading = groupHeading, formatStyle = formatStyle,
    reviewed = reviewed.toString(), reviewedAt = if (reviewedAt != 0L) reviewedAt.toString() else ""
)

fun QuestionEntity.toQBankItem() = QBankItem(
    id = fbKey, newId = fbKey, subject = subject, subTopic = subTopic, question = question,
    optionA = optionA, optionB = optionB, optionC = optionC, optionD = optionD,
    answer = answer, explanation = explanation,
    explanationVisibility = if (explanationIsPublic) "public" else "private",
    technique = technique, questionType = questionType, audienceTags = audienceTags,
    year = year, examName = examName, imageUrl = imageUrl, visualUrl = visualUrl,
    questionPaperUrls = questionPaperUrls, important = important,
    subjectId = subjectId, topicId = topicId, subtopicId = subtopicId, groupId = groupId,
    subIndex = if (subIndex != 0) subIndex.toString() else "",
    groupHeading = groupHeading, formatStyle = formatStyle,
    reviewed = reviewed.toString(), reviewedAt = if (reviewedAt != 0L) reviewedAt.toString() else ""
)

fun QuestionEntity.toStudyItem() = StudyItem(
    id = fbKey, newId = fbKey, subject = subject, subTopic = subTopic, question = question,
    answer = answer, correct = answer, explanation = explanation,
    explanationVisibility = if (explanationIsPublic) "public" else "private",
    technique = technique, questionType = questionType, audienceTags = audienceTags,
    visualUrl = visualUrl,
    subjectId = subjectId, topicId = topicId, groupId = groupId,
    subIndex = if (subIndex != 0) subIndex.toString() else "",
    groupHeading = groupHeading,   // StudyItem-এ formatStyle/important নেই
    reviewed = reviewed.toString(), reviewedAt = if (reviewedAt != 0L) reviewedAt.toString() else ""
)

// ── Room Entity → QuestionItem (domain model) ─────────────────────────────────

fun QuestionEntity.toQuestionItem() = QuestionItem(
    id           = fbKey,
    subject      = subject,
    subTopic     = subTopic,
    question     = question,
    optionA      = optionA,
    optionB      = optionB,
    optionC      = optionC,
    optionD      = optionD,
    answer       = answer,
    explanation  = explanation,
    explanationIsPublic = explanationIsPublic,
    technique    = technique,
    questionType = questionType,
    audienceTags = audienceTags,
    year         = year,
    examName     = examName,
    imageUrl     = imageUrl,
    visualUrl    = visualUrl,
    questionPaperUrls = questionPaperUrls,
    subjectId    = subjectId,
    topicId      = topicId,
    subtopicId   = subtopicId,
    groupId      = groupId,
    subIndex     = subIndex,
    groupHeading = groupHeading,
    formatStyle  = formatStyle,
    isImportant  = important,
    reviewed     = reviewed,
    reviewedAt   = reviewedAt,
    // ── FIX: আগে এখানে sourceSheet সেট করা হতো না, তাই Room-first fast-path
    // (navigateToSubTopic → loadQuestionsFromRoom) থেকে লোড হওয়া প্রতিটি
    // QuestionItem-এর sourceSheet খালি ("") থেকে যেত। AdminFieldEditDialog তখন
    // sourceSheet খালি দেখে পুরনো fragile year/examName heuristic-এ fallback
    // করতো, আর QBank প্রশ্ন প্রায়ই ভুলভাবে "Quiz" হিসেবে patch হতো — টোস্ট
    // "✅ সংরক্ষিত" দেখাতো (কারণ patchContentAndPersist exception ছাড়াই চলতো)
    // কিন্তু আসল QBank list-এ কোনো পরিবর্তন দেখা যেত না, কারণ ভুল array
    // ("quiz") patch হচ্ছিল। entity.sheet ("QUIZ"/"QBANK"/"STUDY") থেকে সঠিক
    // "Quiz"/"QBank"/"Study" বসানো হলো। ──
    sourceSheet  = when (sheet.uppercase()) {
        "QUIZ"  -> "Quiz"
        "QBANK" -> "QBank"
        "STUDY" -> "Study"
        else    -> ""
    }
)
