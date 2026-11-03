package com.wander.android.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialNotificationKeysTest {

    @Test
    fun `one friend is one thread whatever the case of their name`() {
        assertEquals(SocialNotificationKeys.dropThreadTag("Marie"), SocialNotificationKeys.dropThreadTag(" marie "))
        assertEquals(SocialNotificationKeys.shortcutId("MARIE"), SocialNotificationKeys.shortcutId("marie"))
    }

    @Test
    fun `each kind of news about one person has its own tag`() {
        val tags = setOf(
            SocialNotificationKeys.dropThreadTag("marie"),
            SocialNotificationKeys.requestTag("marie"),
            SocialNotificationKeys.acceptTag("marie"),
            SocialNotificationKeys.blendTag("marie")
        )
        assertEquals(4, tags.size)
    }

    @Test
    fun `two friends never share a thread`() {
        assertNotEquals(SocialNotificationKeys.dropThreadTag("marie"), SocialNotificationKeys.dropThreadTag("mario"))
    }

    @Test
    fun `conversation shortcuts are told apart from the launcher's own`() {
        assertTrue(SocialNotificationKeys.isConversationShortcut(SocialNotificationKeys.shortcutId("marie")))
        assertFalse(SocialNotificationKeys.isConversationShortcut("search"))
        assertFalse(SocialNotificationKeys.isConversationShortcut("shuffle_liked"))
    }

    @Test
    fun `each action and person gets its own intent data`() {
        val play = SocialNotificationKeys.actionData("play", "marie")
        assertNotEquals(play, SocialNotificationKeys.actionData("love", "marie"))
        assertNotEquals(play, SocialNotificationKeys.actionData("play", "mario"))
        assertEquals(play, SocialNotificationKeys.actionData("play", "Marie"))
    }

    @Test
    fun `links carry the username encoded`() {
        assertEquals("wanda://inbox?with=marie", SocialNotificationKeys.inboxThreadLink("marie"))
        assertEquals("wanda://inbox?with=a%26b", SocialNotificationKeys.inboxThreadLink("a&b"))
        assertEquals("wanda://profile?user=j+d", SocialNotificationKeys.profileLink(" j d "))
    }

    @Test
    fun `ids stay apart from the summary`() {
        val ids = setOf(
            SocialNotificationKeys.DROP_ID,
            SocialNotificationKeys.REQUEST_ID,
            SocialNotificationKeys.ACCEPT_ID,
            SocialNotificationKeys.BLEND_ID,
            SocialNotificationKeys.SUMMARY_ID
        )
        assertEquals(5, ids.size)
    }
}
