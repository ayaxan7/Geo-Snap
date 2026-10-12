package com.patashot.myapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.patashot.myapp.core.LocaleHelper
import com.patashot.myapp.presentation.navigation.GeoSnapAppRoot
import com.patashot.myapp.presentation.update.InAppUpdateHost
import com.patashot.myapp.ui.theme.GeoSnapTheme
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
                    val snackbarHostState = remember { SnackbarHostState() }
                    Box(modifier = Modifier.fillMaxSize()) {
                        GeoSnapAppRoot()
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                    InAppUpdateHost(snackbarHostState = snackbarHostState)
                }
            }
        }
    }
}