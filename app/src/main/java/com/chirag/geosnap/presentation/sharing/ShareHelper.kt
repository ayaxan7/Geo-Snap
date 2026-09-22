package com.chirag.geosnap.presentation.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.chirag.geosnap.R

/**
 * Builds an [Intent.ACTION_SEND] chooser for a captured media [uri]. The content
 * uri is carried as the stream with an explicit read grant (via ClipData) so
 * target apps can open it.
 */
fun shareMediaChooser(context: Context, uri: Uri, isVideo: Boolean): Intent? =
    Intent(Intent.ACTION_SEND).apply {
        type = if (isVideo) "video/mp4" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "GeoSnap", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.let { send ->
        Intent.createChooser(send, context.getString(R.string.share_title)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }