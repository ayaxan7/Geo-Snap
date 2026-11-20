package com.patashot.myapp.presentation.gallery

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.patashot.myapp.R
import com.patashot.myapp.domain.model.GeoMedia
import com.patashot.myapp.domain.model.MediaType
import com.patashot.myapp.presentation.core.hasMediaPermission
import com.patashot.myapp.presentation.core.mediaPermissionsForSdk
import com.patashot.myapp.presentation.sharing.shareMediaChooser
import kotlinx.coroutines.flow.Flow
import com.patashot.myapp.ui.theme.GeoSnapTheme
import com.patashot.myapp.ui.theme.PrimaryGreen
import androidx.core.net.toUri

private const val LONG_HOLD_MILLIS = 500L

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onItemClick: (Int) -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    GalleryContent(
        uiState = uiState,
        events = viewModel.events,
        onBack = onBack,
        onItemClick = onItemClick,
        onRefresh = viewModel::refresh,
        onEnterSelection = viewModel::enterSelection,
        onStartSelectionAt = viewModel::startSelectionAt,
        onExitSelection = viewModel::exitSelection,
        onToggleSelection = viewModel::toggleSelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onSaveSelection = viewModel::saveSelection,
        onDeleteSelection = viewModel::deleteSelection,
        onClearDeleteSelectionFailed = viewModel::clearDeleteSelectionFailed
    )
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun GalleryContent(
    uiState: GalleryViewModel.UiState,
    events: Flow<GalleryViewModel.GalleryEvent>? = null,
    onBack: () -> Unit,
    onItemClick: (Int) -> Unit = {},
    onRefresh: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
    onStartSelectionAt: (String) -> Unit = {},
    onExitSelection: () -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onSaveSelection: () -> Unit = {},
    onDeleteSelection: () -> Unit = {},
    onClearDeleteSelectionFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val mediaPermissions = remember { mediaPermissionsForSdk() }
    val savePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (context.hasMediaPermission() || results.values.any { it }) {
            onSaveSelection()
        }
    }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val selectedMedia = uiState.media.filter { it.uri.toString() in uiState.selection }
    val canActOnSelection = selectedMedia.isNotEmpty() && !uiState.deletingSelection

    fun shareSelected() {
        if (selectedMedia.isEmpty()) return
        val mime = when {
            selectedMedia.none { it.type == MediaType.VIDEO } -> "image/jpeg"
            selectedMedia.none { it.type == MediaType.IMAGE } -> "video/mp4"
            else -> "*/*"
        }
        shareMediaChooser(context, selectedMedia.map { it.uri }, mime)?.let(shareLauncher::launch)
        onExitSelection()
    }

    val deleteErrorText = stringResource(R.string.gallery_delete_error)
    val singleSavedText = stringResource(R.string.gallery_image_saved_success)
    val saveErrorText = stringResource(R.string.gallery_save_error)

    LaunchedEffect(uiState.deleteSelectionFailed) {
        if (uiState.deleteSelectionFailed) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(deleteErrorText)
            onClearDeleteSelectionFailed()
        }
    }

    LaunchedEffect(events) {
        events?.collect { event ->
            when (event) {
                is GalleryViewModel.GalleryEvent.SavedToGallery -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val msg = if (event.count == 1) {
                        singleSavedText
                    } else {
                        context.getString(R.string.gallery_items_saved_success, event.count)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is GalleryViewModel.GalleryEvent.SaveToGalleryFailed -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(saveErrorText)
                }
            }
        }
    }

    Scaffold(containerColor = Color.White, topBar = {
        GalleryTopBar(
            onBack = onBack,
            selectionMode = uiState.selectionMode,
            selectedCount = selectedMedia.size,
            totalCount = uiState.media.size,
            savingSelection = uiState.savingSelection,
            onCancelSelection = onExitSelection,
            onEnterSelection = onEnterSelection,
            onToggleSelectAll = onToggleSelectAll,
            onSaveSelection = {
                if (context.hasMediaPermission()) {
                    onSaveSelection()
                } else {
                    savePermissionLauncher.launch(mediaPermissions.toTypedArray())
                }
            }
        )
    }, bottomBar = {
        // Hidden entirely until something is selected: a greyed-out pair of buttons
        // invites a tap that does nothing, whereas nothing there is unambiguous.
        if (uiState.selectionMode && selectedMedia.isNotEmpty()) {
            GalleryBottomBar(
                canActOnSelection = canActOnSelection,
                onDeleteClick = { showDeleteDialog = true },
                onShareClick = { shareSelected() }
            )
        }
    }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.loading && uiState.media.isEmpty() -> LoadingContent()
                uiState.loadingError -> ErrorContent(onRetry = onRefresh)
                uiState.media.isEmpty() -> EmptyContent()
                else -> GalleryGrid(
                    sections = uiState.sections,
                    flatIndexByUri = uiState.media.mapIndexed { index, media -> media.uri.toString() to index }
                        .toMap(),
                    selectionMode = uiState.selectionMode,
                    selectedUris = uiState.selection,
                    onItemClick = onItemClick,
                    onToggleSelection = onToggleSelection,
                    onStartSelection = onStartSelectionAt)
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }

    if (showDeleteDialog) {
        val count = selectedMedia.size
        val hasPhoto = selectedMedia.any { it.type == MediaType.IMAGE }
        val hasVideo = selectedMedia.any { it.type == MediaType.VIDEO }
        val isMixed = hasPhoto && hasVideo
        val deleteTitleRes = when {
            isMixed -> R.string.gallery_delete_title_media
            hasVideo -> R.string.gallery_delete_title_video
            else -> R.string.gallery_delete_title_photo
        }
        val deleteMessageRes = when {
            isMixed -> R.string.gallery_delete_message_media
            hasVideo -> if (count == 1) R.string.gallery_delete_message_video
            else R.string.gallery_delete_message_videos
            else -> if (count == 1) R.string.gallery_delete_message_photo
            else R.string.gallery_delete_message_photos
        }
        Dialog(onDismissRequest = { showDeleteDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFE53935), Color(0xFF4B1110))
                        ), shape = RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_delete_photo),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(deleteTitleRes),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(deleteMessageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color.White),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(stringResource(R.string.gallery_delete_cancel))
                        }
                        Button(
                            onClick = {
                                showDeleteDialog = false
                                onDeleteSelection()
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White, contentColor = Color(0xFFD32F2F)
                            ), shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.gallery_delete_proceed))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(
    onBack: () -> Unit,
    selectionMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    savingSelection: Boolean,
    onCancelSelection: () -> Unit,
    onEnterSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onSaveSelection: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = if (selectionMode) {
                    stringResource(R.string.gallery_selected_count, selectedCount)
                } else {
                    stringResource(R.string.gallery_title)
                }, style = MaterialTheme.typography.titleLarge
            )
        }, navigationIcon = {
            IconButton(onClick = if (selectionMode) onCancelSelection else onBack) {
                Icon(
                    painter = painterResource(
                        if (selectionMode) android.R.drawable.ic_menu_close_clear_cancel
                        else R.drawable.ic_arrow_back
                    ), contentDescription = if (selectionMode) {
                        stringResource(R.string.gallery_delete_cancel)
                    } else {
                        stringResource(R.string.camera_back)
                    }
                )
            }
        }, actions = {
            if (!selectionMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectActionButton(
                        text = stringResource(R.string.gallery_select),
                        onClick = onEnterSelection
                    )
                    Spacer(Modifier.width(8.dp))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedCount > 0) {
                        IconButton(
                            onClick = onSaveSelection,
                            enabled = !savingSelection
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = stringResource(R.string.gallery_save_to_device),
                                tint = Color.Black
                            )
                        }
                    }
                    val isAllSelected = selectedCount == totalCount && totalCount > 0
                    SelectActionButton(
                        text = stringResource(
                            if (isAllSelected) R.string.gallery_deselect_all else R.string.gallery_select_all
                        ),
                        onClick = onToggleSelectAll
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
    )
}

