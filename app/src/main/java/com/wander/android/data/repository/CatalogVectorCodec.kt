package com.wander.android.data.repository

import kotlin.math.roundToInt

/**
 * Handles int8 vector quantization and dequantization for the Agro catalogue wire protocol.
 */
internal object CatalogVectorCodec {
    const val INT8_SCALE = 127f
    private const val HEX = "0123456789abcdef"

    /**
     * Packs vectors to hex int8 — a quarter of float32, for a change in cosine similarity that
     * does not reach the second decimal place.
     */
    fun quantiseToHex(vectors: Array<FloatArray>): String {
        val out = StringBuilder(vectors.sumOf { it.size } * 2)
        for (vector in vectors) {
            for (value in vector) {
                val q = (value * INT8_SCALE).roundToInt().coerceIn(-127, 127)
                out.append(HEX[(q shr 4) and 0xF]).append(HEX[q and 0xF])
            }
        }
        return out.toString()
    }

    /** Inverse of [quantiseToHex]. Null when the blob is not a whole number of vectors. */
    fun unpackHex(hex: String, dim: Int): Array<FloatArray>? {
        if (dim <= 0 || hex.length % 2 != 0) return null
        val bytes = hex.length / 2
        if (bytes == 0 || bytes % dim != 0) return null
        return runCatching {
            Array(bytes / dim) { segment ->
                FloatArray(dim) { d ->
                    val at = (segment * dim + d) * 2
                    hex.substring(at, at + 2).toInt(16).toByte() / INT8_SCALE
                }
            }
        }.getOrNull()
    }
}
