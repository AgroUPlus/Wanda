package com.wander.android.ui.components.player

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

/**
 * The organic, slowly-morphing glow behind Now Playing's cover on Android 13+, in place of
 * [MorphingArtwork]'s plain radial gradient.
 *
 * AGSL (`RuntimeShader`) needs API 33 — [MorphingArtwork] already draws a static radial gradient
 * for everything below that, or when reduce-motion is on, and this is deliberately the same shape
 * of brush so swapping one for the other is the only change either caller has to make.
 *
 * Time is frozen at zero under reduce motion rather than the shader not being used at all: a
 * paused fluid gradient is still a richer, more organic *shape* than three flat gradient stops —
 * only the animation is what reduce motion asks to remove.
 */
@Composable
internal fun fluidAmbientBrush(color1: Color, color2: Color, backdrop: Color, reduceMotion: Boolean): Brush {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return staticFallbackBrush(color1, color2, backdrop)
    }

    val transition = rememberInfiniteTransition(label = "fluidAmbient")
    val time by if (reduceMotion) {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = LOOP_SECONDS,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = (LOOP_SECONDS * 1000).toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fluidAmbientTime"
        )
    }

    val shader = remember { RuntimeShader(AGSL_SOURCE) }
    return remember(shader, color1, color2, backdrop, time) {
        shader.setFloatUniform("time", time)
        shader.setColorUniform("color1", color1.toArgb())
        shader.setColorUniform("color2", color2.toArgb())
        shader.setColorUniform("color3", backdrop.toArgb())
        FluidShaderBrush(shader)
    }
}

/**
 * Sets the `resolution` uniform from the actual draw size.
 *
 * Compose's `ShaderBrush` calls [createShader] with the size being painted rather than exposing it
 * as a uniform on its own, so this is the one place that size is available — a plain
 * `ShaderBrush(shader)` would leave `resolution` at its zero default and every `uv` coordinate
 * would be the raw pixel count instead of `0..1`.
 */
private class FluidShaderBrush(private val shader: RuntimeShader) : ShaderBrush() {
    override fun createShader(size: Size): android.graphics.Shader {
        shader.setFloatUniform("resolution", size.width, size.height)
        return shader
    }
}

/** What every caller below API 33, or under reduce motion with no shader wanted, gets instead. */
private fun staticFallbackBrush(color1: Color, color2: Color, backdrop: Color): Brush =
    Brush.radialGradient(
        colors = listOf(color1.copy(alpha = 0.55f), color2.copy(alpha = 0.22f), backdrop.copy(alpha = 0f))
    )

/** One loop of the aurora's drift, in seconds. Slow: this sits behind a cover, not on top of it. */
private const val LOOP_SECONDS = 24f

/**
 * Two-octave drifting field blended between [color1] and [color2], with [color3] vignetting the
 * edges — the AGSL equivalent of the three-stop radial gradient it replaces, but organic rather
 * than a fixed ring. `resolution` is set by [FluidShaderBrush] from the actual draw size.
 */
private const val AGSL_SOURCE = """
    uniform float time;
    uniform half4 color1;
    uniform half4 color2;
    uniform half4 color3;
    uniform float2 resolution;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / max(resolution, float2(1.0, 1.0));
        float t = time * 0.25;

        float wave1 = sin((uv.x * 2.6 + t) * 3.14159) * 0.5 + 0.5;
        float wave2 = sin((uv.y * 3.1 - t * 1.4 + 1.7) * 3.14159) * 0.5 + 0.5;
        float wave3 = sin(((uv.x + uv.y) * 1.8 + t * 0.7) * 3.14159) * 0.5 + 0.5;
        float mixer = clamp((wave1 * 0.4 + wave2 * 0.4 + wave3 * 0.2), 0.0, 1.0);

        half4 base = mix(color1, color2, mixer);

        float centerDist = distance(uv, float2(0.5, 0.5));
        float vignette = smoothstep(0.85, 0.1, centerDist);
        half4 result = mix(color3, base, vignette);
        return result * result.a;
    }
"""
