package com.wander.android.data.sources.navidrome

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicResponseRoot(
    @SerialName("subsonic-response") val response: SubsonicResponse
)

@Serializable
data class SubsonicResponse(
    val status: String,
    val version: String? = null,
    val type: String? = null,
    val serverVersion: String? = null,
    val error: SubsonicError? = null,
    val searchResult3: SubsonicSearchResult3? = null,
    val starred2: SubsonicStarred2? = null,
    val albumList2: SubsonicAlbumList2? = null,
    val album: SubsonicAlbumDetail? = null,
    val playlists: SubsonicPlaylistsRoot? = null,
    val playlist: SubsonicPlaylistDetail? = null,
    val lyrics: SubsonicLyrics? = null,
    val lyricsList: SubsonicLyricsList? = null,
    val similarSongs2: SubsonicSimilarSongs? = null
)

@Serializable
data class SubsonicError(
    val code: Int,
    val message: String
)

@Serializable
data class SubsonicSearchResult3(
    val song: List<SubsonicSong>? = null,
    val album: List<SubsonicAlbum>? = null,
    val artist: List<SubsonicArtist>? = null
)

@Serializable
data class SubsonicStarred2(
    val song: List<SubsonicSong>? = null,
    val album: List<SubsonicAlbum>? = null,
    val artist: List<SubsonicArtist>? = null
)

@Serializable
data class SubsonicAlbumList2(
    val album: List<SubsonicAlbum>? = null
)

@Serializable
data class SubsonicAlbumDetail(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Long = 0L,
    val year: Int? = null,
    val genre: String? = null,
    val song: List<SubsonicSong>? = null
)

@Serializable
data class SubsonicSong(
    val id: String,
    val title: String,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val duration: Long? = null,
    val bitRate: Int? = null,
    val path: String? = null,
    val isVideo: Boolean = false,
    val playCount: Int = 0,
    val starred: String? = null
)

@Serializable
data class SubsonicAlbum(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Long = 0L,
    val year: Int? = null,
    val genre: String? = null
)

@Serializable
data class SubsonicArtist(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int = 0
)

@Serializable
data class SubsonicPlaylistsRoot(
    val playlist: List<SubsonicPlaylist>? = null
)

@Serializable
data class SubsonicPlaylist(
    val id: String,
    val name: String,
    val comment: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Long = 0L,
    val public: Boolean = false
)

@Serializable
data class SubsonicPlaylistDetail(
    val id: String,
    val name: String,
    val comment: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Long = 0L,
    val public: Boolean = false,
    val entry: List<SubsonicSong>? = null
)

@Serializable
data class SubsonicLyrics(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null
)

@Serializable
data class SubsonicLyricsList(
    val structuredLyrics: List<SubsonicStructuredLyrics>? = null
)

@Serializable
data class SubsonicStructuredLyrics(
    val lang: String? = null,
    val synced: Boolean = false,
    val line: List<SubsonicLyricsLine>? = null
)

@Serializable
data class SubsonicLyricsLine(
    val start: Long? = null, // ms
    val value: String
)

@Serializable
data class SubsonicSimilarSongs(
    val song: List<SubsonicSong>? = null
)
