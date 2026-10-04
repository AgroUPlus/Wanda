package com.wander.android.data.sources.agro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A blend is a shared playlist with one more field — which an older server does not have and would
 * refuse to be asked for.
 */
class BlendParsingTest {

    @Test
    fun isBlendIsAskedForOnlyWhenTheServerSaysItKnowsIt() {
        assertFalse(AgroSharedPlaylistParsing.playlistFields(serverHasBlends = false).contains("isBlend"))
        assertTrue(AgroSharedPlaylistParsing.playlistFields(serverHasBlends = true).endsWith(" isBlend"))
    }

    @Test
    fun aBlendReadsAsOneAndAnOlderServersPlaylistReadsAsNot() {
        fun parse(extra: String) = AgroSharedPlaylistParsing.playlist(
            Json.parseToJsonElement(
                """{"id":"p","userId":"alpha","title":"Us","visibility":"PRIVATE","editAccess":"OFF",
                   "myRole":"VIEWER","revision":3,"isFollowing":true,"items":[]$extra}"""
            ).jsonObject
        )
        assertTrue(parse(""","isBlend":true""").isBlend)
        assertFalse(parse("").isBlend)
    }

    @Test
    fun aBlendRecipeDefaultsToWhatTheSheetOffersFirst() {
        val recipe = BlendRecipe()
        assertTrue(recipe.size in BlendRecipe.SIZES)
        assertEquals(BlendRefresh.WEEKLY, recipe.refresh)
        assertEquals(BlendWindow.SIX_MONTHS, recipe.window)
    }
}
