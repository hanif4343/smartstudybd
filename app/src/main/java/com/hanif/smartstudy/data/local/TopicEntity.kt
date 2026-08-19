package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * "Topics" reference-টেবিলের local cache — প্রতিটা row একটা Subject-এর আন্ডারে একটা Topic।
 * rowStart/rowCount — GAS-এর `rebuildIndex` action Topics ট্যাবে বসায় (Quiz/QBank/Study
 * sheet-এ ওই topic_id-এর row-range) — future `getQuestionsPage` GAS pagination call-এ
 * পুরো sheet স্ক্যান না করে সরাসরি এই range থেকে পড়ার জন্য কাজে লাগবে।
 *
 * ── FIX ("টপিকে ভুল প্রশ্ন-সংখ্যা দেখাচ্ছে — Quiz-এ ঢুকলে ভিতরে সংখ্যা মিলছে না"):
 * একই topic_id Quiz/QBank/Study — তিনটা sheet-এই আলাদা আলাদা সংখ্যক প্রশ্ন থাকতে
 * পারে (GAS-এর rebuildIndex কমেন্ট দেখো — "কোনো topic_id একাধিক শিটে থাকলে")। আগে
 * শুধু একটা mode-নিরপেক্ষ `rowCount` কলাম ছিল, যেটাতে rebuildIndex আসলে Quiz→QBank→
 * Study — এই ক্রমে প্রসেস করে সবার শেষে Study-এর কাউন্ট বসিয়ে দিতো (legacy generic
 * কলাম, একবারই, শেষ শিট জেতে)। ফলে Quiz/QBank মোডে ব্রাউজ করলেও আসলে Study-sheet-এর
 * কাউন্ট দেখানো হতো — এই জন্যই "Article: 74 প্রশ্ন" দেখাতো অথচ Quiz শুরু করলে ভিতরে
 * মাত্র ২৩টা প্রশ্ন থাকতো (২৩ ছিল Quiz sheet-এর real count, ৭৪ ছিল Study sheet-এর)।
 * এখন তিনটা sheet-এর জন্য আলাদা কলাম রাখা হলো, আর কোন কলাম দেখাতে হবে সেটা বর্তমান
 * StudyMode অনুযায়ী ঠিক হয় (দেখো QuizViewModel.navigateToSubjectLazy)। পুরনো generic
 * `rowCount` কলামটা backward-compat fallback হিসেবে থেকে গেল (নতুন কলাম ফাঁকা/অমিল
 * হলে এটাই ব্যবহার হবে)। ──
 */
@Entity(
    tableName = "topics",
    primaryKeys = ["topicId"],
    indices = [Index(value = ["subjectId"])]
)
data class TopicEntity(
    val topicId        : String = "",
    val subjectId       : String = "",
    val name            : String = "",
    val rowStart        : Int = 0,
    val rowCount        : Int = 0,   // legacy/generic — fallback-only, নতুন কোডে ব্যবহার করা উচিত না
    val rowCountQuiz    : Int = 0,
    val rowCountQbank   : Int = 0,
    val rowCountStudy   : Int = 0
)
