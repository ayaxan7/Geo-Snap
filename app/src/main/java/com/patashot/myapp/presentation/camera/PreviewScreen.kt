package com.patashot.myapp.presentation.camera

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.rememberAsyncImagePainter
import com.patashot.myapp.R
import com.patashot.myapp.data.media.FilterRgbMatrix
import com.patashot.myapp.domain.model.NormalizedCropRect
import com.patashot.myapp.ui.theme.GeoSnapTheme
import com.patashot.myapp.ui.theme.PrimaryGreen

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
    isProcessing: Boolean = false,
    rotationDegrees: Int = 0,
    cropRect: NormalizedCropRect? = null,
    onRotateLeft: () -> Unit = {},
    onRotateRight: () -> Unit = {},
    onCropRectChange: (NormalizedCropRect?) -> Unit = {},
    filter: CaptureFilter = CaptureFilter.ORIGINAL,
    filterStrength: Float = 0f,
    onFilterChange: (CaptureFilter) -> Unit = {},
    onFilterStrengthChange: (Float) -> Unit = {},
    mediaOverlay: @Composable BoxScope.() -> Unit = {}
) {
    var isMuted by rememberSaveable { mutableStateOf(true) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var isCropping by rememberSaveable { mutableStateOf(false) }
    var tempCropRect by remember(cropRect, isCropping) {
        mutableStateOf(cropRect ?: NormalizedCropRect())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        PreviewTopBar(
            isVideo = isVideo,
            isMuted = isMuted,
            isEditing = isEditing,
            isCropping = isCropping,
            isProcessing = isProcessing,
            onToggleMute = { isMuted = !isMuted },
            onRotateLeft = onRotateLeft,
            onCrop = { isCropping = true },
            onRotateRight = onRotateRight,
            onShare = onShare,
            onSaveEdit = { isEditing = false },
            onResetCrop = { tempCropRect = NormalizedCropRect() },
            onSaveCrop = {
                onCropRectChange(if (tempCropRect.isDefault) null else tempCropRect)
                isCropping = false
            },
            onBack = {
                when {
                    isCropping -> {
                        tempCropRect = cropRect ?: NormalizedCropRect()
                        isCropping = false
                    }
                    isEditing -> isEditing = false
                    else -> onBack()
                }
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .clipToBounds()
        ) {
            // Crop wrapper box: zooms & offsets to the selected crop region when not actively cropping
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val activeCrop = if (isCropping) null else cropRect
                        if (activeCrop != null && !activeCrop.isDefault) {
                            val cw = (activeCrop.right - activeCrop.left).coerceAtLeast(0.01f)
                            val ch = (activeCrop.bottom - activeCrop.top).coerceAtLeast(0.01f)
                            val cropScale = 1f / maxOf(cw, ch)
                            val cropCenterX = (activeCrop.left + activeCrop.right) / 2f
                            val cropCenterY = (activeCrop.top + activeCrop.bottom) / 2f
                            scaleX = cropScale
                            scaleY = cropScale
                            translationX = (0.5f - cropCenterX) * size.width * cropScale
                            translationY = (0.5f - cropCenterY) * size.height * cropScale
                        }
                    }
            ) {
                // Rotation wrapper box: rotates media and scales to fit letterbox
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val normRot = ((rotationDegrees % 360) + 360) % 360
                            rotationZ = normRot.toFloat()
                            if (normRot == 90 || normRot == 270) {
                                val w = size.width
                                val h = size.height
                                if (w > 0 && h > 0) {
                                    val fitScale = minOf(w / h, h / w)
                                    scaleX = fitScale
                                    scaleY = fitScale
                                }
                            }
                        }
                ) {
                    if (isVideo) {
                        PreviewVideo(
                            uri = uri,
                            isMuted = isMuted,
                            filter = filter,
                            filterStrength = filterStrength
                        )
                    } else {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = stringResource(R.string.preview_image),
                            contentScale = ContentScale.Fit,
                            colorFilter = filter.composeColorFilter(filterStrength),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Location card overlay stays here untouched at bottom center (hidden during crop mode)
            if (!isCropping) {
                mediaOverlay()
            }

            // Interactive crop overlay when in crop mode
            if (isCropping) {
                FreeformCropOverlay(
                    cropRect = tempCropRect,
                    onCropRectChange = { tempCropRect = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
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
            when {
                isCropping -> {
                    CropBottomBar(
                        onReset = { tempCropRect = NormalizedCropRect() },
                        onCancel = {
                            tempCropRect = cropRect ?: NormalizedCropRect()
                            isCropping = false
                        },
                        onDone = {
                            onCropRectChange(if (tempCropRect.isDefault) null else tempCropRect)
                            isCropping = false
                        }
                    )
                }
                isEditing -> {
                    EditBottomBar(
                        uri = uri,
                        filter = filter,
                        filterStrength = filterStrength,
                        onFilterChange = onFilterChange,
                        onFilterStrengthChange = onFilterStrengthChange
                    )
                }
                else -> {
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
                            onClick = {
                                onEdit()
                                isEditing = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(BOTTOM_BAR_SECTION_GAP))

                    Button(
                        onClick = onSaveToGallery,
                        enabled = !isProcessing,
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
    }
}

/**
 * Top bar with back arrow, optional video mute, rotate left, crop, rotate right, and share buttons.
 * Ordering: [Rotate Left] [Crop] [Rotate Right] [Share]
 */
@Composable
private fun PreviewTopBar(
    isVideo: Boolean,
    isMuted: Boolean,
    isEditing: Boolean,
    isCropping: Boolean,
    isProcessing: Boolean,
    onToggleMute: () -> Unit,
    onRotateLeft: () -> Unit,
    onCrop: () -> Unit,
    onRotateRight: () -> Unit,
    onShare: () -> Unit,
    onSaveEdit: () -> Unit,
    onResetCrop: () -> Unit,
    onSaveCrop: () -> Unit,
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
                contentDescription = stringResource(
                    if (isCropping) R.string.preview_crop_cancel else R.string.preview_back
                ),
                tint = Color.White
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isVideo && !isEditing && !isCropping) {
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

            if (isCropping) {
                Button(
                    onClick = onResetCrop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E2E2E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(stringResource(R.string.preview_crop_reset), fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.size(4.dp))
                Button(
                    onClick = onSaveCrop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = PrimaryGreen
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(stringResource(R.string.preview_crop_done), fontWeight = FontWeight.Bold)
                }
            } else if (isEditing) {
                Button(
                    onClick = onSaveEdit,
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
            } else {
                IconButton(onClick = onRotateLeft, enabled = !isProcessing) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rotate_left),
                        contentDescription = stringResource(R.string.preview_rotate_left),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onCrop, enabled = !isProcessing) {
                    Icon(
                        painter = painterResource(R.drawable.ic_crop),
                        contentDescription = stringResource(R.string.preview_crop),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onRotateRight, enabled = !isProcessing) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rotate_right),
                        contentDescription = stringResource(R.string.preview_rotate_right),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onShare, enabled = !isProcessing) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = stringResource(R.string.preview_share),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private enum class DragHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    EDGE_LEFT,
    EDGE_RIGHT,
    EDGE_TOP,
    EDGE_BOTTOM,
    CENTER
}

/**
 * Free-form crop overlay with draggable corner handles, edge handles, center move, rule-of-thirds grid, and dimming scrim.
 */
@Composable
private fun FreeformCropOverlay(
    cropRect: NormalizedCropRect,
    onCropRectChange: (NormalizedCropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }
    val currentCropRect by rememberUpdatedState(cropRect)
    val currentOnCropRectChange by rememberUpdatedState(onCropRectChange)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                val cornerRadiusPx = 48.dp.toPx()
                val edgeMarginPx = 28.dp.toPx()
                val minFraction = 0.08f

                var gestureCropRect = currentCropRect

                detectDragGestures(
                    onDragStart = { offset ->
                        gestureCropRect = currentCropRect
                        val leftPx = gestureCropRect.left * size.width
                        val topPx = gestureCropRect.top * size.height
                        val rightPx = gestureCropRect.right * size.width
                        val bottomPx = gestureCropRect.bottom * size.height

                        fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
                            kotlin.math.hypot(x1 - x2, y1 - y2)

                        activeHandle = when {
                            // Corners
                            dist(offset.x, offset.y, leftPx, topPx) <= cornerRadiusPx -> DragHandle.TOP_LEFT
                            dist(offset.x, offset.y, rightPx, topPx) <= cornerRadiusPx -> DragHandle.TOP_RIGHT
                            dist(offset.x, offset.y, leftPx, bottomPx) <= cornerRadiusPx -> DragHandle.BOTTOM_LEFT
                            dist(offset.x, offset.y, rightPx, bottomPx) <= cornerRadiusPx -> DragHandle.BOTTOM_RIGHT

                            // Edges
                            kotlin.math.abs(offset.x - leftPx) <= edgeMarginPx &&
                                offset.y in (topPx - edgeMarginPx)..(bottomPx + edgeMarginPx) -> DragHandle.EDGE_LEFT

                            kotlin.math.abs(offset.x - rightPx) <= edgeMarginPx &&
                                offset.y in (topPx - edgeMarginPx)..(bottomPx + edgeMarginPx) -> DragHandle.EDGE_RIGHT

                            kotlin.math.abs(offset.y - topPx) <= edgeMarginPx &&
                                offset.x in (leftPx - edgeMarginPx)..(rightPx + edgeMarginPx) -> DragHandle.EDGE_TOP

                            kotlin.math.abs(offset.y - bottomPx) <= edgeMarginPx &&
                                offset.x in (leftPx - edgeMarginPx)..(rightPx + edgeMarginPx) -> DragHandle.EDGE_BOTTOM

                            // Center pan
                            offset.x in leftPx..rightPx && offset.y in topPx..bottomPx -> DragHandle.CENTER

                            else -> DragHandle.NONE
                        }
                    },
                    onDragEnd = {
                        activeHandle = DragHandle.NONE
                    },
                    onDragCancel = {
                        activeHandle = DragHandle.NONE
                    },
                    onDrag = { change, dragAmount ->
                        if (activeHandle == DragHandle.NONE) return@detectDragGestures
                        change.consume()

                        val dx = dragAmount.x / size.width.toFloat()
                        val dy = dragAmount.y / size.height.toFloat()

                        gestureCropRect = when (activeHandle) {
                            DragHandle.TOP_LEFT -> {
                                val nl = (gestureCropRect.left + dx).coerceIn(0f, gestureCropRect.right - minFraction)
                                val nt = (gestureCropRect.top + dy).coerceIn(0f, gestureCropRect.bottom - minFraction)
                                gestureCropRect.copy(left = nl, top = nt)
                            }
                            DragHandle.TOP_RIGHT -> {
                                val nr = (gestureCropRect.right + dx).coerceIn(gestureCropRect.left + minFraction, 1f)
                                val nt = (gestureCropRect.top + dy).coerceIn(0f, gestureCropRect.bottom - minFraction)
                                gestureCropRect.copy(right = nr, top = nt)
                            }
                            DragHandle.BOTTOM_LEFT -> {
                                val nl = (gestureCropRect.left + dx).coerceIn(0f, gestureCropRect.right - minFraction)
                                val nb = (gestureCropRect.bottom + dy).coerceIn(gestureCropRect.top + minFraction, 1f)
                                gestureCropRect.copy(left = nl, bottom = nb)
                            }
                            DragHandle.BOTTOM_RIGHT -> {
                                val nr = (gestureCropRect.right + dx).coerceIn(gestureCropRect.left + minFraction, 1f)
                                val nb = (gestureCropRect.bottom + dy).coerceIn(gestureCropRect.top + minFraction, 1f)
                                gestureCropRect.copy(right = nr, bottom = nb)
                            }
                            DragHandle.EDGE_LEFT -> {
                                val nl = (gestureCropRect.left + dx).coerceIn(0f, gestureCropRect.right - minFraction)
                                gestureCropRect.copy(left = nl)
                            }
                            DragHandle.EDGE_RIGHT -> {
                                val nr = (gestureCropRect.right + dx).coerceIn(gestureCropRect.left + minFraction, 1f)
                                gestureCropRect.copy(right = nr)
                            }
                            DragHandle.EDGE_TOP -> {
                                val nt = (gestureCropRect.top + dy).coerceIn(0f, gestureCropRect.bottom - minFraction)
                                gestureCropRect.copy(top = nt)
                            }
                            DragHandle.EDGE_BOTTOM -> {
                                val nb = (gestureCropRect.bottom + dy).coerceIn(gestureCropRect.top + minFraction, 1f)
                                gestureCropRect.copy(bottom = nb)
                            }
                            DragHandle.CENTER -> {
                                val w = gestureCropRect.right - gestureCropRect.left
                                val h = gestureCropRect.bottom - gestureCropRect.top
                                val nl = (gestureCropRect.left + dx).coerceIn(0f, 1f - w)
                                val nt = (gestureCropRect.top + dy).coerceIn(0f, 1f - h)
                                gestureCropRect.copy(left = nl, top = nt, right = nl + w, bottom = nt + h)
                            }
                            DragHandle.NONE -> gestureCropRect
                        }
                        currentOnCropRectChange(gestureCropRect)
                    }
                )
            }
    ) {
        val leftPx = cropRect.left * size.width
        val topPx = cropRect.top * size.height
        val rightPx = cropRect.right * size.width
        val bottomPx = cropRect.bottom * size.height
        val cropW = rightPx - leftPx
        val cropH = bottomPx - topPx

        val scrimColor = Color(0x99000000)
        // 4 Scrim rectangles
        if (topPx > 0f) {
            drawRect(scrimColor, topLeft = Offset(0f, 0f), size = Size(size.width, topPx))
        }
        if (bottomPx < size.height) {
            drawRect(scrimColor, topLeft = Offset(0f, bottomPx), size = Size(size.width, size.height - bottomPx))
        }
        if (leftPx > 0f) {
            drawRect(scrimColor, topLeft = Offset(0f, topPx), size = Size(leftPx, cropH))
        }
        if (rightPx < size.width) {
            drawRect(scrimColor, topLeft = Offset(rightPx, topPx), size = Size(size.width - rightPx, cropH))
        }

        // Crop border
        drawRect(
            color = Color.White,
            topLeft = Offset(leftPx, topPx),
            size = Size(cropW, cropH),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Rule-of-thirds grid lines
        val gridColor = Color.White.copy(alpha = 0.35f)
        val gridStroke = 1.dp.toPx()
        drawLine(gridColor, Offset(leftPx + cropW / 3f, topPx), Offset(leftPx + cropW / 3f, bottomPx), strokeWidth = gridStroke)
        drawLine(gridColor, Offset(leftPx + 2f * cropW / 3f, topPx), Offset(leftPx + 2f * cropW / 3f, bottomPx), strokeWidth = gridStroke)
        drawLine(gridColor, Offset(leftPx, topPx + cropH / 3f), Offset(rightPx, topPx + cropH / 3f), strokeWidth = gridStroke)
        drawLine(gridColor, Offset(leftPx, topPx + 2f * cropH / 3f), Offset(rightPx, topPx + 2f * cropH / 3f), strokeWidth = gridStroke)

        // Corner brackets
        val bracketLen = 20.dp.toPx()
        val bracketStroke = 3.5.dp.toPx()
        // Top-left
        drawLine(Color.White, Offset(leftPx - 1f, topPx), Offset(leftPx + bracketLen, topPx), strokeWidth = bracketStroke)
        drawLine(Color.White, Offset(leftPx, topPx - 1f), Offset(leftPx, topPx + bracketLen), strokeWidth = bracketStroke)
        // Top-right
        drawLine(Color.White, Offset(rightPx + 1f, topPx), Offset(rightPx - bracketLen, topPx), strokeWidth = bracketStroke)
        drawLine(Color.White, Offset(rightPx, topPx - 1f), Offset(rightPx, topPx + bracketLen), strokeWidth = bracketStroke)
        // Bottom-left
        drawLine(Color.White, Offset(leftPx - 1f, bottomPx), Offset(leftPx + bracketLen, bottomPx), strokeWidth = bracketStroke)
        drawLine(Color.White, Offset(leftPx, bottomPx + 1f), Offset(leftPx, bottomPx - bracketLen), strokeWidth = bracketStroke)
        // Bottom-right
        drawLine(Color.White, Offset(rightPx + 1f, bottomPx), Offset(rightPx - bracketLen, bottomPx), strokeWidth = bracketStroke)
        drawLine(Color.White, Offset(rightPx, bottomPx + 1f), Offset(rightPx, bottomPx - bracketLen), strokeWidth = bracketStroke)
    }
}

@Composable
private fun CropBottomBar(
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E2E2E),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Text(stringResource(R.string.preview_crop_cancel))
        }

        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E2E2E),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Text(stringResource(R.string.preview_crop_reset))
        }

        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = PrimaryGreen
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Text(stringResource(R.string.preview_crop_done), fontWeight = FontWeight.Bold)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PreviewVideo(
    uri: Uri,
    isMuted: Boolean,
    filter: CaptureFilter,
    filterStrength: Float
) {
    val context = LocalContext.current
    val rgbMatrix = remember { FilterRgbMatrix() }
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setVideoEffects(listOf(rgbMatrix))
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(rgbMatrix, filter, filterStrength) {
        rgbMatrix.set(filter.recipe(filterStrength))
    }

    LaunchedEffect(exoPlayer, isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBottomBar(
    uri: Uri,
    filter: CaptureFilter,
    filterStrength: Float,
    onFilterChange: (CaptureFilter) -> Unit,
    onFilterStrengthChange: (Float) -> Unit
) {
    val filters = CaptureFilter.entries.toList()
    var sliderValue by remember(filter) { mutableFloatStateOf((filterStrength - 1f) * 100f) }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(filters) { item ->
                FilterItem(
                    name = stringResource(item.labelRes),
                    uri = uri,
                    filter = item,
                    isSelected = item == filter,
                    onClick = { onFilterChange(item) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onFilterStrengthChange(1f + it / 100f)
            },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryGreen
            ),
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryGreen
                    ),
                    thumbSize = DpSize(20.dp, 20.dp)
                )
            },
            track = { sliderState ->
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                ) {
                    val centerX = size.width / 2f
                    val thumbX = (
                            (sliderState.value - sliderState.valueRange.start) /
                                    (sliderState.valueRange.endInclusive -
                                            sliderState.valueRange.start)
                            ) * size.width

                    val strokeWidth = 3.dp.toPx()
                    val y = size.height / 2f

                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )

                    if (thumbX != centerX) {
                        drawLine(
                            color = PrimaryGreen,
                            start = Offset(centerX, y),
                            end = Offset(thumbX, y),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        )
        Text(text = "${sliderValue.toInt()}", color = PrimaryGreen, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FilterItem(
    name: String,
    uri: Uri,
    filter: CaptureFilter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) PrimaryGreen else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                colorFilter = filter.composeColorFilter(1f),
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
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

private fun CaptureFilter.composeColorFilter(strength: Float): ColorFilter? {
    if (this == CaptureFilter.ORIGINAL || strength <= 0f) return null
    return ColorFilter.colorMatrix(ColorMatrix(recipe(strength)))
}

@get:StringRes
private val CaptureFilter.labelRes: Int
    get() = when (this) {
        CaptureFilter.ORIGINAL -> R.string.filter_original
        CaptureFilter.VIBRANT -> R.string.filter_vibrant
        CaptureFilter.SOFT_TONE -> R.string.filter_soft_tone
        CaptureFilter.SHARP_BLACK -> R.string.filter_sharp_black
        CaptureFilter.GRAY -> R.string.filter_gray
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
