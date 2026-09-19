package com.chirag.geosnap.presentation.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.chirag.geosnap.ui.theme.PrimaryGreen

@Composable
fun CameraTopBar(
    mode: CameraMode,
    flashEnabled: Boolean,
    hasFlash: Boolean,
    onBack: () -> Unit,
    onFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Timer icon
        IconButton(onClick = { /* TODO */ }) {
            Icon(
                painter = painterResource(R.drawable.ic_timer),
                contentDescription = "Timer",
                tint = Color.White
            )
        }
        
        // Grid icon
        IconButton(onClick = { /* TODO */ }) {
            Icon(
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = "Grid",
                tint = Color.White
            )
        }
        
        // Flash icon
        if (hasFlash) {
            IconButton(onClick = onFlash) {
                Icon(
                    painter = painterResource(R.drawable.ic_flash),
                    contentDescription = stringResource(R.string.camera_flash),
                    tint = if (flashEnabled) Color.Yellow else Color.White
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
fun CameraBottomControls(
    uiState: CameraUiState,
    mode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    onCapture: () -> Unit,
    onFlip: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeText(
                text = "Photo",
                isSelected = mode == CameraMode.PHOTO,
                onClick = { onModeSelected(CameraMode.PHOTO) }
            )
            ModeText(
                text = "Quick Share",
                isSelected = mode == CameraMode.QUICK_SHARE,
                onClick = { onModeSelected(CameraMode.QUICK_SHARE) }
            )
            ModeText(
                text = "Video",
                isSelected = mode == CameraMode.VIDEO,
                onClick = { onModeSelected(CameraMode.VIDEO) }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Shutter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery
            IconButton(
                onClick = onGallery,
                modifier = Modifier
                    .size(56.dp)
                    .border(1.dp, Color.White, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_gallery),
                    contentDescription = "Gallery",
                    tint = Color.White
                )
            }
            
            // Shutter
            CaptureButton(
                state = uiState,
                mode = mode,
                onCapture = onCapture
            )
            
            // Flip
            IconButton(
                onClick = onFlip,
                modifier = Modifier
                    .size(56.dp)
                    .border(1.dp, Color.White, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flip_camera),
                    contentDescription = stringResource(R.string.camera_flip),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ModeText(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = if (isSelected) PrimaryGreen else Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) PrimaryGreen else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun CaptureButton(
    state: CameraUiState,
    mode: CameraMode,
    onCapture: () -> Unit
) {
    val isVideo = mode == CameraMode.VIDEO
    val color = if (isVideo) Color(0xFFD32F2F) else Color.White
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .border(3.dp, Color.White, CircleShape)
            .padding(6.dp)
            .background(color, CircleShape)
            .clickable(enabled = state.isReady, onClick = onCapture),
        contentAlignment = Alignment.Center
    ) {
        if (isVideo && state.isRecording) {
            // Inner square for stop recording
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
            )
        }
    }
}