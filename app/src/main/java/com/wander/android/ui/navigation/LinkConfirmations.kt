package com.wander.android.ui.navigation

import androidx.annotation.StringRes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A link that wants to change something about the user's account, waiting on a yes.
 *
 * Any web page or app can fire a `wanda:` or `agro:` link, so a link that pairs an account must
 * not act on its own. [messageArgs] are shown as-is so the user sees exactly which server or
 * person the link names.
 */
class LinkConfirmation(
    @StringRes val title: Int,
    @StringRes val message: Int,
    val messageArgs: List<String>,
    @StringRes val confirmLabel: Int,
    val onConfirm: suspend () -> Unit
)

/**
 * Holds the one link confirmation the shell should show, the same way [DeepLinkRouter] holds a
 * route: the intent handler decides, the shell asks. A StateFlow so a link that starts the process
 * cold is still waiting when the shell first composes.
 */
@Singleton
class LinkConfirmations @Inject constructor() {
    private val _pending = MutableStateFlow<LinkConfirmation?>(null)
    val pending: StateFlow<LinkConfirmation?> = _pending.asStateFlow()

    fun ask(confirmation: LinkConfirmation) {
        _pending.value = confirmation
    }

    /** Clears [confirmation] and returns whether it was still the pending one. */
    fun resolve(confirmation: LinkConfirmation): Boolean =
        _pending.compareAndSet(confirmation, null)
}
