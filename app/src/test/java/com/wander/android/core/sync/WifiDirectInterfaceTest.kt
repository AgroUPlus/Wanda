package com.wander.android.core.sync

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectInterfaceTest {

    @Test
    fun acceptsWifiDirectInterfaceNames() {
        listOf("p2p0", "p2p-wlan0-0", "p2p-p2p0-1").forEach {
            assertTrue(it, WifiDirectInterface.isWifiDirectName(it))
        }
    }

    @Test
    fun refusesOrdinaryInterfaceNames() {
        listOf(null, "", "wlan0", "eth0", "rmnet_data0", "lo").forEach {
            assertFalse(it.toString(), WifiDirectInterface.isWifiDirectName(it))
        }
    }

    @Test
    fun refusesLoopbackAndMissingAddresses() {
        assertFalse(WifiDirectInterface.carries(null))
        assertFalse(WifiDirectInterface.carries(InetAddress.getLoopbackAddress()))
    }
}