/**
 * Enter-selection / select-all affordance for the gallery top bar.
 *
 * Matches the Save button on the Templates screen: outlined, 1dp primary-green
 * border, 12dp corners, white ground and bold label, so the two top-bar actions
 * read as the same control across the app.
 */
@Composable
private fun SelectActionButton(
    text: String = stringResource(R.string.gallery_select),
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = PrimaryGreen
        ),
        border = BorderStroke(1.dp, PrimaryGreen),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 0.dp
        ),
        modifier = Modifier.height(36.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            lineHeight = 1.em,
            maxLines = 1
        )
    }
}

@Composable
private fun GalleryGrid(
    sections: List<MediaSection>,
    flatIndexByUri: Map<String, Int>,
    selectionMode: Boolean,
    selectedUris: Set<String>,
    onItemClick: (Int) -> Unit,
    onToggleSelection: (String) -> Unit,
    onStartSelection: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        sections.forEach { section ->
            item(key = "header-${section.section}", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(section.section)
            }
            items(section.items, key = { it.uri.toString() }) { media ->
                val uri = media.uri.toString()
                MediaThumbnail(
                    media = media,
                    selectionMode = selectionMode,
                    selected = uri in selectedUris,
                    onTap = {
                        if (selectionMode) {
                            onToggleSelection(uri)
                        } else {
                            flatIndexByUri[uri]?.let(onItemClick)
                        }
                    },
                    onLongHold = { onStartSelection(uri) })
            }
        }
    }
}

