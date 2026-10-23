package com.hanif.smartstudy.ui.ink

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.mutableStateMapOf
import com.hanif.smartstudy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt

/* ══════════════════════════════════════════════════════════════════════════
   ✍ হাতে লেখা (টাচপেন) — ডাটা-মডেল, সংকোচন (gzip+base64) ও সার্ভার-যোগাযোগ
   ──────────────────────────────────────────────────────────────────────────
   • ছবি নয়, স্ট্রোক সংরক্ষণ: প্রতিটা পয়েন্ট (x, y, সময়) — ১০০০ একক চওড়া লজিক্যাল ক্যানভাস।
     তাই যেকোনো স্ক্রিনে ধারালো, ডার্ক মোডে রং বদলায়, এডিট করা যায়, আর "ধাপে ধাপে প্লে" সম্ভব।
   • GAS `Ink` শিটে থাকে (code_updated.gs: save_ink / getInkIndex / getInk) — Publish Now লাগে না,
     সবাই সাথে সাথে দেখে। ডিভাইসে ভার্সন ধরে ক্যাশ হয় (একই লেখা আবার ডাউনলোড হয় না)।
   ══════════════════════════════════════════════════════════════════════════ */

/** t = এই স্ট্রোকের শুরু থেকে মিলিসেকেন্ড */
data class InkPt(val x: Float, val y: Float, val t: Int)
data class InkStroke(val color: Int, val width: Float, val pts: List<InkPt>)
/** w = সবসময় ১০০০ (লজিক্যাল), h = লেখার উচ্চতা */
/** text = হাতের লেখা থেকে রূপান্তরিত (অ্যাডমিন-যাচাই করা) টেক্সট; ফাঁকা হলে নেই */
data class InkDoc(val w: Int, val h: Int, val strokes: List<InkStroke>, val text: String = "")

object InkCodec {
    fun encode(doc: InkDoc): String {
        val sb = StringBuilder()
        sb.append("{\"w\":").append(doc.w).append(",\"h\":").append(doc.h).append(",\"s\":[")
        doc.strokes.forEachIndexed { i, st ->
            if (i > 0) sb.append(',')
            sb.append('[').append(st.color).append(',').append((st.width * 10f).roundToInt()).append(",[")
            st.pts.forEachIndexed { j, p ->
                if (j > 0) sb.append(',')
                sb.append(p.x.roundToInt()).append(',').append(p.y.roundToInt()).append(',').append(p.t)
            }
            sb.append("]]")
        }
        sb.append("]}")
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun decode(b64: String): InkDoc? = try {
        val raw = Base64.decode(b64, Base64.NO_WRAP)
        val json = GZIPInputStream(ByteArrayInputStream(raw)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val o = JSONObject(json)
        val arr = o.getJSONArray("s")
        val strokes = ArrayList<InkStroke>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.getJSONArray(i)
            val flat = a.getJSONArray(2)
            val pts = ArrayList<InkPt>(flat.length() / 3)
            var k = 0
            while (k + 2 < flat.length()) {
                pts.add(InkPt(flat.getInt(k).toFloat(), flat.getInt(k + 1).toFloat(), flat.getInt(k + 2)))
                k += 3
            }
            strokes.add(InkStroke(a.getInt(0), a.getInt(1) / 10f, pts))
        }
        InkDoc(o.getInt("w"), o.getInt("h"), strokes)
    } catch (e: Exception) {
        null
    }
}

object InkRepository {
    /** প্রশ্নের id → "E<ভার্সন>;T<ভার্সন>" (E = ব্যাখ্যা, T = টেকনিক)। Compose state, তাই বদলালে UI নিজে আপডেট হয় */
    val index = mutableStateMapOf<String, String>()

