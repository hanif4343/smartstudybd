package com.hanif.smartstudy.util

import android.content.Intent
import android.net.Uri

/**
 * Deep link: smartstudy://quiz/বিজ্ঞান
 *            smartstudy://qbank/গণিত
 *            smartstudy://study/বাংলা
 *            smartstudy://search/keyword
 *            smartstudy://reports              ← admin: report list
 *            smartstudy://techniques           ← admin: technique list
 *            smartstudy://menu/stats           ← menu page (stats, bookmarks, etc)
 *
 * FCM data payload থেকেও parse হয়:
 *   data["url"]        = "reports" | "techniques" | "quiz" | "qbank" | "study" | "menu"
 *   data["questionId"] = "Q123"
 *   data["tab"]        = "quiz" | "qbank" | "study"   (report/technique কোন tab থেকে)
 */
data class DeepLinkAction(
    val type          : Type,
    val subject       : String? = null,
    val query         : String? = null,
    val questionId    : String? = null,
    val tab           : String? = null,
    val menuPage      : String? = null,
    val challengeId   : String? = null,
    val routineItemId : String? = null   // Daily Routine item id → highlight on Home tab
) {
    enum class Type { QUIZ, QBANK, STUDY, SEARCH, REPORTS, TECHNIQUES, MENU, CHALLENGE, ROUTINE, FOCUS, NONE }
}

/** 🔔 in-app নোটিফিকেশন ইনবক্সে (NotificationsSheet) কোনো আইটেমে ট্যাপ করলে —
 *  ঠিক system-tray notification tap করলে যেভাবে রাউট হতো, একই নিয়মে রাউট করে। */
fun com.hanif.smartstudy.data.model.AppNotification.toDeepLinkAction(): DeepLinkAction =
    deepLinkFromNotificationData(
        url         = url.ifBlank { null },
        type        = type.ifBlank { null },
        questionId  = questionId.ifBlank { null },
        tab         = tab.ifBlank { null },
        challengeId = challengeId.ifBlank { null }
    )
fun Intent.parseDeepLink(): DeepLinkAction {
    // ── FCM data payload (notification click) ──
    val fcmUrl    = getStringExtra("url")
    val fcmQid    = getStringExtra("questionId")
    val fcmTab    = getStringExtra("tab")
    val fcmType   = getStringExtra("type")  // "admin_technique" | "admin_report" | "challenge_invite"
    val fcmChallengeId = getStringExtra("challengeId")

    if (fcmUrl != null || fcmType != null) {
        return deepLinkFromNotificationData(
            url         = fcmUrl,
            type        = fcmType,
            questionId  = fcmQid,
            tab         = fcmTab,
            challengeId = fcmChallengeId,
            subject     = getStringExtra("subject"),
            routineItemId = getStringExtra("routineItemId") ?: getStringExtra("item_id"),
            qsheet      = getStringExtra("qsheet")
        )
    }

    // ── URI deep link ──
    val uri: Uri = data ?: return DeepLinkAction(DeepLinkAction.Type.NONE)
    if (uri.scheme != "smartstudy") return DeepLinkAction(DeepLinkAction.Type.NONE)

    val host = uri.host ?: return DeepLinkAction(DeepLinkAction.Type.NONE)
    val path = uri.pathSegments.firstOrNull()

    return when (host.lowercase()) {
        "quiz"       -> DeepLinkAction(DeepLinkAction.Type.QUIZ,       subject = path)
        "qbank"      -> DeepLinkAction(DeepLinkAction.Type.QBANK,      subject = path)
        "study"      -> DeepLinkAction(DeepLinkAction.Type.STUDY,      subject = path)
        "search"     -> DeepLinkAction(DeepLinkAction.Type.SEARCH,     query   = path)
        "reports"    -> DeepLinkAction(DeepLinkAction.Type.REPORTS)
        "techniques" -> DeepLinkAction(DeepLinkAction.Type.TECHNIQUES)
        "menu"       -> DeepLinkAction(DeepLinkAction.Type.MENU,       menuPage = path)
        "routine"    -> DeepLinkAction(DeepLinkAction.Type.ROUTINE,    routineItemId = path)
        else         -> DeepLinkAction(DeepLinkAction.Type.NONE)
    }
}

