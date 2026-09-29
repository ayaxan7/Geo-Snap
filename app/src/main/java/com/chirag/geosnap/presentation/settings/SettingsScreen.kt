package com.chirag.geosnap.presentation.settings

import androidx.annotation.DrawableRes
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chirag.geosnap.R
import com.chirag.geosnap.ui.theme.PrimaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLanguageClick: () -> Unit
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var syncEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                title = "Notifications",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
            SettingsDivider()
            
            SettingsItemArrow(
                icon = R.drawable.ic_language,
                title = "Language",
                onClick = onLanguageClick
            )
            SettingsDivider()

            SettingsItemArrow(
                icon = R.drawable.ic_templates,
                title = "Templates",
                onClick = { }
            )
            SettingsDivider()

            SettingsItemArrow(
                icon = R.drawable.ic_subscription,
                title = "Subscription Plans",
                onClick = { }
            )
            SettingsDivider()

            SettingsItemSwitch(
                icon = R.drawable.ic_sync,
                title = "Sync - Drive",
                checked = syncEnabled,
                onCheckedChange = { syncEnabled = it }
            )
            SettingsDivider()

            SettingsItemArrow(
                icon = R.drawable.ic_app_rating,
                title = "Rate the App",
                onClick = { }
            )
            SettingsDivider()

            SettingsItemArrow(
                icon = R.drawable.ic_terms,
                title = "Terms and conditions",
                onClick = { }
            )
            SettingsDivider()

            SettingsItemArrow(
                icon = R.drawable.ic_privacy,
                title = "Privacy Policy",
                onClick = { }
            )
            SettingsDivider()

            SettingsItemArrow(
                icon = R.drawable.ic_help,
                title = "Help and Support",
                onClick = { }
            )
            
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
                    contentDescription = "Proudly Made",
                    tint = PrimaryGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = "Proudly Made in India",
                    color = PrimaryGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
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
    title: String,
    onClick: () -> Unit
) {
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
            modifier = Modifier.size(24.dp)
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
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = title,
            modifier = Modifier.size(24.dp)
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
