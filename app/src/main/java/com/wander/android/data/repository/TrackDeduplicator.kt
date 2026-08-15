package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Collapses the same recording appearing from several backends down to the best-ranked one, so a
 * song held locally does not also show up as a YouTube Music row.
 *
 * Matching is deliberately conservative. Hiding a track the user wanted is far worse than showing
 * a duplicate, so anything short of strong evidence leaves both rows in place.
 */
object TrackDeduplicator {

    /**
     * Two transfers of one recording run to nearly the same length; different arrangements do not.
     * Three seconds absorbs encoder and tagging drift without merging distinct takes.
     */
    private const val DURATION_TOLERANCE_MS = 3_000L

    /**
     * Editorial noise describing the *release* rather than the performance. "Song" and
     * "Song (Remastered 2011)" are the same recording, so this is removed before matching.
     */
    private val NOISE = Regex(
        """\b(remaster(ed)?(\s+\d{4})?|\d{4}\s+remaster|official\s+(music\s+)?(video|audio)""" +
            """|lyrics?(\s+video)?|album\s+version|single\s+version|original\s+mix""" +
            """|explicit|clean|hd|hq|visualizer|mv)\b"""
    )

    /**
     * Markers of a genuinely different performance. Compared as a set, so two tracks only merge
     * when they carry the *same* markers — folding a live take into the studio cut would silently
     * hide a version the user deliberately owns, which is the one failure this must never make.
     */
    private val VARIANT = Regex(
        """\b(live|acoustic|unplugged|remix|rmx|demo|instrumental|karaoke|reprise|edit""" +
            """|mix|version|cover|session|extended|club|dub|slowed|sped\s*up|orchestral""" +
            """|piano|deluxe|bonus)\b"""
    )

    private val FEATURED = Regex("""\b(feat|ft|featuring|with)\b.*""")
    private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}\s]""")
    private val WHITESPACE = Regex("""\s+""")
    private val DIACRITICS = Regex("""\p{Mn}+""")

    /** Everything except duration, which needs a tolerance comparison rather than equality. */
    internal data class RecordingKey(
        val artist: String,
        val title: String,
        val variants: Set<String>
    )

    /**
     * Keeps one track per distinct recording, preferring the source with the lowest
     * [com.wander.android.data.model.SourceType.priority]. Input order is otherwise preserved.
     */
    fun deduplicate(tracks: List<UnifiedTrack>): List<UnifiedTrack> {
        if (tracks.size < 2) return tracks

        // Group by the exact-match part of the key first, so the duration comparison — the only
        // part needing a tolerance — runs within small buckets instead of across every pair.
        val buckets = LinkedHashMap<RecordingKey, MutableList<UnifiedTrack>>()
        val unmergeable = mutableListOf<UnifiedTrack>()

        for (track in tracks) {
            // An unknown duration is not evidence of a match, so it never merges with anything.
            if (track.durationMs <= 0L) {
                unmergeable += track
                continue
            }
            buckets.getOrPut(keyOf(track)) { mutableListOf() } += track
        }

        val originalOrder = tracks.withIndex().associate { (index, track) -> track.id to index }
        return (buckets.values.flatMap(::collapseByDuration) + unmergeable)
            .sortedBy { originalOrder[it.id] }
    }

    /** Within one bucket, merge tracks whose lengths agree and keep the best-ranked of each. */
    private fun collapseByDuration(bucket: List<UnifiedTrack>): List<UnifiedTrack> {
        if (bucket.size == 1) return bucket
        val winners = mutableListOf<UnifiedTrack>()
        for (track in bucket) {
            val match = winners.indexOfFirst {
                abs(it.durationMs - track.durationMs) <= DURATION_TOLERANCE_MS
            }
            when {
                match < 0 -> winners += track
                track.source.priority < winners[match].source.priority -> winners[match] = track
            }
        }
        return winners
    }

    /**
     * A stable identity for the *recording* rather than the row.
     *
     * The same song from Navidrome and from YouTube Music produces the same string, so a list can
     * key on it: when a late-arriving Navidrome copy displaces the YouTube one, the row is the same
     * item moving rather than one item vanishing and another appearing in its place.
     *
     * Coarser than [deduplicate], which also compares durations — two distinct takes of one title
     * can share this key, so callers building list keys must make repeats unique.
     */
    fun recordingKey(track: UnifiedTrack): String = with(keyOf(track)) {
        "$artist|$title|${variants.sorted().joinToString(",")}"
    }

    internal fun keyOf(track: UnifiedTrack) = RecordingKey(
        artist = normalizeArtist(track.artist),
        title = normalizeTitle(track.title),
        variants = variantsOf(track.title)
    )

    /** Primary artist only — "A feat. B" and "A" are the same performer for matching purposes. */
    internal fun normalizeArtist(artist: String): String =
        fold(artist)
            .replace(FEATURED, " ")
            .substringBefore(" & ")
            .substringBefore(", ")
            .let { WHITESPACE.replace(it, " ").trim() }

    /** Song title with featured-artist clauses, release noise and variant markers removed. */
    internal fun normalizeTitle(title: String): String =
        fold(title)
            .replace(FEATURED, " ")
            .replace(NOISE, " ")
            .replace(VARIANT, " ")
            .let { WHITESPACE.replace(it, " ").trim() }

    /**
     * The variant markers present in a title. Read from the folded text after noise removal, so
     * "(Album Version)" does not register as a variant while "(Live)" does.
     */
    internal fun variantsOf(title: String): Set<String> =
        VARIANT.findAll(fold(title).replace(NOISE, " "))
            .map { WHITESPACE.replace(it.value, " ") }
            .toSet()

    /**
     * Lowercase, strip diacritics and punctuation. Punctuation removal is what lets "(Live)",
     * "- Live" and "[live]" all reduce to the same token.
     */
    private fun fold(value: String): String =
        DIACRITICS
            .replace(Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
            .let { NON_ALPHANUMERIC.replace(it, " ") }
            .let { WHITESPACE.replace(it, " ") }
            .trim()
}
