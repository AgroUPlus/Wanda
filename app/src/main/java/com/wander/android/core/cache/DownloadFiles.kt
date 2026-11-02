package com.wander.android.core.cache

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Free space a download must leave behind, so offline music never fills the device to the brim. */
internal const val MIN_FREE_SPACE_BYTES = 64L * 1024 * 1024

/**
 * How old an unreferenced file must be before the sweep deletes it. A periodic sweep and a
 * "download now" run can overlap, and the other run's file in progress — or one it has finished
 * but not yet recorded in Room — is unreferenced but very much alive; its mtime is recent.
 */
internal const val ORPHAN_MIN_AGE_MS = 60L * 60 * 1000

private const val PART_SUFFIX = ".part"

/** Thrown when the volume holding downloads cannot fit a track plus [MIN_FREE_SPACE_BYTES]. */
class InsufficientStorageException(val requiredBytes: Long, val usableBytes: Long) :
    IOException("Not enough free space to download: need $requiredBytes bytes, $usableBytes usable")

/**
 * The files side of [DownloadWorker]: where a download is written, and how it gets into place.
 *
 * A track is streamed into a uniquely named `.part` file and only renamed over its final name
 * once every byte has arrived. Writing straight to the final file left a truncated track at a
 * path Room could already point at whenever the network dropped or the worker was stopped.
 */
internal class DownloadFiles(private val dir: File) {

    /** True when [dir]'s volume has room for [bytes] more (unknown length: `-1`) plus the reserve. */
    fun hasRoomFor(bytes: Long): Boolean = dir.usableSpace >= bytes.coerceAtLeast(0L) + MIN_FREE_SPACE_BYTES

    fun requireRoomFor(bytes: Long) {
        if (!hasRoomFor(bytes)) {
            throw InsufficientStorageException(bytes.coerceAtLeast(0L) + MIN_FREE_SPACE_BYTES, dir.usableSpace)
        }
    }

    /**
     * Copies [body] to [destination] via a `.part` file, through [copy] (plain copy by default;
     * Deezer downloads pass a decrypting copy). The part file is deleted on every path that does
     * not end in a successful rename, exception included.
     */
    fun write(
        body: InputStream,
        destination: File,
        copy: (InputStream, OutputStream) -> Unit = { input, out -> input.copyTo(out) },
    ) {
        dir.mkdirs()
        val part = File.createTempFile("${destination.nameWithoutExtension}.", PART_SUFFIX, dir)
        try {
            part.outputStream().use { out -> copy(body, out) }
            if (!part.renameTo(destination)) {
                throw IOException("Could not move the finished download into place")
            }
        } finally {
            if (part.exists()) part.delete()
        }
    }

    /**
     * Deletes files no track row points at: part files a killed process never cleaned up, and
     * downloads whose row was deleted. Matched by file name, not full path, so a row written
     * under another spelling of the app's files directory is never mistaken for an orphan.
     *
     * @return how many files were deleted.
     */
    fun sweepOrphans(referencedPaths: Collection<String>, nowMs: Long): Int {
        val referenced = referencedPaths.mapTo(HashSet()) { File(it).name }
        val cutoff = nowMs - ORPHAN_MIN_AGE_MS
        val orphans = dir.listFiles()
            ?.filter { it.isFile && it.name !in referenced && it.lastModified() < cutoff }
            .orEmpty()
        return orphans.count { it.delete() }
    }
}
