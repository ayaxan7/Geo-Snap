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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patashot.myapp.R
import com.patashot.myapp.ui.theme.PrimaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionsScreen(
    onBack: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_terms),
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
                text = stringResource(R.string.terms_last_updated),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.terms_header_title),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.terms_header_desc),
                fontSize = 13.sp,
                color = Color(0xFF6B7280),
                lineHeight = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Eligibility
            ExpandableTermsCard(
                title = stringResource(R.string.terms_s1_title),
                content = {
                    TermsBulletItem(text = stringResource(R.string.terms_s1_b1))
                    TermsBulletItem(text = stringResource(R.string.terms_s1_b2))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. About PataShot
            ExpandableTermsCard(
                title = stringResource(R.string.terms_s2_title),
                content = {
                    TermsIntroText(text = stringResource(R.string.terms_s2_intro))
                    TermsBulletItem(text = stringResource(R.string.terms_s2_b1), indent = 12.dp)
                    TermsBulletItem(text = stringResource(R.string.terms_s2_b2), indent = 12.dp)
                    TermsBulletItem(text = stringResource(R.string.terms_s2_b3), indent = 12.dp)
                    TermsBulletItem(text = stringResource(R.string.terms_s2_b4), indent = 12.dp)
                    TermsBulletItem(text = stringResource(R.string.terms_s2_b5), indent = 12.dp)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Location & GPS Data
            ExpandableTermsCard(
                title = stringResource(R.string.terms_s3_title),
                content = {
                    TermsBulletItem(text = stringResource(R.string.terms_s3_b1))
                    TermsBulletItem(text = stringResource(R.string.terms_s3_b2))
                    TermsBulletItem(text = stringResource(R.string.terms_s3_b3))
                    TermsBulletItem(text = stringResource(R.string.terms_s3_b4))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Free Usage & Subscriptions
            ExpandableTermsCard(
                title = stringResource(R.string.terms_s4_title),
                content = {
                    TermsBulletItem(text = stringResource(R.string.terms_s4_b1))
                    TermsBulletItem(text = stringResource(R.string.terms_s4_b2))
                    TermsBulletItem(text = stringResource(R.string.terms_s4_b3))
                    TermsBulletItem(text = stringResource(R.string.terms_s4_b4))
                    TermsBulletItem(text = stringResource(R.string.terms_s4_b5))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5. User Responsibilities
            ExpandableTermsCard(
                title = stringResource(R.string.terms_s5_title),
                content = {
                    TermsIntroText(text = stringResource(R.string.terms_s5_intro))
                    TermsBulletItem(text = stringResource(R.string.terms_s5_b1), indent = 12.dp)
                    TermsBulletItem(text = stringResource(R.string.terms_s5_b2), indent = 12.dp)
                    TermsBulletItem(text = stringResource(R.string.terms_s5_b3), indent = 12.dp)
                    TermsBulletItem(text = stringResource(R.string.terms_s5_b4), indent = 12.dp)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Photos & Content
            ExpandableTermsCard(
                title = stringResource(R.string.terms_s6_title),
                content = {
                    TermsBulletItem(text = stringResource(R.string.terms_s6_b1))
                    TermsBulletItem(text = stringResource(R.string.terms_s6_b2))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 7. Privacy & Permissions
            ExpandableTermsCard(
                title = stringResource(R.string.terms_s7_title),
                content = {
                    TermsIntroText(text = stringResource(R.string.terms_s7_intro))
                    TermsBulletItem(text = stringResource(R.string.terms_s7_b1), indent = 12.dp)
                    TermsBulletItem(text = stringResource(R.string.terms_s7_b2), indent = 12.dp)
                    TermsBulletItem(text = stringResource(R.string.terms_s7_b3), indent = 12.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.terms_s7_note),
                        fontSize = 12.5.sp,
                        color = Color(0xFFE53935),
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPrivacyPolicyClick)
                            .padding(vertical = 4.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 8. Service Availability
            ExpandableTermsCard(
                title = stringResource(R.string.terms_s8_title),
                content = {
                    Text(
                        text = stringResource(R.string.terms_s8_p1),
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 9. Changes to These Terms
            ExpandableTermsCard(
                title = stringResource(R.string.terms_s9_title),
                content = {
                    Text(
                        text = stringResource(R.string.terms_s9_p1),
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
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
                        text = stringResource(R.string.terms_summary_title),
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp
                    )
//                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.terms_summary_desc),
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
private fun ExpandableTermsCard(
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
private fun TermsIntroText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = Color(0xFF4B5563),
        lineHeight = 13.sp,
        modifier = modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun TermsBulletItem(
    text: String,
    indent: Dp = 0.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 2.5.dp),
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
