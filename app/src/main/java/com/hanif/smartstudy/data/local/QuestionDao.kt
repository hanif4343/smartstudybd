package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QuestionDao {

    // ── Insert / Upsert ──────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(questions: List<QuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(question: QuestionEntity)

    // ── FIX (Speed Plan Task 3.5): "getContent() progressive হবে" — এখন পর্যন্ত
    // Room-এ যতটুকু cache হয়েছে (যেসব topic ইউজার ভিজিট করেছে) তার সবটা একসাথে —
    // পুরো sheet না, শুধু যা আছে তাই। CDN থেকে নতুন topic যোগ হতে থাকলে এই লিস্টও
    // ধীরে ধীরে বড় হবে, কোনো bulk GAS/CDN fetch ছাড়াই।
    @Query("SELECT * FROM questions WHERE sheet = :sheet")
    suspend fun getAll(sheet: String): List<QuestionEntity>

    // ── Subject list (distinct) ───────────────────────────────────────────────
    @Query("SELECT DISTINCT subject FROM questions WHERE sheet = :sheet AND subject != '' ORDER BY subject")
    suspend fun getSubjects(sheet: String): List<String>

    // ── SubTopic list for a subject ───────────────────────────────────────────
    @Query("""
        SELECT DISTINCT subTopic FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic != '' 
        ORDER BY subTopic
    """)
    suspend fun getSubTopics(sheet: String, subject: String): List<String>

    // ── Question count per subject ────────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM questions WHERE sheet = :sheet AND subject = :subject")
    suspend fun countBySubject(sheet: String, subject: String): Int

    // ── Question count per subTopic ───────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM questions WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic")
    suspend fun countBySubTopic(sheet: String, subject: String, subTopic: String): Int

    // ── PAGINATED questions for a subTopic — এটাই সবচেয়ে গুরুত্বপূর্ণ query ──
    // LIMIT + OFFSET দিয়ে শুধু একটা page আনে — সব ১০,০০০ প্রশ্ন মেমরিতে আনতে হয় না
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic
        ORDER BY fbKey
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPagedQuestions(
        sheet   : String,
        subject : String,
        subTopic: String,
        limit   : Int,
        offset  : Int
    ): List<QuestionEntity>

    // ── ALL questions for a subTopic (quiz mode-এ shuffle এর জন্য) ──────────
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic
        ORDER BY fbKey
    """)
    suspend fun getAllForSubTopic(sheet: String, subject: String, subTopic: String): List<QuestionEntity>

    // ── FIX ("সঠিক উত্তর দেওয়া প্রশ্ন পরে আর নিচে যায় না" বাগ, root cause): আগে
    // getPagedQuestionsFiltered (নিচে) দিয়ে SQL LIMIT/OFFSET-এ *আগে* একটা ফিক্সড
    // পেজ (৫০টা) আনা হতো, তারপর ViewModel-এ isMastered দিয়ে sort হতো — কিন্তু সেই
    // sort শুধু ওই ৫০টার ভেতরেই কাজ করত, অন্য পেজের প্রশ্নের সাথে তুলনা হতো না।
    // ফলে একটা টপিকে অনেক পেজ থাকলে, সঠিক-উত্তর-দেওয়া প্রশ্নগুলো নিজের পেজের
    // শেষেই আটকে থাকত, পরের পেজের নতুন/ভুল প্রশ্ন কখনো ১ম পেজে উঠে আসত না। এই
    // নতুন all-fetch (audience-filtered, কিন্তু LIMIT/OFFSET ছাড়া) দিয়ে
    // ViewModel পুরো টপিকের সব প্রশ্ন একসাথে এনে গ্লোবালি sort করে, *তারপর*
    // পেজ কাটে — এখন সব পেজ জুড়েই সঠিক ঠিকমতো "নিচে" যায়। ──
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
        ORDER BY fbKey
    """)
    suspend fun getAllForSubTopicFiltered(sheet: String, subject: String, subTopic: String, tag: String): List<QuestionEntity>

    // ── Audience-filtered paginated query ────────────────────────────────────
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
        ORDER BY fbKey
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPagedQuestionsFiltered(
        sheet   : String,
        subject : String,
        subTopic: String,
        tag     : String,
        limit   : Int,
        offset  : Int
    ): List<QuestionEntity>

    // ── Count filtered questions (total pages জানার জন্য) ────────────────────
    @Query("""
        SELECT COUNT(*) FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
    """)
    suspend fun countFiltered(sheet: String, subject: String, subTopic: String, tag: String): Int

    // ── SubjectEntry data (subject + count) একসাথে ───────────────────────────
    @Query("""
        SELECT subject, COUNT(*) as count FROM questions 
        WHERE sheet = :sheet AND subject != ''
        GROUP BY subject
        ORDER BY subject
    """)
    suspend fun getSubjectCounts(sheet: String): List<SubjectCount>

    // ── SubTopicEntry data ────────────────────────────────────────────────────
    @Query("""
        SELECT subTopic, COUNT(*) as count FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic != ''
        GROUP BY subTopic
        ORDER BY subTopic
    """)
    suspend fun getSubTopicCounts(sheet: String, subject: String): List<SubTopicCount>

    // ═════════════════════════════════════════════════════════════════════════
    // QBank-only ফিল্টার: "প্রতিষ্ঠান-ভিত্তিক" ও "সাল-ভিত্তিক" — এই দুটোতে
    // সাধারণ subject/subTopic হায়ারার্কি উল্টে বা পাশ কাটিয়ে দেখতে হয়, তাই
    // subject-নিরপেক্ষভাবে subTopic (Institution) বা year ধরে group করা লাগে।
    // ═════════════════════════════════════════════════════════════════════════

    // ── প্রতিষ্ঠান তালিকা (subject-নিরপেক্ষ, সব ডিজিগনেশন মিলিয়ে unique subTopic) —
    // SubjectCount ক্লাসই রিইউজ করা হলো (subTopic কে "subject" কলাম নামে alias করে) ──
    @Query("""
        SELECT subTopic as subject, COUNT(*) as count FROM questions
        WHERE sheet = :sheet AND subTopic != ''
        GROUP BY subTopic
        ORDER BY subTopic
    """)
    suspend fun getInstitutionCounts(sheet: String): List<SubjectCount>

    // ── একটা নির্দিষ্ট প্রতিষ্ঠানের আন্ডারে যত পদবী (Designation) আছে —
    // SubTopicCount ক্লাস রিইউজ করা হলো (subject কে "subTopic" কলাম নামে alias করে) ──
    @Query("""
        SELECT subject as subTopic, COUNT(*) as count FROM questions
        WHERE sheet = :sheet AND subTopic = :institution AND subject != ''
        GROUP BY subject
        ORDER BY subject
    """)
    suspend fun getDesignationsUnderInstitution(sheet: String, institution: String): List<SubTopicCount>

    // ── সালের তালিকা (subject/subTopic-নিরপেক্ষ, সব মিলিয়ে unique year) ──
    @Query("""
        SELECT year as subject, COUNT(*) as count FROM questions
        WHERE sheet = :sheet AND year != ''
        GROUP BY year
        ORDER BY year DESC
    """)
    suspend fun getYearCounts(sheet: String): List<SubjectCount>

    // ── সাল-ভিত্তিক flat প্রশ্ন-লিস্ট (subject/subTopic নির্বিশেষে) — পেজিনেটেড ──
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND year = :year
        ORDER BY fbKey
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPagedByYear(sheet: String, year: String, limit: Int, offset: Int): List<QuestionEntity>

    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND year = :year
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
        ORDER BY fbKey
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPagedByYearFiltered(sheet: String, year: String, tag: String, limit: Int, offset: Int): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE sheet = :sheet AND year = :year")
    suspend fun countByYear(sheet: String, year: String): Int

    @Query("""
        SELECT COUNT(*) FROM questions WHERE sheet = :sheet AND year = :year
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
    """)
    suspend fun countByYearFiltered(sheet: String, year: String, tag: String): Int

    // ── DB তে কতটা data আছে ──────────────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM questions WHERE sheet = :sheet")
    suspend fun countAll(sheet: String): Int

    // ── সর্বশেষ sync time (delta sync এর জন্য) ──────────────────────────────
    @Query("SELECT MAX(syncedAt) FROM questions WHERE sheet = :sheet")
    suspend fun getLastSyncTime(sheet: String): Long?

    // ── পুরো sheet মুছো (full refresh) ──────────────────────────────────────
    @Query("DELETE FROM questions WHERE sheet = :sheet")
    suspend fun deleteSheet(sheet: String)

    // ── FIX ("ডিলিট করলে অ্যাপে সাথে সাথে হারিয়ে যায় না" বাগ): Admin ডিলিট করলে
    // আগে শুধু পুরনো bulk cache-এ patch হতো, Room-এর topicId-ভিত্তিক ক্যাশ (আসল
    // টপিক-স্ক্রিন যেটা পড়ে) অস্পর্শিত থেকে যেত — Sheet থেকে সত্যিই ডিলিট হয়ে গেলেও
    // অ্যাপে প্রশ্নটা দেখা যেতেই থাকতো যতক্ষণ না পুরো টপিক আবার রিফ্রেশ হয়। এই query
    // দিয়ে এখন Room থেকেও একই সাথে সরাসরি ডিলিট হয়ে যায়। ──
    @Query("DELETE FROM questions WHERE sheet = :sheet AND fbKey = :fbKey")
    suspend fun deleteByFbKey(sheet: String, fbKey: String)

    // ── FIX ("সাবজেক্ট/টপিক ডিলিট হচ্ছে না" বাগ): Admin যখন একটা পুরো Subject/SubTopic
    // ডিলিট করে (তার আন্ডারের সব প্রশ্নসহ) — deleteByFbKey-এর মতোই সাথে সাথে Room থেকে
    // বাদ দেওয়া দরকার, নাহলে টপিক-স্ক্রিন (যেটা topicId/subject/subTopic দিয়ে Room পড়ে)
    // পুরনো প্রশ্নই দেখাতে থাকে যতক্ষণ না পুরো টপিক আবার রিফ্রেশ হয়। ──
    @Query("DELETE FROM questions WHERE sheet = :sheet AND subject = :subject")
    suspend fun deleteBySubject(sheet: String, subject: String)

    @Query("DELETE FROM questions WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic")
    suspend fun deleteBySubjectAndSubTopic(sheet: String, subject: String, subTopic: String)

    // ── Admin "Move" (ফাইল ম্যানেজারের মতো) — নির্দিষ্ট কয়েকটা প্রশ্ন (fbKey list)
    // অথবা একটা গোটা Topic (topicId দিয়ে) অন্য Subject/Topic-এ move করে। প্রশ্নের fbKey
    // (নিজের id) অপরিবর্তিত থাকে — শুধু subject/subTopic/subjectId/topicId বদলায়। ──
    @Query("""
        UPDATE questions
        SET subject = :newSubject, subTopic = :newSubTopic, subjectId = :newSubjectId, topicId = :newTopicId
        WHERE sheet = :sheet AND fbKey IN (:ids)
    """)
    suspend fun moveQuestionsByIds(
        sheet: String, ids: List<String>,
        newSubject: String, newSubTopic: String, newSubjectId: String, newTopicId: String
    )

    @Query("""
        UPDATE questions
        SET subject = :newSubject, subTopic = :newSubTopic, subjectId = :newSubjectId, topicId = :newTopicId
        WHERE sheet = :sheet AND topicId = :oldTopicId
    """)
    suspend fun moveQuestionsByTopicId(
        sheet: String, oldTopicId: String,
        newSubject: String, newSubTopic: String, newSubjectId: String, newTopicId: String
    )

    // ── "নতুন Topic যোগ করে Move" — অস্থায়ী লোকাল topicId (adminAddQuestion-এর
    // "-local..." id প্যাটার্নের মতোই) ব্যাকগ্রাউন্ডে GAS-এর দেওয়া আসল topicId দিয়ে
    // replace করতে হয়, নাহলে Room-এ প্রশ্নগুলো এতিম (orphan) topicId ধরে থেকে যাবে ──
    @Query("UPDATE questions SET topicId = :newTopicId WHERE sheet = :sheet AND topicId = :oldTopicId")
    suspend fun replaceTopicId(sheet: String, oldTopicId: String, newTopicId: String)

    // ── সব data মুছো ──────────────────────────────────────────────────────────
    @Query("DELETE FROM questions")
    suspend fun deleteAll()

    // ── একটা প্রশ্ন ID দিয়ে খোঁজো (admin edit/report এর জন্য) ──────────────
    @Query("SELECT * FROM questions WHERE sheet = :sheet AND fbKey = :fbKey LIMIT 1")
    suspend fun getById(sheet: String, fbKey: String): QuestionEntity?

    // ── Phase 6: multi-part প্রশ্ন — একই groupId-এর সব sub-question, sub_index অনুযায়ী
    // সাজানো। খালি groupId পাঠালে কিছুই ফেরত আসবে না (standalone প্রশ্নের জন্য এই কল লাগেই না) ──
    @Query("""
        SELECT * FROM questions
        WHERE sheet = :sheet AND groupId = :groupId AND groupId != ''
        ORDER BY subIndex
    """)
    suspend fun getGroupMates(sheet: String, groupId: String): List<QuestionEntity>

    // ── Phase 6: "পদ অনুযায়ী ব্রাউজ" — Exam_Appearances থেকে একগুচ্ছ questionId পেলে
    // সরাসরি সেই নির্দিষ্ট প্রশ্নগুলো টেনে আনার জন্য (audience-filtered, একটা নির্দিষ্ট
    // Post+Institution-এর আন্ডারে যত প্রশ্ন appear করেছে সবগুলো একসাথে) ──
    @Query("""
        SELECT * FROM questions
        WHERE sheet = :sheet AND fbKey IN (:ids)
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
        ORDER BY fbKey
    """)
    suspend fun getByFbKeysFiltered(sheet: String, ids: List<String>, tag: String): List<QuestionEntity>

    // ── FIX ("পদ/পদবী-মোডে প্রশ্ন ০/০ দেখানো" বাগ): দেওয়া id-লিস্টের মধ্যে যেগুলো Room-এ
    // ইতিমধ্যে আছে সেগুলোই রিটার্ন করে — বাকিগুলো (Room-এ নেই) GAS থেকে এনে upsert করতে
    // হবে (দেখো ContentRepository.ensureRoomQuestionsByIds)। আগে এই চেক-ধাপটাই ছিল না,
    // getRoomQuestionsByIds() সরাসরি Room-এ যা আছে শুধু সেটাই ফেরত দিত — Exam_Appearances
    // যেই প্রশ্নগুলোর লিংক দিত সেগুলো যদি কখনো স্বাভাবিক Subject→Topic পথে ব্রাউজ করে
    // ডাউনলোড না হয়ে থাকতো, Room-এ তারা কখনোই থাকতো না, ফলে "০/০ প্রশ্ন" দেখাতো। ──
    @Query("SELECT fbKey FROM questions WHERE sheet = :sheet AND fbKey IN (:ids)")
    suspend fun getExistingFbKeys(sheet: String, ids: List<String>): List<String>

    // ── Review System (Admin-only): লোকাল Room cache-এ reviewed status আপডেট —
    // GAS-এ লেখার পর Room-ও সাথে সাথে sync রাখার জন্য (fresh fetch ছাড়াই cache নির্ভুল থাকে) ──
    @Query("UPDATE questions SET reviewed = :reviewed, reviewedAt = :reviewedAt WHERE sheet = :sheet AND fbKey = :fbKey")
    suspend fun updateReviewed(sheet: String, fbKey: String, reviewed: Boolean, reviewedAt: Long)

    // ── Progressive topic-fill: এই topicId-এর জন্য এখন পর্যন্ত Room-এ যতটুকু cache
    // হয়েছে সব — audience-filtered। GAS getQuestionsPage থেকে ব্যাচ-ব্যাচ করে এখানে জমা
    // হয় (দেখো ContentRepository.cacheNextTopicBatch) ──
    @Query("""
        SELECT * FROM questions
        WHERE sheet = :sheet AND topicId = :topicId
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
        ORDER BY fbKey
    """)
    suspend fun getByTopicId(sheet: String, topicId: String, tag: String): List<QuestionEntity>

    // ── FIX ("পরবর্তী বাটনে ফাঁকা স্ক্রিন, ব্যাক বাটন কাজ করে না" বাগ, root cause):
    // আগে টপিক প্রথমবার খুললে topicId দিয়ে (getByTopicId, উপরে) Room থেকে সব ক্যাশড
    // প্রশ্ন একসাথে দেখানো হতো, কিন্তু "পরবর্তী" পেজে গেলে (goToPage/loadQuestionsFromRoom)
    // পুরনো subject/subTopic টেক্সট-কলাম দিয়ে আলাদা query চলতো — এই দুটো সম্পূর্ণ
    // ভিন্ন ডেটা-পাথ, subject/subTopic টেক্সট অনেক সময় ফাঁকা/অমিল থাকায় ২য় পেজ থেকে
    // কিছুই মিলতো না, স্ক্রিন সাদা হয়ে যেত। এখন topicId দিয়েই paginate করার query
    // যোগ হলো, যাতে ১ম পেজ আর পরের পেজ একই (নির্ভরযোগ্য) ডেটা-পাথ ব্যবহার করে। ──
    @Query("""
        SELECT * FROM questions
        WHERE sheet = :sheet AND topicId = :topicId
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
        ORDER BY fbKey
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getByTopicIdPaged(
        sheet   : String,
        topicId : String,
        tag     : String,
        limit   : Int,
        offset  : Int
    ): List<QuestionEntity>

    // ── উপরের getByTopicIdPaged()-এর সাথে ব্যবহারের জন্য — audience-filtered মোট সংখ্যা
    // (countByTopicId উপরে filter ছাড়া, cache-completeness চেক করতে ব্যবহৃত, এটা আলাদা) ──
    @Query("""
        SELECT COUNT(*) FROM questions
        WHERE sheet = :sheet AND topicId = :topicId
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
    """)
    suspend fun countByTopicIdFiltered(sheet: String, topicId: String, tag: String): Int

    // ── এই topicId-এর জন্য Room-এ (audience-filter ছাড়াই) কতগুলো প্রশ্ন cache হয়ে
    // আছে তার শুধু COUNT — cacheNextTopicBatch()-এ ব্যবহৃত হয় "sync.hasMore==false
    // অথচ আসলে ০ প্রশ্ন cache আছে" (আগের কোনো ব্যর্থ ফেচকে ভুল করে 'সম্পূর্ণ' ধরে
    // ফেলা) অবস্থা শনাক্ত করে স্বয়ংক্রিয়ভাবে আবার ফেচ করার চেষ্টা করার জন্য ──
    @Query("SELECT COUNT(*) FROM questions WHERE sheet = :sheet AND topicId = :topicId")
    suspend fun countByTopicId(sheet: String, topicId: String): Int

    // ── Global search — Room cache (offline/persistent) থেকে সব ফিল্ড মিলিয়ে খোঁজে।
    // ⚠️ Phase 6 ফিক্স: আগে এখানে audience-tag ফিল্টার ছিলই না — যেকোনো ইউজারের সার্চে
    // অন্য audience group-এর (ভিন্ন চাকরি/ক্লাসের) প্রশ্নও চলে আসতো, আর LIMIT 50-এর
    // স্লট সেগুলো দখল করে আসল প্রাসঙ্গিক ফলাফল বাদ দিয়ে ফেলতে পারতো। এখন
    // getPagedQuestionsFiltered/getByFbKeysFiltered-এর মতোই একই audience-tag শর্ত ──
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%') AND (
            question LIKE '%' || :query || '%' OR 
            answer   LIKE '%' || :query || '%' OR
            subject  LIKE '%' || :query || '%' OR
            subTopic LIKE '%' || :query || '%' OR
            optionA  LIKE '%' || :query || '%' OR
            optionB  LIKE '%' || :query || '%'
        )
        LIMIT 50
    """)
    suspend fun search(sheet: String, query: String, tag: String): List<QuestionEntity>
}

// ── Helper projection classes ─────────────────────────────────────────────────
data class SubjectCount(val subject: String, val count: Int)
data class SubTopicCount(val subTopic: String, val count: Int)
