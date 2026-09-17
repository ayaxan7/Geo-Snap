package com.chirag.geosnap.presentation.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chirag.geosnap.R
import com.chirag.geosnap.presentation.camera.CameraMode
import com.chirag.geosnap.presentation.camera.CameraUiState

@Composable
fun CameraTopBar(
    mode: CameraMode,
    flashEnabled: Boolean,
    hasFlash: Boolean,
    onBack: () -> Unit,
    onFlash: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.camera_back),
                tint = Color.White
            )
        }
        if (mode == CameraMode.QUICK_SHARE) {
            Text(
                text = stringResource(R.string.camera_quick_share_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (hasFlash) {
            IconButton(
                onClick = onFlash,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flash),
                    contentDescription = stringResource(R.string.camera_flash),
                    tint = if (flashEnabled) Color.Yellow else Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun CameraBottomControls(
    uiState: CameraUiState,
    mode: CameraMode,
    onCapture: () -> Unit,
    onFlip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (mode == CameraMode.VIDEO) {
            Text(
                text = if (uiState.isRecording) {
                    formatElapsed(uiState.recordingElapsedMs)
                } else {
                    stringResource(R.string.camera_capture)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (uiState.isRecording) Color(0xFFFF5252) else Color.White
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onFlip) {
                    Icon(
                        painter = painterResource(R.drawable.ic_flip_camera),
                        contentDescription = stringResource(R.string.camera_flip),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            CaptureButton(
                state = uiState,
                mode = mode,
                onCapture = onCapture
            )
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.size(64.dp))
        }
    }
}

@Composable
private fun CaptureButton(
    state: CameraUiState,
    mode: CameraMode,
    onCapture: () -> Unit
) {
    val isVideo = mode == CameraMode.VIDEO
    val recording = isVideo && state.isRecording
    val shape = if (recording) RoundedCornerShape(12.dp) else CircleShape
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(4.dp, Color.White, shape)
            .background(
                color = if (recording) Color(0xFFFF5252) else Color.Transparent,
                shape = shape
            )
            .clickable(enabled = state.isReady, onClick = onCapture)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!recording && !isVideo) {
            Icon(
                painter = painterResource(R.drawable.ic_camera),
                contentDescription = stringResource(R.string.camera_capture),
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}