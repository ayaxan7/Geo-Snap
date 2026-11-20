package com.patashot.myapp.testutils

import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.patashot.myapp.domain.model.GeoCoordinate
import com.patashot.myapp.domain.model.GeoMedia
import com.patashot.myapp.domain.model.NormalizedCropRect
import com.patashot.myapp.domain.repository.LocationRepository
import com.patashot.myapp.domain.repository.MediaRepository
import com.patashot.myapp.domain.repository.PreferencesRepository
import com.patashot.myapp.presentation.camera.CameraEngine
import com.patashot.myapp.presentation.camera.CameraMode
import com.patashot.myapp.presentation.camera.CameraCaptureException
import com.patashot.myapp.presentation.camera.GeoTagCardContent
import com.patashot.myapp.presentation.camera.GeoTagOverlayRenderer
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import com.patashot.myapp.core.update.AppUpdateType
import com.patashot.myapp.core.update.UpdateSignal
import com.patashot.myapp.core.update.UpdateSignalSource
import com.patashot.myapp.data.maps.GeocodeResponse
import com.patashot.myapp.data.maps.GeocodeResult
import com.patashot.myapp.data.maps.MapsApi
import com.patashot.myapp.data.media.CapturedMediaEntity
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody

/** Fully in-memory DataStore<Preferences> so repository logic runs on the JVM. */
class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** Scripted PreferencesRepository for ViewModel tests. */
class FakePreferencesRepository(
    initialOnboarding: Boolean = false,
    initialLanguage: String? = null,
    initialCardTemplateId: String? = null,
    initialAutoSave: Boolean = false,
    initialHomeLaunchCount: Int = 0
) : PreferencesRepository {

    private val _onboarding = MutableStateFlow(initialOnboarding)
    private val _language = MutableStateFlow(initialLanguage)
    private val _cardTemplateId = MutableStateFlow(initialCardTemplateId)
    private val _autoSave = MutableStateFlow(initialAutoSave)
    private val _homeLaunchCount = MutableStateFlow(initialHomeLaunchCount)

    override val hasCompletedOnboarding: Flow<Boolean> = _onboarding.asStateFlow()
    override val selectedLanguage: Flow<String?> = _language.asStateFlow()
    override val selectedCardTemplateId: Flow<String?> = _cardTemplateId.asStateFlow()
    override val isAutoSaveEnabled: Flow<Boolean> = _autoSave.asStateFlow()
    override val homeLaunchCount: Flow<Int> = _homeLaunchCount.asStateFlow()

    val onboardingValue: Boolean get() = _onboarding.value
    val languageValue: String? get() = _language.value
    val cardTemplateValue: String? get() = _cardTemplateId.value

    /** Simulates a persisted template written outside this ViewModel, e.g. by Settings. */
    fun emitCardTemplateId(templateId: String?) {
        _cardTemplateId.value = templateId
    }

    override suspend fun setOnboardingCompleted() {
        _onboarding.value = true
    }

    override suspend fun setSelectedLanguage(languageCode: String) {
        _language.value = languageCode
    }

    override suspend fun setSelectedCardTemplateId(templateId: String) {
        _cardTemplateId.value = templateId
    }

    override suspend fun setAutoSaveEnabled(enabled: Boolean) {
        _autoSave.value = enabled
    }

    override suspend fun incrementHomeLaunchCount(): Int {
        val next = _homeLaunchCount.value + 1
        _homeLaunchCount.value = next
        return next
    }
}

/** Scripted LocationRepository returning a fixed result (or failing). */
class FakeLocationRepository(
    var result: GeoCoordinate? = null
) : LocationRepository {

    var calls = 0
    var failWith: Throwable? = null
    var locationEnabled = true

    override suspend fun getCurrentLocation(): GeoCoordinate? {
        calls++
        failWith?.let { throw it }
        return result
    }

    override suspend fun isLocationEnabled(): Boolean = locationEnabled
}

