package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.ArtistCredits
import com.wander.android.data.repository.ArtistIdentity
import com.wander.android.data.repository.artistIdFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One stored credit string, several people: "A, B & C" is nobody's page. */
class ArtistCreditsTest {

    @Test
    fun `splits the separators credits actually use`() {
        assertEquals(listOf("A", "B", "C"), ArtistCredits.split("A, B & C"))
        assertEquals(listOf("A", "B"), ArtistCredits.split("A; B"))
        assertEquals(listOf("A", "B"), ArtistCredits.split("A feat. B"))
        assertEquals(listOf("A", "B", "C"), ArtistCredits.split("A (feat. B & C)"))
        assertEquals(listOf("A", "B"), ArtistCredits.split("A x B"))
        assertEquals(listOf("YOASOBI", "Ado"), ArtistCredits.split("YOASOBI、Ado"))
    }

    @Test
    fun `leaves single names whole`() {
        assertEquals(listOf("Soft Cell"), ArtistCredits.split("Soft Cell"))
        assertEquals(listOf("AC/DC"), ArtistCredits.split("AC/DC"))
        assertEquals(listOf("Florence and the Machine"), ArtistCredits.split("Florence and the Machine"))
        assertEquals(listOf("Xavier"), ArtistCredits.split("Xavier"))
    }

    /** "&" and ", The" are as often inside one act's name, so the whole credit stays on offer. */
    @Test
    fun `offers the whole credit when the split may have broken a name`() {
        assertEquals(listOf("Simon & Garfunkel", "Simon", "Garfunkel"), ArtistCredits.choices("Simon & Garfunkel"))
        assertEquals("Tyler, The Creator", ArtistCredits.choices("Tyler, The Creator").first())
        assertEquals(listOf("A", "B"), ArtistCredits.choices("A, B"))
    }

    /** The backend id is for the credit as filed; a name picked out of it would open the wrong page. */
    @Test
    fun `keeps the artist id only for the whole credit`() {
        val track = UnifiedTrack(id = "nd:1", source = SourceType.NAVIDROME, title = "T", artist = "A, B", artistId = "nd:ab")
        assertNull(track.artistIdFor("A"))
        assertEquals("nd:ab", track.artistIdFor("A, B"))
    }

    /** The featured artist's page used to miss the song; only the lead's matched. */
    @Test
    fun `featured and semicolon credits match each artist`() {
        assertTrue(ArtistIdentity.creditsMatch("A feat. B", "B"))
        assertTrue(ArtistIdentity.creditsMatch("A; B", "B"))
        assertTrue(ArtistIdentity.creditsMatch("A/B", "B"))
    }
}
