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
                text = stringResource(R.string.privacy_policy_last_updated),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.privacy_policy_header_title),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.privacy_policy_header_desc),
                fontSize = 13.sp,
                color = Color(0xFF6B7280),
                lineHeight = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Introduction
            ExpandablePolicyCard(
                title = stringResource(R.string.privacy_policy_s1_title),
                content = {
                    PolicyParagraph(
                        text = stringResource(R.string.privacy_policy_s1_p1)
                    )
                    PolicyParagraph(
                        text = stringResource(R.string.privacy_policy_s1_p2)
                    )
                    PolicyParagraph(
                        text = stringResource(R.string.privacy_policy_s1_p3)
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Information and Permissions We Use
            ExpandablePolicyCard(
                title = stringResource(R.string.privacy_policy_s2_title),
                content = {
                    Text(
                        text = stringResource(R.string.privacy_policy_s2_intro),
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    PolicyBulletPoint(
                        boldPrefix = stringResource(R.string.privacy_policy_s2_camera_title),
                        text = stringResource(R.string.privacy_policy_s2_camera_desc)
                    )
                    PolicyBulletPoint(
                        boldPrefix = stringResource(R.string.privacy_policy_s2_mic_title),
                        text = stringResource(R.string.privacy_policy_s2_mic_desc)
                    )
                    PolicyBulletPoint(
                        boldPrefix = stringResource(R.string.privacy_policy_s2_location_title),
                        text = stringResource(R.string.privacy_policy_s2_location_desc)
                    )
                    PolicyBulletPoint(
                        boldPrefix = stringResource(R.string.privacy_policy_s2_media_title),
                        text = stringResource(R.string.privacy_policy_s2_media_desc)
                    )
                    PolicyBulletPoint(
                        boldPrefix = stringResource(R.string.privacy_policy_s2_internet_title),
                        text = stringResource(R.string.privacy_policy_s2_internet_desc)
                    )
                    PolicyBulletPoint(
                        boldPrefix = stringResource(R.string.privacy_policy_s2_notifications_title),
                        text = stringResource(R.string.privacy_policy_s2_notifications_desc)
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. How Information Is Used
            ExpandablePolicyCard(
                title = stringResource(R.string.privacy_policy_s3_title),
                content = {
                    Text(
                        text = stringResource(R.string.privacy_policy_s3_intro),
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s3_b1))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s3_b2))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s3_b3))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s3_b4))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s3_b5))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s3_b6))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s3_b7))
                    Spacer(modifier = Modifier.height(6.dp))
                    PolicyParagraph(
                        text = stringResource(R.string.privacy_policy_s3_footer)
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Data Storage and Retention
            ExpandablePolicyCard(
                title = stringResource(R.string.privacy_policy_s4_title),
                content = {
                    PolicyParagraph(
                        text = stringResource(R.string.privacy_policy_s4_p1)
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Data Sharing and Third-Party Services
            ExpandablePolicyCard(
                title = stringResource(R.string.privacy_policy_s5_title),
                content = {
                    PolicyParagraph(
                        text = stringResource(R.string.privacy_policy_s5_p1)
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Data Security
            ExpandablePolicyCard(
                title = stringResource(R.string.privacy_policy_s6_title),
                content = {
                    PolicyParagraph(
                        text = stringResource(R.string.privacy_policy_s6_p1)
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 7. Your Choices and Permissions
            ExpandablePolicyCard(
                title = stringResource(R.string.privacy_policy_s7_title),
                content = {
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s7_b1))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s7_b2))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s7_b3))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 8. Data Deletion
            ExpandablePolicyCard(
                title = stringResource(R.string.privacy_policy_s8_title),
                content = {
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s8_b1))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s8_b2))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s8_b3))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 9. Children's Privacy
            ExpandablePolicyCard(
                title = stringResource(R.string.privacy_policy_s9_title),
                content = {
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s9_b1))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s9_b2))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 10. Changes to This Privacy Policy
            ExpandablePolicyCard(
                title = stringResource(R.string.privacy_policy_s10_title),
                content = {
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s10_b1))
                    PolicySimpleBullet(text = stringResource(R.string.privacy_policy_s10_b2))
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
                        text = stringResource(R.string.privacy_policy_summary_title),
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.privacy_policy_summary_desc),
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
                text = stringResource(R.string.home_made_for_bharat),
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
