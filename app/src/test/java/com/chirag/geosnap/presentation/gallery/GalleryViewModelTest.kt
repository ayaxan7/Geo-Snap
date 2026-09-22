package com.chirag.geosnap.presentation.gallery

import android.net.TestUri
import com.chirag.geosnap.domain.model.GeoMedia
import com.chirag.geosnap.domain.model.MediaType
import com.chirag.geosnap.testutils.FakeMediaRepository
import com.chirag.geosnap.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun media(uri: String, timestamp: Long, type: MediaType = MediaType.IMAGE): GeoMedia =
        GeoMedia(uri = TestUri(uri), type = type, timestamp = timestamp, displayName = uri)

    @Test
    fun `load shows gallery media newest first`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(
                media("content://geosnap.test/old", timestamp = 100),
                media("content://geosnap.test/new", timestamp = 300),
                media("content://geosnap.test/mid", timestamp = 200, type = MediaType.VIDEO)
            )
        }
        val vm = GalleryViewModel(mediaRepository = repo)

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertFalse(state.loading)
        assertFalse(state.loadingError)
        assertEquals(
            listOf("content://geosnap.test/new", "content://geosnap.test/mid", "content://geosnap.test/old"),
            state.media.map { it.uri.toString() }
        )
    }

    @Test
    fun `load failure surfaces loadingError`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply { failGetAppMedia = true }
        val vm = GalleryViewModel(mediaRepository = repo)

        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.uiState.first().loadingError)
    }

    @Test
    fun `retry after failure loads gallery`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply { failGetAppMedia = true }
        val vm = GalleryViewModel(mediaRepository = repo)

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.first().loadingError)

        repo.failGetAppMedia = false
        repo.media = mutableListOf(media("content://geosnap.test/one", timestamp = 1))
        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertFalse(state.loadingError)
        assertEquals(1, state.media.size)
    }
}