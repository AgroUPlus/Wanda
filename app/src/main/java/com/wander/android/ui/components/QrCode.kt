package com.wander.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * A QR code, drawn straight onto a Canvas.
 *
 * No bitmap in between. The matrix is a grid of booleans and the target is a square of known size,
 * so drawing rectangles is both simpler and sharper than scaling a small bitmap up — a
 * nearest-neighbour upscale of a 33-module code is exactly the blurry result that makes a code
 * hard to scan.
 *
 * [dark] and [light] are passed in rather than read from the theme: a QR code needs real contrast
 * to be readable, and a code rendered in a surface tint against a surface is a code no camera will
 * find. Callers give it black on white and mean it.
 */
@Composable
internal fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    dark: Color = Color.Black,
    light: Color = Color.White
) {
    val matrix = remember(content) { encode(content) } ?: return

    Canvas(modifier = modifier) {
        val modules = matrix.width
        // Floored to whole pixels, so no module is a fraction wider than its neighbour. A
        // half-pixel seam between two dark modules is what a scanner reads as a gap.
        val module = kotlin.math.floor(size.minDimension / modules)
        val drawn = module * modules
        val originX = (size.width - drawn) / 2f
        val originY = (size.height - drawn) / 2f

        drawRect(color = light, topLeft = Offset(originX, originY), size = Size(drawn, drawn))
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (!matrix.get(x, y)) continue
                drawRect(
                    color = dark,
                    topLeft = Offset(originX + x * module, originY + y * module),
                    size = Size(module, module)
                )
            }
        }
    }
}

/**
 * Null when the content cannot be encoded at all, which for a code this short means never — but
 * `QRCodeWriter` throws rather than returning, and a screen must not crash because a string was
 * unexpected.
 *
 * Error correction stays at the default M. A friend code is looked at from thirty centimetres on
 * a lit screen, not printed on a box; buying resilience with a denser grid would make it *harder*
 * to scan, not easier. The margin is 1 module rather than the default 4 — the panel around it
 * already provides quiet space, and four modules of it inside the square wastes a third of the
 * width the code has to be read at.
 */
private fun encode(content: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
    )
}.getOrNull()
