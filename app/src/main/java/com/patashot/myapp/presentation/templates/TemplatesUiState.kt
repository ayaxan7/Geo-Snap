package com.patashot.myapp.presentation.templates

import androidx.compose.runtime.Immutable
import com.patashot.myapp.presentation.camera.CardTemplate
import com.patashot.myapp.presentation.camera.CardTemplates

/**
 * State of the geotag card template picker.
 *
 * [templates] is the full registry so the picker renders every available design.
 *
 * Selection is split in two on purpose:
 *  - [pendingTemplateId] is the draft the user is currently looking at. Tapping a
 *    row only moves this, so the highlight follows the finger immediately.
 *  - [selectedTemplateId] is the value actually persisted in DataStore, and only
 *    changes when the user confirms with Save.
 *
 * Leaving the screen without saving therefore leaves the preference untouched.
 */
@Immutable
data class TemplatesUiState(
    val templates: List<CardTemplate> = CardTemplates.ALL,
    val selectedTemplateId: String = CardTemplates.DEFAULT_ID,
    val pendingTemplateId: String = CardTemplates.DEFAULT_ID,
    val hasPendingEdit: Boolean = false
) {
    /** The saved template, resolved so an unknown id still yields a usable card. */
    val selectedTemplate: CardTemplate get() = CardTemplates.fromId(selectedTemplateId)

    /** The template the user has tapped but not yet saved. */
    val pendingTemplate: CardTemplate get() = CardTemplates.fromId(pendingTemplateId)
}
