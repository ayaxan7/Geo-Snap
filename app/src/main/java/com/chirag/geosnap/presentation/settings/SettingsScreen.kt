package com.chirag.geosnap.presentation.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chirag.geosnap.R
import com.chirag.geosnap.presentation.core.findActivity
import com.chirag.geosnap.presentation.core.hasMediaPermission
import com.chirag.geosnap.presentation.core.hasNotificationPermission
import com.chirag.geosnap.presentation.core.mediaPermissionsForSdk
import com.chirag.geosnap.presentation.core.notificationPermissionsForSdk
import com.chirag.geosnap.presentation.core.openAppSettings
import com.chirag.geosnap.presentation.core.openNotificationSettings
import com.chirag.geosnap.ui.theme.PrimaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLanguageClick: () -> Unit,
    onTemplatesClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var syncEnabled by remember { mutableStateOf(true) }

    val mediaPermissions = remember { mediaPermissionsForSdk() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val permanentlyDenied = results.filterValues { !it }.keys.filter { permission ->
            context.findActivity()?.shouldShowRequestPermissionRationale(permission) == false
        }.toSet()
        if (permanentlyDenied.isNotEmpty()) {
            viewModel.requestEnableViaSettings()
        }
        viewModel.setAutoSaveToDevice(context.hasMediaPermission())
    }

    val notificationPermissions = remember { notificationPermissionsForSdk() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val permanentlyDenied = results.filterValues { !it }.keys.filter { permission ->
            context.findActivity()?.shouldShowRequestPermissionRationale(permission) == false
        }.toSet()
        if (permanentlyDenied.isNotEmpty()) {
            viewModel.requestEnableNotificationsViaSettings()
        }
        viewModel.setNotificationsEnabled(context.hasNotificationPermission())
    }

    // Seed and re-sync the switches with the real permission states. When the user
    // comes back from system settings (having revoked or not), this is what flips
    // the switches to match.
    LaunchedEffect(context) {
        viewModel.setAutoSaveToDevice(context.hasMediaPermission())
        viewModel.setNotificationsEnabled(context.hasNotificationPermission())
    }
    LifecycleResumeEffect(context) {
        viewModel.setAutoSaveToDevice(context.hasMediaPermission())
        viewModel.setNotificationsEnabled(context.hasNotificationPermission())
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        viewModel.navigation.collect { target ->
            when (target) {
                SettingsViewModel.NavigationTarget.AppInfo -> context.openAppSettings()
                SettingsViewModel.NavigationTarget.Notifications -> context.openNotificationSettings()
            }
        }
    }

    when {
        uiState.showRevokeDialog -> AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.settings_auto_save_revoke_title)) },
            text = { Text(stringResource(R.string.settings_auto_save_revoke_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOpenSettings) {
                    Text(stringResource(R.string.settings_auto_save_open_app_info))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) {
                    Text(stringResource(R.string.settings_auto_save_not_now))
                }
            }
        )

        uiState.showEnableDialog -> AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.settings_auto_save_enable_title)) },
            text = { Text(stringResource(R.string.settings_auto_save_enable_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOpenSettings) {
                    Text(stringResource(R.string.settings_auto_save_open_app_info))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) {
                    Text(stringResource(R.string.settings_auto_save_not_now))
                }
            }
        )

        uiState.showNotificationRevokeDialog -> AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.settings_notifications_revoke_title)) },
            text = { Text(stringResource(R.string.settings_notifications_revoke_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOpenNotificationSettings) {
                    Text(stringResource(R.string.settings_notifications_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) {
                    Text(stringResource(R.string.settings_auto_save_not_now))
                }
            }
        )

        uiState.showNotificationEnableDialog -> AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.settings_notifications_enable_title)) },
            text = { Text(stringResource(R.string.settings_notifications_enable_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOpenNotificationSettings) {
                    Text(stringResource(R.string.settings_notifications_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) {
                    Text(stringResource(R.string.settings_auto_save_not_now))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_settings),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.camera_back)
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
                .verticalScroll(rememberScrollState())
        ) {
            SettingsItemSwitch(
                icon = R.drawable.ic_notification,
                titleRes = R.string.settings_notifications,
                checked = uiState.notificationsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        // Android 13+ needs an explicit grant; below that notifications
                        // are allowed by default so there is nothing to request.
                        val missing = notificationPermissions.filter {
                            ContextCompat.checkSelfPermission(context, it) !=
                                    PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isNotEmpty()) {
                            notificationPermissionLauncher.launch(missing.toTypedArray())
                        } else {
                            viewModel.setNotificationsEnabled(context.hasNotificationPermission())
                        }
                    } else if (uiState.notificationsEnabled) {
                        // The SDK gives apps no way to revoke their own grant, so send
                        // the user to notification settings; LifecycleResumeEffect
                        // re-reads the real state when they come back.
                        viewModel.requestTurnOffNotifications()
                    }
                }
            )
            SettingsDivider()

            SettingsItemArrow(
                icon = R.drawable.ic_language,
                titleRes = R.string.settings_language,
                onClick = onLanguageClick
            )
            SettingsDivider()

            SettingsItemArrow(
                icon = R.drawable.ic_templates,
                titleRes = R.string.settings_templates,
                onClick = onTemplatesClick
            )
            SettingsDivider()

//            SettingsItemArrow(
//                icon = R.drawable.ic_subscription,
//                titleRes = R.string.settings_subscription_plans,
//                onClick = { }
//            )
//            SettingsDivider()

//            SettingsItemSwitch(
//                icon = R.drawable.ic_sync,
//                titleRes = R.string.settings_sync_drive,
//                checked = syncEnabled,
//                onCheckedChange = { syncEnabled = it }
//            )
//            SettingsDivider()

            SettingsItemSwitch(
                icon = R.drawable.ic_media,
                titleRes = R.string.settings_auto_save_title,
                checked = uiState.autoSaveToDevice,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        val missing = mediaPermissions.filter {
                            ContextCompat.checkSelfPermission(
                                context,
                                it
                            ) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isNotEmpty()) {
                            permissionLauncher.launch(missing.toTypedArray())
                        } else {
                            viewModel.setAutoSaveToDevice(true)
                        }
                    } else if (uiState.autoSaveToDevice) {
                        viewModel.requestTurnOff()
                    }
                }
            )
            SettingsDivider()

            SettingsItemArrow(
                icon = R.drawable.ic_app_rating,
                titleRes = R.string.settings_rate_app,
                onClick = { }
            )
//            SettingsDivider()
//
//            SettingsItemArrow(
//                icon = R.drawable.ic_terms,
//                titleRes = R.string.settings_terms,
//                onClick = { }
//            )
//            SettingsDivider()
//
//            SettingsItemArrow(
//                icon = R.drawable.ic_privacy,
//                titleRes = R.string.settings_privacy_policy,
//                onClick = { }
//            )
//            SettingsDivider()
//
//            SettingsItemArrow(
//                icon = R.drawable.ic_help,
//                titleRes = R.string.settings_help_support,
//                onClick = { }
//            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.settings_proudly_made),
                    tint = PrimaryGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = stringResource(R.string.home_made_for_bharat),
                    color = PrimaryGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = Color(0xFFF0F0F0),
        thickness = (0.8f).dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingsItemArrow(
    @DrawableRes icon: Int,
    @StringRes titleRes: Int,
    onClick: () -> Unit
) {
    val title = stringResource(titleRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = title,
            tint = Color.Black,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = title,
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = PrimaryGreen,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SettingsItemSwitch(
    @DrawableRes icon: Int,
    @StringRes titleRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val title = stringResource(titleRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = title,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = title,
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.LightGray,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
