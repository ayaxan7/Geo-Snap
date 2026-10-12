package com.patashot.myapp.core

import com.patashot.myapp.presentation.onboarding.LanguageViewModel
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleHelperTest {

    @Test
    fun `localeFor maps supported codes`() {
        assertEquals("en", LocaleHelper.localeFor("en").language)
        assertEquals("hi", LocaleHelper.localeFor("hi").language)
        assertEquals("te", LocaleHelper.localeFor("te").language)
        assertEquals("ta", LocaleHelper.localeFor("ta").language)
        assertEquals("kn", LocaleHelper.localeFor("kn").language)
    }

    @Test
    fun `localeFor falls back to English for unknown or null codes`() {
        assertEquals(Locale("en"), LocaleHelper.localeFor("fr"))
        assertEquals(Locale("en"), LocaleHelper.localeFor(null))
        assertEquals(Locale("en"), LocaleHelper.localeFor(""))
    }

    @Test
    fun `supported languages match the language picker`() {
        val pickerCodes = LanguageViewModel.supportedLanguages().map { it.code }.toSet()
        assertEquals(pickerCodes, LocaleHelper.SUPPORTED_LANGUAGES)
    }
}