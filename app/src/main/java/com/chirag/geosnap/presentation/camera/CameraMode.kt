package com.chirag.geosnap.presentation.camera

/**
 * The behaviour of the reusable camera screen.
 *
 * A single camera destination is used for every mode; only the mode argument
 * changes at navigation time (`camera/photo`, `camera/video`, `camera/quick_share`).
 */
enum class CameraMode(val routeName: String) {
    PHOTO("photo"),
    VIDEO("video"),
    QUICK_SHARE("quick_share");

    companion object {
        fun fromRoute(value: String?): CameraMode? =
            entries.firstOrNull { it.routeName == value }
    }
}