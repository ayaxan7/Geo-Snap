package com.chirag.geosnap.presentation.camera

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.rememberAsyncImagePainter
import com.chirag.geosnap.R
import com.chirag.geosnap.ui.theme.GeoSnapTheme
import com.chirag.geosnap.ui.theme.PrimaryGreen

/**
 * Review screen for a freshly captured photo or video.
 *
 * Laid out as three stacked regions rather than a full-bleed image with floating
 * controls: a top bar, the media itself, and a solid black bottom bar. The media
 * is drawn only inside its own region, so it can never run underneath the bars
 * and the surrounding black is structural (letterbox) rather than a scrim.
 *
 * [mediaOverlay] is drawn on top of the media region — the caller uses it for the
 * location card, which floats above the bottom bar.
 */
@Composable
fun PreviewScreen(
    uri: Uri,
    isVideo: Boolean,
    onDelete: () -> Unit,
    onRetake: () -> Unit,
    onEdit: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mediaOverlay: @Composable BoxScope.() -> Unit = {}
) {
    var isMuted by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        PreviewTopBar(
            isVideo = isVideo,
            isMuted = isMuted,
            onToggleMute = { isMuted = !isMuted },
            onShare = onShare,
            onBack = onBack
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            if (isVideo) {
                PreviewVideo(uri = uri, isMuted = isMuted)
            } else {
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = stringResource(R.string.preview_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            mediaOverlay()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    horizontal = BOTTOM_BAR_HORIZONTAL_PADDING,
                    vertical = BOTTOM_BAR_VERTICAL_PADDING
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                PreviewAction(
                    iconRes = R.drawable.ic_delete,
                    labelRes = R.string.preview_delete,
                    onClick = onDelete
                )
                PreviewAction(
                    iconRes = R.drawable.ic_retake,
                    labelRes = R.string.preview_retake,
                    onClick = onRetake
                )
                PreviewAction(
                    iconRes = R.drawable.ic_edit,
                    labelRes = R.string.preview_edit,
                    onClick = onEdit
                )
            }

            Spacer(modifier = Modifier.height(BOTTOM_BAR_SECTION_GAP))

            Button(
                onClick = onSaveToGallery,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SAVE_BUTTON_HEIGHT),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = PrimaryGreen
                )
            ) {
                Text(
                    text = stringResource(R.string.preview_save_to_gallery),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * Back arrow, audio toggle and share. Sits on the solid black column background
 * rather than over the media, so it needs no scrim of its own.
 */
@Composable
private fun PreviewTopBar(
    isVideo: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(TOP_BAR_HEIGHT)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.preview_back),
                tint = Color.White
            )
        }
        if (isVideo) {
            IconButton(onClick = onToggleMute) {
                Icon(
                    imageVector = if (isMuted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = stringResource(
                        if (isMuted) R.string.preview_unmute else R.string.preview_mute
                    ),
                    tint = Color.White
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.preview_share),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun PreviewVideo(uri: Uri, isMuted: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(exoPlayer, isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // PlayerView already defaults to RESIZE_MODE_FIT, so the video letterboxes
    // like the photo branch without touching its unstable resize-mode API.
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** Icon-over-label tile for the delete / retake / edit row. */
@Composable
private fun PreviewAction(
    iconRes: Int,
    labelRes: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(labelRes),
            tint = Color.White,
            modifier = Modifier.size(ACTION_ICON_SIZE)
        )
        Text(
            text = stringResource(labelRes),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private val TOP_BAR_HEIGHT = 56.dp
private val ACTION_ICON_SIZE = 24.dp
private val BOTTOM_BAR_HORIZONTAL_PADDING = 16.dp
private val BOTTOM_BAR_VERTICAL_PADDING = 12.dp
private val BOTTOM_BAR_SECTION_GAP = 20.dp
private val SAVE_BUTTON_HEIGHT = 56.dp

@Preview
@Composable
private fun PreviewScreenPreview() {
    GeoSnapTheme {
        PreviewScreen(
            uri = Uri.EMPTY,
            isVideo = false,
            onDelete = {},
            onRetake = {},
            onEdit = {},
            onSaveToGallery = {},
            onShare = {},
            onBack = {}
        )
    }
}
