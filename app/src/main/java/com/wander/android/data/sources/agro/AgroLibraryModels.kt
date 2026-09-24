package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** A peer source device that holds this track. */
data class PeerSource(
    val deviceId: String,
    val petname: String,
    val lanAddress: String? = null,
    val isOnline: Boolean = false,
    val isServerArchive: Boolean = false
)

/** A track another device holds that this one does not. */
data class MissingTrack(
    val contentHash: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    /** Container as the server indexed it — "flac", "mp3", "m4a". Null when it never learned. */
    val format: String? = null,
    val peerSources: List<PeerSource> = emptyList()
)

/**
 * How this deployment moves music between devices, as the server sees it.
 */
enum class SyncMode {
    /** Streamable from Navidrome: a local copy is a convenience, not the only way to hear it. */
    NAVIDROME,

    /** The server keeps the files but nothing streams them, so a missing track is offered. */
    PEER_TO_PEER,

    /** Index only. The server is not a durable copy, so it never suggests deleting one. */
    INDEX_ONLY;

    /** Whether a device without a track should be offered the bytes. */
    val offersDownloads: Boolean get() = this != NAVIDROME

    /** Whether a redundant local copy is safe to suggest removing. */
    val offersReclaim: Boolean get() = this == NAVIDROME
}

/**
 * How much of the account's allowance is gone.
 *
 * [quotaBytes] is null when the account is uncapped.
 */
data class StorageUsage(
    val usedBytes: Long,
    val quotaBytes: Long?
) {
    /** Null when uncapped, so a caller cannot accidentally divide by a missing limit. */
    val fraction: Float? get() = quotaBytes
        ?.takeIf { it > 0L }
        ?.let { (usedBytes.toFloat() / it).coerceIn(0f, 1f) }
}

data class LibraryStats(
    val trackCount: Int,
    val archivedCount: Int,
    val totalBytes: Long,
    val spoolBytes: Long
)

internal fun parseMissingTrack(element: JsonElement): MissingTrack? {
    val obj = element as? JsonObject ?: return null
    val peerSources = obj["peerSources"]?.jsonArray?.mapNotNull { item ->
        val p = item as? JsonObject ?: return@mapNotNull null
        PeerSource(
            deviceId = p["deviceId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            petname = p["petname"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            lanAddress = p["lanAddress"]?.jsonPrimitive?.contentOrNull,
            isOnline = p["isOnline"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            isServerArchive = p["isServerArchive"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        )
    } ?: emptyList()

    return MissingTrack(
        contentHash = obj["contentHash"]?.jsonPrimitive?.contentOrNull ?: return null,
        title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        artist = obj["artist"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        album = obj["album"]?.jsonPrimitive?.contentOrNull,
        durationMs = obj["durationMs"]?.jsonPrimitive?.long ?: 0L,
        sizeBytes = obj["sizeBytes"]?.jsonPrimitive?.long ?: 0L,
        format = obj["format"]?.jsonPrimitive?.contentOrNull,
        peerSources = peerSources
    )
}
