package com.patashot.myapp.presentation.gallery

import android.net.TestUri
import com.patashot.myapp.domain.model.GeoMedia
import com.patashot.myapp.domain.model.MediaType
import com.patashot.myapp.testutils.FakeMediaRepository
import com.patashot.myapp.testutils.MainDispatcherRule
import java.time.LocalDate
import java.time.ZoneId
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
                media("content://myapp.test/old", timestamp = 100),
                media("content://myapp.test/new", timestamp = 300),
                media("content://myapp.test/mid", timestamp = 200, type = MediaType.VIDEO)
            )
        }
        val vm = GalleryViewModel(mediaRepository = repo)

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertFalse(state.loading)
        assertFalse(state.loadingError)
        assertEquals(
            listOf("content://myapp.test/new", "content://myapp.test/mid", "content://myapp.test/old"),
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
        repo.media = mutableListOf(media("content://myapp.test/one", timestamp = 1))
        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertFalse(state.loadingError)
        assertEquals(1, state.media.size)
    }

    @Test
    fun `groupMediaByDate buckets media newest first`() {
        val now = LocalDate.of(2026, 9, 22)

        fun at(daysAgo: Long): Long =
            now.minusDays(daysAgo)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

        val media = listOf(
            media("content://myapp.test/older", timestamp = at(100)),
            media("content://myapp.test/today", timestamp = at(0)),
            media("content://myapp.test/last-week", timestamp = at(10)),
            media("content://myapp.test/this-month", timestamp = at(20)),
            media("content://myapp.test/this-week", timestamp = at(5)),
            media("content://myapp.test/last-month", timestamp = at(45))
        )

        val sections = groupMediaByDate(media, now = now)

        assertEquals(
            listOf(
                GallerySection.TODAY,
                GallerySection.THIS_WEEK,
                GallerySection.LAST_WEEK,
                GallerySection.THIS_MONTH,
                GallerySection.LAST_MONTH,
                GallerySection.OLDER
            ),
            sections.map { it.section }
        )
        assertEquals(listOf("content://myapp.test/today"), sections[0].items.map { it.displayName })
        assertEquals(listOf("content://myapp.test/this-week"), sections[1].items.map { it.displayName })
        assertEquals(listOf("content://myapp.test/last-week"), sections[2].items.map { it.displayName })
        assertEquals(listOf("content://myapp.test/this-month"), sections[3].items.map { it.displayName })
        assertEquals(listOf("content://myapp.test/last-month"), sections[4].items.map { it.displayName })
        assertEquals(listOf("content://myapp.test/older"), sections[5].items.map { it.displayName })
    }

    @Test
    fun `groupMediaByDate keeps newest item first within a section`() {
        val now = LocalDate.of(2026, 9, 22)
        fun at(hoursAgo: Long): Long = now.atStartOfDay(ZoneId.systemDefault())
            .plusHours(12)
            .minusHours(hoursAgo)
            .toInstant()
            .toEpochMilli()

        val media = listOf(
            media("content://myapp.test/older-today", timestamp = at(5)),
            media("content://myapp.test/newer-today", timestamp = at(1))
        )

        val sections = groupMediaByDate(media, now = now)
        assertEquals(1, sections.size)
        assertEquals(GallerySection.TODAY, sections[0].section)
        assertEquals(
            listOf("content://myapp.test/newer-today", "content://myapp.test/older-today"),
            sections[0].items.map { it.displayName }
        )
    }

    @Test
    fun `enterSelection and toggling tracks selected uris`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(
                media("content://myapp.test/a", timestamp = 100),
                media("content://myapp.test/b", timestamp = 200)
            )
        }
        val vm = GalleryViewModel(mediaRepository = repo)
        advanceUntilIdle()

        vm.enterSelection()
        assertTrue(vm.uiState.first().selectionMode)

        vm.toggleSelection("content://myapp.test/a")
        vm.toggleSelection("content://myapp.test/b")
        assertEquals(
            setOf("content://myapp.test/a", "content://myapp.test/b"),
            vm.uiState.first().selection
        )

        vm.toggleSelection("content://myapp.test/a")
        assertEquals(setOf("content://myapp.test/b"), vm.uiState.first().selection)

        vm.exitSelection()
        assertFalse(vm.uiState.first().selectionMode)
        assertTrue(vm.uiState.first().selection.isEmpty())
    }

    @Test
    fun `enterSelection clears any previous selection`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(
                media("content://myapp.test/a", timestamp = 1),
                media("content://myapp.test/b", timestamp = 2)
            )
        }
        val vm = GalleryViewModel(mediaRepository = repo)
        advanceUntilIdle()

        vm.startSelectionAt("content://myapp.test/a")
        vm.toggleSelection("content://myapp.test/b")
        assertEquals(2, vm.uiState.first().selection.size)

        vm.exitSelection()
        vm.enterSelection()

        assertTrue(vm.uiState.first().selectionMode)
        assertTrue(vm.uiState.first().selection.isEmpty())
    }

    @Test
    fun `toggleSelection is ignored outside selection mode`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(media("content://myapp.test/a", timestamp = 1))
        }
        val vm = GalleryViewModel(mediaRepository = repo)
        advanceUntilIdle()

        vm.toggleSelection("content://myapp.test/a")

        assertTrue(vm.uiState.first().selection.isEmpty())
    }

    @Test
    fun `startSelectionAt enters selection mode with the given uri selected`() =
        runTest(mainDispatcherRule.scheduler) {
            val repo = FakeMediaRepository().apply {
                media = mutableListOf(
                    media("content://myapp.test/a", timestamp = 1),
                    media("content://myapp.test/b", timestamp = 2)
                )
            }
            val vm = GalleryViewModel(mediaRepository = repo)
            advanceUntilIdle()

            vm.startSelectionAt("content://myapp.test/b")

            val state = vm.uiState.first()
            assertTrue(state.selectionMode)
            assertEquals(setOf("content://myapp.test/b"), state.selection)
        }

    @Test
    fun `deleteSelection softly deletes selected items and refreshes`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(
                media("content://myapp.test/a", timestamp = 100),
                media("content://myapp.test/b", timestamp = 200),
                media("content://myapp.test/c", timestamp = 300)
            )
        }
        val vm = GalleryViewModel(mediaRepository = repo)
        advanceUntilIdle()

        vm.enterSelection()
        vm.toggleSelection("content://myapp.test/a")
        vm.toggleSelection("content://myapp.test/b")
        vm.deleteSelection()
        advanceUntilIdle()

        assertEquals(
            listOf("content://myapp.test/a", "content://myapp.test/b"),
            repo.deletedUris
        )
        assertEquals(
            listOf("content://myapp.test/c"),
            vm.uiState.first().media.map { it.uri.toString() }
        )
        assertFalse(vm.uiState.first().selectionMode)
        assertTrue(vm.uiState.first().selection.isEmpty())
    }

    @Test
    fun `deleteSelection surfaces error when every deletion fails`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(media("content://myapp.test/a", timestamp = 1))
            failDelete = true
        }
        val vm = GalleryViewModel(mediaRepository = repo)
        advanceUntilIdle()

        vm.enterSelection()
        vm.toggleSelection("content://myapp.test/a")
        vm.deleteSelection()
        advanceUntilIdle()

        assertTrue(vm.uiState.first().deleteSelectionFailed)
        assertTrue(vm.uiState.first().selectionMode)
        assertEquals(1, vm.uiState.first().media.size)
        assertTrue(repo.deletedUris.isEmpty())
    }

    @Test
    fun `deleteSelection is a no-op without a selection`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeMediaRepository().apply {
            media = mutableListOf(media("content://myapp.test/a", timestamp = 1))
        }
        val vm = GalleryViewModel(mediaRepository = repo)
        advanceUntilIdle()

        vm.deleteSelection()
        advanceUntilIdle()

        assertTrue(repo.deletedUris.isEmpty())
        assertFalse(vm.uiState.first().deleteSelectionFailed)
    }
}