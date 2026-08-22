package com.wander.android.ui.screens.settings

import com.wander.android.data.sources.agro.AgroAuthError
import com.wander.android.data.sources.agro.AgroSignup

/**
 * Where a pairing or signup attempt currently stands.
 *
 * Lives in its own file rather than beside the dialog because four things read it — the dialog, the
 * ViewModel, [SettingsUiState] and [SettingsDialogs] — and only one of them draws it.
 */
internal sealed interface AgroPairingState {
    data object Idle : AgroPairingState
    data object Connecting : AgroPairingState

    /** Paired and proven: the server answered a registration with this device's petname. */
    data class Paired(val username: String, val petname: String) : AgroPairingState

    /**
     * An account was created. Not the same as being paired — on a server that queues signups for
     * approval, this account cannot log in yet, and the passphrase in here is the only copy that
     * will ever exist.
     */
    data class Registered(val signup: AgroSignup) : AgroPairingState

    data class Failed(val error: AgroAuthError) : AgroPairingState
}

/**
 * Whether the credentials already in storage still work.
 *
 * Distinct from [AgroPairingState], which is about an attempt in progress. This is about the
 * standing pairing, and it is the thing Settings had no way to report: a revoked token or a
 * suspended account produced no event on the device, so the connection row went on looking healthy
 * while every request it made was refused.
 */
internal sealed interface AgroConnectionState {
    data object Unpaired : AgroConnectionState
    data object Checking : AgroConnectionState
    data class Connected(val username: String, val role: String) : AgroConnectionState

    /** The server no longer recognises this device's token. Re-pairing is the only fix. */
    data object Rejected : AgroConnectionState

    /** The token is fine; the account behind it is pending approval or suspended. */
    data class NotActive(val detail: String) : AgroConnectionState

    /** No answer. Says nothing about whether the credentials are good. */
    data object Unreachable : AgroConnectionState
}
