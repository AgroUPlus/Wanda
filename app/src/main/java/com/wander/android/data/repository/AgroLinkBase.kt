package com.wander.android.data.repository

import android.net.Uri
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroGraphQl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The origin a paired Agro server is reached on by someone tapping a link: the share domain
 * configured on that server when there is one, otherwise the address this device pairs with.
 *
 * The server's scheme is kept rather than forced to `https`, so a link names an address the server
 * actually answers on; see `ShareLinkRewriter.shareBase` for why that is `https` in practice.
 */
@Singleton
class AgroLinkBase @Inject constructor(
    private val secureStorage: SecureStorage,
    private val agroGraphQl: AgroGraphQl
) {
    /** Null when no Agro server is paired. */
    fun origin(): String? {
        if (!agroGraphQl.isConfigured) return null
        secureStorage.agroShareDomain.value.takeIf { it.isNotBlank() }?.let { return "https://$it" }
        return serverOrigin()
    }

    /** The paired server's own address, ignoring any share domain. */
    fun serverOrigin(): String? {
        if (!agroGraphQl.isConfigured) return null
        val server = runCatching { Uri.parse(secureStorage.agroServerUrl) }.getOrNull() ?: return null
        val scheme = server.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
        val authority = server.authority?.takeIf(String::isNotBlank) ?: return null
        return "$scheme://$authority"
    }
}
