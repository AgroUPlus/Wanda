package com.wander.android.ui.screens.home

import com.wander.android.ui.screens.home.layout.HomeLayoutApplier
import com.wander.android.ui.screens.home.layout.ShelfConfig
import com.wander.android.ui.screens.home.layout.ShelfConfigCodec
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeLayoutApplierTest {

    private fun section(id: String, style: HomeSectionStyle = HomeSectionStyle.TRACK_CAROUSEL) =
        HomeSection(id = id, title = id, style = style)

    private val sections = listOf(section("a"), section("b"), section("c"))

    @Test
    fun `no configs leaves home untouched`() {
        assertSame(sections, HomeLayoutApplier.apply(sections, emptyList()))
    }

    @Test
    fun `configured order wins and unconfigured shelves go last`() {
        val result = HomeLayoutApplier.apply(sections, listOf(ShelfConfig("c"), ShelfConfig("a")))
        assertEquals(listOf("c", "a", "b"), result.map { it.id })
    }

    @Test
    fun `removed shelves are dropped`() {
        val configs = listOf(ShelfConfig("a"), ShelfConfig("b", enabled = false), ShelfConfig("c"))
        assertEquals(listOf("a", "c"), HomeLayoutApplier.apply(sections, configs).map { it.id })
    }

    @Test
    fun `style override applies to track shelves`() {
        val configs = listOf(ShelfConfig("a", style = HomeSectionStyle.LARGE_GRID))
        assertEquals(HomeSectionStyle.LARGE_GRID, HomeLayoutApplier.apply(sections, configs).first().style)
    }

    @Test
    fun `configs for shelves that are gone are ignored`() {
        val result = HomeLayoutApplier.apply(sections, listOf(ShelfConfig("zzz"), ShelfConfig("b")))
        assertEquals(listOf("b", "a", "c"), result.map { it.id })
    }

    @Test
    fun `seed appends new shelves and keeps saved order`() {
        val seeded = HomeLayoutApplier.seed(sections, listOf(ShelfConfig("b"), ShelfConfig("a", enabled = false)))
        assertEquals(listOf("b", "a", "c"), seeded.map { it.id })
        assertEquals(false, seeded[1].enabled)
    }

    @Test
    fun `codec round trips and survives garbage`() {
        val configs = listOf(ShelfConfig("a", false, HomeSectionStyle.TRACK_LIST, 6), ShelfConfig("b"))
        assertEquals(configs, ShelfConfigCodec.decode(ShelfConfigCodec.encode(configs)))
        assertEquals(emptyList<ShelfConfig>(), ShelfConfigCodec.decode("{not json"))
        assertEquals(emptyList<ShelfConfig>(), ShelfConfigCodec.decode(null))
    }

    @Test
    fun `a style this build no longer has reads as the default`() {
        val raw = """[{"id":"liked","style":"FAVORITES_CAROUSEL","count":6},{"id":"discover"}]"""
        val decoded = ShelfConfigCodec.decode(raw)
        assertEquals(listOf("liked", "discover"), decoded.map { it.id })
        assertEquals(null, decoded[0].style)
        assertEquals(6, decoded[0].count)
    }

    @Test
    fun `a shelf limited to a source keeps only that source's songs`() {
        val mixed = HomeSection(
            "a", "a", HomeSectionStyle.TRACK_CAROUSEL,
            tracks = listOf(
                UnifiedTrack(id = "1", source = SourceType.YTMUSIC, title = "t1", artist = "x"),
                UnifiedTrack(id = "2", source = SourceType.NAVIDROME, title = "t2", artist = "x")
            )
        )
        val limited = HomeLayoutApplier.apply(listOf(mixed), listOf(ShelfConfig("a", sources = listOf("NAVIDROME"))))
        assertEquals(listOf("2"), limited.single().tracks.map { it.id })
        assertEquals(2, HomeLayoutApplier.apply(listOf(mixed), listOf(ShelfConfig("a"))).single().tracks.size)
    }
}
