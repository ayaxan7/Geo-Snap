package com.chirag.geosnap.presentation.gallery

import android.net.TestUri
import androidx.lifecycle.SavedStateHandle
import com.chirag.geosnap.domain.model.GeoMedia
import com.chirag.geosnap.domain.model.MediaType
import com.chirag.geosnap.presentation.navigation.Destination
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
class ViewerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun media(uri: String, timestamp: Long): GeoMedia =
        GeoMedia(uri = TestUri(uri), type = MediaType.IMAGE, timestamp = timestamp)

    private fun createViewModel(
        repo: FakeMediaRepository,
        index: Int = 0
    ): ViewerViewModel = ViewerViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(Destination.GalleryViewer.ARGUMENT_INDEX to index)
        ),
        mediaRepository = repo
    )

    @Test
    fun `initial index comes from saved state`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(media("content://geosnap.test/a", 1), media("content://geosnap.test/b", 2))
        }
        val vm = createViewModel(repo, index = 1)
        assertEquals(1, vm.initialIndex)
    }

    @Test
    fun `load populates media list`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(media("content://geosnap.test/a", 1))
        }
        val vm = createViewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertFalse(state.loading)
        assertEquals(1, state.media.size)
    }

    @Test
    fun `delete removes the item softly and reports it`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(
                media("content://geosnap.test/a", 1),
                media("content://geosnap.test/b", 2)
            )
        }
        val vm = createViewModel(repo)
        advanceUntilIdle()

        var popped = false
        vm.deleteAt(index = 0, onEmpty = { popped = true })
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertEquals(listOf("content://geosnap.test/a"), state.media.map { it.uri.toString() })
        assertEquals(listOf("content://geosnap.test/b"), repo.deletedUris)
        assertFalse(popped)
        assertFalse(state.deleteError)
    }

    @Test
    fun `deleting the last item invokes onEmpty`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(media("content://geosnap.test/only", 1))
        }
        val vm = createViewModel(repo)
        advanceUntilIdle()

        var popped = false
        vm.deleteAt(index = 0, onEmpty = { popped = true })
        advanceUntilIdle()

        assertTrue(popped)
        assertTrue(vm.uiState.first().media.isEmpty())
    }

    @Test
    fun `failed delete surfaces deleteError without changing list`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(media("content://geosnap.test/a", 1))
            failDelete = true
        }
        val vm = createViewModel(repo)
        advanceUntilIdle()

        vm.deleteAt(index = 0, onEmpty = {})
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertTrue(state.deleteError)
        assertEquals(1, state.media.size)
        assertTrue(repo.deletedUris.isEmpty())
    }
}