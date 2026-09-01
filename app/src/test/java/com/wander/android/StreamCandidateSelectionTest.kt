package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.RecordingLinkSet
import com.wander.android.data.repository.SplitSet
import com.wander.android.data.repository.selectSameRecording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What playback is allowed to substitute.
 *
 * The bug this pins: the stream resolver matched on title alone and took the first row, so every
 * track named "Memories" played one particular "Memories" — beabadoobee's row resolved to the
 * Until Then theme because that file happened to be on the device. A title is not an identity.
 */
class StreamCandidateSelectionTest {

    private fun track(
        id: String,
        title: String,
        artist: String,
        durationMs: Long = 210_000,
        source: SourceType = SourceType.YTMUSIC
    ) = UnifiedTrack(
        id = id,
        source = source,
        title = title,
        artist = artist,
        album = null,
        durationMs = durationMs
    )

    @Test
    fun `a shared title with a different artist is not a substitute`() {
        val wanted = track("yt:1", "Memories", "beabadoobee")
        val onDevice = track("local:1", "Memories", "Until Then", source = SourceType.LOCAL)

        assertNull(selectSameRecording(wanted, listOf(onDevice)))
    }

    @Test
    fun `the same recording from another backend still is`() {
        val wanted = track("yt:1", "Memories", "beabadoobee")
        val onDevice = track("local:1", "Memories", "beabadoobee", source = SourceType.LOCAL)

        assertEquals("local:1", selectSameRecording(wanted, listOf(onDevice))?.id)
    }

    /** Candidate order is the caller's preference — a downloaded copy first — and is kept. */
    @Test
    fun `the first passing candidate wins, wrong ones ahead of it are skipped`() {
        val wanted = track("yt:1", "Memories", "beabadoobee")
        val candidates = listOf(
            track("local:1", "Memories", "Until Then", source = SourceType.LOCAL),
            track("local:2", "Memories", "Maroon 5", source = SourceType.LOCAL),
            track("local:3", "Memories", "beabadoobee", source = SourceType.LOCAL)
        )

        assertEquals("local:3", selectSameRecording(wanted, candidates)?.id)
    }

    /** A length the tagger never filled is not evidence, so it cannot carry a substitution. */
    @Test
    fun `an untagged length no longer substitutes on its own`() {
        val wanted = track("yt:1", "Memories", "beabadoobee")
        val onDevice = track("local:1", "Memories", "beabadoobee", durationMs = 0, source = SourceType.LOCAL)

        assertNull(selectSameRecording(wanted, listOf(onDevice)))
    }

    /** …unless the fingerprinter says it is the same audio, which outranks the missing tag. */
    @Test
    fun `a fingerprint link substitutes where the tags cannot`() {
        val wanted = track("yt:1", "Memories (Official Video) [HQ]", "beabadoobee Topic")
        val onDevice = track("local:1", "Memories", "beabadoobee", durationMs = 0, source = SourceType.LOCAL)

        val links = RecordingLinkSet.of(listOf("yt:1" to "local:1"))
        assertEquals("local:1", selectSameRecording(wanted, listOf(onDevice), links = links)?.id)
    }

    /** A pin beats the audio, here as everywhere else. */
    @Test
    fun `a split refuses a substitution a link would have made`() {
        val wanted = track("yt:1", "Memories", "beabadoobee")
        val onDevice = track("local:1", "Memories", "beabadoobee", source = SourceType.LOCAL)

        val splits = SplitSet.of(listOf("yt:1" to "local:1"))
        val links = RecordingLinkSet.of(listOf("yt:1" to "local:1"))
        assertNull(selectSameRecording(wanted, listOf(onDevice), splits, links))
    }

    /** Resolving a track that is also in the candidate list must not answer with itself. */
    @Test
    fun `a track is not its own fallback`() {
        val wanted = track("local:1", "Memories", "beabadoobee", source = SourceType.LOCAL)

        assertNull(selectSameRecording(wanted, listOf(wanted)))
    }
}
