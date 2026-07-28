package com.hanif.smartstudy.data.local

import com.hanif.smartstudy.data.model.QBankItem
import com.hanif.smartstudy.data.model.QuizItem
import com.hanif.smartstudy.data.model.StudyItem
import com.hanif.smartstudy.data.model.QuestionItem

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
    syncedAt     = syncedAt
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
