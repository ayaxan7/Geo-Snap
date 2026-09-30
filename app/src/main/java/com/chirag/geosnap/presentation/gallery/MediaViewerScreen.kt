package com.chirag.geosnap.presentation.gallery

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.Image
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.chirag.geosnap.R
import com.chirag.geosnap.domain.model.MediaType
import com.chirag.geosnap.presentation.sharing.shareMediaChooser
import kotlinx.coroutines.launch

/**
 * Full-screen media viewer: swipe left/right through the app gallery. Photos
 * support pinch-zoom and pan; videos play through a single ExoPlayer that only
 * plays the visible page. A share and a delete (app gallery only) action are
 * available from the top bar.
 */
@Composable
fun MediaViewerScreen(
    initialIndex: Int,
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val player = remember(context) { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        player.pause()
    }

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val deleteErrorText = stringResource(R.string.gallery_delete_error)
    LaunchedEffect(uiState.deleteError) {
        if (uiState.deleteError) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(deleteErrorText)
            viewModel.clearDeleteError()
        }
    }

    val media = uiState.media
    when {
        uiState.loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        uiState.loadingError -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.gallery_error),
                        color = Color.White
                    )
                    Button(onClick = viewModel::load) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }

        media.isEmpty() -> {
            LaunchedEffect(Unit) { onBack() }
        }

        else -> {
            val pageCount = media.size
            val pagerState = rememberPagerState(
                initialPage = initialIndex.coerceIn(0, pageCount - 1)
            ) { pageCount }

            // Keep the current page in range after an item is deleted.
            LaunchedEffect(pageCount) {
                if (pagerState.currentPage >= pageCount) {
                    pagerState.scrollToPage((pageCount - 1).coerceAtLeast(0))
                }
            }

            val currentMedia = media.getOrNull(pagerState.currentPage)

            // Only the visible video plays; images stop playback.
            LaunchedEffect(pagerState.currentPage, currentMedia) {
                if (currentMedia != null && currentMedia.type == MediaType.VIDEO) {
                    player.setMediaItem(MediaItem.fromUri(currentMedia.uri))
                    player.prepare()
                    player.playWhenReady = true
                    player.play()
                } else {
                    player.stop()
                    player.clearMediaItems()
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = Color.Black,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val item = media[page]
                        when (item.type) {
                            MediaType.IMAGE -> ZoomablePhoto(uri = item.uri)
                            MediaType.VIDEO -> VideoPage(uri = item.uri, player = player)
                        }
                    }

                    ViewerTopBar(
                        current = pagerState.currentPage + 1,
                        total = pageCount,
                        shareEnabled = currentMedia != null,
                        deleteEnabled = currentMedia != null && !uiState.deleting,
                        onBack = onBack,
                        onShare = {
                            currentMedia?.let {
                                shareMediaChooser(
                                    context,
                                    it.uri,
                                    isVideo = it.type == MediaType.VIDEO
                                )?.let(shareLauncher::launch)
                            }
                        },
                        onDelete = { showDeleteDialog = true },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.systemBars)
                    )
                }
            }

            if (showDeleteDialog) {
                Dialog(onDismissRequest = { showDeleteDialog = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFFE53935), Color(0xFF4B1110))
                                ),
                                shape = RoundedCornerShape(16.dp)
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
                                text = stringResource(R.string.gallery_delete_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.gallery_delete_message),
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
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.gallery_delete_cancel))
                                }
                                Button(
                                    onClick = {
                                        showDeleteDialog = false
                                        viewModel.deleteAt(pagerState.currentPage, onEmpty = onBack)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFFD32F2F)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.gallery_delete_confirm))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerTopBar(
    current: Int,
    total: Int,
    shareEnabled: Boolean,
    deleteEnabled: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.camera_back),
                tint = Color.White
            )
        }
        Text(
            text = stringResource(R.string.gallery_viewer_counter, current, total),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onShare, enabled = shareEnabled) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_share),
                contentDescription = stringResource(R.string.gallery_share),
                tint = Color.White
            )
        }
        IconButton(onClick = onDelete, enabled = deleteEnabled) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_delete),
                contentDescription = stringResource(R.string.gallery_delete_confirm),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun VideoPage(uri: Uri, player: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Full-screen photo with pinch-zoom, pan while zoomed, and double-tap to toggle
 * between 1x and max zoom.
 *
 * Gestures are only consumed while pinching or when already zoomed, so at 1x a
 * horizontal swipe passes through to the pager (swipe to navigate works); once
 * zoomed in, drags pan the photo instead of flipping pages.
 */
@Composable
private fun ZoomablePhoto(uri: Uri) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scope.launch {
                        scale = if (scale > 1f) 1f else MAX_ZOOM
                        offset = Offset.Zero
                    }
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val zoomed = scale > 1f
                        if (event.changes.fastAll { it.pressed } && (zoomed || zoomChange != 1f)) {
                            val newScale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
                            if (newScale > 1f) {
                                scale = newScale
                                offset = clampOffset(offset + panChange, newScale, size)
                            } else {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            event.changes.fastForEach { it.consume() }
                        }
                    } while (event.changes.fastAny { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun clampOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}

private const val MAX_ZOOM = 5f