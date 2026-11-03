package com.wander.android.core.playback

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.wander.android.core.network.HttpClientFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * Where [SavedQueue] lives between processes: one small file, not a Room table.
 *
 * The queue is the player's working state rather than library data — most of what it holds was
 * never written to Room, and none of it is queried — so a table would buy a migration and nothing
 * else. The file sits in `noBackupFilesDir`: a queue belongs to this device's session and has no
 * business arriving on a new phone by device transfer.
 *
 * Writes are serialised on one IO lane and outlive whoever asked for them, which is what lets the
 * service save its last state from `onDestroy` after its own scope is gone.
 */
@Singleton
internal class SavedQueueStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val file by lazy { AtomicFile(File(context.noBackupFilesDir, FILE_NAME)) }

    /** Replaces what is saved with [queue], or removes it when there is no queue to keep. */
    fun save(queue: SavedQueue?) {
        writes.launch {
            if (queue == null) {
                file.delete()
                return@launch
            }
            val bytes = HttpClientFactory.jsonConfig.encodeToString(SavedQueue.serializer(), queue).toByteArray()
            val out = try {
                file.startWrite()
            } catch (e: IOException) {
                Log.w(TAG, "Could not open the saved queue for writing: ${e.javaClass.simpleName}")
                return@launch
            }
            try {
                out.write(bytes)
                file.finishWrite(out)
            } catch (e: IOException) {
                file.failWrite(out)
                Log.w(TAG, "Could not write the saved queue: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * The saved queue, or null when there is none. A file that cannot be read back is deleted and
     * reported as none, rather than retried at every start.
     */
    suspend fun load(): SavedQueue? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext null
        try {
            HttpClientFactory.jsonConfig.decodeFromString(SavedQueue.serializer(), file.readFully().decodeToString())
        } catch (e: IOException) {
            Log.w(TAG, "Could not read the saved queue: ${e.javaClass.simpleName}")
            null
        } catch (e: SerializationException) {
            Log.w(TAG, "Discarding a saved queue this version cannot read: ${e.javaClass.simpleName}")
            file.delete()
            null
        }
    }

    private companion object {
        const val TAG = "SavedQueueStore"
        const val FILE_NAME = "playback_queue.json"
    }
}
