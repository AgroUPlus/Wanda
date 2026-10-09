package com.wander.android.ui.screens.home

import com.wander.android.ui.screens.home.layout.HomeLayoutApplier
import com.wander.android.ui.screens.home.layout.ShelfConfig
import com.wander.android.ui.screens.home.layout.ShelfConfigCodec
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
    fun `hidden shelves are dropped unless the editor asks for them`() {
        val configs = listOf(ShelfConfig("a"), ShelfConfig("b", enabled = false), ShelfConfig("c"))
        assertEquals(listOf("a", "c"), HomeLayoutApplier.apply(sections, configs).map { it.id })
        assertEquals(
            listOf("a", "b", "c"),
            HomeLayoutApplier.apply(sections, configs, includeHidden = true).map { it.id }
        )
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
}
