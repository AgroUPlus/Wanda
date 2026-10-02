package com.wander.android

import com.wander.android.data.sources.navidrome.SubsonicShare
import com.wander.android.data.sources.navidrome.SubsonicSong
import com.wander.android.data.sources.navidrome.reusableShare
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavidromeShareReuseTest {

    private val now = Instant.parse("2026-10-02T12:00:00Z")

    private fun share(id: String, songs: List<String>, expires: String? = null) = SubsonicShare(
        id = id,
        url = "https://nd.example/share/$id",
        expires = expires,
        entry = songs.map { SubsonicSong(id = it, title = it) }
    )

    @Test
    fun `reuses an open share of exactly that song`() {
        val shares = listOf(
            share("both", listOf("s1", "s2")),
            share("gone", listOf("s1"), expires = "2026-10-01T12:00:00Z"),
            share("mine", listOf("s1"), expires = "2026-11-01T12:00:00+02:00")
        )
        assertEquals("mine", reusableShare(shares, "s1", now)?.id)
    }

    @Test
    fun `a share with no expiry stays open`() {
        assertEquals("forever", reusableShare(listOf(share("forever", listOf("s1"))), "s1", now)?.id)
    }

    @Test
    fun `nothing to reuse means a new share`() {
        assertNull(reusableShare(listOf(share("other", listOf("s2"))), "s1", now))
        assertNull(reusableShare(listOf(share("odd", listOf("s1"), expires = "soon")), "s1", now))
    }
}
