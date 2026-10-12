package com.patashot.myapp.presentation.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patashot.myapp.R
import com.patashot.myapp.presentation.components.CommonButton
import com.patashot.myapp.presentation.core.findActivity
import com.patashot.myapp.presentation.core.mediaPermissionsForSdk
import com.patashot.myapp.presentation.core.openAppSettings
import com.patashot.myapp.ui.theme.PrimaryGreen

/**
 * Runtime permissions the app asks for during onboarding.
 *
 * Includes optional ones too: they are still worth offering on this screen rather
 * than hiding until Settings, but [permissionsCopy] marks them `isOptional` so a
 * denial never blocks the user from getting into the app.
 */
fun requiredRuntimePermissions(): List<String> = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    addAll(mediaPermissionsForSdk())
}

private fun Context.currentPermissionGrants(permissions: List<String>): Map<String, Boolean> =
    permissions.associateWith { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

private fun permissionsCopy(permissions: List<String>): List<PermissionsViewModel.PermissionRequirement> =
    permissions.map { permission ->
        when (permission) {
            Manifest.permission.CAMERA -> PermissionsViewModel.PermissionRequirement(
                permission,
                R.string.permission_camera_label,
                R.string.permission_camera_description,
                R.drawable.ic_camera
            )

            Manifest.permission.RECORD_AUDIO -> PermissionsViewModel.PermissionRequirement(
                permission,
                R.string.permission_microphone_label,
                R.string.permission_microphone_description,
                R.drawable.ic_microphone
            )

            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION -> PermissionsViewModel.PermissionRequirement(
                permission,
                R.string.permission_location_label,
                R.string.permission_location_description,
                R.drawable.ic_location
            )

            Manifest.permission.POST_NOTIFICATIONS -> PermissionsViewModel.PermissionRequirement(
                permission,
                R.string.permission_notifications_label,
                R.string.permission_notifications_description,
                R.drawable.ic_notification,
                isOptional = true
            )

            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> PermissionsViewModel.PermissionRequirement(
                permission,
                R.string.permission_storage_label,
                R.string.permission_storage_description,
                R.drawable.ic_media,
                isOptional = true
            )

            else -> PermissionsViewModel.PermissionRequirement(
                permission,
                R.string.permission_storage_label, // Fallback
                R.string.permission_storage_description,
                R.drawable.ic_media,
                isOptional = true
            )
        }
    }.distinctBy { it.label } // Group permissions with the same label so we only show one row

@Composable
fun PermissionsScreen(
    onAllPermissionsGranted: () -> Unit, viewModel: PermissionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val requirements = rememberPermissionsCopy()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val permanentlyDenied = results.filterValues { !it }.keys.filter { permission ->
            context.findActivity()?.shouldShowRequestPermissionRationale(permission) == false
        }.toSet()
        viewModel.onPermissionResults(results, permanentlyDenied)
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(
            requirements, context.currentPermissionGrants(requirements.map { it.permission })
        )
    }
    LifecycleResumeEffect(Unit) {
        val grants = context.currentPermissionGrants(requirements.map { it.permission })
        viewModel.refreshGrants(grants)
        onPauseOrDispose { }
    }

    // We no longer automatically navigate on all granted. 
    // We let the user click Get Started.

    PermissionsContent(
        uiState = uiState, onRequest = { permissionList ->
        if (permissionList.isNotEmpty()) {
            launcher.launch(permissionList.toTypedArray())
        }
    }, onOpenSettings = { context.openAppSettings() }, onGetStarted = onAllPermissionsGranted
    )
}

@Composable
private fun rememberPermissionsCopy(): List<PermissionsViewModel.PermissionRequirement> {
    val context = LocalContext.current
    return remember(context) { permissionsCopy(requiredRuntimePermissions()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsContent(
    uiState: PermissionsViewModel.UiState,
    onRequest: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onGetStarted: () -> Unit
) {
    var isAgreed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.permissions_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
//                    .safeDrawingPadding()
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAgreed = !isAgreed }
                        .padding(bottom = 8.dp,top=4.dp)
                ) {
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides 4.dp,
                    ) {
                        Checkbox(
                            checked = isAgreed,
                            onCheckedChange = { isAgreed = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryGreen,
                                uncheckedColor = PrimaryGreen
                            ),
                            modifier = Modifier.size(28.dp)
                        )
                    }
//
//                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = stringResource(R.string.permissions_agree),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false
                            ),
                            fontStyle = FontStyle.Italic
                        ),
                        color = Color.Gray,
                        lineHeight = 14.sp,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                CommonButton(
                    text = stringResource(R.string.permissions_get_started),
                    enabled = isAgreed,
                    onClick = {
                        val requestable = viewModelRequestable(uiState)

                        if (requestable.isNotEmpty()) {
                            onRequest(requestable)
                        } else {
                            onGetStarted()
                        }
                    }
                )
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = stringResource(R.string.permissions_subtitle_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.permissions_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                lineHeight = 14.sp
            )

            uiState.items.forEach { item ->
                PermissionRow(
                    item = item,
                    onToggle = {
                        // Android stops showing the prompt once a permission has been
                        // denied for good, so re-requesting would silently do nothing.
                        // Send the user to system settings instead, which is the only
                        // route left. Applies to optional rows too: the user chose to
                        // grant one, so don't leave them tapping a dead switch.
                        if (item.granted || item.permanentlyDenied) {
                            onOpenSettings()
                        } else {
                            val relatedPermissions = uiState.items
                                .filter {
                                    it.label == item.label && !it.granted
                                }
                                .map {
                                    it.permission
                                }

                            if (relatedPermissions.isNotEmpty()) {
                                onRequest(relatedPermissions)
                            } else {
                                onRequest(listOf(item.permission))
                            }
                        }
                    }
                )
            }

            if (uiState.canOpenSettings) {

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.permissions_permanently_denied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        stringResource(R.string.permissions_open_settings)
                    )
                }
            }
        }
    }
}

private fun viewModelRequestable(uiState: PermissionsViewModel.UiState): List<String> =
    uiState.items.filter { !it.granted && !it.permanentlyDenied && !it.isOptional }.map { it.permission }

@Composable
private fun PermissionRow(
    item: PermissionsViewModel.PermissionItemState, onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = stringResource(item.label),
            modifier = Modifier
                .padding(top = 4.dp)
                .size(18.dp),
            tint = PrimaryGreen
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    append(stringResource(item.label))
                    if (!item.isOptional) {
                        withStyle(style = SpanStyle(color = Color.Red)) {
                            append(" " + stringResource(R.string.permissions_required_marker))
                        }
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(item.description),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                lineHeight = 14.sp
            )
        }
        Switch(
            checked = item.granted,
            onCheckedChange = { onToggle() },
            modifier = Modifier.offset(y = (-12).dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.LightGray
            )
        )
    }
}