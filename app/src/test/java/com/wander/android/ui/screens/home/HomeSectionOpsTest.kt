package com.wander.android.ui.screens.home

import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionOpsTest {

    private fun track(id: String, title: String = id) =
        UnifiedTrack(id = id, source = SourceType.YTMUSIC, title = title, artist = "artist")

    @Test
    fun `a shelf holds each track once, in first-seen order`() {
        val built = shelf("s", "S", HomeSectionStyle.TRACK_CAROUSEL, listOf(track("a"), track("b"), track("a")))
        assertEquals(listOf("a", "b"), built.tracks.map { it.id })
    }

    @Test
    fun `feed shelves with the same id are folded in once`() {
        val feed = listOf(
            RecommendedShelf("ytm_mixed", "Mixed for you", listOf(track("a"))),
            RecommendedShelf("ytm_mixed", "Mixed for you", listOf(track("b")))
        )
        val result = emptyList<HomeSection>().withFeed(feed, seed = null, suggestions = emptyList(), becauseTitle = {
            it.title
        })
        assertEquals(listOf("ytm_mixed"), result.map { it.id })
    }

    @Test
    fun `a feed shelf with repeated tracks still fills to the carousel size`() {
        val tracks = listOf(track("a"), track("a")) + (1..CarouselSize).map { track("t$it") }
        val feed = listOf(RecommendedShelf("ytm_x", "X", tracks))
        val result = emptyList<HomeSection>().withFeed(feed, seed = null, suggestions = emptyList(), becauseTitle = {
            it.title
        })
        assertEquals(CarouselSize, result.single().tracks.size)
        assertEquals(result.single().tracks.size, result.single().tracks.map { it.id }.toSet().size)
    }
}
