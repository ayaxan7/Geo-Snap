package com.chirag.geosnap.presentation.camera

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.chirag.geosnap.BuildConfig
import com.chirag.geosnap.R
import com.chirag.geosnap.presentation.camera.components.CameraBottomControls
import com.chirag.geosnap.presentation.camera.components.CameraTopBar
import com.chirag.geosnap.ui.theme.PrimaryGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Locale.getDefault

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
                    launchShareChooser(context, event.uri)?.let(shareLauncher::launch)
                }
            }
        }
    }

    val currentError = uiState.error.toString()
    LaunchedEffect(currentError) {
        if (currentError != null && currentError.lowercase() !="null") {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = "Error: $currentError"
            )
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
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .safeDrawingPadding()
        ) {
            CameraTopBar(
                mode = currentMode,
                flashEnabled = uiState.flashEnabled,
                hasFlash = uiState.hasFlash,
                onBack = onBack,
                onFlash = viewModel::toggleFlash
            )

            // Middle section with preview and overlay
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                val bannerError = currentError
                if (!uiState.isReady && bannerError != null) {
                    CameraUnavailableBanner(
                        text = bannerError,
                        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
                    )
                }

                // Location Card Overlay
                LocationCard(
                    uiState = uiState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            CameraBottomControls(
                uiState = uiState,
                mode = currentMode,
                onModeSelected = { newMode -> currentMode = newMode },
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
                onGallery = {
                    // Navigate to gallery (not passed directly to CameraScreen right now, so we placeholder)
                }
            )

//            // Bottom Green Bar
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(64.dp)
//                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
//                    .background(PrimaryGreen)
//            )
        }
    }
}

@Composable
fun LocationCard(uiState: CameraUiState, modifier: Modifier = Modifier) {
    val lat = uiState.currentLocation?.latitude
    val lng = uiState.currentLocation?.longitude
    val cityStr = uiState.cityString ?: "Fetching location..."
    val fullAddressStr = uiState.fullAddress ?: uiState.cityString ?: "Fetching location..."

    val apiKey = BuildConfig.MAPS_API_KEY
    Log.d("LocationCard", "API Key: $apiKey, Lat: $lat, Lng: $lng ,city: $cityStr, Address: $fullAddressStr")
    val mapUrl = if (lat != null && lng != null && apiKey.isNotBlank()) {
        "https://maps.googleapis.com/maps/api/staticmap?center=$lat,$lng&zoom=15&size=200x200&markers=color:red%7C$lat,$lng&key=$apiKey"
    } else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Map Image
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color.Gray, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (mapUrl != null) {
                AsyncImage(
                    model = mapUrl,
                    contentDescription = "Static Map",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "Map",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cityStr,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_mylocation),
                    contentDescription = "",
                    tint = Color.Red,
                    modifier = Modifier.size(12.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = fullAddressStr,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "LAT: ${lat?.let { "%.4f°".format(it) } ?: "--"} / LOG: ${lng?.let { "%.4f°".format(it) } ?: "--"}",
                color = Color.White,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_today),
                    contentDescription = "Date",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val dateFormat = SimpleDateFormat("EEEE , dd/MM/yyyy - h:mm a", Locale.getDefault())
                val currentDateTime = dateFormat.format(Date())
                Text(
                    text = currentDateTime,
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Logo Placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.geo_snap_icon),
                contentDescription = "Our Logo",
                modifier=Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
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