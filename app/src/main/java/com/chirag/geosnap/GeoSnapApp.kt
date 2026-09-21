package com.chirag.geosnap

import android.app.Application
import android.content.Context
import com.chirag.geosnap.core.LocaleHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GeoSnapApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyToContext(base))
    }
}