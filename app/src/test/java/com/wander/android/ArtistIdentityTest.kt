package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.ArtistIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Two artists can share a name. Room cannot tell them apart, so this has to.
 */
class ArtistIdentityTest {

    private data class Song(val title: String, val artistId: String?)

    private fun track(title: String, source: SourceType, artistId: String?) = UnifiedTrack(
        id = "$source:$title",
        source = source,
        title = title,
        artist = "Mili",
        artistId = artistId
    )

    /** The reported bug: a Japanese singer "misa" showing songs by an unrelated "MISA". */
    @Test
    fun `drops items credited to a different artist id`() {
        val items = listOf(
            Song("Hers", artistId = "ytm:UC_misa"),
            Song("Not hers", artistId = "ytm:UC_MISA_other"),
            Song("Also hers", artistId = "ytm:UC_misa")
        )

        val kept = ArtistIdentity.sameArtist(items, aliases = setOf("ytm:UC_misa")) { it.artistId }

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

        val kept = ArtistIdentity.sameArtist(items, aliases = setOf("ytm:UC_misa")) { it.artistId }

        assertEquals(listOf("From my server", "From YouTube"), kept.map { it.title })
    }

    /** Before the backend page loads there is no id, and nothing may be filtered on a guess. */
    @Test
    fun `keeps everything when the page has no id yet`() {
        val items = listOf(Song("A", "ytm:1"), Song("B", "ytm:2"), Song("C", null))

        assertEquals(items, ArtistIdentity.sameArtist(items, aliases = emptySet()) { it.artistId })
    }

    /**
     * The reported bug: an artist tagged inconsistently across releases on the *same* backend
     * (two Navidrome artist rows for one real person, a common ID3-tagging reality) collapsed to
     * a single track the moment either id got trusted — from a cache hit, or from opening the
     * page off one specific track. Nothing about that split crosses a backend boundary, so the
     * cross-source dedup bridge below never saw it; a same-backend id has to be trusted directly.
     */
    @Test
    fun `bridges a same-backend artist id split`() {
        val tracks = listOf(
            track("Rightfully", SourceType.NAVIDROME, artistId = "nd:mili-1"),
            track("Ken ga Mamoru", SourceType.NAVIDROME, artistId = "nd:mili-2"),
            track("Diorama", SourceType.NAVIDROME, artistId = "nd:mili-1")
        )

        val aliases = ArtistIdentity.aliasesOf(tracks, pageArtistId = "nd:mili-1")
        val kept = ArtistIdentity.sameArtist(tracks, aliases) { it.artistId }

        assertEquals(setOf("nd:mili-1", "nd:mili-2"), aliases)
        assertEquals(tracks.map { it.title }, kept.map { it.title })
    }

    /** The bridge is scoped to the trusted id's own backend — it must not launder a namesake in. */
    @Test
    fun `does not bridge a different backend's id`() {
        val tracks = listOf(
            track("Hers", SourceType.NAVIDROME, artistId = "nd:mili-1"),
            track("A stranger's song", SourceType.YTMUSIC, artistId = "ytm:someone_else")
        )

        val aliases = ArtistIdentity.aliasesOf(tracks, pageArtistId = "nd:mili-1")

        assertEquals(setOf("nd:mili-1"), aliases)
    }
}
