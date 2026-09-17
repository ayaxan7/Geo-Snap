package com.chirag.geosnap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.chirag.geosnap.presentation.navigation.GeoSnapAppRoot
import com.chirag.geosnap.ui.theme.GeoSnapTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeoSnapTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GeoSnapAppRoot()
                }
            }
        }
    }
}