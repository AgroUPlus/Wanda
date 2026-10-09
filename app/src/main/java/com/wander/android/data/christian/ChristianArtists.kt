package com.wander.android.data.christian

import java.text.Normalizer

/** A language the Christian shelf can be narrowed to. [code] is what a saved layout stores. */
enum class ChristianLanguage(val code: String) {
    PORTUGUESE("pt"),
    ENGLISH("en"),
    FRENCH("fr");

    companion object {
        fun fromCode(code: String): ChristianLanguage? = entries.firstOrNull { it.code == code }
    }
}

internal data class ChristianArtist(val name: String, val language: ChristianLanguage)

/**
 * The artists the Christian shelf draws from, by the language they sing in.
 *
 * Curated by hand; a language with no artists here is not offered, so French stays out until it has
 * a list. Songs are found by searching the artist's name on the connected sources, and only songs
 * credited to that artist are kept.
 */
internal object ChristianArtists {

    val all: List<ChristianArtist> =
        listOf(
            "Gabriela Rocha", "Isadora Pompeio", "Fernandinho", "Aline Barros",
            "Pregador Luo", "Thalles Roberto", "Morada", "Anderson Freire", "Julia Vitória"
        ).map { ChristianArtist(it, ChristianLanguage.PORTUGUESE) } +
            listOf(
                "Hillsong Worship", "Bethel Music", "Elevation Worship", "Maverick City Music",
                "Phil Wickham", "Chris Tomlin", "Lauren Daigle", "for KING & COUNTRY",
                "Casting Crowns", "Housefires"
            ).map { ChristianArtist(it, ChristianLanguage.ENGLISH) }

    /** Languages that have at least one artist, in display order. */
    val languages: List<ChristianLanguage> = ChristianLanguage.entries.filter { language ->
        all.any { it.language == language }
    }

    /**
     * Whether a track's credit names [artist]. Accents and case are ignored, and a credit with
     * several artists ("Fernandinho, Anderson Freire") matches each of them. Whole words only, so
     * "Morada" does not match "Moradas Vivas".
     */
    fun credits(trackArtist: String, artist: String): Boolean {
        val credit = " ${plain(trackArtist)} "
        return credit.contains(" ${plain(artist)} ")
    }

    private fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
