package com.patashot.myapp.presentation.camera

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale

/** Short, upper-case English weekday names, indexed by Calendar.DAY_OF_WEEK (1 = Sunday). */
private val SHORT_WEEKDAYS_UPPER =
    arrayOf("", "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

/**
 * The date formatter for the location card, shared by the Compose card and the
 * burned-in renderer so both always show the same text.
 *
 * Weekdays are abbreviated and upper-case (MON, TUE, WED...) to save space: the
 * full-name pattern letters (EEEE) are swapped for the short ones (EEE), and the
 * short names are upper-cased through [DateFormatSymbols].
 */
internal fun createCardDateFormat(): SimpleDateFormat =
    SimpleDateFormat(CARD_DATE_PATTERN.replace("EEEE", "EEE"), Locale.ENGLISH).apply {
        dateFormatSymbols = DateFormatSymbols(Locale.ENGLISH).apply {
            shortWeekdays = SHORT_WEEKDAYS_UPPER
        }
    }