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
    reviewed     = reviewed?.lowercase()?.trim() == "true",
    reviewedAt   = reviewedAt?.toLongOrNull() ?: 0L,
    syncedAt     = syncedAt
)

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
    rowStart  = rowStart?.toIntOrNull() ?: 0,
    rowCount  = rowCount?.toIntOrNull() ?: 0
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
