package com.wander.android.core.playback

import androidx.media3.session.MediaController
import com.wander.android.data.model.UnifiedTrack
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages queue mutations, queue caching, and snapshot/restoration for [PlayerConnection].
 */
internal class PlayerQueueManager {

    val trackCache = ConcurrentHashMap<String, UnifiedTrack>()
    var lastQueue: List<UnifiedTrack> = emptyList()

    fun play(ctrl: MediaController, tracks: List<UnifiedTrack>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        tracks.forEach { trackCache[it.id] = it }
        ctrl.setMediaItems(
            tracks.map(UnifiedTrack::toMediaItem),
            startIndex.coerceIn(0, tracks.lastIndex),
            startPositionMs
        )
        ctrl.prepare()
        ctrl.play()
    }

    fun addToQueue(ctrl: MediaController, tracks: List<UnifiedTrack>) {
        if (tracks.isEmpty()) return
        tracks.forEach { trackCache[it.id] = it }
        ctrl.addMediaItems(tracks.map(UnifiedTrack::toMediaItem))
    }

    fun playNext(ctrl: MediaController, tracks: List<UnifiedTrack>) {
        if (tracks.isEmpty()) return
        tracks.forEach { trackCache[it.id] = it }
        val items = tracks.map(UnifiedTrack::toMediaItem)
        if (ctrl.mediaItemCount == 0) {
            ctrl.setMediaItems(items)
            ctrl.prepare()
            ctrl.play()
        } else {
            ctrl.addMediaItems((ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount), items)
        }
    }

    fun removeFromQueue(ctrl: MediaController, index: Int) {
        ctrl.removeMediaItem(index)
    }

    fun insertInQueue(ctrl: MediaController, index: Int, track: UnifiedTrack) {
        trackCache[track.id] = track
        ctrl.addMediaItems(index.coerceIn(0, ctrl.mediaItemCount), listOf(track.toMediaItem()))
    }

    fun moveInQueue(ctrl: MediaController, from: Int, to: Int, orderLocked: Boolean) {
        if (orderLocked) return
        if (from == to) return
        val count = ctrl.mediaItemCount
        if (from !in 0 until count || to !in 0 until count) return
        ctrl.moveMediaItem(from, to)
    }

    fun clearQueue(ctrl: MediaController) {
        ctrl.clearMediaItems()
        lastQueue = emptyList()
    }

    fun snapshotQueue(ctrl: MediaController?): PlayerConnection.QueueSnapshot? {
        if (ctrl == null) return null
        val tracks = lastQueue.ifEmpty { return null }
        return PlayerConnection.QueueSnapshot(
            tracks = tracks,
            index = ctrl.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = ctrl.currentPosition.coerceAtLeast(0L)
        )
    }

    fun restoreQueue(ctrl: MediaController, snapshot: PlayerConnection.QueueSnapshot) {
        if (snapshot.tracks.isEmpty()) return
        snapshot.tracks.forEach { trackCache[it.id] = it }
        ctrl.setMediaItems(
            snapshot.tracks.map(UnifiedTrack::toMediaItem),
            snapshot.index.coerceIn(0, snapshot.tracks.lastIndex),
            snapshot.positionMs
        )
        ctrl.prepare()
        ctrl.play()
    }

    fun clear() {
        trackCache.clear()
        lastQueue = emptyList()
    }
}
