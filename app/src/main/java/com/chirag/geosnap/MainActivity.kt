package com.chirag.geosnap

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.chirag.geosnap.core.LocaleHelper
import com.chirag.geosnap.presentation.navigation.GeoSnapAppRoot
import com.chirag.geosnap.ui.theme.GeoSnapTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyToContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
        setContent {
            GeoSnapTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GeoSnapAppRoot()
                }
            }
        }
    }
}