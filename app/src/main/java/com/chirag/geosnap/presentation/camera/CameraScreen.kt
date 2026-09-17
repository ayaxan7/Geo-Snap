package com.chirag.geosnap.presentation.camera

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chirag.geosnap.R
import com.chirag.geosnap.presentation.camera.components.CameraBottomControls
import com.chirag.geosnap.presentation.camera.components.CameraTopBar

@Composable
fun CameraScreen(
    mode: CameraMode,
    onBack: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val engine = remember(context) { CameraXController(context.applicationContext) }

    val hasCameraPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onBack()
    }

    LaunchedEffect(previewView, lifecycleOwner, mode) {
        if (!hasCameraPermission) {
            viewModel.reportError(CameraError.CAMERA_PERMISSION)
            return@LaunchedEffect
        }
        try {
            engine.bind(previewView, lifecycleOwner, uiState.lensFacing, mode)
            viewModel.attachEngine(engine)
            viewModel.onCameraReady()
        } catch (e: Throwable) {
            viewModel.reportError(CameraError.INIT_FAILED)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.release()
            viewModel.detachEngine()
        }
    }

    val photoSavedMessage = stringResource(R.string.camera_photo_saved)
    val videoSavedMessage = stringResource(R.string.camera_video_saved)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CameraEvent.MediaSaved -> {
                    snackbarHostState.showSnackbar(
                        if (event.isVideo) videoSavedMessage else photoSavedMessage
                    )
                }
                is CameraEvent.QuickShareRequested -> {
                    launchShareChooser(context, event.uri)?.let(shareLauncher::launch)
                }
            }
        }
    }

    val currentError = uiState.error
    LaunchedEffect(currentError) {
        if (currentError != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = context.getString(currentError.stringRes())
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                CameraTopBar(
                    mode = mode,
                    flashEnabled = uiState.flashEnabled,
                    hasFlash = uiState.hasFlash,
                    onBack = onBack,
                    onFlash = viewModel::toggleFlash
                )
                Spacer(Modifier.weight(1f))
                val bannerError = currentError
                if (!uiState.isReady && bannerError != null) {
                    CameraUnavailableBanner(text = stringResource(bannerError.stringRes()))
                    Spacer(Modifier.height(16.dp))
                }
                CameraBottomControls(
                    uiState = uiState,
                    mode = mode,
                    onCapture = {
                        when (mode) {
                            CameraMode.PHOTO, CameraMode.QUICK_SHARE -> viewModel.capturePhoto()
                            CameraMode.VIDEO -> if (uiState.isRecording) {
                                viewModel.stopRecording()
                            } else {
                                viewModel.startRecording()
                            }
                        }
                    },
                    onFlip = viewModel::flipLensFacing
                )
            }
        }
    }
}

@Composable
private fun CameraUnavailableBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 420.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun launchShareChooser(
    context: Context,
    uri: String
): Intent? {
    val nestedUri = Uri.parse(uri) ?: return null
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, nestedUri)
        clipData = ClipData.newUri(context.contentResolver, "GeoSnap", nestedUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(
        send,
        context.getString(R.string.share_title)
    ).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}