package com.patashot.myapp.presentation.gallery

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patashot.myapp.R
import com.patashot.myapp.presentation.camera.EditLocationScreen
import com.patashot.myapp.presentation.camera.GeoTagCard
import com.patashot.myapp.presentation.camera.PreviewScreen
import com.patashot.myapp.presentation.sharing.shareMediaChooser

@Composable
fun EditMediaScreen(
    onBack: () -> Unit,
    viewModel: EditMediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEditLocation by remember { mutableStateOf(false) }

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareEvents.collect { uriStr ->
            Uri.parse(uriStr)?.let { uri ->
                shareMediaChooser(context, uri, isVideo = uiState.isVideo)?.let(shareLauncher::launch)
            }
        }
    }

    BackHandler {
        if (showEditLocation) {
            showEditLocation = false
        } else {
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            uiState.error != null && uiState.file == null -> {
                Text(
                    text = uiState.error ?: "Error loading media",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.uri != null && uiState.cardContent != null -> {
                if (showEditLocation) {
                    val origLat = uiState.originalCoordinate?.latitude ?: 0.0
                    val origLng = uiState.originalCoordinate?.longitude ?: 0.0
                    val curLat = uiState.cardContent?.latitude ?: origLat
                    val curLng = uiState.cardContent?.longitude ?: origLng
                    EditLocationScreen(
                        initialLat = curLat,
                        initialLng = curLng,
                        centerLat = origLat,
                        centerLng = origLng,
                        initialAddress = uiState.cardContent?.fullAddress,
                        initialLandmark = uiState.cardContent?.cityString,
                        onResolveLocation = { lat, lng ->
                            viewModel.resolveLocation(lat, lng)
                        },
                        onSave = { city, address, lat, lng ->
                            viewModel.updateLocation(city, address, lat, lng)
                            showEditLocation = false
                        },
                        onBack = { showEditLocation = false }
                    )
                } else {
                    PreviewScreen(
                        uri = uiState.uri!!,
                        isVideo = uiState.isVideo,
                        onDelete = { },
                        onRetake = { },
                        onEdit = { },
                        onSaveToGallery = viewModel::saveChanges,
                        onShare = viewModel::share,
                        onBack = onBack,
                        modifier = Modifier.fillMaxSize(),
                        isProcessing = uiState.isSaving,
                        rotationDegrees = uiState.rotationDegrees,
                        cropRect = uiState.cropRect,
                        onRotateLeft = viewModel::rotateLeft,
                        onRotateRight = viewModel::rotateRight,
                        onCropRectChange = viewModel::setCropRect,
                        filter = uiState.filter,
                        filterStrength = uiState.filterStrength,
                        onFilterChange = viewModel::setFilter,
                        onFilterStrengthChange = viewModel::setFilterStrength,
                        showDelete = false,
                        showRetake = false,
                        saveButtonText = stringResource(R.string.edit_save_changes),
                        mediaOverlay = {
                            GeoTagCard(
                                content = uiState.cardContent!!,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                onEditClick = { showEditLocation = true }
                            )
                        }
                    )

                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
