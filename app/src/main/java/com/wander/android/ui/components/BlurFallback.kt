package com.wander.android.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.asComposeRenderEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/** How much the content behind the player sheet blurs at the peak of a predictive-back gesture. */
private const val MaxBlurRadiusPx = 24f

/** How dark the pre-API-31 scrim fallback gets at the peak of the gesture. */
private const val MaxScrimAlpha = 0.5f

/**
 * Blurs (or, below API 31, just dims) whatever this is applied to, in proportion to
 * [progress] — used to blur the screen behind the player sheet while a predictive-back gesture is
 * shrinking it away, clearing back to nothing once the gesture ends or is cancelled.
 *
 * `RenderEffect.createBlurEffect` needs API 31; minSdk here is 26, so 26-30 falls back to a plain
 * scrim instead of silently doing nothing.
 */
fun Modifier.predictiveBackBlur(progress: () -> Float): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        graphicsLayer {
            val amount = progress()
            renderEffect = if (amount > 0f) {
                RenderEffect
                    .createBlurEffect(
                        MaxBlurRadiusPx * amount,
                        MaxBlurRadiusPx * amount,
                        Shader.TileMode.CLAMP
                    )
                    .asComposeRenderEffect()
            } else {
                null
            }
        }
    } else {
        drawWithContent {
            drawContent()
            val amount = progress()
            if (amount > 0f) {
                drawRect(Color.Black.copy(alpha = amount * MaxScrimAlpha))
            }
        }
    }
