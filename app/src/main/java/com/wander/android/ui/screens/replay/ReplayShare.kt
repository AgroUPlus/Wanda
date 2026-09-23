package com.wander.android.ui.screens.replay

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.core.content.FileProvider
import com.wander.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Sharing one card of the story as a picture.
 *
 * The card is recorded into a [GraphicsLayer] as it draws, so sharing is a snapshot of exactly
 * what is on screen — covers, shapes and all — without laying the card out a second time
 * off-screen. The file lives in the cache, is overwritten by the next share, and leaves the app
 * only through a read grant on the one URI handed to the share sheet.
 */

/**
 * Draws as normal, and keeps [layer] holding the latest frame over [background] with [footer]
 * along the bottom.
 *
 * The footer exists only in the picture: on screen the rail and the year picker already say what
 * this is, but a card posted on its own is just a big number without it.
 */
internal fun Modifier.replayCapture(
    layer: GraphicsLayer,
    footer: String,
    measurer: TextMeasurer,
    style: TextStyle,
    background: () -> Color,
    content: () -> Color
): Modifier = drawWithContent {
    // Both resolved out here, against this scope, and not inside `record`. While a layer is
    // recording, the scope handed to the block reports its density by asking the scope that
    // started the recording, which asks it back: a `Dp` converted in there recurses until the
    // stack is gone. Everything below is either already in pixels or does not need a density.
    val footerBottom = FooterBottom.toPx()
    val text = measurer.measure(footer, style.copy(color = content().copy(alpha = FooterAlpha)))
    layer.record {
        // The flat colour belongs to the scaffold, not the card, so it is painted in here or
        // the picture would come out with a transparent background.
        drawRect(background())
        this@drawWithContent.drawContent()
        drawText(
            textLayoutResult = text,
            topLeft = Offset(
                x = (size.width - text.size.width) / 2f,
                y = size.height - text.size.height - footerBottom
            )
        )
    }
    drawLayer(layer)
}

/**
 * Writes the captured card to a PNG and opens the system share sheet on it. False when the image
 * could not be written, for the caller to tell the user.
 */
internal suspend fun shareReplayCard(context: Context, layer: GraphicsLayer): Boolean {
    // A software copy: the layer's bitmap is hardware-backed, which PNG encoding does not take.
    val bitmap = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    val file = try {
        withContext(Dispatchers.IO) { writePng(context, bitmap) }
    } catch (cause: IOException) {
        return false
    } finally {
        bitmap.recycle()
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}$ShareAuthoritySuffix", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PngMime
        putExtra(Intent.EXTRA_STREAM, uri)
        // ClipData as well as the extra, so the grant reaches the share sheet's own preview.
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.replay_share_chooser))
    )
    return true
}

private fun writePng(context: Context, bitmap: Bitmap): File {
    val dir = File(context.cacheDir, ShareDir)
    if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create ${dir.name}")
    val file = File(dir, ShareFile)
    file.outputStream().use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, PngQuality, out)) {
            throw IOException("PNG encoding failed")
        }
    }
    return file
}

/** Clear of the gesture bar, which the picture includes but has nothing drawn in. */
private val FooterBottom = 40.dp
private const val FooterAlpha = 0.74f

/** Must match the `<provider>` authority and `res/xml/replay_share_paths.xml`. */
private const val ShareAuthoritySuffix = ".replayshare"
private const val ShareDir = "replay"
private const val ShareFile = "replay-card.png"
private const val PngMime = "image/png"
/** Ignored by PNG, which is lossless; required by the API. */
private const val PngQuality = 100
