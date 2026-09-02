package com.wander.android.core.p2p

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Finding other Wanda devices in the room, and being findable.
 *
 * BLE is used only to say "I am here" and to hear it back. It carries no audio and no library: at a
 * few hundred kilobits it would take a day to move an album, and that is what [WifiDirectLink] is
 * for. What BLE buys is that discovery costs almost nothing — a phone can advertise for hours
 * without a noticeable battery cost, where keeping a Wi-Fi Direct group alive to be discoverable
 * cannot.
 *
 * Advertising is a deliberate act with a lifetime, never a background default. A device that
 * broadcasts continuously is a device that can be followed between rooms by anyone with a scanner,
 * so this starts when the user asks to share and stops when they stop.
 */
@Singleton
internal class BleDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private companion object {
        const val TAG = "BleDiscovery"
    }

    private val manager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val serviceUuid = ParcelUuid.fromString(OffGridBeacon.SERVICE_UUID)
    private var advertiseCallback: AdvertiseCallback? = null

    /** The refusals worth telling apart, in the words of what the user could do about them. */
    private fun describeAdvertiseFailure(errorCode: Int): String = when (errorCode) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE ->
            "the beacon does not fit in an advertisement"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
            "this chipset has no peripheral mode"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "already advertising"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
        else -> "error $errorCode"
    }

    /** Whether the radio is present and switched on. Everything below returns nothing without it. */
    val isAvailable: Boolean
        get() = manager?.adapter?.isEnabled == true

    /**
     * Starts broadcasting [beacon] until [stopAdvertising].
     *
     * Suspends until the radio has actually accepted or refused, which is the only way to know.
     * `startAdvertising` returns `Unit` and reports through its callback, so the obvious
     * `runCatching { ... }.isSuccess` that used to stand here was always `true` — the feature
     * reported itself working while the refusal arrived somewhere nobody was reading. The failure
     * carries the reason: Bluetooth off, permission declined, or a chipset with no peripheral
     * mode, which is a real and unfixable category of Android device rather than a thing to retry.
     */
    @SuppressLint("MissingPermission")
    suspend fun advertise(beacon: OffGridBeacon): Result<Unit> {
        val advertiser = manager?.adapter?.bluetoothLeAdvertiser
            ?: return Result.failure(IOException("this phone cannot advertise over Bluetooth"))
        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            // Balanced, not low-latency: discovery within a second or two is what a person waiting
            // to share music actually perceives, and the fastest mode costs battery all the while.
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            // The name is left out on purpose. It is the device's Bluetooth name, which is usually
            // its owner's, and it would be broadcast to the whole room — see [OffGridBeacon].
            .setIncludeDeviceName(false)
            // Service *data* only, and no separate service-UUID field. A legacy advertisement is
            // 31 bytes; naming the 128-bit UUID twice costs 18 of them for nothing, and the packet
            // was refused outright for being 50 bytes long. The scan below filters on this same
            // service data, so the second copy bought no discoverability either.
            .addServiceData(serviceUuid, beacon.toBytes())
            .build()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }

                override fun onStartFailure(errorCode: Int) {
                    val reason = describeAdvertiseFailure(errorCode)
                    Log.w(TAG, "the radio refused to advertise: $reason")
                    // Cleared here, so a refused attempt does not leave a callback registered that
                    // `stopAdvertising` would later hand back to a radio that never started.
                    advertiseCallback = null
                    if (continuation.isActive) continuation.resume(Result.failure(IOException(reason)))
                }
            }
            advertiseCallback = callback

            val started = runCatching { advertiser.startAdvertising(settings, data, callback) }
            if (started.isFailure) {
                advertiseCallback = null
                if (continuation.isActive) {
                    continuation.resume(Result.failure(started.exceptionOrNull()!!))
                }
            }

            // A cancelled caller must not leave the radio broadcasting.
            continuation.invokeOnCancellation { stopAdvertising() }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        val callback = advertiseCallback ?: return
        advertiseCallback = null
        runCatching { manager?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(callback) }
    }

    /** The scan refusals, in the same terms as [describeAdvertiseFailure]. */
    private fun describeScanFailure(errorCode: Int): String = when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "already scanning"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "the scan could not be registered"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "this chipset cannot scan this way"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "internal error"
        else -> "error $errorCode"
    }

    /**
     * Every Wanda beacon heard, with its signal strength, until the collector goes away.
     *
     * Filtered on the service UUID in the scanner itself rather than in this process: an unfiltered
     * scan wakes the app for every device in range, and in a city that is continuous.
     *
     * The flow **fails** when the radio refuses rather than staying open and empty. Those two look
     * identical on screen — an empty list under "Looking for phones nearby…" — and only one of them
     * is something the user can do anything about.
     */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<Pair<OffGridBeacon, Int>> = callbackFlow {
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IOException("this phone cannot scan for Bluetooth devices"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val payload = result.scanRecord?.getServiceData(serviceUuid)
                val beacon = OffGridBeacon.fromBytes(payload) ?: return
                trySend(beacon to result.rssi)
            }

            override fun onScanFailed(errorCode: Int) {
                val reason = describeScanFailure(errorCode)
                Log.w(TAG, "the radio refused to scan: $reason")
                close(IOException(reason))
            }
        }

        // Matched on service data rather than on a service-UUID field, because the advertisement
        // no longer carries one — see [advertise]. The version byte is included in the match, so
        // the scanner itself drops anything that is not one of ours instead of waking this process.
        val filter = ScanFilter.Builder()
            .setServiceData(
                serviceUuid,
                byteArrayOf(OffGridBeacon.VERSION),
                byteArrayOf(0xFF.toByte())
            )
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        // Only the synchronous throw is caught here; an asynchronous refusal arrives at
        // `onScanFailed` above, which closes the flow with the same kind of cause.
        runCatching { scanner.startScan(listOf(filter), settings, callback) }
            .onFailure { close(it) }

        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }
}
