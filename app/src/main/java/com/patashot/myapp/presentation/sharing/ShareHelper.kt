package com.patashot.myapp.presentation.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.patashot.myapp.R

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

/**
 * Builds a sharing chooser for a whole selection of media. [mimeType] should be
 * `image/jpeg` when all items are images, `video/mp4` when all are videos, or a
 * wildcard MIME type for a mix. A single item falls back to single-uri sharing.
 */
fun shareMediaChooser(
    context: Context,
    uris: List<Uri>,
    mimeType: String
): Intent? {
    if (uris.isEmpty()) return null
    if (uris.size == 1) {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
            clipData = ClipData.newUri(context.contentResolver, "GeoSnap", uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { send ->
            Intent.createChooser(send, context.getString(R.string.share_title)).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = ClipData(
            "GeoSnap",
            arrayOf(mimeType),
            ClipData.Item(uris.first())
        ).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.let { send ->
        Intent.createChooser(send, context.getString(R.string.share_title)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}