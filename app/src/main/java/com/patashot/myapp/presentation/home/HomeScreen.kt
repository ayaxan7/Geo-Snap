package com.patashot.myapp.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.patashot.myapp.R
import com.patashot.myapp.presentation.core.findActivity
import com.patashot.myapp.presentation.home.HomeViewModel.HomeAction
import com.patashot.myapp.ui.theme.PrimaryGreen

@Composable
fun HomeScreen(
    onTakePhoto: () -> Unit,
    onTakeVideo: () -> Unit,
    onQuickShare: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.onHomeScreenOpened(context.findActivity())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.geo_snap_icon_with_text),
                    contentDescription = stringResource(R.string.home_logo),
                    modifier = Modifier
                        .size(60.dp)
                        .align(Alignment.Start),
                    alignment = Alignment.CenterStart
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_made_for_bharat),
                    color = PrimaryGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize =10.sp,
                    modifier = Modifier.align(Alignment.Start),
                    textAlign = TextAlign.Left,
                    fontStyle = FontStyle.Italic
                )
            }
            IconButton(onClick = { onOpenSettings() }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.home_settings),
                    tint = PrimaryGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Center Map Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.home_india),
                contentDescription = stringResource(R.string.home_india_map),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Bottom Sheet area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                .background(PrimaryGreen)
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Grid items manually placed to perfectly match design (or we can use LazyVerticalGrid)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        iconRes = R.drawable.ic_take_photo,
                        title = stringResource(R.string.home_take_photo),
                        subtitle = stringResource(R.string.home_take_photo_desc),
                        onClick = { onTakePhoto() })
                    ActionCard(
                        iconRes = R.drawable.ic_quick_share,
                        title = stringResource(R.string.home_quick_share),
                        subtitle = stringResource(R.string.home_quick_share_desc),
                        onClick = { onQuickShare() })
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        iconRes = R.drawable.ic_take_video,
                        title = stringResource(R.string.home_take_video),
                        subtitle = stringResource(R.string.home_take_video_desc),
                        onClick = { onTakeVideo() })
                    ActionCard(
                        iconRes = R.drawable.ic_gallery,
                        title = stringResource(R.string.home_gallery),
                        subtitle = stringResource(R.string.home_gallery_desc),
                        onClick = { onOpenGallery() })
                }
            }

//            Spacer(modifier = Modifier.height(24.dp))

//            Button(
//                onClick = { onTakePhoto() },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(48.dp),
//                shape = RoundedCornerShape(50),
//                colors = ButtonDefaults.buttonColors(
//                    containerColor = Color.White, contentColor = PrimaryGreen
//                )
//            ) {
//                Text(
//                    text = "Start with Photo", fontSize = 18.sp, fontWeight = FontWeight.Bold
//                )
//            }
//            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ActionCard(
    iconRes: Int, title: String, subtitle: String, onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(16.dp)
            )
            .border(
                border = BorderStroke(1.dp, Color.White), shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

//        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            fontStyle = FontStyle.Italic,
            fontSize = 10.sp
        )
    }
}