package com.wander.android.core.sync

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SHA-256 of a local audio file, which is the identity Agro's library index keys on.
 *
 * Content, not path: two devices file the same recording in different places, and MediaStore ids
 * are not even stable across a re-index of the same phone. Hashing the bytes is the only way two
 * devices can agree they hold the same file.
 *
 * Reading goes through the `ContentResolver` rather than a filesystem path — under scoped storage
 * the app has no path for media it does not own, but `READ_MEDIA_AUDIO` is enough to open the
 * content URI. That is also why no storage permission beyond the existing one is needed.
 */
@Singleton
class ContentHasher @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Null when the file cannot be read — deleted since the scan, or permission withdrawn. */
    suspend fun hash(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
                // Lowercase hex: the server validates the format and compares as a string.
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }.getOrNull()
    }

    suspend fun hash(uriString: String): String? = hash(uriString.toUri())

    /**
     * Hashes a file this app downloaded, by path.
     *
     * Separate from [hash] because `localFilePath` is a bare filesystem path with no scheme, and
     * `openInputStream` needs a URI — passing one to the other silently produced null, which is
     * how a downloaded track stayed unhashed for ever while looking as though it had been tried.
     */
    suspend fun hashFile(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = java.io.File(path)
            if (!file.exists()) return@runCatching null
            file.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }.getOrNull()
    }

    private companion object {
        /**
         * Large enough that a 40 MB FLAC is a few hundred reads, small enough that it is nothing
         * against an app heap. The whole point is that a file is never held in memory at once.
         */
        const val BUFFER_BYTES = 64 * 1024
    }
}
