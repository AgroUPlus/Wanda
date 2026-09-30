package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack
import java.text.Normalizer
import java.util.Locale

/**
 * Text normalization, noise removal, and variant extraction for recording matching.
 */
internal object TrackNormalizer {

    private val NOISE_TERMS = listOf(
        """remaster(ed)?(\s+\d{4})?""",
        """\d{4}\s+remaster""",
        """official\s+(music\s+)?(video|audio)""",
        """lyrics?(\s+video)?""",
        """album\s+version""",
        """single\s+version""",
        """original\s+mix""",
        "explicit",
        "clean",
        "hd",
        "hq",
        "visualizer",
        "mv"
    )

    private val NOISE = Regex("""\b(${NOISE_TERMS.joinToString("|")})\b""")

    private val VARIANT_TERMS = listOf(
        "live",
        "acoustic",
        "unplugged",
        "remix",
        "rmx",
        "demo",
        "instrumental",
        "karaoke",
        "reprise",
        "edit",
        "mix",
        "version",
        "cover",
        "session",
        "extended",
        "club",
        "dub",
        "slowed",
        """sped\s*up""",
        "orchestral",
        "piano",
        "deluxe",
        "bonus"
    )

    private val VARIANT = Regex("""\b(${VARIANT_TERMS.joinToString("|")})\b""")

    private val FEATURED = Regex("""\b(feat|ft|featuring|with)\b.*""")
    private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}\s]""")
    private val WHITESPACE = Regex("""\s+""")
    private val DIACRITICS = Regex("""\p{Mn}+""")

    /** Everything except duration, which needs a tolerance comparison rather than equality. */
    data class RecordingKey(
        val artist: String,
        val title: String,
        val variants: Set<String>
    )

    fun keyOf(track: UnifiedTrack) = RecordingKey(
        artist = normalizeArtist(track.artist),
        title = normalizeTitle(track.title),
        variants = variantsOf(track.title)
    )

    /** Primary artist only — "A feat. B" and "A" are the same performer for matching purposes. */
    fun normalizeArtist(artist: String): String =
        fold(artist)
            .replace(FEATURED, " ")
            .substringBefore(" & ")
            .substringBefore(", ")
            .let { WHITESPACE.replace(it, " ").trim() }

    /** Song title with featured-artist clauses, release noise and variant markers removed. */
    fun normalizeTitle(title: String): String =
        fold(title)
            .replace(FEATURED, " ")
            .replace(NOISE, " ")
            .replace(VARIANT, " ")
            .let { WHITESPACE.replace(it, " ").trim() }

    /**
     * The variant markers present in a title. Read from the folded text after noise removal, so
     * "(Album Version)" does not register as a variant while "(Live)" does.
     */
    fun variantsOf(title: String): Set<String> =
        VARIANT.findAll(fold(title).replace(NOISE, " "))
            .map { WHITESPACE.replace(it.value, " ") }
            .toSet()

    /**
     * Lowercase, strip diacritics and punctuation. Punctuation removal is what lets "(Live)",
     * "- Live" and "[live]" all reduce to the same token.
     */
    fun fold(value: String): String =
        DIACRITICS
            .replace(Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
            .let { NON_ALPHANUMERIC.replace(it, " ") }
            .let { WHITESPACE.replace(it, " ") }
            .trim()
}
