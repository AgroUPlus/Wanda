package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import com.wander.android.data.sources.agro.AgroDrop

/** What the inbox screen draws. */
@Immutable
internal data class InboxUiState(
    val received: List<AgroDrop> = emptyList(),
    val sent: List<AgroDrop> = emptyList(),
    val unread: Int = 0,
    /**
     * True only for the very first load.
     *
     * Room answers immediately with whatever was cached, so a spinner after that would flash on
     * every refresh over data that is already on screen.
     */
    val loading: Boolean = true,
    /** The drop currently being looked up, so its row can say so instead of seeming inert. */
    val resolving: String? = null
)
