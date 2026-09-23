package com.wander.android.data.replay

import androidx.compose.runtime.Immutable
import com.wander.android.core.database.dao.ArtistDao
import com.wander.android.core.database.dao.TrackDao
import javax.inject.Inject

/**
 * Pictures for the names in a recap, keyed by those names.
 *
 * Kept beside the report rather than inside it: a report is written to disk and outlives the
 * library, while a cover URL is only as good as what this device knows today. Resolved fresh every
 * time the story opens, and a name with nothing behind it is simply absent.
 */
@Immutable
data class ReplayArtwork(
    val artists: Map<String, String> = emptyMap(),
    /** Keyed by the same `Title — Artist` text the report carries its top tracks in. */
    val tracks: Map<String, String> = emptyMap()
) {
    fun artist(name: String): String? = artists[name]
    fun track(title: String, artist: String): String? = tracks["$title$NAME_SEPARATOR$artist"]
}

internal class ReplayArtworkResolver @Inject constructor(
    private val artistDao: ArtistDao,
    private val trackDao: TrackDao
) {
    /** Local lookups only — the story never waits on the network for a picture. */
    suspend fun resolve(report: ReplayReport): ReplayArtwork {
        val artists = report.topArtists.take(ARTISTS_SHOWN).map { it.name }
        val tracks = listOfNotNull(
            report.topTracks.firstOrNull()?.name,
            report.circle?.let { circle ->
                val title = circle.anthemTitle ?: return@let null
                val artist = circle.anthemArtist ?: return@let null
                "$title$NAME_SEPARATOR$artist"
            }
        )
        return ReplayArtwork(
            artists = artists.mapNotNull { name -> artistPicture(name)?.let { name to it } }.toMap(),
            tracks = tracks.mapNotNull { joined -> trackCover(joined)?.let { joined to it } }.toMap()
        )
    }

    private suspend fun artistPicture(name: String): String? =
        artistDao.getByName(name.lowercase())?.imageUrl?.takeIf { it.isNotBlank() }
            ?: artistDao.trackArtworkFor(name)

    private suspend fun trackCover(joined: String): String? {
        val split = joined.lastIndexOf(NAME_SEPARATOR)
        if (split <= 0) return null
        return trackDao.artworkFor(
            title = joined.take(split),
            artist = joined.substring(split + NAME_SEPARATOR.length)
        )
    }
}

private const val NAME_SEPARATOR = " — "

/** The top-artists card shows five; discovery names three of the same. */
private const val ARTISTS_SHOWN = 5
