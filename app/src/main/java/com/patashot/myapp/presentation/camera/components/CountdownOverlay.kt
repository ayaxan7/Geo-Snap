package com.patashot.myapp.presentation.camera.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun CountdownOverlay(
    seconds: Int?,
    modifier: Modifier = Modifier
) {
    if (seconds == null || seconds < 1) return
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = seconds.toString(),
            fontSize = 120.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}