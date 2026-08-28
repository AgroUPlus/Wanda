package com.wander.android.core.audio.fingerprint

/**
 * In-place iterative radix-2 FFT.
 *
 * Hand-rolled rather than pulled in: the whole of what the fingerprinter needs is one power-of-two
 * transform over a few hundred frames per track, and a DSP dependency for that would be more code
 * to audit than the twenty lines it replaces — on a project whose rule is that nothing third-party
 * phones home.
 *
 * Iterative, not recursive: this runs once per frame over an entire library, and the allocation a
 * recursive split does per level is the difference between indexing a record in a second and in a
 * minute.
 */
internal object Fft {

    /**
     * Transforms [real] and [imag] in place. Both must be the same power-of-two length.
     *
     * Only the first half of the result is meaningful for real input — the second half is its
     * mirror — which is why callers read `size / 2` bins out of it.
     */
    fun transform(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n == imag.size) { "real and imaginary parts must match in length" }
        require(n > 0 && n and (n - 1) == 0) { "size must be a power of two, was $n" }

        // Bit-reversal permutation: reorders the input so the butterflies below can run over
        // contiguous strides instead of chasing indices.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wReal = Math.cos(angle).toFloat()
            val wImag = Math.sin(angle).toFloat()
            var i = 0
            while (i < n) {
                // The twiddle factor is advanced by repeated complex multiplication rather than
                // recomputed with cos/sin per butterfly. Single-precision drift over a 1024-point
                // transform is far below the peak-picking resolution downstream.
                var curReal = 1f
                var curImag = 0f
                for (k in 0 until len / 2) {
                    val uReal = real[i + k]
                    val uImag = imag[i + k]
                    val vReal = real[i + k + len / 2] * curReal - imag[i + k + len / 2] * curImag
                    val vImag = real[i + k + len / 2] * curImag + imag[i + k + len / 2] * curReal
                    real[i + k] = uReal + vReal
                    imag[i + k] = uImag + vImag
                    real[i + k + len / 2] = uReal - vReal
                    imag[i + k + len / 2] = uImag - vImag
                    val nextReal = curReal * wReal - curImag * wImag
                    curImag = curReal * wImag + curImag * wReal
                    curReal = nextReal
                }
                i += len
            }
            len = len shl 1
        }
    }
}
