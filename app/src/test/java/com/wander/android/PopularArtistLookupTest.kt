package com.wander.android

import com.wander.android.data.repository.primaryArtist
import org.junit.Assert.assertEquals
import org.junit.Test

/** [primaryArtist]: the text a library row must contain to be worth keying for the popular shelf. */
class PopularArtistLookupTest {

    @Test
    fun `a lone artist is kept as typed`() {
        assertEquals("Radiohead", primaryArtist("Radiohead"))
    }

    @Test
    fun `featured and joint credits are cut to the first name`() {
        assertEquals("Daft Punk", primaryArtist("Daft Punk feat. Pharrell Williams"))
        assertEquals("Simon", primaryArtist("Simon & Garfunkel"))
        assertEquals("Silk", primaryArtist("Silk x Sonic"))
        assertEquals("Calvin Harris", primaryArtist("Calvin Harris, Dua Lipa"))
    }

    @Test
    fun `a name that merely contains a separator word is left whole`() {
        assertEquals("Malcolm X", primaryArtist("Malcolm X"))
        assertEquals("Withered Hand", primaryArtist("Withered Hand"))
    }
}
