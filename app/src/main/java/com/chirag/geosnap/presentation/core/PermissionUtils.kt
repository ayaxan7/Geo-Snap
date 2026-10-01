package com.chirag.geosnap.presentation.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Runtime permissions guarding access to the device photo/video gallery.
 *
 * Writing to the gallery via MediaStore needs no runtime permission on
 * Android 10+, but reading media from MediaStore and (on Android 9 and below)
 * writing to public directories do. These are the permissions the
 * "Auto Save to Device" switch reflects.
 */
fun mediaPermissionsForSdk(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}

/**
 * Whether the app has been granted access to the device photo/video gallery.
 */
fun Context.hasMediaPermission(): Boolean {
    val permissions = mediaPermissionsForSdk()
    return permissions.isNotEmpty() && permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Opens the system App Info screen for this app, where the user can switch
 * individual permissions (including "Photos & media") on or off.
 */
fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)
    )
    startActivity(intent)
}

/**
 * Unwraps a Context chain to the host [Activity], used to decide whether a
 * denied permission was denied permanently.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}