data class SavedMedia(
    val file: File,
    val coordinate: GeoCoordinate?,
    val overlay: Bitmap? = null,
    val filter: FloatArray? = null,
    val saveToMediaStore: Boolean = true,
    val rotationDegrees: Int = 0,
    val cropRect: NormalizedCropRect? = null
)

/** Scripted MediaRepository that records calls instead of touching MediaStore. */
class FakeMediaRepository : MediaRepository {

    var failSave = false
    var failShare = false
    var failGetAppMedia = false
    var failDelete = false
    val savedImages = mutableListOf<SavedMedia>()
    val savedVideos = mutableListOf<SavedMedia>()
    val sharedCopies = mutableListOf<SavedMedia>()
    val deletedUris = mutableListOf<String>()
    var media = mutableListOf<GeoMedia>()
    private var nextId = 0

    override suspend fun saveCapturedImage(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?,
        filter: FloatArray?,
        saveToMediaStore: Boolean,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect?,
        cardContent: GeoTagCardContent?
    ): String {
        failSaveOnce()
        savedImages += SavedMedia(
            file = sourceFile,
            coordinate = coordinate,
            overlay = overlay,
            filter = filter,
            saveToMediaStore = saveToMediaStore,
            rotationDegrees = rotationDegrees,
            cropRect = cropRect
        )
        return "content://myapp.test/images/${nextId++}"
    }

    override suspend fun saveCapturedVideo(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?,
        filter: FloatArray?,
        saveToMediaStore: Boolean,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect?,
        cardContent: GeoTagCardContent?
    ): String {
        failSaveOnce()
        savedVideos += SavedMedia(
            file = sourceFile,
            coordinate = coordinate,
            overlay = overlay,
            filter = filter,
            saveToMediaStore = saveToMediaStore,
            rotationDegrees = rotationDegrees,
            cropRect = cropRect
        )
        return "content://myapp.test/videos/${nextId++}"
    }

    override suspend fun prepareShareCopy(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?,
        filter: FloatArray?,
        isVideo: Boolean,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect?
    ): String {
        if (failShare) throw IOException("simulated share failure")
        sharedCopies += SavedMedia(
            file = sourceFile,
            coordinate = coordinate,
            overlay = overlay,
            filter = filter,
            saveToMediaStore = false,
            rotationDegrees = rotationDegrees,
            cropRect = cropRect
        )
        return "content://myapp.test/share/${nextId++}?video=$isVideo"
    }

    override suspend fun getAppMedia(): List<GeoMedia> {
        if (failGetAppMedia) throw IOException("simulated query failure")
        return media.sortedByDescending { it.timestamp }
    }

    override suspend fun deleteMedia(contentUri: String): Boolean {
        if (failDelete) return false
        deletedUris += contentUri
        media.removeAll { it.uri.toString() == contentUri }
        return true
    }

    var failSaveToDeviceGallery = false
    val exportedToGallery = mutableListOf<GeoMedia>()

    override suspend fun saveToDeviceGallery(media: GeoMedia): Boolean {
        if (failSaveToDeviceGallery) return false
        exportedToGallery += media
        return true
    }

    override suspend fun getMediaEntityById(id: Long): CapturedMediaEntity? = null

    override suspend fun resolveMediaFile(contentUri: String): File? = null

    override suspend fun updateCapturedMedia(
        id: Long,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?,
        filter: FloatArray?,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect?,
        cardContent: GeoTagCardContent
    ): Boolean = true

    private fun failSaveOnce() {
        if (failSave) throw IOException("simulated storage failure")
    }
}

/** Scripted GeoTagOverlayRenderer. Bitmaps cannot be constructed on the JVM, so
 * [result] is left null by default; tests assert the card content that was
 * requested for rendering rather than the pixels.
 */
class FakeGeoTagOverlayRenderer(
    var result: Bitmap? = null
) : GeoTagOverlayRenderer {

    val rendered = mutableListOf<GeoTagCardContent>()

    override suspend fun render(content: GeoTagCardContent): Bitmap? {
        rendered += content
        return result
    }
}

