package com.wander.android.data.repository

import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.model.isPlayableOffline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every copy of one recording, across every source that has one.
 *
 * A *rendition* is one backend's copy of a performance: your file, your server's stream, YouTube
 * Music's upload. The app already treats these as interchangeable when it deduplicates a list —
 * this is the same judgement made deliberately, for one track, so the user can be shown the choice
 * and take it.
 *
 * Identity comes from [TrackDeduplicator.isSameRecording], not from the search engine's ranking.
 * A search answers *something* for almost any query, and a picker that offered the top hit
 * unchecked would quietly hand the user a cover or a karaoke version under the label of a source
 * they trust.
 */
@Singleton
class RenditionFinder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val splitRepository: RecordingSplitRepository
) {

    /**
     * The playing rendition, plus one per other source that has the same recording.
     *
     * Ordered by whether it plays **without a network first**, and only then by
     * [SourceType.priority]. Source rank alone gets this wrong in the case that matters most: a
     * downloaded YouTube Music track keeps `source = YTMUSIC` and its priority of 2, so ranking on
     * priority would offer a Navidrome *stream* above a file already sitting on the phone. What the
     * user is choosing between here is partly "will this work on the train".
     *
     * One per source: several uploads of one song on YouTube Music are not a choice worth
     * presenting.
     */
    suspend fun findRenditions(track: UnifiedTrack): List<UnifiedTrack> = coroutineScope {
        // A copy the user has said is a different performance is not an alternative to this one.
        val splits = splitRepository.splits()
        val query = listOf(track.artist, track.title)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) return@coroutineScope listOf(track)

        val others = musicRepository.searchableSources()
            .map { it.sourceType }
            .filter { it != track.source }

        val found = others
            .map { source ->
                async(Dispatchers.IO) {
                    runCatching {
                        musicRepository
                            .searchAllSources(query, onlySources = setOf(source), kind = SearchKind.TRACKS)
                            .firstOrNull { TrackDeduplicator.isSameRecording(track, it, splits) }
                    }.getOrNull()
                }
            }
            .mapNotNull { it.await() }

        (listOf(track) + found + downloadedCopies(track, splits))
            .distinctBy { it.source }
            .sortedWith(
                compareByDescending<UnifiedTrack> { it.isPlayableOffline() }
                    .thenBy { it.source.priority }
            )
    }

    /**
     * Copies of this recording already on the device, whatever source they came from.
     *
     * Asked of Room directly rather than left to the searches above, because those need a network
     * and this is precisely the answer that matters when there isn't one. A download is not a
     * source of its own — it is an ordinary rendition that happens to have a file behind it — so
     * nothing else would have looked for it.
     */
    private suspend fun downloadedCopies(
        track: UnifiedTrack,
        splits: SplitSet
    ): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            musicRepository.downloadedTracks()
                .filter { it.id != track.id && TrackDeduplicator.isSameRecording(track, it, splits) }
        }

    /**
     * Whether looking is worth the searches at all.
     *
     * [durationMs] is the *player's* length, not the track's. A YouTube Music row whose subtitle
     * carried no `3:45` reaches Room with a duration of zero, and identity here is partly a
     * duration comparison — so reading it off the metadata made the picker silently unavailable on
     * exactly the tracks whose metadata is thinnest. The player knows the real length once the
     * track is loaded; that is the number to ask.
     */
    fun canSwitch(track: UnifiedTrack, durationMs: Long): Boolean =
        durationMs > 0L &&
            musicRepository.searchableSources().any { it.sourceType != track.source }
}
