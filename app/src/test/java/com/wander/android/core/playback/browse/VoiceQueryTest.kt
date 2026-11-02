package com.wander.android.core.playback.browse

import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceQueryTest {

    @Test
    fun noWordsAtAllMeansAnything() {
        assertEquals(VoiceQuery.Anything, VoiceQuery.of(null, null, null, null))
        assertEquals(VoiceQuery.Anything, VoiceQuery.of("  ", null, null, null))
    }

    @Test
    fun plainWordsAreAText() {
        assertEquals(VoiceQuery.Text("one more time"), VoiceQuery.of(" one more time ", null, null, null))
    }

    @Test
    fun anAlbumFocusWithItsNameIsAnAlbum() {
        val query = VoiceQuery.of("discovery", MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE, "Daft Punk", "Discovery")
        assertEquals(VoiceQuery.Album("Discovery"), query)
    }

    @Test
    fun anArtistFocusWithItsNameIsAnArtist() {
        val query = VoiceQuery.of("daft punk", MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE, "Daft Punk", null)
        assertEquals(VoiceQuery.Artist("Daft Punk"), query)
    }

    @Test
    fun aFocusWithoutItsNameFallsBackToTheWords() {
        val query = VoiceQuery.of("discovery", MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE, null, " ")
        assertEquals(VoiceQuery.Text("discovery"), query)
    }
}
