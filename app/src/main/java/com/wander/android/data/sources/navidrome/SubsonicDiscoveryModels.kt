package com.wander.android.data.sources.navidrome

import kotlinx.serialization.Serializable

/** `getRandomSongs.view` and `getTopSongs.view`: a bare list of songs. */
@Serializable
data class SubsonicSongList(
    val song: List<SubsonicSong>? = null
)

/** `getInternetRadioStations.view`. */
@Serializable
data class SubsonicRadioStations(
    val internetRadioStation: List<SubsonicRadioStation>? = null
)

/** A live station the server's admin added. [streamUrl] points at the broadcaster, not the server. */
@Serializable
data class SubsonicRadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null
)