@Composable
private fun SectionHeader(section: GallerySection) {
    Text(
        text = sectionTitle(section),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun sectionTitle(section: GallerySection): String = when (section) {
    GallerySection.TODAY -> stringResource(R.string.gallery_section_today)
    GallerySection.THIS_WEEK -> stringResource(R.string.gallery_section_this_week)
    GallerySection.LAST_WEEK -> stringResource(R.string.gallery_section_last_week)
    GallerySection.THIS_MONTH -> stringResource(R.string.gallery_section_this_month)
    GallerySection.LAST_MONTH -> stringResource(R.string.gallery_section_last_month)
    GallerySection.OLDER -> stringResource(R.string.gallery_section_older)
}

@Composable
private fun MediaThumbnail(
    media: GeoMedia,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongHold: () -> Unit
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongHold by rememberUpdatedState(onLongHold)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val outcome = withTimeoutOrNull(LONG_HOLD_MILLIS.toLong()) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val released = event.changes.any { !it.pressed }
                            val consumed = event.changes.any { it.isConsumed }
                            if (!released && !consumed) continue
                            return@withTimeoutOrNull released && !consumed
                        }
                    }
                    when (outcome) {
                        null -> currentOnLongHold()
                        true -> currentOnTap()
                        false -> Unit
                    }
                }
            }
            .background(Color.Transparent, RoundedCornerShape(8.dp))) {
        AsyncImage(
            model = media.uri,
            contentDescription = media.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder = painterResource(R.drawable.ic_gallery),
            error = painterResource(R.drawable.ic_gallery)
        )
        if (media.type == MediaType.VIDEO) {
            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        if (media.latitude != null && media.longitude != null) {
            Icon(
                painter = painterResource(R.drawable.ic_location),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(20.dp)
            )
        }
        if (selectionMode) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
            Checkbox(
                checked = selected, onCheckedChange = null, colors = CheckboxDefaults.colors(
                    checkedColor = PrimaryGreen,
                    checkmarkColor = Color.White,
                    uncheckedColor = Color.White
                ), modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            )
        }
    }
}


@Composable
private fun GalleryBottomBar(
    canActOnSelection: Boolean, onDeleteClick: () -> Unit, onShareClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    enabled = canActOnSelection, onClick = onDeleteClick
                )
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(
                    R.string.gallery_delete_confirm
                ),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.gallery_delete_confirm),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    enabled = canActOnSelection, onClick = onShareClick
                )
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_share), contentDescription = stringResource(
                    R.string.gallery_share
                ), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.gallery_share),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_gallery),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.gallery_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.gallery_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Preview
@Composable
private fun GalleryScreenPreview() {
    val sampleMedia = listOf(
        GeoMedia(
            uri = "file:///sample1.jpg".toUri(),
            type = MediaType.IMAGE,
            timestamp = System.currentTimeMillis(),
            latitude = 37.7749,
            longitude = -122.4194,
            displayName = "Sample Image 1"
        ), GeoMedia(
            uri = "file:///sample2.mp4".toUri(),
            type = MediaType.VIDEO,
            timestamp = System.currentTimeMillis() - 86400000L * 2,
            displayName = "Sample Video 1"
        )
    )
    val sampleUiState = GalleryViewModel.UiState(
        media = sampleMedia, sections = groupMediaByDate(sampleMedia), loading = false
    )
    GeoSnapTheme {
        GalleryContent(
            uiState = sampleUiState, onBack = {})
    }
}
