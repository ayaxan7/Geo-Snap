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
 * Production [CameraSound].
 *
 * - Capture/shutter → USAGE_ASSISTANCE_SONIFICATION (system-sounds stream)
 * - Video start/stop → USAGE_MEDIA (media/music stream, significantly louder)
 *
 * Call [release] when the owning ViewModel is cleared.
 */
class CameraSoundImpl(context: Context) : CameraSound {

    // Shutter sound — plays on the quiet system-sounds stream
    private val shutterPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // Video sounds — plays on the louder media/music stream
    private val videoPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()

    private val idCapture: Int = shutterPool.load(context, R.raw.sound_capture, 1)
    private val idVideoStart: Int = videoPool.load(context, R.raw.sound_video_start, 1)
    private val idVideoStop: Int = videoPool.load(context, R.raw.sound_video_stop, 1)

    override fun playCapture() { shutterPool.play(idCapture, 1f, 1f, 1, 0, 1f) }
    override fun playVideoStart() { videoPool.play(idVideoStart, 1f, 1f, 1, 0, 1f) }
    override fun playVideoStop() { videoPool.play(idVideoStop, 1f, 1f, 1, 0, 1f) }

    override fun release() {
        shutterPool.release()
        videoPool.release()
    }
}

/** No-op stub used in unit tests (avoids SoundPool Android dependency). */
class NoOpCameraSound : CameraSound {
    override fun playCapture() = Unit
    override fun playVideoStart() = Unit
    override fun playVideoStop() = Unit
    override fun release() = Unit
}
