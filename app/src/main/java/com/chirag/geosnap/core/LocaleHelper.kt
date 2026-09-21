package com.chirag.geosnap.core

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.chirag.geosnap.data.datastore.SELECTED_LANGUAGE_KEY
import com.chirag.geosnap.di.geoSnapDataStore
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Applies the persisted app language to any base context at `attachBaseContext`
 * time. Both the Activity (Compose UI) and the Application (Hilt
 * `@ApplicationContext`, used by the burn renderer) go through here so all
 * resources resolve against the chosen language.
 */
object LocaleHelper {

    val SUPPORTED_LANGUAGES: Set<String> = setOf("en", "hi", "te", "ta", "kn")
    private const val DEFAULT_LANGUAGE = "en"

    @Volatile
    var activeLanguageCode: String? = null
        private set

    fun localeFor(code: String?): Locale =
        Locale(code?.takeIf { it in SUPPORTED_LANGUAGES } ?: DEFAULT_LANGUAGE)

    fun applyToContext(base: Context): Context {
        val code = storedLanguageCode(base)
        activeLanguageCode = code
        Locale.setDefault(localeFor(code))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base.createConfigurationContext(
                Configuration(base.resources.configuration).apply {
                    setLocales(android.os.LocaleList(localeFor(code)))
                }
            )
        }
        @Suppress("DEPRECATION")
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply {
                setLocale(localeFor(code))
            }
        )
    }

    private fun storedLanguageCode(base: Context): String =
        runBlocking { base.geoSnapDataStore.data.first()[SELECTED_LANGUAGE_KEY] }
            ?: defaultFromSystem()

    private fun defaultFromSystem(): String {
        val system = Locale.getDefault().language.lowercase()
        return if (system in SUPPORTED_LANGUAGES) system else DEFAULT_LANGUAGE
    }
}