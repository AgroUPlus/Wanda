package com.wander.android.data.repository

import androidx.compose.runtime.Immutable
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.model.isOneShotTrackId

/**
 * Which tracks are on the device and which are waiting to be, as one snapshot for a whole screen —
 * the same reason `FingerprintStatusRepository` hands rows one map rather than a query each.
 */
@Immutable
data class DownloadMarks(
    val downloaded: Set<String> = emptySet(),
    /** Asked for by hand and not on the device yet. */
    val queued: Set<String> = emptySet()
) {
    fun isDownloaded(track: UnifiedTrack): Boolean = track.isDownloaded || track.id in downloaded

    fun isQueued(track: UnifiedTrack): Boolean = !isDownloaded(track) && track.id in queued

    internal companion object {
        fun of(downloaded: Set<String>, manual: Set<String>) = DownloadMarks(downloaded, manual - downloaded)
    }
}

/**
 * The pure half of [ManualDownloadRepository]: which tracks a manual download may be offered for.
 * Kept apart from the DAO and WorkManager for the reason [StorageButlerRules] is.
 */
internal object ManualDownloadRules {

    /**
     * A local file is already on the device, and an imported placeholder has nothing behind it. A
     * livestream never ends, so there is no file to write. A peer's or relayed track is borrowed
     * for one playback — see `DownloadWorker`, which refuses those too. Agro holds playlists only.
     */
    fun canDownload(track: UnifiedTrack): Boolean =
        track.source !in NotDownloadable && !track.isLive && !isOneShotTrackId(track.id)

    /** Whether an album's tracks can be, by the same source rule as [canDownload]'s. */
    fun canDownload(album: UnifiedAlbum): Boolean = album.source !in NotDownloadable

    /** A download the user can take back: never a local file, which is the user's own. */
    fun canRemove(track: UnifiedTrack, marks: DownloadMarks): Boolean =
        track.source != SourceType.LOCAL && (marks.isDownloaded(track) || marks.isQueued(track))

    private val NotDownloadable = setOf(SourceType.LOCAL, SourceType.UNRESOLVED, SourceType.AGRO)
}
