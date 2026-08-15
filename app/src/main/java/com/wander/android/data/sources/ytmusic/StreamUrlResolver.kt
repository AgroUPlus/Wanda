package com.wander.android.data.sources.ytmusic

import com.zemer.cipher.CipherDeobfuscator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a `/player` audio format into a URL googlevideo will actually serve.
 *
 * Two transforms, both delegated to zemer-cipher's WebView-backed player-JS runner:
 *
 * - **signature**: web clients (`WEB_EMBEDDED`, `WEB_REMIX`) return a `signatureCipher` blob
 *   instead of a `url`. Only YouTube's obfuscated player JS can unscramble it.
 * - **`n` param**: every googlevideo URL carries a throttling nonce. Left untransformed the
 *   stream still connects, then trickles at a few kB/s — a stall that looks like a slow network
 *   rather than a bug, so it is applied unconditionally.
 */
@Singleton
class StreamUrlResolver @Inject constructor() {

    suspend fun resolve(format: JsonObject, videoId: String): String = withContext(Dispatchers.IO) {
        val direct = format["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val signed = direct ?: deobfuscate(format, videoId)

        // A failed n-transform is not fatal on its own — the URL plays, just throttled — but
        // swallowing it would hide the cause of a stall, so it surfaces.
        try {
            CipherDeobfuscator.transformNParamInUrl(signed)
        } catch (e: Exception) {
            throw IOException("Could not transform the YouTube stream throttling parameter", e)
        }
    }

    private suspend fun deobfuscate(format: JsonObject, videoId: String): String {
        val cipher = format.signatureCipher()?.takeIf { it.isNotBlank() }
            ?: throw IOException("YouTube Music returned no playable audio for this track")
        val url = try {
            CipherDeobfuscator.deobfuscateStreamUrl(cipher, videoId)
        } catch (e: Exception) {
            throw IOException("Could not unscramble the YouTube stream signature", e)
        }
        // The deobfuscator answers null when the player JS it fetched no longer exposes the
        // signature function — a rotation, not a property of this track.
        return url ?: throw IOException("Could not unscramble the YouTube stream signature")
    }
}
