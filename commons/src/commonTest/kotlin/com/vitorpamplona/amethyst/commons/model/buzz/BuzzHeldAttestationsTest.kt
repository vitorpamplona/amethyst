/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuzzHeldAttestationsTest {
    private val agent = "a".repeat(64)
    private val owner = "b".repeat(64)
    private val attestation = OwnerAttestation(ownerPubKey = owner, conditions = "kind=40002", sig = "c".repeat(128))

    @AfterTest
    fun tearDown() = BuzzHeldAttestations.clearForTesting()

    @Test
    fun emptyStoreYieldsNoTag() {
        assertNull(BuzzHeldAttestations.attestationFor(agent))
        assertNull(BuzzHeldAttestations.authTagFor(agent))
    }

    @Test
    fun heldAttestationSurfacesAsItsAuthTag() {
        BuzzHeldAttestations.put(agent, attestation)
        assertEquals(attestation, BuzzHeldAttestations.attestationFor(agent))
        // The tag attached to the agent's AUTH is exactly the attestation's ["auth", …] tag.
        assertContentEquals(attestation.toTag(), BuzzHeldAttestations.authTagFor(agent))
    }

    @Test
    fun removeClearsTheHeldAttestation() {
        BuzzHeldAttestations.put(agent, attestation)
        BuzzHeldAttestations.remove(agent)
        assertNull(BuzzHeldAttestations.authTagFor(agent))
    }

    @Test
    fun oneAgentsAttestationDoesNotLeakToAnother() {
        BuzzHeldAttestations.put(agent, attestation)
        assertNull(BuzzHeldAttestations.authTagFor("d".repeat(64)))
    }
}