/** Scripted MapsApi: canned reverse-geocode results, no network calls. */
class FakeMapsApi : MapsApi {

    var formattedAddress: String? = null

    /** Number of upcoming calls that should fail before [formattedAddress] is served. */
    var failTimes = 0
    val reverseGeocodeCalls = mutableListOf<String>()
    val staticMapRequests = mutableListOf<String>()

    override suspend fun reverseGeocode(
        latLng: String,
        language: String,
        apiKey: String
    ): GeocodeResponse {
        reverseGeocodeCalls += latLng
        if (failTimes > 0) {
            failTimes--
            throw IOException("simulated geocode failure")
        }
        val address = formattedAddress
        return GeocodeResponse(
            status = "OK",
            results = address?.let { listOf(GeocodeResult(formattedAddress = it)) }.orEmpty()
        )
    }

    override suspend fun staticMap(url: String): ResponseBody {
        staticMapRequests += url
        if (failTimes > 0) {
            failTimes--
            throw IOException("simulated static map failure")
        }
        return ByteArray(0).toResponseBody(null)
    }
}

/** Scripted CameraEngine for ViewModel tests (no hardware). */
class FakeCameraEngine : CameraEngine {

    override val hasFlash = MutableStateFlow(true)
    override val flashEnabled = MutableStateFlow(false)
    override val isRecording = MutableStateFlow(false)

    var bindCount = 0
    var lensFacing = CameraEngine.LensFacing.BACK
    var failCapture = false
    private val captureFile = File("fake_capture.jpg")
    val recordedFile = File("fake_recording.mp4")

    override suspend fun bind(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        lensFacing: CameraEngine.LensFacing,
        mode: CameraMode
    ) {
        bindCount++
        this.lensFacing = lensFacing
    }

    override suspend fun setLensFacing(lensFacing: CameraEngine.LensFacing): Boolean {
        this.lensFacing = lensFacing
        return true
    }

    override suspend fun toggleFlash(): Boolean {
        flashEnabled.value = !flashEnabled.value
        return flashEnabled.value
    }

    val zoomFactors = mutableListOf<Float>()

    override suspend fun zoomBy(factor: Float) {
        zoomFactors.add(factor)
    }

    override suspend fun capturePhoto(): File {
        if (failCapture) {
            throw CameraCaptureException(1, File("failed.jpg"))
        }
        return captureFile
    }

    override suspend fun startRecording(): File {
        isRecording.value = true
        return recordedFile
    }

    override suspend fun stopRecording(): File {
        isRecording.value = false
        return recordedFile
    }

    override fun release() = Unit
}

/**
 * Stand-in for the Play update dialog launcher. [UpdateSignalSource.start] only
 * forwards it, so no test ever launches an update flow.
 */
class NoOpUpdateLauncher : ActivityResultLauncher<IntentSenderRequest>() {
    override fun launch(input: IntentSenderRequest) = Unit
    override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) = Unit
    override fun unregister() = Unit
    override fun getContract(): ActivityResultContract<IntentSenderRequest, *> =
        throw UnsupportedOperationException("Tests never launch an update flow")
}

/** Scripted [UpdateSignalSource]: replays a fixed list of signals, no Play Store. */
class FakeUpdateSignalSource(
    private val signalsToEmit: List<UpdateSignal> = emptyList()
) : UpdateSignalSource {

    /** Types the app asked Play to show, in order. */
    val startedFlows = mutableListOf<AppUpdateType>()

    var completeCalls = 0
    var startError: Throwable? = null
    var completeError: Throwable? = null

    override fun signals(): Flow<UpdateSignal> = signalsToEmit.asFlow()

    override fun start(
        type: AppUpdateType,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        startedFlows += type
        startError?.let { throw it }
    }

    override suspend fun complete() {
        completeCalls++
        completeError?.let { throw it }
    }
}