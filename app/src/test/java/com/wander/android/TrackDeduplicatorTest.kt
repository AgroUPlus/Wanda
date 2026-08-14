package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.TrackDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDeduplicatorTest {

    private fun track(
        source: SourceType,
        title: String = "Paranoid Android",
        artist: String = "Radiohead",
        durationMs: Long = 383_000L
    ) = UnifiedTrack(
        id = "${source.idPrefix}${title.hashCode()}-$durationMs",
        source = source,
        title = title,
        artist = artist,
        durationMs = durationMs
    )

    @Test
    fun `same song from three sources collapses to the local copy`() {
        val result = TrackDeduplicator.deduplicate(
            listOf(
                track(SourceType.YTMUSIC),
                track(SourceType.NAVIDROME),
                track(SourceType.LOCAL)
            )
        )

        assertEquals(1, result.size)
        assertEquals(SourceType.LOCAL, result.single().source)
    }

    @Test
    fun `navidrome beats youtube music when there is no local copy`() {
        val result = TrackDeduplicator.deduplicate(
            listOf(track(SourceType.YTMUSIC), track(SourceType.NAVIDROME))
        )

        assertEquals(1, result.size)
        assertEquals(SourceType.NAVIDROME, result.single().source)
    }

    @Test
    fun `a song only youtube music has is still shown`() {
        val result = TrackDeduplicator.deduplicate(
            listOf(
                track(SourceType.LOCAL, title = "Karma Police"),
                track(SourceType.YTMUSIC, title = "Paranoid Android")
            )
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `release noise does not prevent a match`() {
        val noisyTitles = listOf(
            "Paranoid Android (Remastered 2011)",
            "Paranoid Android - Official Video",
            "Paranoid Android (Album Version)",
            "Paranoid Android [Explicit]"
        )

        noisyTitles.forEach { noisy ->
            val result = TrackDeduplicator.deduplicate(
                listOf(track(SourceType.YTMUSIC, title = noisy), track(SourceType.LOCAL))
            )
            assertEquals("expected '$noisy' to match the plain title", 1, result.size)
            assertEquals(SourceType.LOCAL, result.single().source)
        }
    }

    @Test
    fun `featured artists do not prevent a match`() {
        val result = TrackDeduplicator.deduplicate(
            listOf(
                track(SourceType.YTMUSIC, title = "Paranoid Android (feat. Someone)"),
                track(SourceType.LOCAL)
            )
        )

        assertEquals(1, result.size)
    }

    /** The failure that matters most: never hide a distinct performance. */
    @Test
    fun `variant recordings stay separate`() {
        val variants = listOf("Live", "Acoustic", "Remix", "Demo", "Instrumental", "Extended")

        variants.forEach { marker ->
            val result = TrackDeduplicator.deduplicate(
                listOf(
                    track(SourceType.LOCAL),
                    track(SourceType.YTMUSIC, title = "Paranoid Android ($marker)")
                )
            )
            assertEquals("'$marker' must not merge into the studio cut", 2, result.size)
        }
    }

    @Test
    fun `two live versions from different sources still merge with each other`() {
        val result = TrackDeduplicator.deduplicate(
            listOf(
                track(SourceType.YTMUSIC, title = "Paranoid Android (Live)"),
                track(SourceType.NAVIDROME, title = "Paranoid Android - live")
            )
        )

        assertEquals(1, result.size)
        assertEquals(SourceType.NAVIDROME, result.single().source)
    }

    @Test
    fun `a large duration gap keeps tracks separate`() {
        val result = TrackDeduplicator.deduplicate(
            listOf(
                track(SourceType.LOCAL, durationMs = 383_000L),
                track(SourceType.YTMUSIC, durationMs = 413_000L)
            )
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `small duration drift still merges`() {
        val result = TrackDeduplicator.deduplicate(
            listOf(
                track(SourceType.YTMUSIC, durationMs = 383_000L),
                track(SourceType.LOCAL, durationMs = 385_000L)
            )
        )

        assertEquals(1, result.size)
        assertEquals(SourceType.LOCAL, result.single().source)
    }

    @Test
    fun `unknown duration never merges`() {
        val result = TrackDeduplicator.deduplicate(
            listOf(
                track(SourceType.LOCAL, durationMs = 383_000L),
                track(SourceType.INTERNET_ARCHIVE, durationMs = 0L)
            )
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `different artists never merge`() {
        val result = TrackDeduplicator.deduplicate(
            listOf(
                track(SourceType.LOCAL, artist = "Radiohead"),
                track(SourceType.YTMUSIC, artist = "Weird Al")
            )
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `diacritics and punctuation do not prevent a match`() {
        val result = TrackDeduplicator.deduplicate(
            listOf(
                track(SourceType.YTMUSIC, title = "Björk — Jóga", artist = "Björk"),
                track(SourceType.LOCAL, title = "Bjork - Joga", artist = "Bjork")
            )
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `original input order is preserved`() {
        val first = track(SourceType.LOCAL, title = "Airbag", durationMs = 284_000L)
        val second = track(SourceType.LOCAL, title = "Karma Police", durationMs = 261_000L)
        val third = track(SourceType.LOCAL, title = "No Surprises", durationMs = 229_000L)

        val result = TrackDeduplicator.deduplicate(listOf(first, second, third))

        assertEquals(listOf(first.id, second.id, third.id), result.map { it.id })
    }

    @Test
    fun `an empty or single-track list is returned untouched`() {
        assertTrue(TrackDeduplicator.deduplicate(emptyList()).isEmpty())
        assertEquals(1, TrackDeduplicator.deduplicate(listOf(track(SourceType.LOCAL))).size)
    }
}
