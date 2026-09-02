package com.wander.android

import com.wander.android.core.sync.GrantBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Which key a peer stream may be sealed to.
 *
 * `P2PServer` cannot be constructed on the JVM, so the rule lives in [GrantBinding] where it can be
 * asked directly. Worth pinning: the failure it prevents is silent, because sealing to the wrong
 * key still returns audio — to the wrong person.
 */
class P2PGrantBindingTest {

    /** A grant that names no key is the off-grid and older-server case: the header stands alone. */
    @Test
    fun `an unbound grant falls back to the header`() {
        assertEquals("header-key", GrantBinding.sealingKey(emptyList(), "header-key"))
    }

    /** The ordinary path: the listener's real device asks, and is sealed to. */
    @Test
    fun `a header key the grant names is used`() {
        assertEquals(
            "phone-key",
            GrantBinding.sealingKey(listOf("phone-key", "laptop-key"), "phone-key")
        )
    }

    /**
     * The attack #46 exists to close. The request is rewritten in flight and carries a key the
     * listener never published; it must not become the key the room key is sealed to.
     */
    @Test
    fun `a substituted header key is never sealed to`() {
        val sealedTo = GrantBinding.sealingKey(listOf("phone-key", "laptop-key"), "attacker-key")
        assertNotEquals("attacker-key", sealedTo)
        assertEquals("phone-key", sealedTo)
    }

    /** A listener's second device is as valid as its first — the set is the point of the set. */
    @Test
    fun `any key in the bound set is accepted`() {
        assertEquals(
            "laptop-key",
            GrantBinding.sealingKey(listOf("phone-key", "laptop-key"), "laptop-key")
        )
    }

    /** No header at all still serves the listener, using a key that came from Agro. */
    @Test
    fun `a missing header falls back to a bound key`() {
        assertEquals("phone-key", GrantBinding.sealingKey(listOf("phone-key"), null))
    }

    /** Neither side offering anything is the one case with no answer. */
    @Test
    fun `nothing bound and no header seals to nothing`() {
        assertEquals(null, GrantBinding.sealingKey(emptyList(), null))
    }
}
