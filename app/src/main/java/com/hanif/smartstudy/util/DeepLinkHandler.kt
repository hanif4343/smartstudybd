package com.hanif.smartstudy.util

import android.content.Intent
import android.net.Uri

/**
 * Deep link: smartstudy://quiz/বিজ্ঞান
 *            smartstudy://qbank/গণিত
 *            smartstudy://study/বাংলা
 *            smartstudy://search/keyword
 */
data class DeepLinkAction(
    val type    : Type,
    val subject : String? = null,
    val query   : String? = null
) {
    enum class Type { QUIZ, QBANK, STUDY, SEARCH, NONE }
}

fun Intent.parseDeepLink(): DeepLinkAction {
    val uri: Uri = data ?: return DeepLinkAction(DeepLinkAction.Type.NONE)
    if (uri.scheme != "smartstudy") return DeepLinkAction(DeepLinkAction.Type.NONE)

    val host    = uri.host ?: return DeepLinkAction(DeepLinkAction.Type.NONE)
    val path    = uri.pathSegments.firstOrNull()

    return when (host.lowercase()) {
        "quiz"   -> DeepLinkAction(DeepLinkAction.Type.QUIZ,   subject = path)
        "qbank"  -> DeepLinkAction(DeepLinkAction.Type.QBANK,  subject = path)
        "study"  -> DeepLinkAction(DeepLinkAction.Type.STUDY,  subject = path)
        "search" -> DeepLinkAction(DeepLinkAction.Type.SEARCH, query   = path)
        else     -> DeepLinkAction(DeepLinkAction.Type.NONE)
    }
}
