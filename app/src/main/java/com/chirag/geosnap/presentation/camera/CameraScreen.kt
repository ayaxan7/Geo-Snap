package com.chirag.geosnap.presentation.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chirag.geosnap.R
import com.chirag.geosnap.presentation.camera.components.CameraBottomControls
import com.chirag.geosnap.presentation.camera.components.CameraTopBar
import com.chirag.geosnap.presentation.sharing.shareMediaChooser

@Composable
fun CameraScreen(
    mode: CameraMode,
    onBack: () -> Unit,
    onGallery: () -> Unit = {},
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentMode by remember(mode) { mutableStateOf(mode) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkLocationEnabled()
    }

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
        onBack() // Or whatever post-share behavior you want
    }

    // Separate launcher for the preview so returning from the chooser leaves the
    // pending capture on screen instead of discarding it.
    val previewShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    BackHandler(enabled = uiState.pendingPreview != null) {
        viewModel.discardPreview()
    }

    LaunchedEffect(previewView, lifecycleOwner, currentMode) {
        if (!hasCameraPermission) {
            viewModel.reportError(CameraError.CAMERA_PERMISSION)
            return@LaunchedEffect
        }
        try {
            engine.bind(previewView, lifecycleOwner, uiState.lensFacing, currentMode)
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
                    Uri.parse(event.uri)?.let {
                        shareMediaChooser(context, it, isVideo = false)?.let(shareLauncher::launch)
                    }
                }
            }
        }
    }

    val currentErrorMessage = uiState.error?.stringRes()?.let(context::getString)
    LaunchedEffect(currentErrorMessage) {
        if (currentErrorMessage != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = context.getString(
                    R.string.camera_error_snackbar,
                    currentErrorMessage
                )
            )
            Log.d("CameraScreen", "Error: $currentErrorMessage")
            viewModel.clearError()
        }
    }

    if (uiState.showLocationDialog) {
        LocationDisabledDialog(
            onTurnOn = {
                viewModel.dismissLocationDialog()
                openLocationSettings(context)
            },
            onDismiss = viewModel::dismissLocationDialog
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            val pendingPreview = uiState.pendingPreview
            var showEditLocation by remember { mutableStateOf(false) }

            if (pendingPreview != null) {
                if (showEditLocation) {
                    EditLocationScreen(
                        initialLat = pendingPreview.cardContent.latitude ?: 0.0,
                        initialLng = pendingPreview.cardContent.longitude ?: 0.0,
                        initialAddress = pendingPreview.cardContent.fullAddress,
                        initialLandmark = pendingPreview.cardContent.cityString,
                        onResolveLocation = { lat, lng ->
                            viewModel.resolveLocation(lat, lng)
                        },
                        onSave = { city, address, lat, lng ->
                            viewModel.updatePendingLocation(city, address, lat, lng)
                            showEditLocation = false
                        },
                        onBack = { showEditLocation = false }
                    )
                } else {
                    PreviewScreen(
                        uri = Uri.fromFile(pendingPreview.file),
                        isVideo = pendingPreview.isVideo,
                        onDelete = viewModel::discardPreview,
                        onRetake = viewModel::discardPreview,
                        onEdit = { /* this is the placeholder action in PreviewScreen */ },
                        onSaveToGallery = viewModel::savePreviewToGallery,
                        onShare = {
                            val uri = runCatching {
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    pendingPreview.file
                                )
                            }.getOrNull()
                            if (uri == null) {
                                viewModel.reportError(CameraError.SHARE_FAILED)
                            } else {
                                shareMediaChooser(context, uri, isVideo = pendingPreview.isVideo)
                                    ?.let(previewShareLauncher::launch)
                            }
                        },
                        onBack = viewModel::discardPreview,
                        modifier = Modifier.fillMaxSize(),
                        filter = uiState.filter,
                        filterStrength = uiState.filterStrength,
                        onFilterChange = viewModel::setFilter,
                        onFilterStrengthChange = viewModel::setFilterStrength,
                        mediaOverlay = {
                            GeoTagCard(
                                content = pendingPreview.cardContent,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                onEditClick = { showEditLocation = true }
                            )
                        }
                    )
    
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            } else {
                // Full-bleed camera preview
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                val bannerError = currentErrorMessage
                if (!uiState.isReady && bannerError != null) {
                    CameraUnavailableBanner(
                        text = bannerError,
                        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
                    )
                }

                // Top bar overlay
                CameraTopBar(
                    mode = currentMode,
                    flashEnabled = uiState.flashEnabled,
                    hasFlash = uiState.hasFlash,
                    onBack = onBack,
                    onFlash = viewModel::toggleFlash,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.systemBars)
                )

                // Bottom overlays: location card above the controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    GeoTagCard(
                        content = uiState.toGeoTagCardContent(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    CameraBottomControls(
                        uiState = uiState,
                        mode = currentMode,
                        onModeSelected = { newMode ->
                            currentMode = newMode
                            viewModel.setMode(newMode)
                        },
                        onCapture = {
                            when (currentMode) {
                                CameraMode.PHOTO, CameraMode.QUICK_SHARE -> viewModel.capturePhoto()
                                CameraMode.VIDEO -> if (uiState.isRecording) {
                                    viewModel.stopRecording()
                                } else {
                                    viewModel.startRecording()
                                }
                            }
                        },
                        onFlip = viewModel::flipLensFacing,
                        onGallery = onGallery
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraUnavailableBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
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

@Composable
private fun LocationDisabledDialog(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_dialog_title)) },
        text = { Text(stringResource(R.string.location_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onTurnOn) {
                Text(stringResource(R.string.location_dialog_turn_on))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.location_dialog_not_now))
            }
        }
    )
}

private fun openLocationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}