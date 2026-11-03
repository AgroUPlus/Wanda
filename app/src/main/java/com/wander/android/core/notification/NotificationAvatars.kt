package com.wander.android.core.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Size
import coil3.toBitmap
import com.wander.android.core.database.dao.FriendDao
import com.wander.android.ui.components.avatarPaletteOf
import com.wander.android.ui.components.monogramOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A friend's face for a notification: their profile photo when they have published one and it
 * loads, otherwise the same colours and initial the app draws for them in Compose.
 *
 * Reaches into the UI package for the palette and the monogram on purpose. The point of the drawn
 * avatar is that it matches the one beside the same name in the inbox; a second palette kept here
 * would drift from it the first time either changed.
 *
 * Bitmaps are software ones ([allowHardware] off): a notification is parcelled to the system, and a
 * hardware bitmap cannot cross that boundary.
 */
@Singleton
internal class NotificationAvatars @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val friendDao: FriendDao
) {
    /** A person as the shade shows them: the name the app shows them by, and their face. */
    data class Face(val name: String, val avatar: Bitmap)

    /**
     * Always answers: a photo that cannot be had falls back to the drawn avatar, never to nothing,
     * and a person this device has no profile for yet (a brand-new request) goes by their handle.
     */
    suspend fun face(username: String): Face {
        val friend = friendDao.find(username)
        val photo = friend?.avatarUrl?.takeIf { it.isNotBlank() }?.let { load(it, AVATAR_PX) }
        val name = friend?.displayName?.takeIf { it.isNotBlank() } ?: username
        return Face(name, photo?.let(::circle) ?: drawn(username))
    }

    /**
     * A cover or other picture at roughly [edgePx], or null if it cannot be had. Null on any
     * failure, by design: a drop with no sleeve is still a drop, and a notification that never
     * arrives because an image 404'd would be the worse bug.
     */
    suspend fun load(url: String, edgePx: Int): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size(edgePx, edgePx))
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .build()
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    /** The inbox's gradient disc with the person's initial on it, at notification size. */
    private fun drawn(username: String): Bitmap {
        val palette = avatarPaletteOf(username)
        val bitmap = createBitmap(AVATAR_PX, AVATAR_PX)
        val canvas = Canvas(bitmap)
        val edge = AVATAR_PX.toFloat()
        val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, edge, edge,
                palette.bgStart.toArgb(), palette.bgEnd.toArgb(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(edge / 2, edge / 2, edge / 2, ground)
        val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.featureColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = edge * MONOGRAM_SCALE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Centred on the glyph box rather than the baseline, so the letter sits in the middle.
        val baseline = edge / 2 - (letter.descent() + letter.ascent()) / 2
        canvas.drawText(monogramOf(username), edge / 2, baseline, letter)
        return bitmap
    }

    /** Crops a photo to the disc the drawn avatar is, so the two kinds sit alike in the shade. */
    private fun circle(source: Bitmap): Bitmap {
        val bitmap = createBitmap(AVATAR_PX, AVATAR_PX)
        val scale = AVATAR_PX.toFloat() / minOf(source.width, source.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(
                    android.graphics.Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            (AVATAR_PX - source.width * scale) / 2,
                            (AVATAR_PX - source.height * scale) / 2
                        )
                    }
                )
            }
        }
        val half = AVATAR_PX / 2f
        Canvas(bitmap).drawCircle(half, half, half, paint)
        return bitmap
    }

    private companion object {
        /** Comfortably above what the shade draws a person's icon at, on any density. */
        const val AVATAR_PX = 192
        const val MONOGRAM_SCALE = 0.45f
    }
}
