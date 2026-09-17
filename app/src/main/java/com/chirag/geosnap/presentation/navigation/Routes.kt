package com.chirag.geosnap.presentation.navigation

import com.chirag.geosnap.presentation.camera.CameraMode

/** Centralized navigation routes for the app. */
object Routes {

    const val PERMISSIONS = "permissions"
    const val LANGUAGE = "language"
    const val HOME = "home"
    const val GALLERY = "gallery"

    private const val CAMERA = "camera"
    const val CAMERA_ARGUMENT = "mode"

    fun cameraRoute(mode: CameraMode): String = "$CAMERA/${mode.routeName}"
}