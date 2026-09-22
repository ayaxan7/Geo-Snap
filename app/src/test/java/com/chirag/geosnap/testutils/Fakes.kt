package com.chirag.geosnap.testutils

import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.chirag.geosnap.domain.model.GeoCoordinate
import com.chirag.geosnap.domain.model.GeoMedia
import com.chirag.geosnap.domain.repository.LocationRepository
import com.chirag.geosnap.domain.repository.MediaRepository
import com.chirag.geosnap.domain.repository.PreferencesRepository
import com.chirag.geosnap.presentation.camera.CameraEngine
import com.chirag.geosnap.presentation.camera.CameraMode
import com.chirag.geosnap.presentation.camera.CameraCaptureException
import com.chirag.geosnap.presentation.camera.GeoTagCardContent
import com.chirag.geosnap.presentation.camera.GeoTagOverlayRenderer
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    initialLanguage: String? = null
) : PreferencesRepository {

    private val _onboarding = MutableStateFlow(initialOnboarding)
    private val _language = MutableStateFlow(initialLanguage)

    override val hasCompletedOnboarding: Flow<Boolean> = _onboarding.asStateFlow()
    override val selectedLanguage: Flow<String?> = _language.asStateFlow()

    val onboardingValue: Boolean get() = _onboarding.value
    val languageValue: String? get() = _language.value

    override suspend fun setOnboardingCompleted() {
        _onboarding.value = true
    }

    override suspend fun setSelectedLanguage(languageCode: String) {
        _language.value = languageCode
    }
}

/** Scripted LocationRepository returning a fixed result (or failing). */
class FakeLocationRepository(
    private val result: GeoCoordinate? = null
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

data class SavedMedia(val file: File, val coordinate: GeoCoordinate?, val overlay: Bitmap? = null)

/** Scripted MediaRepository that records calls instead of touching MediaStore. */
class FakeMediaRepository : MediaRepository {

    var failSave = false
    var failGetAppMedia = false
    var failDelete = false
    val savedImages = mutableListOf<SavedMedia>()
    val savedVideos = mutableListOf<SavedMedia>()
    val deletedUris = mutableListOf<String>()
    var media = mutableListOf<GeoMedia>()
    private var nextId = 0

    override suspend fun saveCapturedImage(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?
    ): String {
        failSaveOnce()
        savedImages += SavedMedia(sourceFile, coordinate, overlay)
        return "content://geosnap.test/images/${nextId++}"
    }

    override suspend fun saveCapturedVideo(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?
    ): String {
        failSaveOnce()
        savedVideos += SavedMedia(sourceFile, coordinate, overlay)
        return "content://geosnap.test/videos/${nextId++}"
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

    private fun failSaveOnce() {
        if (failSave) throw IOException("simulated storage failure")
    }
}

/**
 * Scripted GeoTagOverlayRenderer. Bitmaps cannot be constructed on the JVM, so
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