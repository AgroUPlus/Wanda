package com.wander.android.core.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest

/**
 * The parts a backup can carry, each chosen separately. Names are written into the file, so they
 * are stable identifiers — rename one and older backups stop verifying.
 */
internal enum class BackupSection {
    SETTINGS,
    ACCOUNTS,
    HISTORY,
    LIBRARY,
    MERGES,
    EPISODES
}

/** How many records a section held and the SHA-256 of exactly those records. */
@Serializable
internal data class SectionDigest(val count: Int, val sha256: String)

/**
 * ## Why a manifest on top of GCM
 *
 * GCM already refuses a file that was altered after sealing. What it cannot catch is a backup that
 * was *wrong when sealed* — a section serialised short, or a later build decoding a record into
 * nothing. The manifest records, per section, how many records were written and a hash of them;
 * a restore recomputes both from what it actually decoded and refuses to write anything if one
 * differs. So "restored" means every chosen section came back whole, not just that the file opened.
 */
internal fun BackupDocument.sectionDigests(json: Json, sections: Set<BackupSection>): Map<String, SectionDigest> =
    sections.associate { it.name to digestOf(json, it) }

/** @throws IOException naming the first section that did not survive intact, or if none is listed. */
internal fun BackupDocument.verifyIntegrity(json: Json) {
    if (manifest.isEmpty()) throw IOException("The backup lists no sections, so none can be checked")
    manifest.forEach { (name, expected) ->
        val section = BackupSection.entries.firstOrNull { it.name == name }
            ?: throw IOException("The backup has a section this version does not know: $name")
        if (digestOf(json, section) != expected) {
            throw IOException("The backup's ${name.lowercase()} section failed its integrity check")
        }
    }
}

/** Sections the backup carries. */
internal val BackupDocument.includedSections: Set<BackupSection>
    get() = manifest.keys.mapNotNull { name -> BackupSection.entries.firstOrNull { it.name == name } }.toSet()

private fun BackupDocument.digestOf(json: Json, section: BackupSection): SectionDigest = when (section) {
    BackupSection.SETTINGS -> digest(json, entryMap, entries, entries.size)
    BackupSection.ACCOUNTS -> digest(json, entryMap, accounts, accounts.size)
    // Tracks are hashed under both HISTORY and LIBRARY: either one alone carries them.
    BackupSection.HISTORY -> digest(
        json, historyPart, HistoryPart(history, recaps, tracks), history.size + recaps.size
    )
    BackupSection.LIBRARY -> digest(
        json, libraryPart, LibraryPart(tracks, playlists, podcasts), tracks.size + playlists.size + podcasts.size
    )
    BackupSection.MERGES -> digest(json, mergesPart, MergesPart(splits, links), splits.size + links.size)
    BackupSection.EPISODES -> digest(json, episodeList, episodes, episodes.size)
}

private fun <T> digest(json: Json, serializer: KSerializer<T>, value: T, count: Int): SectionDigest {
    val bytes = json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
    return SectionDigest(count, hash.joinToString("") { "%02x".format(it) })
}

@Serializable private data class HistoryPart(
    val plays: List<BackupPlay>,
    val recaps: List<BackupRecap>,
    val tracks: List<BackupTrack>
)
/** [podcasts] is defaulted so an empty list is not encoded: digests of backups from before it existed still match. */
@Serializable private data class LibraryPart(
    val tracks: List<BackupTrack>,
    val playlists: List<BackupPlaylist>,
    val podcasts: List<BackupPodcast> = emptyList()
)
@Serializable private data class MergesPart(val splits: List<BackupRecordingPair>, val links: List<BackupRecordingPair>)

private val entryMap = MapSerializer(String.serializer(), BackupEntry.serializer())
private val historyPart = HistoryPart.serializer()
private val libraryPart = LibraryPart.serializer()
private val mergesPart = MergesPart.serializer()
private val episodeList = ListSerializer(BackupEpisode.serializer())
