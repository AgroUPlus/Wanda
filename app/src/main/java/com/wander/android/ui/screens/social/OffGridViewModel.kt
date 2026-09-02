package com.wander.android.ui.screens.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.p2p.NearbyPeers
import com.wander.android.core.p2p.OffGridPairing
import com.wander.android.core.p2p.OffGridTransport
import com.wander.android.core.sync.P2PServer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Being findable, finding others, and linking to one of them.
 *
 * Nothing here starts on its own. Advertising is a broadcast to a room and a radio link is a link
 * held open; both are battery and both are trackability, paid continuously for an occasional
 * benefit. The user asks, this obliges, and [stop] puts everything back.
 */
@HiltViewModel
internal class OffGridViewModel @Inject constructor(
    private val transport: OffGridTransport,
    private val p2pServer: P2PServer
) : ViewModel() {

    private val _state = MutableStateFlow(OffGridUiState())
    val state: StateFlow<OffGridUiState> = _state.asStateFlow()

    /** The scan, which is the only thing here that costs anything while merely sitting there. */
    private var scanJob: Job? = null

    init {
        _state.value = _state.value.copy(isSupported = transport.isSupported)
    }

    /**
     * Starts telling the room this device is here, and starts listening for others.
     *
     * The server is started first and the promise is read from it rather than from the intention to
     * start it: `servesAudio` is what a peer will find if it connects, so it has to be a fact.
     */
    fun startSharing() {
        if (!transport.isSupported) return
        p2pServer.start()
        viewModelScope.launch {
            // Suspends now, and that is the fix rather than a detail. `startAdvertising` used to
            // return a boolean that was always true — the radio reports its refusal asynchronously,
            // so the message below could never appear no matter how thoroughly the advertisement
            // had been rejected.
            val advertising = transport.startAdvertising(servesAudio = true)
            _state.value = _state.value.copy(
                isAdvertising = advertising.isSuccess,
                // A radio that refused is worth saying plainly, in its own words: "this chipset has
                // no peripheral mode" and "Bluetooth is off" are not the same problem, and neither
                // is something the user can guess from an empty list.
                error = advertising.exceptionOrNull()?.let {
                    "This phone could not start broadcasting: ${it.message}"
                }
            )
            startScanning()
        }
    }

    /** Stops advertising and scanning, and drops any link. What leaving the screen must do. */
    fun stop() {
        transport.stopAdvertising()
        scanJob?.cancel()
        scanJob = null
        viewModelScope.launch { transport.disconnect() }
        _state.value = _state.value.copy(
            isAdvertising = false,
            peers = emptyList(),
            linkedTo = null
        )
    }

    /**
     * Raises a link to [peer] and pairs with it.
     *
     * The three failures are told apart because they mean different things to whoever is holding
     * the phone: nothing answered, the link reached the wrong device, or the grant was not readable.
     * A single "could not connect" would hide the middle one, which is the only one that matters
     * for anybody's safety.
     */
    fun connect(peer: NearbyPeers.Peer) {
        if (_state.value.isConnecting) return
        // Tapping a peer is the gesture that assigns the roles: they host, this device receives.
        // Wi-Fi Direct will not let a group owner join another group, so `WifiDirectLink.connect`
        // stands this device down first. BLE advertising is deliberately left running — being
        // findable costs nothing and lets the user pick somebody else without starting over.
        _state.value = _state.value.copy(isConnecting = true, error = null)
        viewModelScope.launch {
            val result = transport.connect(peer)
            _state.value = _state.value.copy(
                isConnecting = false,
                linkedTo = result.getOrNull()?.let { peer.beacon.deviceId },
                error = result.exceptionOrNull()?.let(::describe)
            )
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun describe(error: Throwable): String = when {
        // The link never came up. `WifiDirectLink` now says which way — a refused scan names the
        // permission, an empty room says so — where this used to advise moving closer whatever the
        // cause, including a nearby-devices grant the user had declined.
        error !is OffGridPairing.PairingException ->
            error.message ?: "No direct link could be formed. Try again, or move closer."
        error.failure is OffGridPairing.Failure.WrongPeer ->
            "The link reached a different device than the one you picked, so it was dropped."
        error.failure is OffGridPairing.Failure.Unreadable ->
            "That device answered with something this phone could not open."
        else -> "That device did not answer."
    }

    /**
     * Rebuilds the list on every sighting.
     *
     * Cancelled by [stop], and the screen calls that when it goes away — a BLE scan left running
     * behind a closed screen is the one thing in this feature that would quietly cost battery all
     * afternoon.
     */
    private fun startScanning() {
        scanJob?.cancel()
        scanJob = transport.nearbyServers()
            .onEach { found -> _state.value = _state.value.copy(peers = found) }
            // A refused scan now ends the flow with its reason instead of staying open and empty.
            // Those two states look identical on screen — nobody here — and only one of them is
            // something the user can act on.
            .catch { error ->
                _state.value = _state.value.copy(
                    error = "This phone could not look for others: ${error.message}"
                )
            }
            .launchIn(viewModelScope)
    }
}
