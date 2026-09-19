package com.chirag.geosnap.presentation.camera

import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chirag.geosnap.domain.model.GeoCoordinate
import com.chirag.geosnap.domain.usecase.GetCurrentLocationUseCase
import com.chirag.geosnap.domain.usecase.IsLocationEnabledUseCase
import com.chirag.geosnap.presentation.navigation.Destination
import com.chirag.geosnap.testutils.FakeCameraEngine
import com.chirag.geosnap.testutils.FakeGeoTagOverlayRenderer
import com.chirag.geosnap.testutils.FakeLocationRepository
import com.chirag.geosnap.testutils.FakeMediaRepository
import com.chirag.geosnap.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val coordinate = GeoCoordinate(latitude = 23.033863, longitude = 72.585022)

    private fun createViewModel(
        mode: String = CameraMode.PHOTO.routeName,
        engine: FakeCameraEngine = FakeCameraEngine(),
        media: FakeMediaRepository = FakeMediaRepository(),
        location: FakeLocationRepository = FakeLocationRepository(coordinate),
        renderer: FakeGeoTagOverlayRenderer = FakeGeoTagOverlayRenderer()
    ): CameraViewModel {
        val viewModel = CameraViewModel(
            context = ContextWrapper(null),
            savedStateHandle = SavedStateHandle(mapOf(Destination.Camera.ARGUMENT_MODE to mode)),
            mediaRepository = media,
            getCurrentLocation = GetCurrentLocationUseCase(location),
            isLocationEnabled = IsLocationEnabledUseCase(location),
            geoTagOverlayRenderer = renderer
        )
        viewModel.attachEngine(engine)
        return viewModel
    }

    @Test
    fun `photo capture saves image with location and emits MediaSaved`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val vm = createViewModel(mode = CameraMode.PHOTO.routeName, media = media)

            vm.capturePhoto()
            advanceUntilIdle()

            assertEquals(1, media.savedImages.size)
            assertEquals(coordinate, media.savedImages.first().coordinate)
            val event = withTimeoutOrNull(1000) { vm.events.first() }
            assertTrue(event is CameraEvent.MediaSaved)
            assertTrue((event as CameraEvent.MediaSaved).isVideo.not())
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `quick share mode emits QuickShareRequested`() = runTest(mainDispatcherRule.scheduler) {
        val media = FakeMediaRepository()
        val vm = createViewModel(mode = CameraMode.QUICK_SHARE.routeName, media = media)

        vm.capturePhoto()
        advanceUntilIdle()

        val event = withTimeoutOrNull(1000) { vm.events.first() }
        assertTrue(event is CameraEvent.QuickShareRequested)
        assertTrue(media.savedImages.isNotEmpty())
    }

    @Test
    fun `switching mode to quick share in camera emits QuickShareRequested`() =
        runTest(mainDispatcherRule.scheduler) {
            val vm = createViewModel() // enters as PHOTO
            vm.setMode(CameraMode.QUICK_SHARE)
            vm.capturePhoto()
            advanceUntilIdle()
            val event = withTimeoutOrNull(1000) { vm.events.first() }
            assertTrue(event is CameraEvent.QuickShareRequested)
        }

    @Test
    fun `capture still succeeds when location is unavailable`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val vm = createViewModel(
                media = media,
                location = FakeLocationRepository(null)
            )

            vm.capturePhoto()
            advanceUntilIdle()

            assertEquals(1, media.savedImages.size)
            assertNull(media.savedImages.first().coordinate)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `engine capture failure maps to CAPTURE_FAILED`() = runTest(mainDispatcherRule.scheduler) {
        val vm = createViewModel(engine = FakeCameraEngine().apply { failCapture = true })

        vm.capturePhoto()
        advanceUntilIdle()

        assertEquals(CameraError.CAPTURE_FAILED, vm.uiState.value.error)
    }

    @Test
    fun `save failure maps to SAVE_FAILED`() = runTest(mainDispatcherRule.scheduler) {
        val vm = createViewModel(media = FakeMediaRepository().apply { failSave = true })

        vm.capturePhoto()
        advanceUntilIdle()

        assertEquals(CameraError.SAVE_FAILED, vm.uiState.value.error)
    }

    @Test
    fun `video records and saves`() = runTest(mainDispatcherRule.scheduler) {
        val media = FakeMediaRepository()
        val vm = createViewModel(mode = CameraMode.VIDEO.routeName, media = media)

        vm.startRecording()
        runCurrent()
        assertTrue(vm.uiState.value.isRecording)
        assertNull(vm.uiState.value.error)

        vm.stopRecording()
        advanceUntilIdle()

        assertEquals(1, media.savedVideos.size)
        val event = withTimeoutOrNull(1000) { vm.events.first() }
        assertTrue(event is CameraEvent.MediaSaved)
        assertTrue((event as CameraEvent.MediaSaved).isVideo)
        assertFalse(vm.uiState.value.isRecording)
    }

    @Test
    fun `flipping lens updates state and engine`() = runTest(mainDispatcherRule.scheduler) {
        val engine = FakeCameraEngine()
        val vm = createViewModel(engine = engine)

        vm.flipLensFacing()
        advanceUntilIdle()

        assertEquals(CameraEngine.LensFacing.FRONT, vm.uiState.value.lensFacing)
        assertEquals(CameraEngine.LensFacing.FRONT, engine.lensFacing)
    }

    @Test
    fun `flash toggles through engine`() = runTest(mainDispatcherRule.scheduler) {
        val vm = createViewModel()

        vm.toggleFlash()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.flashEnabled)
    }

    @Test
    fun `mode parsed from saved state handle`() {
        val vm = createViewModel(mode = CameraMode.QUICK_SHARE.routeName)
        assertEquals(CameraMode.QUICK_SHARE, vm.mode)
    }

    @Test
    fun `onCameraReady clears processing state`() = runTest(mainDispatcherRule.scheduler) {
        val vm = createViewModel()
        vm.onCameraReady()
        assertTrue(vm.uiState.value.isReady)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `attachEngine wires recording timer`() = runTest(mainDispatcherRule.scheduler) {
        val vm = createViewModel(mode = CameraMode.VIDEO.routeName)
        vm.startRecording()
        runCurrent()
        assertTrue(vm.uiState.value.recordingElapsedMs >= 0)
        vm.stopRecording()
        advanceUntilIdle()
    }

    @Test
    fun `location dialog shows when device location is off`() =
        runTest(mainDispatcherRule.scheduler) {
            val location = FakeLocationRepository(coordinate).apply { locationEnabled = false }
            val vm = createViewModel(location = location)
            runCurrent()
            assertFalse(vm.uiState.value.isLocationEnabled)
            assertTrue(vm.uiState.value.showLocationDialog)
            vm.dismissLocationDialog()
            assertFalse(vm.uiState.value.showLocationDialog)
        }

    @Test
    fun `location dialog stays hidden when device location is on`() =
        runTest(mainDispatcherRule.scheduler) {
            val vm = createViewModel()
            runCurrent()
            assertTrue(vm.uiState.value.isLocationEnabled)
            assertFalse(vm.uiState.value.showLocationDialog)
        }

    @Test
    fun `reportError surfaces error`() {
        val vm = createViewModel()
        vm.reportError(CameraError.CAMERA_UNAVAILABLE)
        assertEquals(CameraError.CAMERA_UNAVAILABLE, vm.uiState.value.error)
    }

    @Test
    fun `photo capture renders burn overlay from captured coordinate`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val renderer = FakeGeoTagOverlayRenderer()
            val vm = createViewModel(
                mode = CameraMode.PHOTO.routeName,
                media = media,
                renderer = renderer
            )

            vm.capturePhoto()
            advanceUntilIdle()

            assertEquals(1, renderer.rendered.size)
            val card = renderer.rendered.first()
            assertEquals(coordinate.latitude, card.latitude)
            assertEquals(coordinate.longitude, card.longitude)
            assertEquals(coordinate.timestamp, card.timestamp)
            assertEquals(1, media.savedImages.size)
            assertEquals(null, media.savedImages.first().overlay)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `photo capture renders fallback card when location unavailable`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val renderer = FakeGeoTagOverlayRenderer()
            val vm = createViewModel(
                mode = CameraMode.PHOTO.routeName,
                media = media,
                location = FakeLocationRepository(null),
                renderer = renderer
            )

            vm.capturePhoto()
            advanceUntilIdle()

            assertEquals(1, renderer.rendered.size)
            val card = renderer.rendered.first()
            assertNull(card.latitude)
            assertNull(card.staticMapUrl)
            assertEquals(1, media.savedImages.size)
            assertNull(media.savedImages.first().coordinate)
            assertEquals(null, media.savedImages.first().overlay)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `video capture renders burn overlay content`() = runTest(mainDispatcherRule.scheduler) {
        val media = FakeMediaRepository()
        val renderer = FakeGeoTagOverlayRenderer()
        val vm = createViewModel(
            mode = CameraMode.VIDEO.routeName,
            media = media,
            renderer = renderer
        )

        vm.startRecording()
        runCurrent()
        vm.stopRecording()
        advanceUntilIdle()

        assertEquals(1, renderer.rendered.size)
        assertEquals(coordinate.latitude, renderer.rendered.first().latitude)
        assertEquals(coordinate.longitude, renderer.rendered.first().longitude)
        assertEquals(1, media.savedVideos.size)
        assertEquals(null, media.savedVideos.first().overlay)
    }
}