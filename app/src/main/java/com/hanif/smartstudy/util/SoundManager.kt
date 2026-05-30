package com.hanif.smartstudy.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

object SoundManager {
    private var pool      : SoundPool? = null
    private var sCorrect  : Int = 0
    private var sWrong    : Int = 0
    private var sClick    : Int = 0
    private var sTick     : Int = 0
    private var enabled   : Boolean = true

    fun init(context: Context) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        // Raw resource যদি থাকে — নইলে system sounds fallback করবে
        // sCorrect = pool!!.load(context, R.raw.correct, 1)
        // sWrong   = pool!!.load(context, R.raw.wrong, 1)
    }

    fun setEnabled(on: Boolean) { enabled = on }

    fun playCorrect() { if (enabled) playTone(1500f, 150) }
    fun playWrong()   { if (enabled) playTone(400f,  200) }
    fun playClick()   { if (enabled) playTone(1000f, 80)  }
    fun playTimeUp()  { if (enabled) playTone(300f,  500) }

    private fun playTone(freq: Float, durationMs: Int) {
        try {
            val sr   = 44100
            val n    = (sr * durationMs / 1000.0).toInt()
            val buf  = ShortArray(n)
            for (i in 0 until n) {
                val t   = i.toDouble() / sr
                val env = if (i < n * 0.1) i / (n * 0.1) else if (i > n * 0.8) (n - i) / (n * 0.2) else 1.0
                buf[i]  = (Short.MAX_VALUE * env * Math.sin(2 * Math.PI * freq * t)).toInt().toShort()
            }
            val track = android.media.AudioTrack(
                android.media.AudioManager.STREAM_MUSIC, sr,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                n * 2, android.media.AudioTrack.MODE_STATIC
            )
            track.write(buf, 0, n)
            track.play()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                track.stop(); track.release()
            }, (durationMs + 100).toLong())
        } catch (e: Exception) { /* silent fail */ }
    }

    fun release() { pool?.release(); pool = null }
}
