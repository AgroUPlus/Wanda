package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.TrackDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The key that has to mean the same thing in Kotlin and in Rust.
 *
 * The server counts plays under `norm.rs`'s normalisation and sends back titles, not ids — it has
 * no rows to point at. So the shelf can only find a local copy if the key computed here matches the
 * one computed there. `norm.rs` is a port of [TrackDeduplicator] and says so in its module docs;
 * these are the cases where a drift between the two would show up as a shelf that is silently,
 * inexplicably short.
 */
class PopularShelfMatchingTest {

    private fun track(title: String, artist: String, source: SourceType = SourceType.LOCAL) =
        UnifiedTrack(
            id = "${source.idPrefix}$title",
            source = source,
            title = title,
            artist = artist,
            album = null,
            durationMs = 240_000
        )

    /** A server entry and a local row that are the same recording must produce the same key. */
    @Test
    fun `release noise does not stop a local copy being found`() {
        assertEquals(
            TrackDeduplicator.recordingKey(track("All I Need", "Radiohead")),
            TrackDeduplicator.recordingKey(
                track("All I Need (Official Video) [HQ]", "Radiohead", SourceType.YTMUSIC)
            )
        )
    }

    /** Case and diacritics are folded on both sides, or half a library never matches. */
    @Test
    fun `case and accents do not stop a match`() {
        assertEquals(
            TrackDeduplicator.recordingKey(track("Déjà Vu", "Beyoncé")),
            TrackDeduplicator.recordingKey(track("deja vu", "beyonce", SourceType.NAVIDROME))
        )
    }

    /** A live take is a different performance, and the shelf must not offer one for the other. */
    @Test
    fun `a variant keeps its own key`() {
        assertNotEquals(
            TrackDeduplicator.recordingKey(track("All I Need", "Radiohead")),
            TrackDeduplicator.recordingKey(track("All I Need (Live)", "Radiohead"))
        )
    }

    /** Two different songs sharing nothing but a common title must not collide. */
    @Test
    fun `different songs do not share a key`() {
        assertNotEquals(
            TrackDeduplicator.recordingKey(track("Alive", "Pearl Jam")),
            TrackDeduplicator.recordingKey(track("Alive", "Empire of the Sun"))
        )
    }
}