    private var indexLoadedAt = 0L
    private val mem = java.util.concurrent.ConcurrentHashMap<String, InkDoc>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    private fun base() = BuildConfig.GAS_URL.trim()
    private fun secret() = BuildConfig.GAS_SECRET.trim()
    private fun get(query: String): String? = try {
        val url = base() + "?action=" + query + "&secret=" + URLEncoder.encode(secret(), "UTF-8")
        http.newCall(Request.Builder().url(url).get().build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (e: Exception) { null }

    /** field = "explanation" | "technique" */
    fun version(qid: String, field: String): String? {
        val tag = if (field == "technique") 'T' else 'E'
        return index[qid]?.split(';')?.firstOrNull { it.startsWith(tag) }?.drop(1)?.takeIf { it.isNotBlank() }
    }
    fun has(qid: String, field: String): Boolean = version(qid, field) != null

    suspend fun ensureIndex(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (base().isBlank() || secret().isBlank()) return@withContext
        val now = System.currentTimeMillis()
        if (!force && indexLoadedAt != 0L && now - indexLoadedAt < 5 * 60_000L) return@withContext
        val body = get("getInkIndex") ?: return@withContext
        try {
            val idx = JSONObject(body).optJSONObject("index") ?: return@withContext
            val fresh = HashMap<String, String>()
            val keysIt = idx.keys()
            while (keysIt.hasNext()) { val k = keysIt.next(); fresh[k] = idx.optString(k) }
            index.keys.toList().filter { it !in fresh }.forEach { index.remove(it) }
            fresh.forEach { (k, v) -> if (index[k] != v) index[k] = v }
            indexLoadedAt = now
        } catch (e: Exception) { /* পরের বার আবার চেষ্টা */ }
    }

    /** একটা প্রশ্নের একটা ফিল্ডের লেখা। আগে মেমরি, তারপর ডিভাইস-ক্যাশ (ভার্সনসহ), তারপর সার্ভার */
    suspend fun load(ctx: Context, qid: String, field: String): InkDoc? = withContext(Dispatchers.IO) {
        val ver = version(qid, field) ?: return@withContext null
        val key = "$qid|$field|$ver"
        mem[key]?.let { return@withContext it }
        val file = File(ctx.cacheDir, "ink2_${qid}_${field}_$ver.json")
        try {
            if (file.exists()) {
                val c = JSONObject(file.readText())
                InkCodec.decode(c.optString("d"))?.copy(text = c.optString("t"))?.let { mem[key] = it; return@withContext it }
            }
        } catch (e: Exception) { /* ক্যাশ নষ্ট হলে সার্ভার থেকে */ }
        val body = get("getInk&id=" + URLEncoder.encode(qid, "UTF-8")) ?: return@withContext null
        try {
            val items = JSONObject(body).optJSONArray("items") ?: return@withContext null
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                if (o.optString("field") == field) {
                    val data = o.optString("data")
                    val text = o.optString("text")
                    val doc = InkCodec.decode(data)?.copy(text = text) ?: return@withContext null
                    try { file.writeText(JSONObject().put("d", data).put("t", text).toString()) } catch (e: Exception) { }
                    mem[key] = doc
                    return@withContext doc
                }
            }
        } catch (e: Exception) { }
        null
    }

    /** সেভ (doc == null বা ফাঁকা হলে মোছা)। সফল হলে null, নইলে বাংলা ত্রুটি-বার্তা */
    suspend fun save(qid: String, field: String, doc: InkDoc?, by: String = "admin", text: String? = null): String? = withContext(Dispatchers.IO) {
        if (base().isBlank() || secret().isBlank()) return@withContext "GAS_URL/GAS_SECRET সেট করা নেই"
        try {
            val data = if (doc == null || doc.strokes.isEmpty()) "" else InkCodec.encode(doc)
            val o = JSONObject()
            o.put("secret", secret()); o.put("type", "save_ink")
            o.put("id", qid); o.put("field", field); o.put("data", data)
            o.put("w", doc?.w ?: 0); o.put("h", doc?.h ?: 0); o.put("strokes", doc?.strokes?.size ?: 0); o.put("by", by)
            if (text != null) o.put("text", text)   // না পাঠালে সার্ভারে আগের টেক্সটই থাকে
            val req = Request.Builder().url(base()).post(o.toString().toRequestBody(JSON_MT)).build()
            http.newCall(req).execute().use { r ->
                val txt = r.body?.string() ?: ""
                if (!r.isSuccessful) return@withContext "সার্ভার ত্রুটি (HTTP ${r.code})"
                val res = JSONObject(txt)
                if (res.optString("status") != "success") return@withContext res.optString("message", "সেভ ব্যর্থ")
                mem.keys.removeAll { it.startsWith("$qid|$field|") }
                val tag = if (field == "technique") 'T' else 'E'
                val others = (index[qid] ?: "").split(';').filter { it.isNotBlank() && !it.startsWith(tag) }
                val newTag = if (res.optBoolean("deleted", false)) null else "$tag${res.optString("version")}"
                val merged = (others + listOfNotNull(newTag)).joinToString(";")
                if (merged.isBlank()) index.remove(qid) else index[qid] = merged
                null
            }
        } catch (e: Exception) {
            "নেটওয়ার্ক ত্রুটি: ${e.message ?: ""}"
        }
    }

    /** ✍ → Aa: লেখাকে পরিষ্কার PNG বানিয়ে গ্যাসে পাঠায় (Gemini ভিশন)। ফেরত (টেক্সট, ত্রুটি) — একটাই null থাকে */
    suspend fun toText(doc: InkDoc): Pair<String?, String?> = withContext(Dispatchers.IO) {
        if (base().isBlank() || secret().isBlank()) return@withContext Pair(null, "GAS_URL/GAS_SECRET সেট করা নেই")
        try {
            val png = renderPng(doc) ?: return@withContext Pair(null, "লেখা থেকে ছবি বানানো যায়নি")
            val o = JSONObject()
            o.put("secret", secret()); o.put("type", "ink_to_text"); o.put("image", png)
            val req = Request.Builder().url(base()).post(o.toString().toRequestBody(JSON_MT)).build()
            http.newCall(req).execute().use { r ->
                val txt = r.body?.string() ?: ""
                if (!r.isSuccessful) return@withContext Pair(null, "সার্ভার ত্রুটি (HTTP ${r.code})")
                val res = JSONObject(txt)
                if (res.optString("status") != "success") return@withContext Pair(null, res.optString("message", "রূপান্তর ব্যর্থ"))
                Pair(res.optString("text"), null)
            }
        } catch (e: Exception) {
            Pair(null, "নেটওয়ার্ক ত্রুটি: ${e.message ?: ""}")
        }
    }

    /** সাদা জমিনে কালো/রঙিন লেখা — AI যাতে ভালো পড়তে পারে (১৪০০ পিক্সেল চওড়া) */
    private fun renderPng(doc: InkDoc): String? {
        val outW = 1400
        val scale = outW / doc.w.toFloat()
        val outH = (doc.h * scale).toInt().coerceIn(200, 4200)
        val bmp = android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        cv.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.strokeCap = android.graphics.Paint.Cap.ROUND
        paint.strokeJoin = android.graphics.Paint.Join.ROUND
        for (st in doc.strokes) {
            paint.color = if (st.color == 0xFF111111.toInt()) android.graphics.Color.BLACK else st.color
            paint.strokeWidth = st.width * scale * 1.1f
            if (st.pts.size == 1) {
                paint.style = android.graphics.Paint.Style.FILL
                cv.drawCircle(st.pts[0].x * scale, st.pts[0].y * scale, paint.strokeWidth / 2f, paint)
            } else if (st.pts.size > 1) {
                paint.style = android.graphics.Paint.Style.STROKE
                val path = android.graphics.Path()
                path.moveTo(st.pts[0].x * scale, st.pts[0].y * scale)
                for (i in 1 until st.pts.size) {
                    val a = st.pts[i - 1]
                    val b = st.pts[i]
                    path.quadTo(a.x * scale, a.y * scale, (a.x + b.x) / 2f * scale, (a.y + b.y) / 2f * scale)
                }
                cv.drawPath(path, paint)
            }
        }
        val bos = ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
        bmp.recycle()
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }
}
