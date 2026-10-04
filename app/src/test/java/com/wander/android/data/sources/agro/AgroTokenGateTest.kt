package com.wander.android.data.sources.agro

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A token the server refused must stop being sent — every refusal is a line an access-log bouncer
 * counts toward banning the owner's address — but must never hold back a token that still works.
 */
class AgroTokenGateTest {

    @After
    fun reset() = AgroTokenGate.resetForTest()

    @Test
    fun `a refused token is held back until a new pairing replaces it`() {
        AgroTokenGate.record("old", AgroAuthError.Rejected("Unauthorized"), now = 0)

        assertTrue(AgroTokenGate.refusing("old", now = 0) is AgroAuthError.Rejected)
        assertNotNull(AgroTokenGate.refusing("old", now = 24 * 60 * 60_000L))
        assertNull(AgroTokenGate.refusing("new", now = 0))
    }

    @Test
    fun `an inactive account is asked again once the wait is over, by one request`() {
        AgroTokenGate.record("t", AgroAuthError.NotActive("not active"), now = 0)
        val later = AgroTokenGate.NOT_ACTIVE_RETRY_MS

        assertNotNull(AgroTokenGate.refusing("t", now = later - 1))
        assertNull(AgroTokenGate.refusing("t", now = later))
        assertNotNull(AgroTokenGate.refusing("t", now = later + 1))
    }

    @Test
    fun `failures that say nothing about the token never close the gate`() {
        AgroTokenGate.record("t", AgroAuthError.RateLimited("slow down"))
        AgroTokenGate.record("t", AgroAuthError.Unreachable("offline"))
        AgroTokenGate.recordRestStatus("t", 403)
        AgroTokenGate.recordRestStatus("t", 500)

        assertNull(AgroTokenGate.refusing("t"))
    }

    @Test
    fun `an accepted request clears what was held against the same token`() {
        AgroTokenGate.recordRestStatus("t", 401)
        assertNotNull(AgroTokenGate.refusing("t"))

        AgroTokenGate.accepted("t")
        assertNull(AgroTokenGate.refusing("t"))
    }
}
