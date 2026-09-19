package com.chirag.geosnap.presentation.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.chirag.geosnap.BuildConfig
import com.chirag.geosnap.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The location card composable shared by both the live camera overlay and the
 * off-screen overlay renderer. The same composable is used in both places so the
 * card burned into photos/videos matches what the user sees on screen.
 *
 * When [mapBitmap] is provided it is drawn as the map thumbnail (burned media);
 * otherwise the static-map URL from [GeoTagCardContent.staticMapUrl] is loaded
 * asynchronously (live overlay).
 */
@Composable
fun GeoTagCard(
    content: GeoTagCardContent,
    modifier: Modifier = Modifier,
    mapBitmap: Bitmap? = null
) {
    Log.d(
        "LocationCard",
        "API Key: ${BuildConfig.MAPS_API_KEY}, Lat: ${content.latitude}, Lng: ${content.longitude}," +
            " city: ${content.cityString}, Address: ${content.fullAddress}"
    )
    val cityText = content.cityString ?: "Fetching location..."
    val fullAddressText = content.fullAddress ?: content.cityString ?: "Fetching location..."

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Map Image
        MapThumbnail(mapBitmap ?: content.staticMapUrl)

        Spacer(modifier = Modifier.width(12.dp))

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cityText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_mylocation),
                    contentDescription = "",
                    tint = Color.Red,
                    modifier = Modifier.size(12.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = fullAddressText,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "LAT: ${content.latitude?.let { "%.4f°".format(it) } ?: "--"} / " +
                    "LOG: ${content.longitude?.let { "%.4f°".format(it) } ?: "--"}",
                color = Color.White,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_today),
                    contentDescription = "Date",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val dateFormat = SimpleDateFormat("EEEE , dd/MM/yyyy - h:mm a", Locale.getDefault())
                val currentDateTime = dateFormat.format(Date(content.timestamp))
                Text(
                    text = currentDateTime,
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Logo Placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.geo_snap_icon),
                contentDescription = "Our Logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
    }
}

@Composable
private fun MapThumbnail(image: Any?) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(Color.Gray, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        when (image) {
            is Bitmap -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Static Map",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            is String -> AsyncImage(
                model = image,
                contentDescription = "Static Map",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            else -> Text(
                text = "Map",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}