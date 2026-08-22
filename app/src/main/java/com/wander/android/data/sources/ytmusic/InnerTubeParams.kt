package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.SearchKind

/**
 * The opaque ids and protobuf blobs InnerTube expects.
 *
 * Split out of `InnerTubeClient` to keep it inside the file budget, but they belong together for a
 * better reason too: none of these are documented or stable, so the whole set of things that can
 * quietly stop working one day is in one place, each recorded with when it was last seen working.
 */
internal const val YT_MUSIC_ORIGIN = "https://music.youtube.com"

/** Songs. The original filter, and still the default for every search. */
internal const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="

/** Video uploads. Played as audio like everything else — there is no video surface. */
internal const val VIDEOS_FILTER = "EgWKAQIQAWoKEAkQChAFEAMQBA=="

/**
 * Podcast *episodes*, not shows.
 *
 * The shows filter returns browse targets carrying no `videoId`, which nothing here could play.
 * Episodes are the listenable half, and each one arrives as an ordinary track.
 */
internal const val EPISODES_FILTER = "EgWKAQJIAWoKEAkQChAFEAMQBA=="

/**
 * All three verified against live responses on 2026-08-20, by checking which chip the server
 * marks selected and what it titles the result shelf ("Songs" / "Videos" / "Episodes").
 */
internal fun SearchKind.filterParam(): String = when (this) {
    SearchKind.TRACKS -> SONGS_FILTER
    SearchKind.VIDEOS -> VIDEOS_FILTER
    SearchKind.EPISODES -> EPISODES_FILTER
}

/** The browse id behind music.youtube.com's front page. */
internal const val HOME_BROWSE_ID = "FEmusic_home"

/**
 * Discovery feeds, for when the personalised home page has gone stale or repetitive.
 *
 * Verified 2026-08-20: each answers 200 with `musicCarouselShelfRenderer` shelves the existing
 * home parser already understands. `FEmusic_moods_and_genres` is deliberately absent — it answers
 * 200 but carries *no* carousels (it is a grid of navigation buttons), so the parser would find
 * nothing in it. `FEmusic_non_music_audio`, the obvious guess for podcasts, answers 404.
 */
internal val DISCOVERY_BROWSE_IDS = listOf(
    "FEmusic_new_releases",
    "FEmusic_charts",
    "FEmusic_explore"
)

/** YouTube Music's id for "radio seeded by this video". */
internal const val RADIO_PREFIX = "RDAMVM"

internal const val ANDROID_VR_SDK_VERSION = 32
internal const val ANDROID_VR_OS_VERSION = "12L"
