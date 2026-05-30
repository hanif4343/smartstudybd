package com.hanif.smartstudy.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.hanif.smartstudy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────
//  ImgBB Upload Service
//  Photo → resize to 512px → base64 → ImgBB API → URL
// ─────────────────────────────────────────────────────────────

object ImgBbService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val API_KEY get() = BuildConfig.IMGBB_API_KEY

    suspend fun uploadImage(context: Context, uri: Uri): ImgBbResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeSampledBitmap(context, uri, 512, 512)
                ?: return@withContext ImgBbResult.Error("ছবি পড়তে পারিনি")

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            bitmap.recycle()

            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            val body = FormBody.Builder()
                .add("key",   API_KEY)
                .add("image", base64)
                .build()

            val request = Request.Builder()
                .url("https://api.imgbb.com/1/upload")
                .post(body)
                .build()

            val responseBody = client.newCall(request).execute().body?.string() ?: ""
            Log.d("ImgBB", "Response: $responseBody")

            val json = JSONObject(responseBody)
            if (json.optBoolean("success")) {
                val url = json.getJSONObject("data").getString("url")
                ImgBbResult.Success(url)
            } else {
                val msg = json.optJSONObject("error")?.optString("message") ?: "আপলোড ব্যর্থ"
                ImgBbResult.Error(msg)
            }
        } catch (e: Exception) {
            Log.e("ImgBB", "Upload error: ${e.message}")
            ImgBbResult.Error("আপলোড সমস্যা: ${e.message}")
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return try {
            // Pass 1: measure dimensions
            val opts1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts1)
            }
            // Pass 2: decode with sample size
            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(opts1, reqW, reqH)
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts2)
            }
        } catch (e: Exception) {
            Log.e("ImgBB", "Decode error: ${e.message}")
            null
        }
    }

    private fun calculateInSampleSize(opts: BitmapFactory.Options, rW: Int, rH: Int): Int {
        val h = opts.outHeight
        val w = opts.outWidth
        var sample = 1
        if (h > rH || w > rW) {
            val hHalf = h / 2
            val wHalf = w / 2
            while (hHalf / sample >= rH && wHalf / sample >= rW) sample *= 2
        }
        return sample
    }
}

sealed class ImgBbResult {
    data class Success(val url: String)   : ImgBbResult()
    data class Error(val message: String) : ImgBbResult()
}
