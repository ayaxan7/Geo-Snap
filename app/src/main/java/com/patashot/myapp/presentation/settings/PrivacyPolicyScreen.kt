package com.patashot.myapp.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patashot.myapp.R
import com.patashot.myapp.ui.theme.PrimaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_privacy_policy),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 48.dp),
                        textAlign = TextAlign.Center
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
        containerColor = Color.White,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Text(
                text = "Last Updated: September 2026",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your privacy matters to us",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This Privacy Policy explains how PataShot accesses, uses, stores, and protects information when you use the application.",
                fontSize = 13.sp,
                color = Color(0xFF6B7280),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Introduction
            ExpandablePolicyCard(
                title = "1. Introduction",
                content = {
                    PolicyParagraph(
                        text = "Welcome to PataShot, a location-enabled photo and video application that allows users to capture photos and videos with relevant location information, GPS coordinates, date, time, and map details."
                    )
                    PolicyParagraph(
                        text = "This Privacy Policy explains how PataShot accesses, uses, stores, and protects information when you use the application."
                    )
                    PolicyParagraph(
                        text = "By using PataShot, you acknowledge this Privacy Policy. The application requests only the permissions needed to support its features, subject to your device settings and applicable permissions."
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Information and Permissions We Use
            ExpandablePolicyCard(
                title = "2. Information and Permissions We Use",
                content = {
                    Text(
                        text = "Depending on the features you use and the permissions you grant, PataShot may access the following:",
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    PolicyBulletPoint(
                        boldPrefix = "Camera",
                        text = " — PataShot requests camera access to allow you to capture photos and videos directly through the application."
                    )
                    PolicyBulletPoint(
                        boldPrefix = "Microphone",
                        text = " — PataShot may request microphone access to record audio while capturing videos. Microphone access is relevant to video recording features that include audio."
                    )
                    PolicyBulletPoint(
                        boldPrefix = "Location",
                        text = " — PataShot requests approximate and precise location access to determine your geographic location and support location-based features, including adding geotags, coordinates, and location information to captured photos and videos.\nLocation accuracy depends on your device settings, available location services, and the permissions you grant."
                    )
                    PolicyBulletPoint(
                        boldPrefix = "Photos, Videos, and Media Access",
                        text = " — PataShot may request access to photos and videos on your device so you can select, view, upload, or manage media using supported application features.\nThe scope of access depends on your device's operating system and the permissions you grant."
                    )
                    PolicyBulletPoint(
                        boldPrefix = "Internet Access",
                        text = " — PataShot uses internet access to support online features, which may include displaying map information through Google Maps or other applicable services."
                    )
                    PolicyBulletPoint(
                        boldPrefix = "Notifications",
                        text = " — PataShot may request notification permission to provide relevant app activity notifications, updates, and other important information. You can manage notification permissions through your device settings."
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. How Information Is Used
            ExpandablePolicyCard(
                title = "3. How Information Is Used",
                content = {
                    Text(
                        text = "Information accessed through the permissions described above may be used to:",
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    PolicySimpleBullet(text = "Capture photos and videos.")
                    PolicySimpleBullet(text = "Add location, geographic coordinates, date, time, or map details to media.")
                    PolicySimpleBullet(text = "Display maps and location-related information.")
                    PolicySimpleBullet(text = "Allow you to select, view, or manage supported media.")
                    PolicySimpleBullet(text = "Provide application features and functionality.")
                    PolicySimpleBullet(text = "Deliver relevant notifications, where enabled.")
                    PolicySimpleBullet(text = "Maintain application security and troubleshoot technical issues, where applicable.")
                    Spacer(modifier = Modifier.height(6.dp))
                    PolicyParagraph(
                        text = "PataShot's actual collection and processing of information depend on the features you use and the application's implementation."
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Data Storage and Retention
            ExpandablePolicyCard(
                title = "4. Data Storage and Retention",
                content = {
                    PolicyParagraph(
                        text = "PataShot does not store users' photos or videos on our servers. Media is handled within the app and may be saved to the user's phone with the required permissions. Currently, PataShot does not collect or store users' names, phone numbers, or media details."
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Data Sharing and Third-Party Services
            ExpandablePolicyCard(
                title = "5. Data Sharing and Third-Party Services",
                content = {
                    PolicyParagraph(
                        text = "PataShot does not upload users' photos or videos to our servers. The app may use third-party services, such as Google Maps, to provide map and location-based features. These services may process information according to their own privacy policies."
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Data Security
            ExpandablePolicyCard(
                title = "6. Data Security",
                content = {
                    PolicyParagraph(
                        text = "Users' photos and videos remain on their devices unless they choose to share or transfer them. We take reasonable measures to protect information processed by the app and its services."
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 7. Your Choices and Permissions
            ExpandablePolicyCard(
                title = "7. Your Choices and Permissions",
                content = {
                    PolicySimpleBullet(text = "You can manage camera, microphone, location, media, and notification permissions through your device settings, where supported.")
                    PolicySimpleBullet(text = "Disabling a permission may prevent some application features from functioning correctly.")
                    PolicySimpleBullet(text = "You should review the permissions requested by the application and grant only those you are comfortable allowing.")
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 8. Data Deletion
            ExpandablePolicyCard(
                title = "8. Data Deletion",
                content = {
                    PolicySimpleBullet(text = "You may be able to delete photos and videos stored on your device using your device's available media-management features.")
                    PolicySimpleBullet(text = "If PataShot stores any personal information on its servers or through third-party services, the app operator must provide an appropriate method for requesting deletion of that information, where applicable.")
                    PolicySimpleBullet(text = "For questions or deletion requests, contact us using the details provided below.")
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 9. Children's Privacy
            ExpandablePolicyCard(
                title = "9. Children's Privacy",
                content = {
                    PolicySimpleBullet(text = "PataShot is not intended to collect personal information from children in violation of applicable laws.")
                    PolicySimpleBullet(text = "If you believe that personal information has been collected from a child inappropriately, please contact us so that the matter can be reviewed and appropriate action can be taken.")
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 10. Changes to This Privacy Policy
            ExpandablePolicyCard(
                title = "10. Changes to This Privacy Policy",
                content = {
                    PolicySimpleBullet(text = "We may update this Privacy Policy when application features, data practices, or legal requirements change.")
                    PolicySimpleBullet(text = "Any updated version will be published on this page with a revised \"Last Updated\" date. Please review this page periodically.")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Green Outline Summary Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, PrimaryGreen),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your privacy matters.",
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PataShot only uses information needed to provide and improve your camera and location-based experience.",
                        color = PrimaryGreen,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bottom Footer
            Text(
                text = "Made in India. Made for Bharat 🇮🇳",
                color = PrimaryGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fontStyle = FontStyle.Italic
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExpandablePolicyCard(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrowRotation"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        color = Color.White,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isExpanded = !isExpanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.Black,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun PolicyParagraph(
    text: String
) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = Color(0xFF4B5563),
        lineHeight = 13.sp,
        modifier = Modifier.padding(top = 3.dp)
    )
}

@Composable
private fun PolicyBulletPoint(
    boldPrefix: String,
    text: String
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black)) {
                append("• $boldPrefix")
            }
            withStyle(SpanStyle(color = Color(0xFF4B5563))) {
                append(text)
            }
        },
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}

@Composable
private fun PolicySimpleBullet(
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "• ",
            fontSize = 13.sp,
            color = Color(0xFF4B5563),
            lineHeight = 13.sp
        )
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFF4B5563),
            lineHeight = 13.sp
        )
    }
}
