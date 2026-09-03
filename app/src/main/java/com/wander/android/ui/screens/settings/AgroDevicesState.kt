package com.wander.android.ui.screens.settings

import androidx.compose.runtime.Immutable
import com.wander.android.data.sources.agro.AgroHandoffState
import com.wander.android.data.sources.agro.AgroNode

/**
 * The paired devices and the handoff offered by them.
 *
 * Separate from [SettingsUiState] because it comes from a different ViewModel — the same singleton
 * `AgroSessionViewModel` the resume card reads, so the two can never disagree about who is
 * listening.
 */
@Immutable
internal data class AgroDevicesState(
    val devices: List<AgroNode>,
    val handoff: AgroHandoffState?,
    val isResuming: Boolean
)
