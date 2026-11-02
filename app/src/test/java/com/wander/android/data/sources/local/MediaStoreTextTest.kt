package com.wander.android.data.sources.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaStoreTextTest {

    @Test
    fun `media store placeholder for an untagged value reads as absent`() {
        assertNull("<unknown>".mediaStoreValue())
    }

    @Test
    fun `blank and null read as absent`() {
        assertNull(null.mediaStoreValue())
        assertNull("   ".mediaStoreValue())
    }

    @Test
    fun `a real tag is kept, trimmed`() {
        assertEquals("Radiohead", " Radiohead ".mediaStoreValue())
    }

    /** Only the exact placeholder is MediaStore's; a band can legitimately be called that. */
    @Test
    fun `a name merely containing the placeholder is kept`() {
        assertEquals("The <unknown> Band", "The <unknown> Band".mediaStoreValue())
    }
}
