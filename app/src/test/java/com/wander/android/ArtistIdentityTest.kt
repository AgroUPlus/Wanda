package com.wander.android

import com.wander.android.data.repository.ArtistIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Two artists can share a name. Room cannot tell them apart, so this has to.
 */
class ArtistIdentityTest {

    private data class Song(val title: String, val artistId: String?)

    /** The reported bug: a Japanese singer "misa" showing songs by an unrelated "MISA". */
    @Test
    fun `drops items credited to a different artist id`() {
        val items = listOf(
            Song("Hers", artistId = "ytm:UC_misa"),
            Song("Not hers", artistId = "ytm:UC_MISA_other"),
            Song("Also hers", artistId = "ytm:UC_misa")
        )

        val kept = ArtistIdentity.sameArtist(items, pageArtistId = "ytm:UC_misa") { it.artistId }

        assertEquals(listOf("Hers", "Also hers"), kept.map { it.title })
    }

    /**
     * An unknown id is not evidence of anything.
     *
     * Local files and Navidrome rarely carry a backend artist id, and dropping them would empty
     * the artist page of exactly the music the user owns.
     */
    @Test
    fun `keeps items with no artist id`() {
        val items = listOf(
            Song("From my server", artistId = null),
            Song("From YouTube", artistId = "ytm:UC_misa"),
            Song("Someone else", artistId = "ytm:UC_other")
        )

        val kept = ArtistIdentity.sameArtist(items, pageArtistId = "ytm:UC_misa") { it.artistId }

        assertEquals(listOf("From my server", "From YouTube"), kept.map { it.title })
    }

    /** Before the backend page loads there is no id, and nothing may be filtered on a guess. */
    @Test
    fun `keeps everything when the page has no id yet`() {
        val items = listOf(Song("A", "ytm:1"), Song("B", "ytm:2"), Song("C", null))

        assertEquals(items, ArtistIdentity.sameArtist(items, pageArtistId = null) { it.artistId })
        assertEquals(items, ArtistIdentity.sameArtist(items, pageArtistId = "") { it.artistId })
    }
}
