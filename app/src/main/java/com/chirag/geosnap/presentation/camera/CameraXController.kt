package com.chirag.geosnap.presentation.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * [CameraEngine] backed by CameraX + a [PreviewView] surface.
 *
 * Created (and released) by the camera screen instead of Hilt because hardware
 * binding is tied to the preview's lifecycle, which is composition-owned.
 */
class CameraXController(
    private val appContext: Context
) : CameraEngine {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var boundImageCapture: ImageCapture? = null
    private var boundVideoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var activeSession: RecordingSession? = null
    private var currentLensFacing: CameraEngine.LensFacing = CameraEngine.LensFacing.BACK

    private var boundPreviewView: PreviewView? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundMode: CameraMode = CameraMode.PHOTO

    private val _hasFlash = MutableStateFlow(false)
    override val hasFlash: StateFlow<Boolean> = _hasFlash.asStateFlow()

    private val _flashEnabled = MutableStateFlow(false)
    override val flashEnabled: StateFlow<Boolean> = _flashEnabled.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    override suspend fun bind(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        lensFacing: CameraEngine.LensFacing,
        mode: CameraMode
    ) {
        boundPreviewView = previewView
        boundLifecycleOwner = lifecycleOwner
        boundMode = mode
        currentLensFacing = lensFacing
        _isRecording.value = false

        withContext(Dispatchers.Main.immediate) {
            val provider = provideCameraProvider()
            rebindTo(provider, currentLensFacing)
        }
    }

    override suspend fun setLensFacing(lensFacing: CameraEngine.LensFacing): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val provider = provideCameraProvider()
            val selector = selectorFor(lensFacing)
            val available = runCatching { provider.hasCamera(selector) }.getOrDefault(false)
            if (!available || boundPreviewView == null || boundLifecycleOwner == null) {
                return@withContext false
            }
            currentLensFacing = lensFacing
            rebindTo(provider, lensFacing)
            true
        }

    private fun rebindTo(provider: ProcessCameraProvider, lensFacing: CameraEngine.LensFacing) {
        val lifecycleOwner = boundLifecycleOwner ?: return
        val previewView = boundPreviewView ?: return

        provider.unbindAll()
        _isRecording.value = false
        _flashEnabled.value = false

        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val selector = selectorFor(lensFacing)
        val newCamera = when (boundMode) {
            CameraMode.PHOTO, CameraMode.QUICK_SHARE -> {
                boundImageCapture = ImageCapture.Builder().build()
                boundVideoCapture = null
                provider.bindToLifecycle(lifecycleOwner, selector, preview, boundImageCapture)
            }
            CameraMode.VIDEO -> {
                boundImageCapture = null
                boundVideoCapture = buildVideoCapture()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, boundVideoCapture)
            }
        }
        attachCamera(newCamera)
    }

    private fun buildVideoCapture(): VideoCapture<Recorder> {
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.FHD, Quality.HD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        return VideoCapture.withOutput(recorder)
    }

    private fun selectorFor(lensFacing: CameraEngine.LensFacing): CameraSelector =
        CameraSelector.Builder()
            .requireLensFacing(
                if (lensFacing == CameraEngine.LensFacing.BACK) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
            )
            .build()

    private fun attachCamera(newCamera: Camera) {
        camera = newCamera
        _hasFlash.value = newCamera.cameraInfo.hasFlashUnit()
        _flashEnabled.value = false
    }

    override suspend fun toggleFlash(): Boolean = withContext(Dispatchers.Main.immediate) {
        val cam = camera ?: return@withContext false
        if (!cam.cameraInfo.hasFlashUnit()) return@withContext false

        val next = !_flashEnabled.value
        val imageCapture = boundImageCapture
        if (imageCapture != null) {
            imageCapture.flashMode =
                if (next) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            if (!next) {
                runCatching { cam.cameraControl.enableTorch(false) }
            }
        } else {
            runCatching { cam.cameraControl.enableTorch(next).get() }
        }
        _flashEnabled.value = next
        next
    }

    override suspend fun capturePhoto(): File = withContext(Dispatchers.Main.immediate) {
        val imageCapture = boundImageCapture
            ?: throw CameraInitException("Camera not bound for photos")

        val file = createTempFile("jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        suspendCancellableCoroutine { continuation ->
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (!continuation.isCancelled) continuation.resume(file)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (!continuation.isCancelled) {
                            continuation.resumeWithException(
                                CameraCaptureException(exception.imageCaptureError, file)
                            )
                        }
                    }
                }
            )
        }
    }

    override suspend fun startRecording(): File = withContext(Dispatchers.Main.immediate) {
        val videoCapture = boundVideoCapture
            ?: throw CameraInitException("Camera not bound for video")
        val recorder = videoCapture.output

        val file = createTempFile("mp4")
        val options = FileOutputOptions.Builder(file).build()
        val pending = recorder
            .prepareRecording(appContext, options)
            .withAudioEnabled()
        val session = RecordingSession(file)
        activeSession = session
        activeRecording = pending.start(
            ContextCompat.getMainExecutor(appContext),
            androidx.core.util.Consumer { event -> session.onEvent(event) }
        )
        session.awaitStart()
        _isRecording.value = true
        file
    }

    override suspend fun stopRecording(): File = withContext(Dispatchers.Main.immediate) {
        val recording = activeRecording
            ?: throw CameraInitException("No active recording")
        val session = activeSession
            ?: throw CameraInitException("No active recording session")

        recording.stop()
        val file = session.awaitFinalize()
        activeRecording = null
        activeSession = null
        file
    }

    override fun release() {
        cameraProvider?.unbindAll()
        camera = null
        boundImageCapture = null
        boundVideoCapture = null
        boundPreviewView = null
        boundLifecycleOwner = null
        activeRecording = null
        activeSession = null
        _isRecording.value = false
        _flashEnabled.value = false
        _hasFlash.value = false
    }

    private fun createTempFile(extension: String): File {
        val dir = File(appContext.cacheDir, TEMP_DIR).apply { mkdirs() }
        return File.createTempFile("capture_", ".$extension", dir)
    }

    private suspend fun provideCameraProvider(): ProcessCameraProvider {
        cameraProvider?.let { return it }
        val future = ProcessCameraProvider.getInstance(appContext)
        val provider = suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
            future.addListener(
                {
                    if (!continuation.isCancelled) {
                        val result = runCatching { future.get() }
                        result.onSuccess { continuation.resume(it) }
                        result.onFailure { e ->
                            continuation.resumeWithException(
                                CameraInitException("CameraX init failed", e)
                            )
                        }
                    }
                },
                ContextCompat.getMainExecutor(appContext)
            )
            continuation.invokeOnCancellation { future.cancel(false) }
        }
        cameraProvider = provider
        return provider
    }

    private inner class RecordingSession(private val outputFile: File) {
        private val _events = MutableSharedFlow<VideoRecordEvent>(
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val events = _events.asSharedFlow()

        fun onEvent(event: VideoRecordEvent) {
            _events.tryEmit(event)
        }

        suspend fun awaitStart() {
            val event = events
                .catch { }
                .first { it is VideoRecordEvent.Start || it is VideoRecordEvent.Finalize }
            if (event is VideoRecordEvent.Finalize) {
                throwForFailedFinalize(event)
            }
        }

        suspend fun awaitFinalize(): File {
            val finalize = events
                .catch { }
                .first { it is VideoRecordEvent.Finalize } as VideoRecordEvent.Finalize
            throwForFailedFinalize(finalize)
            return outputFile
        }

        private fun throwForFailedFinalize(finalize: VideoRecordEvent.Finalize) {
            if (finalize.error != VideoRecordEvent.Finalize.ERROR_NONE) {
                throw CameraRecordingException(finalize.error)
            }
        }
    }
}

open class CameraInitException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class CameraCaptureException(
    val errorCode: Int,
    val outputFile: File
) : CameraInitException("Capture failed ($errorCode)")

class CameraRecordingException(val errorCode: Int) : Exception("Recording failed ($errorCode)")

private const val TEMP_DIR = "geosnap_camera"