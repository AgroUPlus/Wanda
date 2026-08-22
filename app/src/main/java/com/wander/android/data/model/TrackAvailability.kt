package com.wander.android.data.model

/**
 * Whether this track can be played with no network at all.
 *
 * Mirrors the rule `MusicRepository.getStreamInfo` enforces at load time: a local file always
 * plays, a downloaded track plays from the file the download worker wrote, and anything else needs
 * the network it is about to be denied. Kept here as one expression so the UI dims exactly the
 * rows that playback would refuse, instead of each screen re-deriving a near-miss of the rule.
 *
 * Note this is *not* the Media3 streaming cache: [UnifiedTrack.isCached] is only ever set for
 * local files today, and a partially cached stream is not something we can promise will play
 * through.
 */
fun UnifiedTrack.isPlayableOffline(): Boolean =
    source == SourceType.LOCAL || isDownloaded
