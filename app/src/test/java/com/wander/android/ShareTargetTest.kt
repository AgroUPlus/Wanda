package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The description is what a share sheet's subject line and Navidrome's share label are both built
 * from, so an album with no artist must not come out as "Kid A — null" or with a dangling dash.
 */
class ShareTargetTest {

    private fun target(title: String, subtitle: String?) = ShareTarget(
        kind = ShareKind.ALBUM,
        source = SourceType.NAVIDROME,
        id = "navidrome:al-42",
        title = title,
        subtitle = subtitle
    )

    @Test
    fun joinsTitleAndSubtitle() {
        assertEquals("Kid A — Radiohead", target("Kid A", "Radiohead").description)
    }

    @Test
    fun titleAloneWhenThereIsNothingToQualifyItWith() {
        assertEquals("Kid A", target("Kid A", null).description)
        assertEquals("Kid A", target("Kid A", "").description)
        assertEquals("Kid A", target("Kid A", "   ").description)
    }
}
