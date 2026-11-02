package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchOutcomeTest {

    @Test
    fun `every source failing is a failed search`() {
        val outcome = SearchOutcome(
            askedSources = listOf(SourceType.NAVIDROME, SourceType.YTMUSIC),
            failedSources = listOf(SourceType.NAVIDROME, SourceType.YTMUSIC)
        )
        assertTrue(outcome.allFailed)
    }

    @Test
    fun `one source answering is not a failed search`() {
        val outcome = SearchOutcome(
            askedSources = listOf(SourceType.NAVIDROME, SourceType.YTMUSIC),
            failedSources = listOf(SourceType.YTMUSIC)
        )
        assertFalse(outcome.allFailed)
    }

    @Test
    fun `asking nobody is not a failure`() {
        assertFalse(SearchOutcome().allFailed)
    }
}
