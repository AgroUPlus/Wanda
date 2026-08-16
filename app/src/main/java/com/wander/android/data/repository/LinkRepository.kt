package com.wander.android.data.repository

import android.net.Uri
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroGraphQl
import com.wander.android.data.sources.navidrome.NavidromeSource
import com.wander.android.data.sources.ytmusic.youTubeVideoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a link someone sent you into a track this app can play.
 *
 * Resolves YouTube and YouTube Music recordings, short-domain links, and Navidrome share links.
 */
@Singleton
class LinkRepository @Inject constructor(
    private val musicRepository: MusicRepository,
    private val shareLinkRewriter: ShareLinkRewriter,
    private val agroGraphQl: AgroGraphQl,
    private val secureStorage: SecureStorage
) {

    /** Whether [uri] is a link this app can do something with. Cheap, no network. */
    fun canOpen(uri: Uri): Boolean {
        if (uri.getQueryParameter("id") != null && uri.scheme in setOf("wanda", "https")) return true
        val targetUri = target(uri)
        return youTubeVideoId(targetUri) != null || isNavidromeLink(targetUri)
    }

    private fun isNavidromeLink(uri: Uri): Boolean {
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return false
        val navHost = runCatching {
            Uri.parse(secureStorage.navidromeServerUrl).host?.lowercase()?.removePrefix("www.")
        }.getOrNull()
        val agroHosts = secureStorage.agroShareHosts
            .split(',')
            .mapNotNull { it.trim().lowercase().takeIf(String::isNotBlank) }
        val matchesHost = (navHost != null && host == navHost) || host in agroHosts
        val isSharePath = uri.pathSegments.contains("share") || uri.fragment?.contains("share") == true
        return matchesHost || isSharePath
    }

    /**
     * The link itself, or what it stands for. A link shared through the user's own domain wraps
     * one of these, so it has to be unwrapped before anything can be made of it.
     */
    private fun target(uri: Uri): Uri = shareLinkRewriter.unwrap(uri) ?: uri

    suspend fun resolve(uri: Uri): Result<UnifiedTrack> = withContext(Dispatchers.IO) {
        var targetUri = target(uri)

        // If it carries a short link UID, resolve the real target via Agro if paired
        val shortId = uri.getQueryParameter("id")
        if (shortId != null && agroGraphQl.isConfigured) {
            val resolved = runCatching {
                val query = "query ResolveShortLink(\$id: String!) { resolveShortLink(id: \$id) }"
                val vars = buildJsonObject { put("id", shortId) }
                agroGraphQl.execute(query, vars).getOrNull()
                    ?.get("resolveShortLink")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            if (!resolved.isNullOrBlank()) {
                targetUri = Uri.parse(resolved)
            }
        }

        // 1. YouTube track resolution
        val videoId = youTubeVideoId(targetUri)
        if (videoId != null) {
            val source = musicRepository.sources.firstOrNull { it.sourceType == SourceType.YTMUSIC }
                ?: return@withContext Result.failure(
                    IllegalStateException("YouTube Music is unavailable.")
                )
            return@withContext source.getTrack("ytm:$videoId").mapCatching { track ->
                track ?: throw IOException("YouTube Music has nothing playable behind that link.")
            }
        }

        // 2. Navidrome share / track resolution
        val navSource = musicRepository.sources.firstOrNull { it.sourceType == SourceType.NAVIDROME } as? NavidromeSource
        if (navSource != null) {
            val shareId = targetUri.pathSegments.lastOrNull { it.isNotBlank() } ?: targetUri.toString()
            val navResult = navSource.resolveShare(shareId).fold(
                onSuccess = { Result.success(it) },
                onFailure = { navSource.resolveShare(targetUri.toString()) }
            )
            if (navResult.isSuccess) {
                return@withContext navResult
            }
        }

        Result.failure(IllegalArgumentException("That link doesn't point at a track Wanda can play."))
    }
}
