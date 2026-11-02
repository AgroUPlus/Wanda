package com.wander.android.data.sources.navidrome

/*
 * The calls behind Navidrome's Home shelves, apart from [SubsonicApiClient], which is at the
 * file-size cap. Each one goes through its `call`, so signing and error mapping are the same.
 */

internal suspend fun SubsonicApiClient.getRandomSongs(size: Int): Result<List<SubsonicSong>> =
    call("getRandomSongs.view", "size" to size).map { it.randomSongs?.song.orEmpty() }

/** The artist's most played songs, by the server's metadata agent; empty without one. */
internal suspend fun SubsonicApiClient.getTopSongs(artist: String, count: Int): Result<List<SubsonicSong>> =
    call("getTopSongs.view", "artist" to artist, "count" to count).map { it.topSongs?.song.orEmpty() }

internal suspend fun SubsonicApiClient.getInternetRadioStations(): Result<List<SubsonicRadioStation>> =
    call("getInternetRadioStations.view").map { it.internetRadioStations?.internetRadioStation.orEmpty() }
