package com.chirag.geosnap.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chirag.geosnap.R
import com.chirag.geosnap.ui.theme.GeoSnapTheme
import com.chirag.geosnap.ui.theme.PrimaryGreen
import androidx.compose.material3.RadioButton
@Composable
fun LanguageScreen(
    onBack: () -> Unit, onCompleted: () -> Unit, viewModel: LanguageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LanguageContent(
        uiState = uiState,
        onLanguageSelected = { viewModel.selectLanguage(it) },
        onConfirmClick = { viewModel.confirmAndContinue(onCompleted) })
}

@Composable
private fun LanguageContent(
    uiState: LanguageViewModel.UiState,
    onLanguageSelected: (String) -> Unit,
    onConfirmClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paint(
                painterResource(id = R.drawable.select_lang_bg),
                contentScale = ContentScale.FillBounds
            )
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.language_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.language_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            uiState.languages.forEach { language ->
                LanguageRow(
                    language = language,
                    selected = language.code == uiState.selectedCode,
                    onClick = { onLanguageSelected(language.code) })
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onConfirmClick,
            enabled = uiState.selectedCode != null && !uiState.saving,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEAEAEA),
                contentColor = PrimaryGreen,
                disabledContainerColor = Color.LightGray,
                disabledContentColor = Color.DarkGray
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.language_continue), fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LanguageRow(
    language: LanguageViewModel.Language,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(
                border = BorderStroke(1.dp, Color.White),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.White,
                unselectedColor = Color.White
            ),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = language.englishName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        val imageRes = getImageForLanguage(language.code)

        Image(
            painter = painterResource(id = imageRes),
            contentDescription = language.englishName,
            modifier = Modifier.height(48.dp)
        )
    }
}

// Function with assumption that images are in drawable folders
private fun getImageForLanguage(code: String): Int {
    // You can replace these placeholders with your actual drawable resources later
    return when (code) {
        "en" -> R.drawable.ic_english
        "te" -> R.drawable.ic_telugu
        "hi" -> R.drawable.ic_hindi
        "ta" -> R.drawable.ic_tamil
        "kn" -> R.drawable.ic_kannada
        else -> R.drawable.ic_english
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguageScreenPreview() {
    GeoSnapTheme {
        LanguageContent(
            uiState = LanguageViewModel.UiState(selectedCode = "en"),
            onLanguageSelected = {},
            onConfirmClick = {})
    }
}