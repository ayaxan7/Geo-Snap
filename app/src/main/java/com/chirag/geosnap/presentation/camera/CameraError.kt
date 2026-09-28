package com.chirag.geosnap.presentation.camera

/** Categories of camera failures shown to the user. */
enum class CameraError {
    CAMERA_UNAVAILABLE,
    CAMERA_PERMISSION,
    INIT_FAILED,
    CAPTURE_FAILED,
    RECORD_FAILED,
    SAVE_FAILED,
    SHARE_FAILED,
    GENERIC
}

fun CameraError.stringRes(): Int = when (this) {
    CameraError.CAMERA_UNAVAILABLE -> com.chirag.geosnap.R.string.camera_error_unavailable
    CameraError.CAMERA_PERMISSION -> com.chirag.geosnap.R.string.camera_error_permission
    CameraError.INIT_FAILED -> com.chirag.geosnap.R.string.camera_error_init
    CameraError.CAPTURE_FAILED -> com.chirag.geosnap.R.string.camera_error_capture
    CameraError.RECORD_FAILED -> com.chirag.geosnap.R.string.camera_error_record
    CameraError.SAVE_FAILED -> com.chirag.geosnap.R.string.camera_error_save
    CameraError.SHARE_FAILED -> com.chirag.geosnap.R.string.camera_error_share
    CameraError.GENERIC -> com.chirag.geosnap.R.string.camera_error_generic
}