/**
 * ── এই ফাংশনটাই আসল রাউটিং-নিয়ম — আগে শুধু Intent.parseDeepLink()-এর ভেতরে
 * (system-tray notification tap হ্যান্ডল করার সময়) ইনলাইন ছিল। এখন আলাদা করে
 * বের করা হলো, যাতে 🔔 in-app নোটিফিকেশন ইনবক্সে ট্যাপ করলেও (NotificationsSheet)
 * একদম একই নিয়মে রাউট করা যায় — Intent extras আর AppNotification, দুটো ভিন্ন
 * উৎস থেকে ডেটা এলেও গন্তব্য ঠিক করার লজিক একজায়গায় থাকে, দুই জায়গায় আলাদা
 * করে maintain করতে হয় না। ──
 */
fun deepLinkFromNotificationData(
    url           : String?,
    type          : String?,
    questionId    : String?,
    tab           : String?,
    challengeId   : String?,
    subject       : String? = null,
    routineItemId : String? = null,
    qsheet        : String? = null
): DeepLinkAction {
    val fcmUrl = url; val fcmQid = questionId; val fcmTab = tab
    val fcmType = type; val fcmChallengeId = challengeId
    return when {
        // Routine reminder tap → go to Home, highlight the specific routine item
        fcmType == "routine_reminder" ->
            DeepLinkAction(DeepLinkAction.Type.ROUTINE, routineItemId = routineItemId?.ifBlank { null })

        // ফোকাস মোড ব্যাকগ্রাউন্ড রিমাইন্ডার tap → সরাসরি ফোকাস-সাবজেক্টের Study স্ক্রিনে (Part ৪)
        fcmType == "focus_reminder" ->
            DeepLinkAction(DeepLinkAction.Type.FOCUS, subject = subject?.ifBlank { null })

        // Challenge invite → go directly to Challenge tab
        fcmType == "challenge_invite" ->
            DeepLinkAction(DeepLinkAction.Type.CHALLENGE, challengeId = fcmChallengeId)

        // Report resolved → navigate to the specific question with highlight
        fcmType == "report_resolved" -> {
            val sheet = qsheet ?: ""
            val t     = fcmTab ?: ""
            val resolvedTab = when {
                t.equals("study", ignoreCase = true) || sheet.contains("study", ignoreCase = true) -> "study"
                t.equals("quiz",  ignoreCase = true) || sheet.contains("quiz",  ignoreCase = true) -> "quiz"
                else -> "qbank"
            }
            DeepLinkAction(DeepLinkAction.Type.REPORTS, questionId = fcmQid, tab = resolvedTab)
        }

        // Technique approved/rejected → go to question
        fcmType == "technique_status" ->
            DeepLinkAction(DeepLinkAction.Type.QBANK, questionId = fcmQid)

        fcmUrl == "techniques" || fcmType == "admin_technique" ->
            DeepLinkAction(DeepLinkAction.Type.TECHNIQUES, questionId = fcmQid, tab = fcmTab)
        fcmUrl == "reports"    || fcmType == "admin_report"    ->
            DeepLinkAction(DeepLinkAction.Type.REPORTS, questionId = fcmQid, tab = fcmTab)
        // অন্য যেকোনো admin_* একটিভিটি (explanation, feedback ইত্যাদি) — এখনো
        // কোনো নির্দিষ্ট case নেই এমন সব notification যেন dead-tap না হয়ে
        // অন্তত Menu > Admin panel এ নিয়ে যায়
        fcmUrl == "admin" || fcmType?.startsWith("admin_") == true ->
            DeepLinkAction(DeepLinkAction.Type.TECHNIQUES, questionId = fcmQid, tab = fcmTab)
        fcmUrl == "challenge" ->
            DeepLinkAction(DeepLinkAction.Type.CHALLENGE, challengeId = fcmChallengeId)
        fcmUrl == "routine" ->
            DeepLinkAction(DeepLinkAction.Type.ROUTINE, routineItemId = routineItemId?.ifBlank { null })
        fcmUrl == "quiz"   -> DeepLinkAction(DeepLinkAction.Type.QUIZ,  questionId = fcmQid)
        fcmUrl == "qbank"  -> DeepLinkAction(DeepLinkAction.Type.QBANK, questionId = fcmQid)
        fcmUrl == "study"  -> DeepLinkAction(DeepLinkAction.Type.STUDY, questionId = fcmQid)
        fcmUrl == "stats"  -> DeepLinkAction(DeepLinkAction.Type.MENU, menuPage = "stats")
        fcmUrl?.startsWith("menu") == true ->
            DeepLinkAction(DeepLinkAction.Type.MENU, menuPage = fcmUrl.removePrefix("menu/"))
        else -> DeepLinkAction(DeepLinkAction.Type.NONE)
    }
}
