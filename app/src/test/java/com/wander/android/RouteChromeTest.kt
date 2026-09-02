package com.wander.android

import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.TopLevelDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each destination keeps at the bottom of the screen.
 *
 * The artist route is the reason this exists. Its pattern carries an optional argument
 * (`artist/{artist}?artistId={artistId}`) and the lookup stripped that tail off the current route
 * before comparing it against a set that still had it — so the one screen most often reached
 * *from* the player was the one screen that lost it.
 */
class RouteChromeTest {

    @Test
    fun `a detail page keeps the player`() {
        assertTrue(Routes.showsChrome(Routes.ARTIST))
        assertTrue(Routes.showsChrome(Routes.ALBUM))
        assertTrue(Routes.showsChrome(Routes.PLAYLIST))
        assertTrue(Routes.showsChrome(Routes.PROFILE))
        assertTrue(Routes.showsChrome(Routes.JAM_ROUTE))
        // Off-grid is reached from the Friends header and keeps the player like its neighbours.
        assertTrue(Routes.showsChrome(Routes.OFFGRID))
    }

    @Test
    fun `a detail page does not keep the dock row`() {
        assertFalse(Routes.showsDock(Routes.ARTIST))
        assertFalse(Routes.showsDock(Routes.ALBUM))
        assertFalse(Routes.showsDock(Routes.SETTINGS))
    }

    @Test
    fun `every root keeps both`() {
        for (destination in TopLevelDestination.entries) {
            assertTrue(destination.route, Routes.showsChrome(destination.route))
            assertTrue(destination.route, Routes.showsDock(destination.route))
        }
    }

    /** The screens that are a task rather than a place, and take the whole screen for it. */
    @Test
    fun `the queue and the login flows take the screen over`() {
        for (route in listOf(Routes.QUEUE, Routes.WELCOME, Routes.NAVIDROME_LOGIN, Routes.YTMUSIC_LOGIN)) {
            assertFalse(route, Routes.showsChrome(route))
            assertFalse(route, Routes.showsDock(route))
        }
    }

    @Test
    fun `an unknown route gets nothing`() {
        assertFalse(Routes.showsChrome(null))
        assertFalse(Routes.showsChrome("nowhere"))
    }
}
