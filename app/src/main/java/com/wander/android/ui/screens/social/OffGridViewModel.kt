package com.wander.android.ui.screens.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.p2p.NearbyPeers
import com.wander.android.core.p2p.OffGridLink
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
    private val p2pServer: P2PServer,
    private val listenAlong: com.wander.android.data.repository.ListenAlongController
) : ViewModel() {

    private val _state = MutableStateFlow(OffGridUiState())
    val state: StateFlow<OffGridUiState> = _state.asStateFlow()

    /** The scan, which is the only thing here that costs anything while merely sitting there. */
    private var scanJob: Job? = null

    init {
        _state.value = _state.value.copy(isSupported = transport.isSupported)
        // Collected for the life of the view model rather than started by `startSharing`, because
        // the device that gets *connected to* never calls that path — someone else does, and this
        // is the only way its screen learns about it.
        transport.links
            .onEach { links -> _state.value = _state.value.copy(links = links) }
            .launchIn(viewModelScope)
        // Read rather than remembered. The radio outlives this view model, so a screen returned to
        // must show what is actually running — otherwise it offers "Be findable" for a phone that
        // has been findable the whole time.
        // The session is owned by the controller, so this screen reads it rather than tracking
        // it — coming back here must show a follow that is still running.
        listenAlong.session
            .onEach { session ->
                _state.value = _state.value.copy(
                    isFollowing = session != null,
                    followingNowPlaying = session?.nowPlaying?.let {
                        listOfNotNull(it.artistName.ifBlank { null }, it.trackTitle).joinToString(" — ")
                    },
                    followingUnresolvable = session?.unresolvable
                )
            }
            .launchIn(viewModelScope)
        transport.isAdvertising
            .onEach { advertising ->
                _state.value = _state.value.copy(isAdvertising = advertising)
                if (advertising && scanJob == null) startScanning()
            }
            .launchIn(viewModelScope)
    }

    /**
     * Starts telling the room this device is here, and starts listening for others.
     *
     * The server is started first and the promise is read from it rather than from the intention to
     * start it: `servesAudio` is what a peer will find if it connects, so it has to be a fact.
     */
    fun startSharing() {
        if (!transport.isSupported) return
        viewModelScope.launch {
            // Awaited, so `servesAudio` below is a fact rather than an intention. Starting the
            // server used to be fire-and-forget: a port already taken by another build of Wanda
            // failed in the log, this went on to advertise that it would serve audio, and peers
            // paired with a device that could never answer them.
            val serving = p2pServer.start()
            if (serving.isFailure) {
                _state.value = _state.value.copy(
                    isAdvertising = false,
                    error = serving.exceptionOrNull()?.message
                        ?: "This phone could not start serving audio."
                )
                return@launch
            }

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

    /**
     * Stops advertising, stops scanning, and drops every link.
     *
     * What the **"Stop sharing" button** means, and nothing else calls it. Leaving the screen must
     * not do this — see [onScreenLeft]. It used to, and that made the feature impossible to use:
     * you connected to a phone, navigated to the player to actually listen to something, and the
     * link you had just made was torn down on the way out.
     */
    fun stop() {
        transport.stopAdvertising()
        scanJob?.cancel()
        scanJob = null
        viewModelScope.launch { transport.disconnect() }
        _state.value = _state.value.copy(peers = emptyList())
    }

    /**
     * What leaving the screen means: stop looking, keep everything the user turned on.
     *
     * The scan is the only part of this that is a function of *looking at the list*, and it is the
     * expensive part — a BLE scan left running behind a closed screen would quietly cost battery
     * all afternoon. Advertising and any live link are decisions the user made deliberately, and
     * they end when the user ends them, not when a screen goes away.
     */
    fun onScreenLeft() {
        scanJob?.cancel()
        scanJob = null
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
            // `links` is not set here: the transport publishes it, and both ends read it from
            // there. Writing it locally on success is what made the connection a fact only the
            // initiator possessed.
            _state.value = _state.value.copy(
                isConnecting = false,
                error = result.exceptionOrNull()?.let(::describe)
            )
        }
    }

    /**
     * Ends one link without giving up on being findable.
     *
     * Distinct from [stop], which puts the whole feature away. Someone who wants to stop serving
     * one phone usually does not want to disappear from the room, and having only the second was
     * why "stop sharing" was the only way to end a connection.
     */
    fun disconnect(link: OffGridLink) {
        viewModelScope.launch { transport.disconnect(link) }
    }

    /**
     * Starts following what the linked peer is playing, with no server involved.
     *
     * The link already carries audio; what it did not carry was which audio. Offered here rather
     * than on the friends list because there is no friend to offer it against: off-grid neither
     * device has an account, and the only thing either can name is the device at the other end.
     */
    fun listenAlongOffGrid() {
        viewModelScope.launch {
            val started = listenAlong.startOffGrid()
            _state.value = _state.value.copy(
                isFollowing = started.isSuccess,
                error = started.exceptionOrNull()?.message
            )
        }
    }

    /** Stops following, leaving the link and the music where they are. */
    fun stopListenAlong() {
        viewModelScope.launch {
            listenAlong.stop()
            _state.value = _state.value.copy(isFollowing = false)
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
