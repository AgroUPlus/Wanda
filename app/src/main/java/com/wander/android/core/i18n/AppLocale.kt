package com.wander.android.core.i18n

import android.content.Context
import java.util.Locale

/**
 * One language the app can be displayed in, as the picker shows it.
 *
 * [endonym] is deliberately the language's name *in that language* — someone looking for Japanese
 * is looking for 日本語, not for the word "Japanese" written in a script they may not read.
 */
data class AppLocale(
    val tag: String,
    val locale: Locale,
    val endonym: String,
    val flag: String
) {
    /** The system-default entry, which follows whatever the phone is set to. */
    val isSystemDefault: Boolean get() = tag.isEmpty()
}

/**
 * The languages this build actually ships.
 *
 * Read from the APK rather than from a hand-kept list: `assets.locales` reports exactly the
 * locales that have a `values-xx` directory, so a language appears in the picker the moment
 * Crowdin's translations land and never before. A hand-kept list would let someone add a
 * language here that has no strings behind it, and the picker would then offer a choice that
 * silently does nothing but fall back to English.
 */
fun supportedAppLocales(context: Context): List<AppLocale> {
    val shipped = context.resources.assets.locales
        .asSequence()
        .filter { it.isNotBlank() && !it.equals("en-US", ignoreCase = true) }
        .map { Locale.forLanguageTag(it.replace('_', '-')) }
        .filter { it.language.isNotBlank() }
        // "fr" and "fr-CA" are one entry in the picker; the region only narrows the same choice.
        .distinctBy { it.language }
        .map { it.toAppLocale() }
        .sortedBy { it.endonym.lowercase(Locale.ROOT) }
        .toList()

    // A bare "en" is always present — it is the untranslated source — but it is only worth
    // offering as a choice once there is something to choose between.
    return if (shipped.isEmpty()) emptyList() else listOf(systemDefaultLocale()) + shipped
}

internal fun systemDefaultLocale(): AppLocale = AppLocale(
    tag = "",
    locale = Locale.getDefault(),
    endonym = "",
    flag = "🌐"
)

private fun Locale.toAppLocale(): AppLocale = AppLocale(
    tag = toLanguageTag(),
    locale = this,
    endonym = getDisplayLanguage(this).replaceFirstChar { it.titlecase(this) },
    flag = flagFor(this)
)

/**
 * A flag for a language.
 *
 * Strictly speaking a language is not a country, and for a handful — Arabic, English, Spanish —
 * any flag chosen is a compromise. The mapping below picks the one a reader scanning the list is
 * most likely to recognise, which is what the flag is there for. Anything unmapped gets a globe
 * rather than a wrong guess.
 */
private fun flagFor(locale: Locale): String {
    val region = locale.country.takeIf { it.length == REGION_LENGTH }
        ?: LanguageRegions[locale.language]
        ?: return "🌐"
    return region.uppercase(Locale.ROOT)
        .map { Character.toChars(REGIONAL_INDICATOR_BASE + (it.code - 'A'.code)) }
        .joinToString("") { String(it) }
}

private const val REGION_LENGTH = 2

/** Offset from 'A' to the Unicode regional indicator symbols that render as flag emoji. */
private const val REGIONAL_INDICATOR_BASE = 0x1F1E6

private val LanguageRegions = mapOf(
    "ar" to "SA", "bg" to "BG", "bn" to "BD", "ca" to "ES", "cs" to "CZ", "da" to "DK",
    "de" to "DE", "el" to "GR", "en" to "GB", "es" to "ES", "et" to "EE", "fa" to "IR",
    "fi" to "FI", "fr" to "FR", "he" to "IL", "hi" to "IN", "hr" to "HR", "hu" to "HU",
    "id" to "ID", "it" to "IT", "ja" to "JP", "ko" to "KR", "lt" to "LT", "lv" to "LV",
    "ms" to "MY", "nb" to "NO", "nl" to "NL", "no" to "NO", "pl" to "PL", "pt" to "PT",
    "ro" to "RO", "ru" to "RU", "sk" to "SK", "sl" to "SI", "sr" to "RS", "sv" to "SE",
    "th" to "TH", "tr" to "TR", "uk" to "UA", "vi" to "VN", "zh" to "CN"
)
