package com.chirag.geosnap.presentation.camera

import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chirag.geosnap.data.maps.MapsApi
import com.chirag.geosnap.domain.model.GeoCoordinate
import com.chirag.geosnap.domain.usecase.GetCurrentLocationUseCase
import com.chirag.geosnap.domain.usecase.IsLocationEnabledUseCase
import com.chirag.geosnap.presentation.navigation.Destination
import com.chirag.geosnap.testutils.FakeCameraEngine
import com.chirag.geosnap.testutils.FakeGeoTagOverlayRenderer
import com.chirag.geosnap.testutils.FakeLocationRepository
import com.chirag.geosnap.testutils.FakeMapsApi
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val coordinate = GeoCoordinate(latitude = 23.033863, longitude = 72.585022)
    private val address = "1 Example Street, Testville"

    private fun createViewModel(
        mode: String = CameraMode.PHOTO.routeName,
        engine: FakeCameraEngine = FakeCameraEngine(),
        media: FakeMediaRepository = FakeMediaRepository(),
        location: FakeLocationRepository = FakeLocationRepository(coordinate),
        renderer: FakeGeoTagOverlayRenderer = FakeGeoTagOverlayRenderer(),
        mapsApi: MapsApi = FakeMapsApi()
    ): CameraViewModel {
        val viewModel = CameraViewModel(
            context = ContextWrapper(null),
            savedStateHandle = SavedStateHandle(mapOf(Destination.Camera.ARGUMENT_MODE to mode)),
            mediaRepository = media,
            getCurrentLocation = GetCurrentLocationUseCase(location),
            isLocationEnabled = IsLocationEnabledUseCase(location),
            geoTagOverlayRenderer = renderer,
            mapsApi = mapsApi
        )
        viewModel.attachEngine(engine)
        return viewModel
    }

    @Test
    fun `photo capture opens a preview instead of saving immediately`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val vm = createViewModel(mode = CameraMode.PHOTO.routeName, media = media)

            vm.capturePhoto()
            advanceUntilIdle()

            val preview = vm.uiState.value.pendingPreview
            assertNotNull(preview)
            assertFalse(preview!!.isVideo)
            assertEquals(coordinate, preview.coordinate)
            assertTrue(media.savedImages.isEmpty())
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `editing the location refreshes the preview card static map url`() =
        runTest(mainDispatcherRule.scheduler) {
            val vm = createViewModel(mode = CameraMode.PHOTO.routeName)

            vm.capturePhoto()
            advanceUntilIdle()

            val originalUrl = vm.uiState.value.pendingPreview?.cardContent?.staticMapUrl
            assertNotNull(originalUrl)

            val newLat = 23.043863
            val newLng = 72.595022
            vm.updatePendingLocation(
                city = "New City",
                address = "9 New Street, Testville",
                lat = newLat,
                lng = newLng
            )

            val card = vm.uiState.value.pendingPreview?.cardContent
            assertNotNull(card)
            assertEquals("New City", card!!.cityString)
            assertEquals("9 New Street, Testville", card.fullAddress)
            assertEquals(newLat, card.latitude)
            assertEquals(newLng, card.longitude)
            val newUrl = card.staticMapUrl
            assertNotNull(newUrl)
            assertTrue(
                "expected the new url to embed the edited coords but was: $newUrl",
                newUrl!!.contains("$newLat,$newLng")
            )
            assertNotEquals(originalUrl, newUrl)
        }

    @Test
    fun `saving a preview stores the image and emits MediaSaved`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val vm = createViewModel(mode = CameraMode.PHOTO.routeName, media = media)

            vm.capturePhoto()
            advanceUntilIdle()
            vm.savePreviewToGallery()
            advanceUntilIdle()

            assertEquals(1, media.savedImages.size)
            assertEquals(coordinate, media.savedImages.first().coordinate)
            val event = withTimeoutOrNull(1000.milliseconds) { vm.events.first() }
            assertTrue(event is CameraEvent.MediaSaved)
            assertFalse((event as CameraEvent.MediaSaved).isVideo)
            assertNull(vm.uiState.value.pendingPreview)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `quick share mode emits QuickShareRequested`() = runTest(mainDispatcherRule.scheduler) {
        val media = FakeMediaRepository()
        val vm = createViewModel(mode = CameraMode.QUICK_SHARE.routeName, media = media)

        vm.capturePhoto()
        advanceUntilIdle()

        val event = withTimeoutOrNull(1000.milliseconds) { vm.events.first() }
        assertTrue(event is CameraEvent.QuickShareRequested)
        assertTrue(media.savedImages.isNotEmpty())
        assertNull(vm.uiState.value.pendingPreview)
    }

    @Test
    fun `switching mode to quick share in camera emits QuickShareRequested`() =
        runTest(mainDispatcherRule.scheduler) {
            val vm = createViewModel() // enters as PHOTO
            vm.setMode(CameraMode.QUICK_SHARE)
            vm.capturePhoto()
            advanceUntilIdle()
            val event = withTimeoutOrNull(1000.milliseconds) { vm.events.first() }
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

            val preview = vm.uiState.value.pendingPreview
            assertNotNull(preview)
            assertNull(preview!!.coordinate)
            assertNull(preview.cardContent.latitude)
            assertTrue(media.savedImages.isEmpty())
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `engine capture failure maps to CAPTURE_FAILED`() = runTest(mainDispatcherRule.scheduler) {
        val vm = createViewModel(engine = FakeCameraEngine().apply { failCapture = true })

        vm.capturePhoto()
        advanceUntilIdle()

        assertEquals(CameraError.CAPTURE_FAILED, vm.uiState.value.error)
        assertNull(vm.uiState.value.pendingPreview)
    }

    @Test
    fun `save failure maps to SAVE_FAILED`() = runTest(mainDispatcherRule.scheduler) {
        val vm = createViewModel(media = FakeMediaRepository().apply { failSave = true })

        vm.capturePhoto()
        advanceUntilIdle()
        vm.savePreviewToGallery()
        advanceUntilIdle()

        assertEquals(CameraError.SAVE_FAILED, vm.uiState.value.error)
    }

    @Test
    fun `discarding a preview deletes the temp file and closes the preview`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val vm = createViewModel(media = media)

            vm.capturePhoto()
            advanceUntilIdle()
            val file = vm.uiState.value.pendingPreview!!.file
            assertTrue(file.exists() || true) // FakeCameraEngine never writes it
            file.writeText("pending capture")
            assertTrue(file.exists())

            vm.discardPreview()
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingPreview)
            assertFalse(file.exists())
            assertTrue(media.savedImages.isEmpty())
        }

    @Test
    fun `video records and opens a preview that saves`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val vm = createViewModel(mode = CameraMode.VIDEO.routeName, media = media)
            vm.startRecording()
            runCurrent()
            assertTrue(vm.uiState.value.isRecording)
            assertNull(vm.uiState.value.error)

            vm.stopRecording()
            advanceUntilIdle()

            val preview = vm.uiState.value.pendingPreview
            assertNotNull(preview)
            assertTrue(preview!!.isVideo)
            assertTrue(media.savedVideos.isEmpty())

            vm.savePreviewToGallery()
            advanceUntilIdle()

            assertEquals(1, media.savedVideos.size)
            val event = withTimeoutOrNull(1000.milliseconds) { vm.events.first() }
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
    fun `saving a preview burns the reviewed card using the captured coordinate`() =
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

            // Nothing is burnt or written until the user commits the capture.
            assertTrue(renderer.rendered.isEmpty())
            assertTrue(media.savedImages.isEmpty())

            vm.savePreviewToGallery()
            advanceUntilIdle()

            assertEquals(1, renderer.rendered.size)
            val card = renderer.rendered.first()
            assertEquals(coordinate.latitude, card.latitude)
            assertEquals(coordinate.longitude, card.longitude)
            assertEquals(coordinate.timestamp, card.timestamp)
            assertEquals(1, media.savedImages.size)
            assertNull(media.savedImages.first().overlay)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `saving a preview burns a fallback card when location is unavailable`() =
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
            vm.savePreviewToGallery()
            advanceUntilIdle()

            assertEquals(1, renderer.rendered.size)
            val card = renderer.rendered.first()
            assertNull(card.latitude)
            assertNull(card.staticMapUrl)
            assertEquals(1, media.savedImages.size)
            assertNull(media.savedImages.first().coordinate)
        }

    @Test
    fun `filter defaults to original for a new capture`() =
        runTest(mainDispatcherRule.scheduler) {
            val vm = createViewModel(mode = CameraMode.PHOTO.routeName)

            vm.capturePhoto()
            advanceUntilIdle()

            assertEquals(CaptureFilter.ORIGINAL, vm.uiState.value.filter)
            assertEquals(0f, vm.uiState.value.filterStrength)
        }

    @Test
    fun `a new capture clears a previously selected filter`() =
        runTest(mainDispatcherRule.scheduler) {
            val vm = createViewModel(mode = CameraMode.PHOTO.routeName)

            vm.capturePhoto()
            advanceUntilIdle()
            vm.setFilter(CaptureFilter.VIBRANT)
            vm.setFilterStrength(0.5f)
            assertEquals(CaptureFilter.VIBRANT, vm.uiState.value.filter)

            vm.capturePhoto()
            advanceUntilIdle()

            assertEquals(CaptureFilter.ORIGINAL, vm.uiState.value.filter)
            assertEquals(0f, vm.uiState.value.filterStrength)
        }

    @Test
    fun `filter strength is clamped between zero and one`() =
        runTest(mainDispatcherRule.scheduler) {
            val vm = createViewModel()
            vm.setFilterStrength(-3f)
            assertEquals(0f, vm.uiState.value.filterStrength)
            vm.setFilterStrength(7f)
            assertEquals(1f, vm.uiState.value.filterStrength)
        }

    @Test
    fun `saving an unedited preview forwards no filter recipe`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val vm = createViewModel(mode = CameraMode.PHOTO.routeName, media = media)

            vm.capturePhoto()
            advanceUntilIdle()
            vm.savePreviewToGallery()
            advanceUntilIdle()

            assertEquals(1, media.savedImages.size)
            assertNull(media.savedImages.first().filter)
        }

    @Test
    fun `saving a preview forwards the selected filter recipe and resets state`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val vm = createViewModel(mode = CameraMode.PHOTO.routeName, media = media)

            vm.capturePhoto()
            advanceUntilIdle()
            vm.setFilter(CaptureFilter.SHARP_BLACK)
            vm.setFilterStrength(1f)
            vm.savePreviewToGallery()
            advanceUntilIdle()

            val saved = media.savedImages.single()
            assertNotNull(saved.filter)
            // The contrast anchor for the full-strength recipe is baked in.
            assertEquals(-64f, saved.filter!![4], 1e-3f)
            // Back on the camera screen, the edits are forgotten.
            assertNull(vm.uiState.value.pendingPreview)
            assertEquals(CaptureFilter.ORIGINAL, vm.uiState.value.filter)
            assertEquals(0f, vm.uiState.value.filterStrength)
            assertTrue(withTimeoutOrNull(1000.milliseconds) { vm.events.first() } is CameraEvent.MediaSaved)
        }

    @Test
    fun `saving a video forwards the selected filter recipe`() =
        runTest(mainDispatcherRule.scheduler) {
            val media = FakeMediaRepository()
            val vm = createViewModel(mode = CameraMode.VIDEO.routeName, media = media)

            vm.startRecording()
            runCurrent()
            vm.stopRecording()
            advanceUntilIdle()

            vm.setFilter(CaptureFilter.GRAY)
            vm.setFilterStrength(0.5f)
            vm.savePreviewToGallery()
            advanceUntilIdle()

            val saved = media.savedVideos.single()
            assertNotNull(saved.filter)
            // Half-strength gray: the red diagonal sits halfway between 1 and the
            // 0.213 luminance weight.
            assertEquals((1f + 0.213f) / 2f, saved.filter!![0], 1e-3f)
            assertEquals(CaptureFilter.ORIGINAL, vm.uiState.value.filter)
            assertEquals(0f, vm.uiState.value.filterStrength)
        }

    @Test
    fun `saving a recorded preview burns the reviewed card`() =
        runTest(mainDispatcherRule.scheduler) {
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

            assertTrue(renderer.rendered.isEmpty())

            vm.savePreviewToGallery()
            advanceUntilIdle()

            assertEquals(1, renderer.rendered.size)
            assertEquals(coordinate.latitude, renderer.rendered.first().latitude)
            assertEquals(coordinate.longitude, renderer.rendered.first().longitude)
            assertEquals(1, media.savedVideos.size)
        }

    @Test
    fun `surfaces Google formatted address from maps api`() =
        runTest(mainDispatcherRule.scheduler) {
            val mapsApi = FakeMapsApi().apply {
                formattedAddress = "1 Example Street, Testville"
            }
            val vm = createViewModel(mapsApi = mapsApi)

            vm.onCameraReady()
            runCurrent()

            assertEquals("1 Example Street, Testville", vm.uiState.value.fullAddress)
            assertEquals(1, mapsApi.reverseGeocodeCalls.size)
            assertEquals("23.033863,72.585022", mapsApi.reverseGeocodeCalls.first())

            vm.detachEngine()
        }

    @Test
    fun `location polls do not re-resolve address while device is stationary`() =
        runTest(mainDispatcherRule.scheduler) {
            val mapsApi = FakeMapsApi().apply {
                formattedAddress = "1 Example Street, Testville"
            }
            val vm = createViewModel(mapsApi = mapsApi)
            vm.onCameraReady()
            runCurrent()
            assertEquals(1, mapsApi.reverseGeocodeCalls.size)

            mainDispatcherRule.scheduler.advanceTimeBy(30_000)
            runCurrent()

            // Three further polls with the same fix must not hit the Maps API.
            assertEquals(1, mapsApi.reverseGeocodeCalls.size)
            vm.detachEngine()
        }

    @Test
    fun `location update after significant movement re-resolves address`() =
        runTest(mainDispatcherRule.scheduler) {
            val mapsApi = FakeMapsApi().apply {
                formattedAddress = "1 Example Street, Testville"
            }
            val location = FakeLocationRepository(coordinate)
            val vm = createViewModel(mapsApi = mapsApi, location = location)
            vm.onCameraReady()
            runCurrent()
            assertEquals(1, mapsApi.reverseGeocodeCalls.size)

            // ~200m north — beyond the 30m threshold.
            location.result = coordinate.copy(latitude = coordinate.latitude + 0.002)
            mainDispatcherRule.scheduler.advanceTimeBy(10_000)
            runCurrent()

            assertEquals(2, mapsApi.reverseGeocodeCalls.size)
            assertEquals("23.035863,72.585022", mapsApi.reverseGeocodeCalls.last())
            vm.detachEngine()
        }

    @Test
    fun `capture resolves the address for the captured coordinate when no poll has landed`() =
        runTest(mainDispatcherRule.scheduler) {
            val mapsApi = FakeMapsApi().apply { formattedAddress = address }
            val vm = createViewModel(mapsApi = mapsApi)

            // onCameraReady() is never called, so the poll has resolved nothing.
            vm.capturePhoto()
            advanceUntilIdle()

            val card = vm.uiState.value.pendingPreview!!.cardContent
            assertEquals(address, card.fullAddress)
            assertEquals(coordinate.latitude, card.latitude)
            assertEquals(coordinate.longitude, card.longitude)
            assertEquals(1, mapsApi.reverseGeocodeCalls.size)
        }

    @Test
    fun `discarding a preview leaves the resolved title on the camera card`() =
        runTest(mainDispatcherRule.scheduler) {
            val mapsApi = FakeMapsApi().apply { formattedAddress = address }
            val vm = createViewModel(mapsApi = mapsApi)

            vm.capturePhoto()
            advanceUntilIdle()
            vm.discardPreview()
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingPreview)
            // Regression: the card used to fall back to "Fetching location..."
            // because the title was only ever written by the poll.
            assertEquals(address, vm.uiState.value.fullAddress)
            assertEquals(address, vm.uiState.value.toGeoTagCardContent().fullAddress)
        }

    @Test
    fun `a failed geocode is retried on a later poll instead of being cached`() =
        runTest(mainDispatcherRule.scheduler) {
            val mapsApi = FakeMapsApi().apply {
                formattedAddress = address
                failTimes = 1
            }
            val vm = createViewModel(mapsApi = mapsApi)

            vm.onCameraReady()
            runCurrent()

            // The first lookup failed, so nothing was cached as "already located".
            assertEquals(1, mapsApi.reverseGeocodeCalls.size)
            assertNull(vm.uiState.value.fullAddress)

            // Poll 2 and 3 are inside the backoff window...
            mainDispatcherRule.scheduler.advanceTimeBy(20_000)
            runCurrent()
            assertEquals(1, mapsApi.reverseGeocodeCalls.size)
            assertNull(vm.uiState.value.fullAddress)

            // ...and the poll after it retries and succeeds.
            mainDispatcherRule.scheduler.advanceTimeBy(10_000)
            runCurrent()
            assertEquals(2, mapsApi.reverseGeocodeCalls.size)
            assertEquals(address, vm.uiState.value.fullAddress)

            vm.detachEngine()
        }

    @Test
    fun `the reviewed card is not overwritten while a preview is open`() =
        runTest(mainDispatcherRule.scheduler) {
            val mapsApi = FakeMapsApi().apply { formattedAddress = address }
            val location = FakeLocationRepository(coordinate)
            val vm = createViewModel(mapsApi = mapsApi, location = location)

            vm.capturePhoto()
            advanceUntilIdle()
            val reviewed = vm.uiState.value.pendingPreview!!.cardContent

            // The user walks ~200m while reviewing.
            location.result = coordinate.copy(latitude = coordinate.latitude + 0.002)
            mainDispatcherRule.scheduler.advanceTimeBy(10_000)
            runCurrent()

            assertEquals(reviewed, vm.uiState.value.pendingPreview!!.cardContent)
            assertEquals(address, vm.uiState.value.fullAddress)
            vm.detachEngine()
        }
}
