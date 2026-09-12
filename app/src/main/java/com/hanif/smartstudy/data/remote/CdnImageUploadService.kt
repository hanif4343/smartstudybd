package com.hanif.smartstudy.data.remote

// ═══════════════════════════════════════════════════════════════════
//  CdnImageUploadService.kt — Image/CDN Hosting Phase
//
//  ImgBbService.kt-এর replacement। ImgBB-তে সরাসরি (client থেকে) আপলোড করার
//  বদলে এখন ছবি GAS-এর "upload_image" action-এ পাঠানো হয় (base64), GAS নিজে
//  GitHub-এ commit করে jsDelivr CDN URL ফেরত দেয় (দেখো code_updated.gs)।
//  GitHub টোকেন কখনো ফোনে থাকে না — এটাই মূল নিরাপত্তা কারণ (ImgBB key ফোনে
//  embedded থাকলেও ঝুঁকি সীমিত ছিল, কিন্তু GitHub টোকেন ফাঁস হলে পুরো কন্টেন্ট
//  রিপো ঝুঁকিতে পড়ত)।
//
//  resize/compress লজিক ImgBbService.kt থেকেই hুবহু নেওয়া (512px downsample +
//  JPEG quality 80) — শুধু destination বদলেছে।
// ═══════════════════════════════════════════════════════════════════

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object CdnImageUploadService {

    /**
     * ইতিমধ্যে তৈরি (যেমন crop স্ক্রিন থেকে আসা) Bitmap আপলোড করে — compress করে
     * base64 বানিয়ে GAS-এ পাঠায়।
     * @param folder "users" (প্রোফাইল ছবি) / "questions" / "attachments" ইত্যাদি
     * @param fileNamePrefix ইউনিক নাম বানানোর জন্য প্রিফিক্স (যেমন phone নাম্বার) — timestamp
     *        নিজে থেকেই যোগ হয়, তাই কলারকে ইউনিকনেস নিয়ে ভাবতে হয় না
     */
    suspend fun uploadBitmap(
        bitmap: Bitmap,
        folder: String,
        fileNamePrefix: String,
        quality: Int = 80
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val safePrefix = fileNamePrefix.replace(Regex("[^a-zA-Z0-9_-]"), "")
            val fileName = "${safePrefix}_${System.currentTimeMillis()}.jpg"
            GasContentService.uploadImage(base64, folder, fileName)
        } catch (e: Exception) {
            Log.e("CdnImageUpload", "uploadBitmap error: ${e.message}")
            ApiResult.Error(e.message ?: "ছবি প্রসেস করতে সমস্যা হয়েছে")
        }
    }

    /**
     * সরাসরি Uri থেকে (crop ছাড়া, সাধারণ অ্যাটাচমেন্ট আপলোডের জন্য) — resize করে
     * তারপর uploadBitmap() রিইউজ করে।
     */
    suspend fun uploadFromUri(
        context: Context,
        uri: Uri,
        folder: String,
        fileNamePrefix: String,
        maxDim: Int = 1000
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val bitmap = decodeSampledBitmap(context, uri, maxDim, maxDim)
            ?: return@withContext ApiResult.Error("ছবি পড়তে পারিনি")
        val result = uploadBitmap(bitmap, folder, fileNamePrefix)
        bitmap.recycle()
        result
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val opts1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts1)
            }
            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(opts1, reqW, reqH)
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts2)
            }
        } catch (e: Exception) {
            Log.e("CdnImageUpload", "Decode error: ${e.message}")
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
