package com.chirag.geosnap.presentation.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chirag.geosnap.R
import com.chirag.geosnap.ui.theme.PrimaryGreen
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

private const val MAX_RADIUS_METERS = 1000.0
private val RadiusFill = Color(0x220F993E)
private val RadiusStroke = Color(0xFF0F993E)
private val PinColor = Color(0xFF0F993E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLocationScreen(
    initialLat: Double,
    initialLng: Double,
    initialAddress: String?,
    initialLandmark: String?,
    onResolveLocation: suspend (Double, Double) -> Pair<String, String?>,
    onSave: (String?, String?, Double, Double) -> Unit,
    onBack: () -> Unit
) {
    var lat by remember { mutableStateOf(initialLat) }
    var lng by remember { mutableStateOf(initialLng) }
    var address by remember { mutableStateOf(initialAddress ?: "") }
    var landmark by remember { mutableStateOf(initialLandmark ?: "") }

    val center = remember { LocationClamp.Coords(initialLat, initialLng) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(center.latitude, center.longitude),
            15f
        )
    }

    // Hard-block: the camera center can never leave the radius circle, even
    // mid-drag. The instant it crosses the edge, the camera is moved back to
    // the boundary, so the map simply stops following the finger at the edge.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.position.target }
            .collect { target ->
                val clamped = LocationClamp.clampToRadius(
                    target = LocationClamp.Coords(target.latitude, target.longitude),
                    center = center,
                    radiusMeters = MAX_RADIUS_METERS
                )
                if (clamped.latitude != target.latitude ||
                    clamped.longitude != target.longitude
                ) {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLng(
                            LatLng(clamped.latitude, clamped.longitude)
                        )
                    )
                }
            }
    }

    // Update location fields when map settles
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val target = cameraPositionState.position.target
            if (target.latitude != lat || target.longitude != lng) {
                lat = target.latitude
                lng = target.longitude
                // Reverse geocode
                val (newLandmark, newAddress) = onResolveLocation(lat, lng)
                landmark = newLandmark
                address = newAddress ?: ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Location",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    Circle(
                        center = LatLng(center.latitude, center.longitude),
                        radius = MAX_RADIUS_METERS,
                        fillColor = RadiusFill,
                        strokeColor = RadiusStroke,
                        strokeWidth = 3f
                    )
                }

                // Fixed pin: always at the exact center of the screen; the map
                // moves underneath it.
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = "Map Pin",
                    tint = PinColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-24).dp)
                        .size(42.dp)
                )
                
                // Warning Banner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(vertical = 16.dp, horizontal = 12.dp)
                        .background(Color(0xFFD32F2F), RoundedCornerShape(8.dp))
                        .padding(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "For accuracy, edited locations must remain within 1 km of the original GPS location.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 12.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Enter Address",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = { Text("Enter Location", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Landmark",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = landmark,
                    onValueChange = { landmark = it },
                    placeholder = { Text("Enter name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { onSave(landmark.ifBlank { null }, address.ifBlank { null }, lat, lng) },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(42.dp)
                        .align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.White,
                        containerColor = PrimaryGreen,
                    ),
                ) {
                    Text(
                        text = "Save Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
