package com.wander.android.core.i18n

import com.wander.android.BuildConfig
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
 * Read from [BuildConfig.TRANSLATED_LANGUAGES], which the build derives from the `values-xx`
 * directories holding a translated `strings.xml` — so a language appears here the moment Crowdin's
 * translations land and never before. Not `assets.locales`: that lists every locale any dependency
 * ships a resource for, and filled the picker with languages Wanda has no strings in.
 */
fun supportedAppLocales(): List<AppLocale> {
    val shipped = BuildConfig.TRANSLATED_LANGUAGES
        .map { Locale.forLanguageTag(it) }
        .filter { it.language.isNotBlank() }
        .distinctBy { it.language }
        .map { it.toAppLocale() }
        .sortedBy { it.endonym.lowercase(Locale.ROOT) }

    // English is always present — it is the untranslated source — so it cannot be what decides
    // whether the picker is worth showing. It still belongs *in* the list once the picker shows,
    // otherwise someone whose phone is set to another language could never force English back.
    val translated = shipped.filterNot { it.locale.language == Locale.ENGLISH.language }
    return if (translated.isEmpty()) emptyList() else listOf(systemDefaultLocale()) + shipped
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
