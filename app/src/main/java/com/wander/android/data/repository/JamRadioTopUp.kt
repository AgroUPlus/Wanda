package com.wander.android.data.repository

import android.database.SQLException
import android.util.Log
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroFeedApi
import com.wander.android.data.sources.agro.Jam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jam radio: keeps an empty room's queue topped up with a blend of the circle's taste.
 *
 * A singleton on purpose. This used to live in `JamViewModel`, which half a dozen screens each
 * construct for themselves — and every one of them ran its own top-up behind its own guard. The
 * instances raced to fill the same empty queue, and since queueing a track displaces the previous
 * auto-radio pick, each one's add removed the last one's: the queue looped between adding and
 * removing songs for as long as more than one of those screens was alive.
 */
@Singleton
internal class JamRadioTopUp @Inject constructor(
    private val repository: JamRepository,
    private val feedApi: AgroFeedApi,
    private val musicRepository: MusicRepository,
    private val resolver: ListenAlongResolver
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)
    private val busy = Mutex()

    /**
     * Tracks this radio has already put in the room, so a sparse circle history does not hand the
     * same song back every time the queue runs dry. Cleared with the jam.
     */
    private val alreadyPicked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Starts watching the jam. Safe to call from every screen that wants the radio alive. */
    fun ensureRunning() {
        if (!started.compareAndSet(false, true)) return
        combine(repository.jam, repository.isJamRadioEnabled) { jam, enabled -> jam to enabled }
            .onEach { (jam, enabled) -> check(jam, enabled) }
            .launchIn(scope)
    }

    private fun check(jam: Jam?, enabled: Boolean) {
        if (jam == null) alreadyPicked.clear()
        if (jam == null || !enabled) {
            repository.noteAutoRadioTrack(null)
            return
        }
        // Only the host — or someone alone in the room — fills it, so a room of five does not get
        // five suggestions every time the queue empties.
        if (!jam.isHost && jam.members.size > 1) return
        if (jam.queue.isNotEmpty() || jam.proposals.isNotEmpty()) return
        val now = jam.nowPlaying ?: return
        // `tryLock` rather than waiting: a jam update arriving while a pick is in flight is the
        // same empty queue, and queueing it up behind the first would add a second track.
        if (!busy.tryLock()) return
        scope.launch {
            try {
                val pick = blendPick(now.trackId) ?: seedRadioPick(now.title, now.artist)
                if (pick != null && repository.addAutoRadio(pick).isSuccess) {
                    alreadyPicked += pick.id
                }
            } catch (e: IOException) {
                // Best-effort: the jam plays on with what it has, and the next track change tries
                // again. An error here would interrupt someone whose music never stopped.
                Log.d(TAG, "Auto top-up skipped", e)
            } catch (e: SQLException) {
                // Same reasoning, for the local library lookups the resolver and radio make.
                Log.d(TAG, "Auto top-up skipped", e)
            } finally {
                busy.unlock()
            }
        }
    }

    /**
     * A track from the circle: the month's shared recap and each friend's recent listening,
     * shuffled together so nobody's taste dominates.
     */
    private suspend fun blendPick(playingId: String): UnifiedTrack? {
        val candidates = mutableListOf<Pair<String, String>>() // (title, artist)
        val recap = feedApi.recap("MONTH").getOrNull()
        recap?.anthem?.let { candidates += it.title to it.artist }
        recap?.topTracks?.forEach { candidates += it.name to "" }
        feedApi.friendActivity(days = 14, limit = 30).getOrNull().orEmpty().forEach { item ->
            val title = item.title
            when {
                !title.isNullOrBlank() -> candidates += title to item.artist
                item.artist.isNotBlank() -> candidates += "" to item.artist
            }
        }
        for ((title, artist) in candidates.distinct().shuffled()) {
            val track = resolver.resolve(title, artist)?.track ?: continue
            if (track.id != playingId && track.id !in alreadyPicked) return track
        }
        return null
    }

    /** Falls back to seed radio off what the room is playing, while the circle's history is thin. */
    private suspend fun seedRadioPick(title: String, artist: String): UnifiedTrack? {
        val seed = resolver.resolve(title, artist)?.track ?: return null
        return musicRepository.generateRadio(seed, RADIO_BATCH)
            .firstOrNull { it.id != seed.id && it.id !in alreadyPicked }
    }

    private companion object {
        const val TAG = "JamRadioTopUp"
        /** A few rather than one, so a track radio already played can be passed over. */
        const val RADIO_BATCH = 5
    }
}
