package com.hanif.smartstudy.util

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object SoundManager {

    private var enabled: Boolean = true
    private val handler = Handler(Looper.getMainLooper())

    fun setEnabled(on: Boolean) { enabled = on }
    fun isEnabled() = enabled

    // ── ভাইব্রেশন — সঠিক/ভুল উত্তরের জন্য shared helper, যাতে প্রতিটা স্ক্রিনে
    //    আলাদা করে Vibrator/VibratorManager boilerplate লিখতে না হয় ──
    fun vibrateCorrect(ctx: Context) = vibrate(ctx, longArrayOf(0, 60))
    fun vibrateWrong(ctx: Context)   = vibrate(ctx, longArrayOf(0, 80, 60, 80))

    fun vibrate(ctx: Context, pattern: LongArray, repeat: Int = -1) {
        if (!enabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            } else {
                @Suppress("DEPRECATION")
                val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(pattern, repeat))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(pattern, repeat)
                }
            }
        } catch (e: Exception) { /* silent fail */ }
    }

    // ── সঠিক উত্তর: দুটো উঠতি tone (ding-ding ✅) ──
    fun playCorrect() {
        if (!enabled) return
        CoroutineScope(Dispatchers.IO).launch {
            playTone(880f, 120)
            Thread.sleep(100)
            playTone(1320f, 180)
        }
    }

    // ── ভুল উত্তর: একটা নামতি buzz (❌) ──
    fun playWrong() {
        if (!enabled) return
        CoroutineScope(Dispatchers.IO).launch {
            playTone(320f, 300)
        }
    }

    fun playClick()  { if (!enabled) return; CoroutineScope(Dispatchers.IO).launch { playTone(1000f, 60) } }
    fun playTimeUp() { if (!enabled) return; CoroutineScope(Dispatchers.IO).launch { playTone(280f, 600) } }

    private fun playTone(freq: Float, durationMs: Int) {
        try {
            val sr  = 44100
            val n   = sr * durationMs / 1000
            val buf = ShortArray(n)
            for (i in 0 until n) {
                val t   = i.toDouble() / sr
                // fade in 10%, fade out 20% — click artifact কমায়
                val env = when {
                    i < n * 0.1  -> i / (n * 0.1)
                    i > n * 0.80 -> (n - i) / (n * 0.20)
                    else -> 1.0
                }
                buf[i] = (Short.MAX_VALUE * 0.7 * env * sin(2 * PI * freq * t)).toInt().toShort()
            }
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC, sr,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                n * 2,
                AudioTrack.MODE_STATIC
            )
            track.write(buf, 0, n)
            track.play()
            handler.postDelayed({ track.stop(); track.release() }, (durationMs + 150).toLong())
        } catch (_: Exception) { /* silent fail */ }
    }

    fun release() {}
}
