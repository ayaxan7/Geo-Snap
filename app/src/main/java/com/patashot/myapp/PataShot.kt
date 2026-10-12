package com.patashot.myapp

import android.app.Application
import android.content.Context
import com.patashot.myapp.core.LocaleHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PataShot : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        // DataStore cannot be read here (applicationContext is still null), so the
        // Application follows the system default; the stored language is picked up
        // in onCreate and by the Activity in its own attachBaseContext.
        super.attachBaseContext(LocaleHelper.wrapApplicationContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { LocaleHelper.refreshLanguage(applicationContext) }
    }
}