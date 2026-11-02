package com.wander.android.core.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a Bluetooth audio output — earbuds, a headset, a speaker, a hearing aid — is connected.
 *
 * Exists for one rule in [offloadAllowed]: no audio offload while one is. Offloaded playback
 * routed over Bluetooth went silent on some phones — the track kept playing, the earbuds heard
 * nothing, and the sound only came back when a volume key made the system re-apply the stream
 * volume. A connected output is used rather than the track's current route because media follows
 * a newly connected headset by default, and the device list is what changes the moment it does.
 *
 * Kept up to date by an [AudioDeviceCallback] for the life of the process, on the main thread.
 */
@Singleton
class BluetoothAudioOutput @Inject constructor(@ApplicationContext context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val _connected = MutableStateFlow(isConnectedNow())
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    init {
        audioManager.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    _connected.value = isConnectedNow()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    _connected.value = isConnectedNow()
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    private fun isConnectedNow(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in BLUETOOTH_OUTPUT_TYPES }

    private companion object {
        /** Media outputs only: SCO is the call path, and media is never routed to it. */
        val BLUETOOTH_OUTPUT_TYPES: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AudioDeviceInfo.TYPE_HEARING_AID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
        }
    }
}
