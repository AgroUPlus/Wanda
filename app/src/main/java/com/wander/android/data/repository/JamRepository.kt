package com.wander.android.data.repository

import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroJamApi
import com.wander.android.data.sources.agro.FriendJam
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The jam this device is in, held in memory only.
 *
 * Nothing here is cached to disk. A jam is a room that exists while people are in it — a queue
 * restored from yesterday's session would be worse than an empty screen, because it would look
 * live. The server is the only source of truth and every mutation returns the whole jam, so there
 * is nothing to reconcile.
 */
@Singleton
internal class JamRepository @Inject constructor(
    private val api: AgroJamApi,
    private val playerConnection: PlayerConnection,
    private val playback: JamPlaybackController,
    private val sharedTrackHash: com.wander.android.core.sync.SharedTrackHash
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _jam = MutableStateFlow<Jam?>(null)
    val jam: StateFlow<Jam?> = _jam.asStateFlow()

    private val _isJamRadioEnabled = MutableStateFlow(false)
    val isJamRadioEnabled: StateFlow<Boolean> = _isJamRadioEnabled.asStateFlow()

    fun setJamRadioEnabled(enabled: Boolean) {
        _isJamRadioEnabled.value = enabled
    }

    /**
     * What this device was playing before it joined, so the jam can give it back.
     *
     * A jam borrows the queue rather than adding to it. Held only in memory: a jam lasts as long as
     * the app is in one, and restoring a queue from a previous run would be a surprise, not a
     * courtesy.
     */
    private var borrowedFrom: PlayerConnection.QueueSnapshot? = null

    /** Re-reads from the server. Also what a `JAM_UPDATED` frame triggers. */
    suspend fun refresh(): Result<Unit> = api.jam()
        .onSuccess { fresh ->
            // A jam that has disappeared server-side is one the creator ended. Restore rather than
            // leaving this device playing a room that no longer exists.
            if (fresh == null && _jam.value != null) {
                onJamEnded()
            } else {
                _jam.value = fresh
                wireJamProposal()
                if (fresh != null) playback.onNowPlaying(fresh.nowPlaying)
            }
        }
        .map { }

    suspend fun create(mode: JamMode): Result<Unit> {
        borrowQueue()
        return api.createJam(mode).store()
    }

    /**
     * Joins a jam. Playback follows on its own.
     *
     * Briefly this started a listen-along with the host, back when one device played and the rest
     * mirrored it. It no longer does: every device plays the shared queue itself, so there is
     * nobody to follow and mirroring would have made the creator a DJ again.
     */
    suspend fun join(code: String): Result<Unit> {
        borrowQueue()
        return api.joinJam(code).store()
    }

    /** Leaves, and hands back whatever this device was playing before. */
    suspend fun leave(): Result<Unit> = api.leaveJam().map {
        _jam.value = null
        wireJamProposal()
        playback.reset()
        returnQueue()
    }

    /** The jam ended under us — the creator left, or it was wound up. Same restoration. */
    fun onJamEnded() {
        _jam.value = null
        wireJamProposal()
        playback.reset()
        returnQueue()
    }

    private fun borrowQueue() {
        // Taken before anything else, or the jam's first track would already have replaced it.
        if (borrowedFrom == null) borrowedFrom = playerConnection.snapshotQueue()
    }

    private fun returnQueue() {
        borrowedFrom?.let(playerConnection::restoreQueue)
        borrowedFrom = null
    }

    /**
     * Queues a track, naming the local file behind it when there is one.
     *
     * The hash is what lets the rest of the room play *this* copy rather than each hunting for the
     * track by name in their own sources. Absent for anything streamed, which is most of a queue —
     * but computed on the spot for a local file the batch worker has not reached, because without
     * it the room is told a file exists that nobody can ask for by name.
     */
    suspend fun add(track: UnifiedTrack): Result<Unit> {
        displaceAutoRadio()
        return api.addTrack(track, contentHash = sharedTrackHash.of(track.id)).store()
    }

    suspend fun approve(trackId: String): Result<Unit> = api.approve(trackId).store()

    suspend fun remove(trackId: String): Result<Unit> = api.removeTrack(trackId).store()

    suspend fun setMode(mode: JamMode): Result<Unit> = api.setMode(mode).store()

    /** Votes to skip whatever the room is playing. */
    suspend fun voteSkip(): Result<Unit> = api.voteSkip().store()

    /** Opens the jam to friends, or shuts it back to code-only. */
    suspend fun setOpenToFriends(open: Boolean): Result<Unit> = api.setVisibility(open).store()

    suspend fun friendJams(): Result<List<FriendJam>> = api.friendJams()

    suspend fun joinFriendJam(jamId: String): Result<Unit> {
        borrowQueue()
        return api.joinFriendJam(jamId).store()
    }


    /** Every mutation answers with the whole jam, so applying one is a straight replacement. */
    private fun Result<Jam>.store(): Result<Unit> = onSuccess { fresh ->
        _jam.value = fresh
        wireJamProposal()
        playback.onNowPlaying(fresh.nowPlaying)
    }.map { }

    /**
     * While a jam exists, choosing a track anywhere in the app proposes it to the room.
     *
     * This used to be installed by `JamViewModel`, which meant it only existed once that ViewModel
     * had been constructed — so a jam joined before the app was restarted, or simply a user who
     * went straight to Library without passing through the Friends tab, had no proposal callback
     * at all and tapping a song played it locally instead. Long-pressing worked the whole time,
     * because that path calls the repository directly, which is exactly the inconsistency reported.
     *
     * Owned here because the jam is owned here: the callback's lifetime is now the jam's, not a
     * screen's.
     */
    private fun wireJamProposal() {
        if (_jam.value == null) {
            playerConnection.setJamProposal(null)
            return
        }
        playerConnection.setJamProposal { tracks, index ->
            tracks.getOrNull(index)?.let { track -> scope.launch { add(track) } }
        }
    }

    /**
     * Remembers the track auto-radio put in an empty queue, so a real choice can displace it.
     *
     * Held here rather than in a ViewModel for the same reason the callback above is: the
     * proposal it guards can now arrive from anywhere in the app.
     */
    fun noteAutoRadioTrack(trackId: String?) {
        autoRadioTrackId = trackId
    }

    private var autoRadioTrackId: String? = null

    /** Drops the auto-radio placeholder, if one is still sitting where the user's pick should go. */
    private suspend fun displaceAutoRadio() {
        val radioTrackId = autoRadioTrackId ?: return
        autoRadioTrackId = null
        val jam = _jam.value ?: return
        val queued = (jam.queue + jam.proposals).firstOrNull {
            it.trackUri == radioTrackId || it.id == radioTrackId
        } ?: return
        api.removeTrack(queued.id).store()
    }
}
