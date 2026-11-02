package com.wander.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastScrollLetterTest {

    @Test
    fun aNameIsFiledUnderItsUpperCaseInitial() {
        assertEquals("A", fastScrollLetter("abba"))
    }

    @Test
    fun leadingSpaceIsIgnored() {
        assertEquals("Z", fastScrollLetter("  zed"))
    }

    @Test
    fun aDigitIsItsOwnLetter() {
        assertEquals("2", fastScrollLetter("2Pac"))
    }

    @Test
    fun punctuationFilesUnderHash() {
        assertEquals("#", fastScrollLetter("(What's the Story)"))
    }

    @Test
    fun noNameHasNoLetter() {
        assertNull(fastScrollLetter(null))
        assertNull(fastScrollLetter("   "))
    }
}
