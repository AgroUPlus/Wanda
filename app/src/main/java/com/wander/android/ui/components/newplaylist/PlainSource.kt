package com.wander.android.ui.components.newplaylist

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack

/**
 * On Navidrome when every track came from it, so the playlist is there for its other clients too;
 * a Wanda playlist otherwise, since that is the one kind that holds tracks from any source.
 */
internal fun plainSourceFor(tracks: List<UnifiedTrack>): SourceType =
    if (tracks.isNotEmpty() && tracks.all { it.source == SourceType.NAVIDROME }) {
        SourceType.NAVIDROME
    } else {
        SourceType.LOCAL
    }
