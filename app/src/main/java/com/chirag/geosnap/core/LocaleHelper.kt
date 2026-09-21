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
 * Applies the persisted app language to a base context at `attachBaseContext`
 * time. The Activity (Compose UI) runs through [applyToContext], which can read
 * DataStore because the Application already exists by then. The Application
 * itself must use [wrapApplicationContext] instead: at its `attachBaseContext`
 * moment `applicationContext` is still null and the DataStore singleton would
 * NPE. Services that only have the app context (the burn renderer) resolve
 * strings via [localizedContext].
 */
object LocaleHelper {

    val SUPPORTED_LANGUAGES: Set<String> = setOf("en", "hi", "te", "ta", "kn")
    private const val DEFAULT_LANGUAGE = "en"

    @Volatile
    var activeLanguageCode: String? = null
        private set

    fun localeFor(code: String?): Locale =
        Locale(code?.takeIf { it in SUPPORTED_LANGUAGES } ?: DEFAULT_LANGUAGE)

    /** Application attach path: no DataStore access, follows the system default. */
    fun wrapApplicationContext(base: Context): Context {
        val locale = localeFor(defaultFromSystem())
        Locale.setDefault(locale)
        return wrapWithLocale(base, locale)
    }

    /** Activity attach path: reads the stored language and applies it. */
    fun applyToContext(base: Context): Context {
        val code = storedLanguageCode(base)
        activeLanguageCode = code
        Locale.setDefault(localeFor(code))
        return wrapWithLocale(base, localeFor(code))
    }

    /** A context whose resources resolve against the stored language. */
    fun localizedContext(context: Context): Context =
        wrapWithLocale(context, localeFor(activeLanguageCode ?: defaultFromSystem()))

    /** Refreshes the in-process language after the Application is fully created. */
    suspend fun refreshLanguage(context: Context) {
        val code = runCatching {
            context.geoSnapDataStore.data.first()[SELECTED_LANGUAGE_KEY]
        }.getOrNull()
        activeLanguageCode = code ?: defaultFromSystem()
        Locale.setDefault(localeFor(code ?: defaultFromSystem()))
    }

    private fun storedLanguageCode(base: Context): String =
        runBlocking {
            runCatching { base.geoSnapDataStore.data.first()[SELECTED_LANGUAGE_KEY] }
                .getOrNull()
        } ?: defaultFromSystem()

    private fun wrapWithLocale(base: Context, locale: Locale): Context {
        val configuration = Configuration(base.resources.configuration).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setLocales(android.os.LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                setLocale(locale)
            }
        }
        return base.createConfigurationContext(configuration)
    }

    private fun defaultFromSystem(): String {
        val system = Locale.getDefault().language.lowercase()
        return if (system in SUPPORTED_LANGUAGES) system else DEFAULT_LANGUAGE
    }
}