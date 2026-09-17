package com.chirag.geosnap.presentation.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chirag.geosnap.R

/**
 * Runtime permissions the app requires. [Manifest.permission.WRITE_EXTERNAL_STORAGE]
 * is only needed below API 29 where MediaStore writes still use the DATA path.
 */
fun requiredRuntimePermissions(): List<String> = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
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
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> PermissionsViewModel.PermissionRequirement(
                permission,
                R.string.permission_location_label,
                R.string.permission_location_description,
                R.drawable.ic_location
            )
            else -> PermissionsViewModel.PermissionRequirement(
                permission,
                R.string.permission_storage_label,
                R.string.permission_storage_description,
                R.drawable.ic_storage
            )
        }
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )
    startActivity(intent)
}

@Composable
fun PermissionsScreen(
    onAllPermissionsGranted: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel()
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
        viewModel.initialize(requirements, context.currentPermissionGrants(requirements.map { it.permission }))
    }
    LifecycleResumeEffect(Unit) {
        val grants = context.currentPermissionGrants(requirements.map { it.permission })
        viewModel.refreshGrants(grants)
        onPauseOrDispose { }
    }

    LaunchedEffect(uiState.allGranted) {
        if (uiState.allGranted) {
            onAllPermissionsGranted()
        }
    }

    PermissionsContent(
        uiState = uiState,
        onRequest = { permissionList ->
            if (permissionList.isNotEmpty()) {
                launcher.launch(permissionList.toTypedArray())
            }
        },
        onOpenSettings = { context.openAppSettings() }
    )
}

@Composable
private fun rememberPermissionsCopy(): List<PermissionsViewModel.PermissionRequirement> {
    val context = LocalContext.current
    return remember(context) { permissionsCopy(requiredRuntimePermissions()) }
}

@Composable
private fun PermissionsContent(
    uiState: PermissionsViewModel.UiState,
    onRequest: (List<String>) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.permissions_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.permissions_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        uiState.items.forEach { item ->
            PermissionRow(item = item)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(Modifier.height(24.dp))

        if (uiState.canOpenSettings) {
            Text(
                text = stringResource(R.string.permissions_permanently_denied),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.permissions_open_settings))
            }
        }

        val permissions = viewModelRequestable(uiState)
        if (permissions.isNotEmpty()) {
            Button(
                onClick = { onRequest(permissions) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.permissions_allow))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun viewModelRequestable(uiState: PermissionsViewModel.UiState): List<String> =
    uiState.items.filter { !it.granted && !it.permanentlyDenied }.map { it.permission }

@Composable
private fun PermissionRow(item: PermissionsViewModel.PermissionItemState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                painterResource(item.iconRes),
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(item.label),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(item.description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PermissionStatus(item = item)
    }
}

@Composable
private fun PermissionStatus(item: PermissionsViewModel.PermissionItemState) {
    val (text, color, container) = when {
        item.granted -> Triple(
            stringResource(R.string.permissions_granted),
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer
        )
        item.permanentlyDenied -> Triple(
            stringResource(R.string.permissions_denied),
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.errorContainer
        )
        else -> Triple(
            stringResource(R.string.permissions_status_needed),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
    }
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}