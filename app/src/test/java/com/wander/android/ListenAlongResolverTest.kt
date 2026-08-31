package com.wander.android

import com.wander.android.data.repository.ResolvedFrom
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenAlongResolverTest {

    @Test
    fun `resolved from enum values cover all 5 tiers`() {
        val tiers = ResolvedFrom.entries.toList()
        assertEquals(5, tiers.size)
        assertEquals(
            listOf(
                ResolvedFrom.LOCAL_STORAGE,
                ResolvedFrom.NAVIDROME,
                ResolvedFrom.YOUTUBE_MUSIC,
                ResolvedFrom.P2P_DIRECT,
                ResolvedFrom.AGRO_RELAY
            ),
            tiers
        )
    }
}
