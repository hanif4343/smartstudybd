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
    val type       : Type,
    val subject    : String? = null,
    val query      : String? = null,
    val questionId : String? = null,
    val tab        : String? = null,   // "quiz" | "qbank" | "study"
    val menuPage   : String? = null    // "reports" | "techniques" | "stats" | "bookmarks"
) {
    enum class Type { QUIZ, QBANK, STUDY, SEARCH, REPORTS, TECHNIQUES, MENU, NONE }
}

fun Intent.parseDeepLink(): DeepLinkAction {
    // ── FCM data payload (notification click) ──
    val fcmUrl    = getStringExtra("url")
    val fcmQid    = getStringExtra("questionId")
    val fcmTab    = getStringExtra("tab")
    val fcmType   = getStringExtra("type")  // "admin_technique" | "admin_report" | ...

    if (fcmUrl != null || fcmType != null) {
        return when {
            fcmUrl == "techniques" || fcmType == "admin_technique" ->
                DeepLinkAction(DeepLinkAction.Type.TECHNIQUES, questionId = fcmQid, tab = fcmTab)
            fcmUrl == "reports"    || fcmType == "admin_report"    ->
                DeepLinkAction(DeepLinkAction.Type.REPORTS, questionId = fcmQid, tab = fcmTab)
            fcmUrl == "quiz"   -> DeepLinkAction(DeepLinkAction.Type.QUIZ,  questionId = fcmQid)
            fcmUrl == "qbank"  -> DeepLinkAction(DeepLinkAction.Type.QBANK, questionId = fcmQid)
            fcmUrl == "study"  -> DeepLinkAction(DeepLinkAction.Type.STUDY, questionId = fcmQid)
            fcmUrl == "stats"  -> DeepLinkAction(DeepLinkAction.Type.MENU, menuPage = "stats")
            fcmUrl?.startsWith("menu") == true ->
                DeepLinkAction(DeepLinkAction.Type.MENU, menuPage = fcmUrl.removePrefix("menu/"))
            else -> DeepLinkAction(DeepLinkAction.Type.NONE)
        }
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
        else         -> DeepLinkAction(DeepLinkAction.Type.NONE)
    }
}
