package com.patashot.myapp.presentation.camera

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.patashot.myapp.R

/**
 * Contract for camera sound effects so the real [SoundPool]-backed
 * implementation can be swapped for a no-op in unit tests.
 */
interface CameraSound {
    fun playCapture()
    fun playVideoStart()
    fun playVideoStop()
    fun release()
}

/**
 * Production [CameraSound] backed by [SoundPool]. Pre-loads all three sound
 * assets on construction; call [release] when the owning ViewModel is cleared.
 */
class CameraSoundImpl(context: Context) : CameraSound {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val idCapture: Int = pool.load(context, R.raw.sound_capture, 1)
    private val idVideoStart: Int = pool.load(context, R.raw.sound_video_start, 1)
    private val idVideoStop: Int = pool.load(context, R.raw.sound_video_stop, 1)

    override fun playCapture() { pool.play(idCapture, 1f, 1f, 1, 0, 1f) }
    override fun playVideoStart() { pool.play(idVideoStart, 1f, 1f, 1, 0, 1f) }
    override fun playVideoStop() { pool.play(idVideoStop, 1f, 1f, 1, 0, 1f) }
    override fun release() { pool.release() }
}

/** No-op stub used in unit tests (avoids SoundPool Android dependency). */
class NoOpCameraSound : CameraSound {
    override fun playCapture() = Unit
    override fun playVideoStart() = Unit
    override fun playVideoStop() = Unit
    override fun release() = Unit
}
