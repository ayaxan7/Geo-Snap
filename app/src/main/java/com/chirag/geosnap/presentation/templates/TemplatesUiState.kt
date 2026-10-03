package com.chirag.geosnap.presentation.templates

import androidx.compose.runtime.Immutable
import com.chirag.geosnap.presentation.camera.CardTemplate
import com.chirag.geosnap.presentation.camera.CardTemplates

/**
 * State of the geotag card template picker.
 *
 * [templates] is the full registry so the picker renders every available design,
 * while [selectedTemplateId] tracks the persisted default that new captures use.
 */
@Immutable
data class TemplatesUiState(
    val templates: List<CardTemplate> = CardTemplates.ALL,
    val selectedTemplateId: String = CardTemplates.DEFAULT_ID
) {
    /** The currently selected template, resolved so an unknown id still yields a card. */
    val selectedTemplate: CardTemplate get() = CardTemplates.fromId(selectedTemplateId)
}
