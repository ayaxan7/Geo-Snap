package com.patashot.myapp.presentation.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import androidx.core.util.Consumer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.exifinterface.media.ExifInterface
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
    private var currentZoom = 1f

    private var boundPreviewView: PreviewView? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundMode: CameraMode = CameraMode.PHOTO
    private var boundPreview: Preview? = null

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
        Log.d(DIAG_TAG, "DEVICE manufacturer=${Build.MANUFACTURER} model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
        Log.d(DIAG_TAG, "CAMERAX_VERSION=1.6.2")
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
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()
            )
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
                boundPreview = it
            }
        val selector = selectorFor(lensFacing)
        val newCamera = when (boundMode) {
            CameraMode.PHOTO, CameraMode.QUICK_SHARE -> {
                boundImageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(100)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                            .build()
                    )
                    .build()
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

        // --- DIAGNOSTICS (non-fatal; do not alter capture behaviour) ---
        runCatching { logCameraDiagnostics(newCamera, lensFacing) }
            .onFailure { Log.e(DIAG_TAG, "Camera diagnostics failed", it) }
        runCatching { logResolutionInfo() }
            .onFailure { Log.e(DIAG_TAG, "ResolutionInfo read failed", it) }
        runCatching { logPreviewResolution(lifecycleOwner.lifecycle) }
            .onFailure { Log.e(DIAG_TAG, "Preview resolution read failed", it) }
        runCatching { observeCameraResolutionOnce(lifecycleOwner.lifecycle) }
            .onFailure { Log.e(DIAG_TAG, "Camera resolution observer failed", it) }
    }

    private fun buildVideoCapture(): VideoCapture<Recorder> {
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.UHD)
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        return VideoCapture.withOutput(recorder)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun logCameraDiagnostics(camera: Camera, lensFacing: CameraEngine.LensFacing) {
        val camera2Info = Camera2CameraInfo.from(camera.cameraInfo)
        val cameraId = camera2Info.cameraId
        Log.d(DIAG_TAG, "CAMERA lensFacing=$lensFacing cameraId=$cameraId")

        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val chars = cm.getCameraCharacteristics(cameraId)
        val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val burstSupported = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)
        Log.d(DIAG_TAG, "HARDWARE_LEVEL=$hwLevel BURST_CAPTURE=$burstSupported")

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        val highResSizes = map?.getHighResolutionOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        Log.d(DIAG_TAG, "JPEG_SIZES=${jpegSizes.joinToString()}")
        Log.d(DIAG_TAG, "HIGH_RES_JPEG_SIZES=${highResSizes.joinToString()}")
        Log.d(DIAG_TAG, "MAX_NORMAL_JPEG=${jpegSizes.maxByOrNull { it.width * it.height }}")
        Log.d(DIAG_TAG, "MAX_HIGH_RES_JPEG=${highResSizes.maxByOrNull { it.width * it.height }}")

        // Aspect ratio analysis from supported JPEG sizes
        val ratios = jpegSizes.map { size ->
            val gcd = gcd(size.width, size.height)
            "${size.width}/${size.height}=${size.width / gcd}:${size.height / gcd}"
        }.toSet()
        Log.d(DIAG_TAG, "SUPPORTED_JPEG_ASPECT_RATIOS=${ratios.joinToString()}")

        val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        Log.d(DIAG_TAG, "SENSOR_PIXEL_ARRAY_SIZE=$sensorSize")
        sensorSize?.let { sensor ->
            Log.d(DIAG_TAG, "SENSOR_ASPECT_RATIO=${sensor.width}:${sensor.height}")
        }
        Log.d(DIAG_TAG, "CAPABILITIES=${caps.contentToString()}")
    }

    private fun logResolutionInfo() {
        val ri = boundImageCapture?.resolutionInfo
        if (ri == null) {
            Log.d(DIAG_TAG, "RESOLUTION_INFO=null (will retry on capture)")
            return
        }
        Log.d(DIAG_TAG, "IMAGECAPTURE_RESOLUTION=${ri.resolution}")
        Log.d(DIAG_TAG, "IMAGECAPTURE_CROP_RECT=${ri.cropRect}")
        Log.d(DIAG_TAG, "IMAGECAPTURE_ROTATION=${ri.rotationDegrees}")
        ri.resolution?.let { size ->
            val gcd = gcd(size.width, size.height)
            Log.d(DIAG_TAG, "IMAGECAPTURE_ASPECT_RATIO=${size.width / gcd}:${size.height / gcd}")
            // Detect cropping: compare selected resolution to sensor size
            Log.d(DIAG_TAG, "IMAGECAPTURE_IS_CROPPED=${isCroppedFromSensor(size)}")
        }
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    @OptIn(ExperimentalCamera2Interop::class)
    private fun isCroppedFromSensor(captureSize: Size): Boolean {
        val map = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camera2Info = Camera2CameraInfo.from(camera?.cameraInfo ?: return false)
        val chars = try {
            map.getCameraCharacteristics(camera2Info.cameraId)
        } catch (e: Exception) {
            return false
        }
        val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return false
        val sensorGcd = gcd(sensorSize.width, sensorSize.height)
        val captureGcd = gcd(captureSize.width, captureSize.height)
        // If aspect ratios differ, cropping occurred
        return sensorSize.width / sensorGcd != captureSize.width / captureGcd ||
            sensorSize.height / sensorGcd != captureSize.height / captureGcd
    }

    /**
     * Best-effort diagnostic: read ImageCapture resolution info once the camera
     * session is configured. Observes the owner lifecycle [START] as a proxy for
     * camera readiness, self-removes after a single read to avoid leaks/polling.
     * Does not block bind() or lens switching, and never delays capture.
     */
    private fun observeCameraResolutionOnce(lifecycle: Lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            private var emitted = false
            override fun onStart(owner: LifecycleOwner) {
                if (emitted) return
                val ri = boundImageCapture?.resolutionInfo
                if (ri != null) {
                    emitted = true
                    lifecycle.removeObserver(this)
                    Log.d(DIAG_TAG, "IMAGECAPTURE_RESOLUTION(lifecycle)=${ri.resolution}")
                    Log.d(DIAG_TAG, "IMAGECAPTURE_CROP_RECT(lifecycle)=${ri.cropRect}")
                    Log.d(DIAG_TAG, "IMAGECAPTURE_ROTATION(lifecycle)=${ri.rotationDegrees}")
                }
            }
        }
        lifecycle.addObserver(observer)
    }

    /**
     * Best-effort diagnostic: read Preview resolution info once the camera
     * session is configured. Observes the owner lifecycle [START] as a proxy for
     * camera readiness, self-removes after a single read.
     */
    private fun logPreviewResolution(lifecycle: Lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            private var emitted = false
            override fun onStart(owner: LifecycleOwner) {
                if (emitted) return
                val ri = boundPreview?.resolutionInfo
                if (ri != null) {
                    emitted = true
                    lifecycle.removeObserver(this)
                    Log.d(DIAG_TAG, "PREVIEW_RESOLUTION=${ri.resolution}")
                    ri.resolution?.let { size ->
                        val gcd = gcd(size.width, size.height)
                        Log.d(DIAG_TAG, "PREVIEW_ASPECT_RATIO=${size.width / gcd}:${size.height / gcd}")
                    }
                    Log.d(DIAG_TAG, "PREVIEW_ROTATION=${ri.rotationDegrees}")
                }
            }
        }
        lifecycle.addObserver(observer)
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
        currentZoom = newCamera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
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

    override suspend fun zoomBy(factor: Float): Unit = withContext(Dispatchers.Main.immediate) {
        val cam = camera ?: return@withContext
        val bounds = cam.cameraInfo.zoomState.value ?: return@withContext
        val target = (currentZoom * factor)
            .coerceIn(bounds.minZoomRatio, bounds.maxZoomRatio)
        currentZoom = target
        cam.cameraControl.setZoomRatio(target)
    }

    override suspend fun capturePhoto(): File = withContext(Dispatchers.Main.immediate) {
        val imageCapture = boundImageCapture
            ?: throw CameraInitException("Camera not bound for photos")

        val file = createTempFile("jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        suspendCancellableCoroutine { continuation ->
            if (boundImageCapture?.resolutionInfo == null) {
                Log.d(DIAG_TAG, "RESOLUTION_INFO still null at capture time")
            }
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        runCatching {
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(file.absolutePath, opts)
                            val exif = ExifInterface(file.absolutePath)
                            val exifW = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1)
                            val exifH = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1)
                            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                            val sizeKb = file.length() / 1024.0
                            val sizeMb = file.length() / (1024.0 * 1024.0)
                            Log.d(DIAG_TAG, "SAVED_JPEG path=${file.absolutePath}")
                            Log.d(DIAG_TAG, "SAVED_JPEG dims=${opts.outWidth}x${opts.outHeight}")
                            Log.d(DIAG_TAG, "SAVED_JPEG exifDims=${exifW}x${exifH} orientation=$orientation")
                            Log.d(DIAG_TAG, "SAVED_JPEG size=${sizeKb}KB (${sizeMb}MB)")
                            opts.outWidth?.let { w ->
                                opts.outHeight?.let { h ->
                                    val gcd = gcd(w, h)
                                    Log.d(DIAG_TAG, "SAVED_JPEG_ASPECT_RATIO=${w / gcd}:${h / gcd}")
                                    Log.d(DIAG_TAG, "SAVED_JPEG_CROPPED=${isCroppedFromSensor(Size(w, h))}")
                                }
                            }

                            val ri = boundImageCapture?.resolutionInfo
                            Log.d(DIAG_TAG, "COMPARE selected=${ri?.resolution} saved=${opts.outWidth}x${opts.outHeight} rotation=${ri?.rotationDegrees}")
                        }.onFailure { Log.e(DIAG_TAG, "JPEG diagnostics failed", it) }

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

    override suspend fun startRecording(): File = withContext(Dispatchers.Main.immediate)
    @androidx.annotation.RequiresPermission(
        android.Manifest.permission.RECORD_AUDIO
    ) {
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
            Consumer { event -> session.onEvent(event) }
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
        _isRecording.value = false
        val file = session.awaitFinalize()
        activeRecording = null
        activeSession = null

        // --- VIDEO DIAGNOSTICS (non-fatal) ---
        runCatching {
            val retriever = android.media.MediaMetadataRetriever().apply {
                setDataSource(file.absolutePath)
            }
            val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: -1
            val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: -1
            val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val sizeKb = file.length() / 1024.0
            val sizeMb = file.length() / (1024.0 * 1024.0)
            Log.d(DIAG_TAG, "SAVED_VIDEO path=${file.absolutePath}")
            Log.d(DIAG_TAG, "SAVED_VIDEO dims=${width}x${height} rotation=$rotation")
            Log.d(DIAG_TAG, "SAVED_VIDEO size=${sizeKb}KB (${sizeMb}MB)")
            runCatching { retriever.release() }
        }.onFailure { Log.e(DIAG_TAG, "Video diagnostics failed", it) }

        file
    }

    override fun release() {
        cameraProvider?.unbindAll()
        camera = null
        boundImageCapture = null
        boundVideoCapture = null
        boundPreviewView = null
        boundPreview = null
        boundLifecycleOwner = null
        activeRecording = null
        activeSession = null
        currentZoom = 1f
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
private const val DIAG_TAG = "PatashotCameraDiagnostics"