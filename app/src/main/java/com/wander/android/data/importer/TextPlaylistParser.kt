package com.wander.android.data.importer

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextPlaylistParser @Inject constructor() {

    fun parse(text: String): Result<RawImportPlaylist> = runCatching {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .toList()

        val tracks = mutableListOf<RawImportTrack>()

        for (line in lines) {
            when {
                line.contains(" - ") -> {
                    val parts = line.split(" - ", limit = 2)
                    tracks.add(RawImportTrack(artist = parts[0].trim(), title = parts[1].trim()))
                }
                line.contains(" – ") -> {
                    val parts = line.split(" – ", limit = 2)
                    tracks.add(RawImportTrack(artist = parts[0].trim(), title = parts[1].trim()))
                }
                line.contains(" by ", ignoreCase = true) -> {
                    val parts = line.split(Regex(" by ", RegexOption.IGNORE_CASE), limit = 2)
                    tracks.add(RawImportTrack(title = parts[0].trim(), artist = parts[1].trim()))
                }
                else -> {
                    tracks.add(RawImportTrack(title = line, artist = "Unknown Artist"))
                }
            }
        }

        require(tracks.isNotEmpty()) { "No tracks found in the provided text." }

        RawImportPlaylist(
            platform = PlatformType.PLAIN_TEXT,
            title = "Imported Playlist (${tracks.size} tracks)",
            tracks = tracks
        )
    }
}
