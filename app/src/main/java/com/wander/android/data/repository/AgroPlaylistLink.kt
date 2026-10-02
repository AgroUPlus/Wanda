package com.wander.android.data.repository

/**
 * A playlist kept on the paired Agro server, named by its id alone: `wanda://playlist?agro=<id>`.
 *
 * Carries no address and no tracks. The receiver asks its *own* paired server for the id, so the
 * link works for accounts on the same server and is useless to anyone else — which is the point of
 * sharing through Agro rather than in the link. Server ids are UUIDs, and nothing else is accepted
 * because the value is sent back to a server.
 */
internal object AgroPlaylistLink {
    private val ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun toUri(id: String): String = "wanda://playlist?" + UniversalLinkCodec.buildQuery(listOf("agro" to id))

    /**
     * The tappable form: the Agro server's `/listen?pl=<id>` page, which hands the app [toUri].
     * A `wanda://` link is not one a chat will open. See `SHARE_LINKS.md` §7a in Agro.
     */
    fun toWebUrl(origin: String, id: String): String = "$origin/listen?pl=$id"

    /** The playlist id in [uri], or null when it is not an Agro playlist link or the id is malformed. */
    fun parse(uri: String): String? {
        if (!UniversalPlaylistLink.matches(uri)) return null
        return UniversalLinkCodec.parseQuery(uri)["agro"]?.trim()?.takeIf { ID.matches(it) }
    }

    /** Whether this link names an Agro playlist at all, malformed id or not. */
    fun isAgroLink(uri: String): Boolean =
        UniversalPlaylistLink.matches(uri) && "agro" in UniversalLinkCodec.parseQuery(uri)
}
