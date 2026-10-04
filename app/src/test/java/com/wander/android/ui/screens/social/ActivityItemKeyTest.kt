package com.wander.android.ui.screens.social

import com.wander.android.data.sources.agro.AgroFeedItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The circle's events are list keys. One play can cross a milestone and put a track on repeat at
 * once, and history imported at the hour stamps many plays alike, so a person and a time are not
 * enough — that crashed the Activity screen with a duplicate key.
 */
class ActivityItemKeyTest {

    private fun event(kind: String, title: String?, count: Long) = AgroFeedItem(
        username = "alpha",
        at = "2026-10-04T14:00:00+00:00",
        kind = kind,
        summary = "",
        artist = "Artist",
        title = title,
        count = count
    )

    @Test
    fun events_from_one_person_at_one_moment_have_distinct_keys() {
        val items = listOf(
            event("MILESTONE", null, 10),
            event("MILESTONE", null, 25),
            event("ON_REPEAT", "Song", 4),
            event("NEW_FAVOURITE", null, 5)
        ).map(ActivityItem::Milestone)

        assertEquals(items.size, items.map { it.key }.toSet().size)
    }
}
