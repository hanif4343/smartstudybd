package com.hanif.smartstudy.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import com.hanif.smartstudy.data.model.QuizResult
import com.hanif.smartstudy.ui.typing.TypingResult
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * কুইজ রেজাল্ট শেয়ার কার্ড — সুন্দর ইমেজ তৈরি করে WhatsApp / FB / যেকোনো জায়গায় শেয়ার করা যাবে
 */
object ResultShareUtil {

    // ─── Typing Practice রেজাল্ট শেয়ার কার্ড ────────────────────────────────
    fun shareTyping(context: Context, result: TypingResult, isNewBest: Boolean) {
        val bmp = buildTypingCard(context, result, isNewBest)
        val uri = saveToCacheAndGetUri(context, bmp)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, buildTypingPromoText(result, isNewBest))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "শেয়ার করুন"))
    }

    private fun buildTypingCard(context: Context, result: TypingResult, isNewBest: Boolean): Bitmap {
        val W = 1080
        val H = 1280
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bgShader = LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            intArrayOf(
                Color.parseColor("#1E1B4B"),
                Color.parseColor("#312E81"),
                Color.parseColor("#4338CA")
            ),
            null, Shader.TileMode.CLAMP
        )
        bgPaint.shader = bgShader
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bgPaint)

        val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6366F1")
            alpha = 40
        }
        c.drawCircle(W * 0.85f, H * 0.05f, 280f, blobPaint)
        blobPaint.alpha = 25
        c.drawCircle(W * 0.1f, H * 0.12f, 200f, blobPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A5B4FC")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText("⌨️ SmartStudy BD — Typing", 80f, 120f, brandPaint)

        val taglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C7D2FE")
            textSize = 28f
        }
        c.drawText("স্মার্টভাবে টাইপ শেখো, গতি বাড়াও", 80f, 165f, taglinePaint)

        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6366F1")
            strokeWidth = 2f
            alpha = 120
        }
        c.drawLine(80f, 185f, W - 80f, 185f, divPaint)

        if (isNewBest) {
            val bestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FBBF24")
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c.drawText("🏆 নতুন রেকর্ড!", W / 2f, 240f, bestPaint)
        }

        val ringCx = W / 2f
        val ringCy = 420f
        val ringR = 170f

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3730A3")
            style = Paint.Style.STROKE
            strokeWidth = 22f
            strokeCap = Paint.Cap.ROUND
        }
        c.drawCircle(ringCx, ringCy, ringR, trackPaint)

        val scoreColor = when {
            result.accuracy >= 95 -> Color.parseColor("#10B981")
            result.accuracy >= 80 -> Color.parseColor("#6366F1")
            result.accuracy >= 60 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = scoreColor
            style = Paint.Style.STROKE
            strokeWidth = 22f
            strokeCap = Paint.Cap.ROUND
        }
        val oval = RectF(ringCx - ringR, ringCy - ringR, ringCx + ringR, ringCy + ringR)
        c.drawArc(oval, -90f, 360f * (result.accuracy / 100f).coerceIn(0f, 1f), false, arcPaint)

        val wpmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 88f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText("${result.wpm}", ringCx, ringCy + 30f, wpmPaint)

        val wpmLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C7D2FE")
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("WPM", ringCx, ringCy + 80f, wpmLabelPaint)

        val stats = listOf(
            Triple("🎯", "${result.accuracy}%", "নির্ভুলতা"),
            Triple("⏱", "${result.timeSec}s", "সময়"),
            Triple("✅", result.correctChars.toString(), "সঠিক অক্ষর"),
            Triple("📝", result.totalChars.toString(), "মোট অক্ষর"),
        )
        val cardW = 200f
        val cardH = 200f
        val cardSpacing = 20f
        val totalCardW = stats.size * cardW + (stats.size - 1) * cardSpacing
        var cardX = (W - totalCardW) / 2f
        val cardY = 760f

        val cardBgColors = listOf(
            Color.parseColor("#052E16"),
            Color.parseColor("#1E1B4B"),
            Color.parseColor("#450A0A"),
            Color.parseColor("#422006"),
        )
        val cardValueColors = listOf(
            Color.parseColor("#4ADE80"),
            Color.parseColor("#A5B4FC"),
            Color.parseColor("#F87171"),
            Color.parseColor("#FBBF24"),
        )

        stats.forEachIndexed { i, (icon, value, label) ->
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cardBgColors[i]
                alpha = 220
            }
            val cardRect = RectF(cardX, cardY, cardX + cardW, cardY + cardH)
            c.drawRoundRect(cardRect, 24f, 24f, cardPaint)

            val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 42f
                textAlign = Paint.Align.CENTER
            }
            c.drawText(icon, cardX + cardW / 2, cardY + 62f, iconP)

            val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cardValueColors[i]
                textSize = 38f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c.drawText(value, cardX + cardW / 2, cardY + 118f, valP)

            val lblP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 22f
                textAlign = Paint.Align.CENTER
            }
            c.drawText(label, cardX + cardW / 2, cardY + 160f, lblP)

            cardX += cardW + cardSpacing
        }

        val promoY = H - 180f
        val promoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#312E81")
            alpha = 200
        }
        c.drawRect(0f, promoY, W.toFloat(), H.toFloat(), promoBgPaint)
        c.drawLine(0f, promoY, W.toFloat(), promoY, divPaint)

        val promoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText("SmartStudy BD অ্যাপে টাইপিং প্র্যাকটিস করো!", ringCx, promoY + 65f, promoPaint)

        val promoSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A5B4FC")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("📲 Play Store থেকে ডাউনলোড করো — বিনামূল্যে!", ringCx, promoY + 115f, promoSub)

        return bmp
    }

    private fun buildTypingPromoText(result: TypingResult, isNewBest: Boolean): String {
        return buildString {
            appendLine("⌨️ আমার টাইপিং রেজাল্ট")
            if (isNewBest) appendLine("🏆 নতুন রেকর্ড!")
            appendLine("গতি: ${result.wpm} WPM  🎯 নির্ভুলতা: ${result.accuracy}%")
            appendLine("⏱ সময়: ${result.timeSec}s  •  ✅ সঠিক অক্ষর: ${result.correctChars}/${result.totalChars}")
            appendLine()
            appendLine("📚 SmartStudy BD দিয়ে স্মার্টভাবে টাইপ শেখো!")
            append("👉 Play Store: https://play.google.com/store/apps/details?id=com.hanif.smartstudy")
        }
    }

    fun share(context: Context, result: QuizResult, subjectName: String = "") {
        val bmp = buildCard(context, result, subjectName)
        val uri = saveToCacheAndGetUri(context, bmp)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                buildPromoText(result, subjectName)
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "শেয়ার করুন"))
    }

    // ─── Card builder ───────────────────────────────────────────────────────
    private fun buildCard(context: Context, result: QuizResult, subjectName: String): Bitmap {
        val W = 1080
        val H = 1440
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c   = Canvas(bmp)

        // ── Background gradient ──
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bgShader = LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            intArrayOf(
                Color.parseColor("#1E1B4B"),   // deep indigo
                Color.parseColor("#312E81"),
                Color.parseColor("#4338CA")
            ),
            null, Shader.TileMode.CLAMP
        )
        bgPaint.shader = bgShader
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bgPaint)

        // ── Top decorative circle blobs ──
        val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6366F1")
            alpha = 40
        }
        c.drawCircle(W * 0.85f, H * 0.05f, 280f, blobPaint)
        blobPaint.alpha = 25
        c.drawCircle(W * 0.1f,  H * 0.12f, 200f, blobPaint)

        // ── App branding ──
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.parseColor("#A5B4FC")
            textSize  = 42f
            typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText("📚 SmartStudy BD", 80f, 120f, brandPaint)

        val taglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.parseColor("#C7D2FE")
            textSize = 28f
        }
        c.drawText("স্মার্টভাবে পড়ো, সাফল্য অর্জন করো", 80f, 165f, taglinePaint)

        // ── Divider ──
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6366F1")
            strokeWidth = 2f
            alpha = 120
        }
        c.drawLine(80f, 185f, W - 80f, 185f, divPaint)

        // ── Score ring ──
        val ringCx = W / 2f
        val ringCy = 400f
        val ringR  = 160f

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color  = Color.parseColor("#3730A3")
            style  = Paint.Style.STROKE
            strokeWidth = 22f
            strokeCap   = Paint.Cap.ROUND
        }
        c.drawCircle(ringCx, ringCy, ringR, trackPaint)

        // Score arc
        val scoreColor = when {
            result.pct >= 80 -> Color.parseColor("#10B981")
            result.pct >= 60 -> Color.parseColor("#6366F1")
            result.pct >= 40 -> Color.parseColor("#F59E0B")
            else             -> Color.parseColor("#EF4444")
        }
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color  = scoreColor
            style  = Paint.Style.STROKE
            strokeWidth = 22f
            strokeCap   = Paint.Cap.ROUND
        }
        val oval = RectF(ringCx - ringR, ringCy - ringR, ringCx + ringR, ringCy + ringR)
        c.drawArc(oval, -90f, 360f * (result.pct / 100f), false, arcPaint)

        // Pct text inside ring
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.WHITE
            textSize = 88f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText("${result.pct}%", ringCx, ringCy + 30f, pctPaint)

        val scoreLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.parseColor("#C7D2FE")
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("স্কোর", ringCx, ringCy + 80f, scoreLabelPaint)

        // ── Emoji + Title ──
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize  = 72f
            textAlign = Paint.Align.CENTER
        }
        c.drawText(result.emoji, ringCx, 640f, emojiPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText(result.title, ringCx, 710f, titlePaint)

        // Subject line
        val subjectLine = buildString {
            if (subjectName.isNotBlank()) append(subjectName)
            append("  •  মোট ${result.total} প্রশ্নের মধ্যে ${result.correct}টি সঠিক")
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.parseColor("#A5B4FC")
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        c.drawText(subjectLine, ringCx, 760f, subPaint)

        // ── Stats cards ──
        val stats = listOf(
            Triple("✅", result.correct.toString(), "সঠিক"),
            Triple("❌", result.wrong.toString(),   "ভুল"),
            Triple("⏭", result.skipped.toString(), "স্কিপ"),
            Triple("⭐", "+${result.xpEarned}",    "XP"),
        )
        val cardW = 200f
        val cardH = 220f
        val cardSpacing = 20f
        val totalCardW = stats.size * cardW + (stats.size - 1) * cardSpacing
        var cardX = (W - totalCardW) / 2f
        val cardY = 820f

        val cardBgColors = listOf(
            Color.parseColor("#052E16"),   // green
            Color.parseColor("#450A0A"),   // red
            Color.parseColor("#422006"),   // amber
            Color.parseColor("#1E1B4B"),   // indigo
        )
        val cardValueColors = listOf(
            Color.parseColor("#4ADE80"),
            Color.parseColor("#F87171"),
            Color.parseColor("#FBBF24"),
            Color.parseColor("#A5B4FC"),
        )

        stats.forEachIndexed { i, (icon, value, label) ->
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cardBgColors[i]
                alpha = 220
            }
            val cardRect = RectF(cardX, cardY, cardX + cardW, cardY + cardH)
            c.drawRoundRect(cardRect, 24f, 24f, cardPaint)

            // icon
            val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize  = 46f
                textAlign = Paint.Align.CENTER
            }
            c.drawText(icon, cardX + cardW / 2, cardY + 68f, iconP)

            // value
            val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = cardValueColors[i]
                textSize  = 46f
                typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c.drawText(value, cardX + cardW / 2, cardY + 130f, valP)

            // label
            val lblP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = Color.parseColor("#94A3B8")
                textSize  = 26f
                textAlign = Paint.Align.CENTER
            }
            c.drawText(label, cardX + cardW / 2, cardY + 175f, lblP)

            cardX += cardW + cardSpacing
        }

        // ── Subject breakdown ──
        if (result.subjectBreakdown.isNotEmpty()) {
            var rowY = cardY + cardH + 70f

            val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color    = Color.parseColor("#C7D2FE")
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            c.drawText("বিষয় ভিত্তিক ফলাফল", 80f, rowY, secPaint)
            rowY += 46f

            result.subjectBreakdown.values.take(4).forEach { sub ->
                val barBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#312E81")
                    alpha = 180
                }
                c.drawRoundRect(RectF(80f, rowY, W - 80f, rowY + 36f), 10f, 10f, barBg)

                val barW = ((W - 160f) * (sub.pct / 100f)).coerceAtLeast(0f)
                val barFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scoreColor }
                if (barW > 0f)
                    c.drawRoundRect(RectF(80f, rowY, 80f + barW, rowY + 36f), 10f, 10f, barFill)

                val rowTxtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color    = Color.WHITE
                    textSize = 24f
                }
                c.drawText(sub.subject.take(20), 90f, rowY + 25f, rowTxtPaint)
                val pctStr = "${sub.correct}/${sub.total}"
                rowTxtPaint.textAlign = Paint.Align.RIGHT
                c.drawText(pctStr, W - 90f, rowY + 25f, rowTxtPaint)

                rowY += 50f
            }
        }

        // ── Bottom promo strip ──
        val promoY = H - 180f
        val promoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color  = Color.parseColor("#312E81")
            alpha  = 200
        }
        c.drawRect(0f, promoY, W.toFloat(), H.toFloat(), promoBgPaint)

        c.drawLine(0f, promoY, W.toFloat(), promoY, divPaint)

        val promoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText("SmartStudy BD অ্যাপে পড়ো এবং এগিয়ে যাও!", ringCx, promoY + 65f, promoPaint)

        val promoSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.parseColor("#A5B4FC")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("📲 Play Store থেকে ডাউনলোড করো — বিনামূল্যে!", ringCx, promoY + 115f, promoSub)

        return bmp
    }

    // ─── Save bitmap → cache → FileProvider URI ─────────────────────────────
    private fun saveToCacheAndGetUri(context: Context, bmp: Bitmap): Uri {
        val dir  = File(context.cacheDir, "share").also { it.mkdirs() }
        val file = File(dir, "result_card.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // ─── Plain text fallback (কিছু অ্যাপ image caption হিসেবে ব্যবহার করে) ──
    private fun buildPromoText(result: QuizResult, subjectName: String): String {
        val subject = if (subjectName.isNotBlank()) " ($subjectName)" else ""
        return buildString {
            appendLine("📊 আমার কুইজ রেজাল্ট$subject")
            appendLine("স্কোর: ${result.pct}%  ${result.emoji}")
            appendLine("✅ সঠিক: ${result.correct}  ❌ ভুল: ${result.wrong}  ⏭ স্কিপ: ${result.skipped}")
            appendLine("⭐ XP অর্জন: +${result.xpEarned}")
            appendLine()
            appendLine("📚 SmartStudy BD দিয়ে স্মার্টভাবে পড়ো!")
            append("👉 Play Store: https://play.google.com/store/apps/details?id=com.hanif.smartstudy")
        }
    }
}
