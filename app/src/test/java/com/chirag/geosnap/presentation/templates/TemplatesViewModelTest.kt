package com.chirag.geosnap.presentation.templates

import com.chirag.geosnap.domain.usecase.GetSelectedCardTemplateUseCase
import com.chirag.geosnap.domain.usecase.SetSelectedCardTemplateUseCase
import com.chirag.geosnap.presentation.camera.CardTemplates
import com.chirag.geosnap.testutils.FakePreferencesRepository
import com.chirag.geosnap.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplatesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        preferences: FakePreferencesRepository = FakePreferencesRepository()
    ) = TemplatesViewModel(
        getSelectedCardTemplate = GetSelectedCardTemplateUseCase(preferences),
        setSelectedCardTemplate = SetSelectedCardTemplateUseCase(preferences)
    )

    @Test
    fun `lists every registered template`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = createViewModel()

        assertEquals(CardTemplates.ALL, viewModel.uiState.value.templates)
    }

    @Test
    fun `defaults to the default template when nothing is persisted`() =
        runTest(mainDispatcherRule.scheduler) {
            val viewModel = createViewModel()

            assertEquals(CardTemplates.DEFAULT_ID, viewModel.uiState.value.selectedTemplateId)
            assertEquals(CardTemplates.DEFAULT, viewModel.uiState.value.selectedTemplate)
        }

    @Test
    fun `reflects an already persisted selection`() = runTest(mainDispatcherRule.scheduler) {
        val preferences = FakePreferencesRepository(
            initialCardTemplateId = CardTemplates.DEFAULT_ID
        )

        val viewModel = createViewModel(preferences)

        assertEquals(CardTemplates.DEFAULT_ID, viewModel.uiState.value.selectedTemplateId)
    }

    @Test
    fun `seeds the pending draft from the persisted selection`() =
        runTest(mainDispatcherRule.scheduler) {
            val preferences = FakePreferencesRepository(
                initialCardTemplateId = "minimal"
            )

            val viewModel = createViewModel(preferences)
            advanceUntilIdle()

            assertEquals("minimal", viewModel.uiState.value.pendingTemplateId)
            assertEquals(false, viewModel.uiState.value.hasPendingEdit)
        }

    @Test
    fun `selecting a template does not persist it`() =
        runTest(mainDispatcherRule.scheduler) {
            val preferences = FakePreferencesRepository()
            val viewModel = createViewModel(preferences)
            val target = CardTemplates.ALL.last()

            viewModel.selectTemplate(target)
            advanceUntilIdle()

            assertEquals(null, preferences.cardTemplateValue)
        }

    @Test
    fun `selecting a template updates the pending id immediately`() =
        runTest(mainDispatcherRule.scheduler) {
            val viewModel = createViewModel()
            val target = CardTemplates.ALL.last()

            viewModel.selectTemplate(target)

            assertEquals(target.id, viewModel.uiState.value.pendingTemplateId)
        }

    @Test
    fun `selecting a template leaves the saved id untouched until save`() =
        runTest(mainDispatcherRule.scheduler) {
            val viewModel = createViewModel()
            val target = CardTemplates.ALL.last()

            viewModel.selectTemplate(target)
            advanceUntilIdle()

            assertEquals(CardTemplates.DEFAULT_ID, viewModel.uiState.value.selectedTemplateId)
        }

    @Test
    fun `save persists the pending template`() =
        runTest(mainDispatcherRule.scheduler) {
            val preferences = FakePreferencesRepository()
            val viewModel = createViewModel(preferences)
            val target = CardTemplates.ALL.last()

            viewModel.selectTemplate(target)
            viewModel.saveSelection()
            advanceUntilIdle()

            assertEquals(target.id, preferences.cardTemplateValue)
            assertEquals(target.id, viewModel.uiState.value.selectedTemplateId)
        }

    @Test
    fun `saving without a change does not write`() =
        runTest(mainDispatcherRule.scheduler) {
            val preferences = FakePreferencesRepository()
            val viewModel = createViewModel(preferences)

            viewModel.saveSelection()
            advanceUntilIdle()

            assertEquals(null, preferences.cardTemplateValue)
        }

    @Test
    fun `tapping back to the saved template clears the pending edit`() =
        runTest(mainDispatcherRule.scheduler) {
            val viewModel = createViewModel()

            viewModel.selectTemplate(CardTemplates.ALL.last())
            viewModel.selectTemplate(CardTemplates.DEFAULT)

            assertEquals(false, viewModel.uiState.value.hasPendingEdit)
        }

    @Test
    fun `ui state resolves an unknown persisted id to a usable template`() =
        runTest(mainDispatcherRule.scheduler) {
            val preferences = FakePreferencesRepository(
                initialCardTemplateId = "retired-template"
            )

            val viewModel = createViewModel(preferences)

            assertEquals(CardTemplates.DEFAULT, viewModel.uiState.value.selectedTemplate)
        }
}
