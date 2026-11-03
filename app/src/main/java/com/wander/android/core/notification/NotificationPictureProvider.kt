package com.wander.android.core.notification

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Hands the system the covers shown inside drop notifications, which `MessagingStyle` takes only
 * as a content URI.
 *
 * Kept in this process's memory under a random name rather than written to a file: nothing is left
 * on disk to clean up, and the name says nothing about the track or who sent it. A cover the
 * process lost (it was killed) is simply not drawn, and the message's text stands in for it. Not
 * exported: the system reads it through the grant the notification carries.
 */
internal class NotificationPictureProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri): String? = pictures.get(uri.lastPathSegment.orEmpty())?.let { MIME_TYPE }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val bytes = pictures.get(uri.lastPathSegment.orEmpty()) ?: throw FileNotFoundException("unknown picture")
        val (read, write) = ParcelFileDescriptor.createPipe()
        thread(name = "notification-picture") {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(bytes) }
        }
        return read
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        args: Array<out String>?,
        sort: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

    companion object {
        const val MIME_TYPE = "image/jpeg"

        // A dozen or so covers at notification size; older ones fall out first.
        private const val CACHE_BYTES = 6 * 1024 * 1024
        private const val JPEG_QUALITY = 85

        // Android 8.x does not grant a notification's URIs to the system by itself; later ones do.
        private const val SYSTEM_UI = "com.android.systemui"

        private val pictures = object : LruCache<String, ByteArray>(CACHE_BYTES) {
            override fun sizeOf(key: String, value: ByteArray) = value.size
        }

        /** Keeps [picture] for the system to read and answers where it can. */
        fun publish(context: Context, picture: Bitmap): Uri {
            val bytes = ByteArrayOutputStream().use { out ->
                picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
            val name = UUID.randomUUID().toString()
            pictures.put(name, bytes)
            val uri = Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.notificationpictures")
                .appendPath(name)
                .build()
            context.grantUriPermission(SYSTEM_UI, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return uri
        }
    }
}
