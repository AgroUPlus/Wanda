package com.wander.android.core.sync

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removing local copies of files the server has confirmed it holds.
 *
 * Under scoped storage an app may only delete media it owns, and a real music library was put
 * there by something else — so this cannot just call `ContentResolver.delete`. The system does it
 * instead, after showing the user its own confirmation dialog, and that dialog needs an
 * [Activity]: it cannot be raised from a Worker. Deletion is therefore always a foreground,
 * user-present step, never part of the background sync.
 *
 * [MediaStore.createTrashRequest] is preferred over `createDeleteRequest`: the files go to the
 * system's 30-day trash instead of vanishing, so a mistake here is recoverable. Both are API 30+,
 * and below that the feature is simply absent rather than half-working — deleting on API 26–29
 * would need `WRITE_EXTERNAL_STORAGE`, a permission this app deliberately does not ask for.
 */
@Singleton
class LocalFileDeleter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Whether this device can delete other apps' media at all. */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Builds one system prompt covering every file at once.
     *
     * Batched deliberately: three hundred separate confirmations is not a feature. Returns null
     * when unsupported or when there is nothing to remove.
     */
    fun buildTrashRequest(uris: List<String>): IntentSender? {
        if (!isSupported || uris.isEmpty()) return null
        val parsed: List<Uri> = uris.mapNotNull { runCatching { it.toUri() }.getOrNull() }
        if (parsed.isEmpty()) return null

        return runCatching {
            MediaStore.createTrashRequest(context.contentResolver, parsed, true).intentSender
        }.getOrNull()
    }

    /** True when the user accepted the system dialog. */
    fun wasAccepted(resultCode: Int): Boolean = resultCode == Activity.RESULT_OK
}
