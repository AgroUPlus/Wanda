package com.wander.android.data.sources.agro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a friend's client does with a session it was handed.
 *
 * The interesting cases are all failures: the sealed feed is only an improvement on the placeholder
 * if a copy that will not open is visibly different from one that was never sealed.
 */
class PresenceUnsealingTest {

    private fun placeholder(sealed: String? = "ciphertext") = AgroFriendNowPlaying(
        username = "alpha",
        trackUri = "encrypted",
        trackTitle = "Private Session",
        artistName = "",
        albumName = null,
        artworkUrl = null,
        positionMs = 0L,
        isPlaying = true,
        updatedAt = "2026-09-04T00:00:00Z",
        encryptedPresence = sealed
    )

    @Test
    fun anOpenedEnvelopeReplacesThePlaceholder() {
        val opened = placeholder().withSealedMetadata(
            """{"trackUri":"ytm:abc","trackTitle":"Kid A","artistName":"Radiohead",""" +
                """"albumName":"Kid A","artworkUrl":"https://example.invalid/a.jpg"}"""
        )

        assertEquals("Kid A", opened.trackTitle)
        assertEquals("Radiohead", opened.artistName)
        assertEquals("ytm:abc", opened.trackUri)
        assertEquals("https://example.invalid/a.jpg", opened.artworkUrl)
        assertFalse(opened.isLocked)
    }

    @Test
    fun anEnvelopeMayLeaveOptionalFieldsOut() {
        // A track with no album and no artwork seals neither. The absent fields must not blank the
        // ones already there, which is what a plain `str(...)` assignment would do.
        val opened = placeholder().withSealedMetadata(
            """{"trackUri":"local:1","trackTitle":"Untitled","artistName":"Unknown"}"""
        )

        assertEquals("Untitled", opened.trackTitle)
        assertEquals(null, opened.albumName)
        assertFalse(opened.isLocked)
    }

    @Test
    fun aCopyThatWillNotOpenIsLockedRatherThanShownAsThePlaceholder() {
        // Sealed to a device key this install no longer holds — a reinstall, or a restore. The
        // friend is playing something and this device cannot read what; saying "Private Session"
        // as though that were the track name would be inventing an answer.
        val locked = placeholder().withSealedMetadata(null)

        assertTrue(locked.isLocked)
    }

    @Test
    fun aMalformedEnvelopeIsLockedRatherThanPartiallyApplied() {
        val locked = placeholder().withSealedMetadata("not json at all")

        assertTrue(locked.isLocked)
        assertEquals("Private Session", locked.trackTitle)
    }

    @Test
    fun anUnsealedSessionIsLeftExactlyAsItArrived() {
        // No ciphertext at all: an ordinary session from a friend with no vault key. It must not be
        // marked locked — nothing failed.
        val ordinary = AgroFriendNowPlaying(
            username = "beta",
            trackUri = "ytm:xyz",
            trackTitle = "In Rainbows",
            artistName = "Radiohead",
            albumName = null,
            artworkUrl = null,
            positionMs = 0L,
            isPlaying = true,
            updatedAt = "2026-09-04T00:00:00Z",
            encryptedPresence = null
        )

        assertEquals("In Rainbows", ordinary.trackTitle)
        assertFalse(ordinary.isLocked)
    }

    @Test
    fun theContentHashTravelsInsideTheEnvelope() {
        // Suppressed from the plaintext columns, so this is the only place a listener can get it.
        // Without it, following along can name the track but never find the file.
        val opened = placeholder().withSealedMetadata(
            """{"trackUri":"local:1","trackTitle":"Kid A","artistName":"Radiohead",""" +
                """"contentHash":"abc123"}"""
        )

        assertEquals("abc123", opened.contentHash)
        assertFalse(opened.isLocked)
    }

    @Test
    fun anEnvelopeWithoutAHashDoesNotEraseOne() {
        val withHash = placeholder().copy(contentHash = "already-known")
        val opened = withHash.withSealedMetadata(
            """{"trackTitle":"Kid A","artistName":"Radiohead"}"""
        )

        assertEquals("already-known", opened.contentHash)
    }
}
