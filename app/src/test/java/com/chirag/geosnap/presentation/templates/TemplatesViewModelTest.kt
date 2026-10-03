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
    fun `selecting a template persists it as the default`() =
        runTest(mainDispatcherRule.scheduler) {
            val preferences = FakePreferencesRepository()
            val viewModel = createViewModel(preferences)
            val target = CardTemplates.ALL.last()

            viewModel.selectTemplate(target)
            advanceUntilIdle()

            assertEquals(target.id, preferences.cardTemplateValue)
        }

    @Test
    fun `selecting a template updates the selected id immediately`() =
        runTest(mainDispatcherRule.scheduler) {
            val viewModel = createViewModel()
            val target = CardTemplates.ALL.last()

            viewModel.selectTemplate(target)

            assertEquals(target.id, viewModel.uiState.value.selectedTemplateId)
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
