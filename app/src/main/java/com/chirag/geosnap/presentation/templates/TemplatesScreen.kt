package com.chirag.geosnap.presentation.templates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chirag.geosnap.R
import com.chirag.geosnap.ui.theme.PrimaryGreen

/**
 * Complete pre-rendered previews, in registry order.
 *
 * [com.chirag.geosnap.presentation.camera.CardTemplates.ALL] lists the classic card first and the minimal card second, so
 * index 0 is the classic design and index 1 the minimal one. Pro has no artwork
 * yet, so it is absent from this list and never reaches [TEMPLATE_PREVIEW_COUNT].
 */
private val TEMPLATE_PREVIEW_IMAGES: List<Int> = listOf(
    R.drawable.ic_template_1,
    R.drawable.ic_template_2
)

/** How many previews exist to show. The picker renders exactly these many rows. */
private const val TEMPLATE_PREVIEW_COUNT = 2

/**
 * Lets the user pick the geotag card design applied to new captures.
 *
 * Each row shows a finished image of the template rather than rebuilding the card,
 * so what the user sees is the delivered artwork itself and the only dynamic part
 * of the row is its selection state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.templates_title),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 48.dp),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.camera_back)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = PrimaryGreen
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(stringResource(R.string.preview_save), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(
                items = uiState.templates.take(TEMPLATE_PREVIEW_COUNT),
                key = { _, template -> template.id }
            ) { index, template ->
                TemplateCard(
                    imageRes = TEMPLATE_PREVIEW_IMAGES[index],
                    selected = template.id == uiState.selectedTemplateId,
                    onClick = { viewModel.selectTemplate(template) }
                )
            }
        }
    }
}

/**
 * One row of the picker: the template's finished artwork, plus the selection
 * affordances.
 *
 * The image is the whole visual, so nothing is layered over it except the check
 * icon. [ContentScale.FillBounds] keeps all of the artwork visible rather than
 * cropping it into the row's aspect ratio.
 */
@Composable
private fun TemplateCard(
    imageRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(3.dp, PrimaryGreen, RoundedCornerShape(12.dp))
                else Modifier
            )
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.template_selected),
                tint = PrimaryGreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(32.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}
