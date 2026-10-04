package com.wander.android.core.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.core.security.SecureStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * The daily cloud backup. Does nothing — and succeeds — when the toggle is off or the server has no
 * vault: both can change between runs, and neither is a failure worth retrying.
 */
@HiltWorker
internal class CloudBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val store: CloudBackupStore,
    private val secureStorage: SecureStorage
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!secureStorage.cloudBackup.value || !store.isAvailable) return Result.success()
        return store.backUpNow().fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                // The network or the server is worth another go, on WorkManager's backoff; anything
                // else — a key that cannot seal, say — will not work tomorrow either.
                if (error is IOException) Result.retry() else Result.failure()
            }
        )
    }
}
