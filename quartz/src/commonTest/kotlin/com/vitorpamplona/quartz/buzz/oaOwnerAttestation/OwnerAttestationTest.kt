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
package com.vitorpamplona.quartz.buzz.oaOwnerAttestation

import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.tags.AuthTag
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnerAttestationTest {
    private val owner = KeyPair()
    private val agent = KeyPair()
    private val agentPub = agent.pubKey.toHexKey()

    @Test
    fun signAndVerifyRoundTrip() {
        val att = OwnerAttestation.sign(agentPub, "kind=1", owner.privKey!!)

        assertEquals(owner.pubKey.toHexKey(), att.ownerPubKey)
        assertEquals("kind=1", att.conditions)
        assertEquals(128, att.sig.length)
        assertTrue(att.verify(agentPub), "attestation should verify for the attested agent")
    }

    @Test
    fun emptyConditionsVerify() {
        val att = OwnerAttestation.sign(agentPub, "", owner.privKey!!)
        assertTrue(att.verify(agentPub))
    }

    @Test
    fun typedConditionsOverloadRoundTrips() {
        val conditions = AttestationConditions(kind = 40002, createdAtBefore = 1713957000L)
        val att = OwnerAttestation.sign(agentPub, conditions, owner)

        assertEquals("kind=40002&created_at<1713957000", att.conditions)
        assertTrue(att.verify(agentPub))
    }

    @Test
    fun verifyFailsForDifferentAgent() {
        val att = OwnerAttestation.sign(agentPub, "kind=1", owner.privKey!!)
        val otherAgent = KeyPair().pubKey.toHexKey()
        assertFalse(att.verify(otherAgent), "attestation must not verify for a key it was not signed for")
    }

    @Test
    fun verifyFailsWhenConditionsTampered() {
        val att = OwnerAttestation.sign(agentPub, "kind=1", owner.privKey!!)
        val tampered = att.copy(conditions = "kind=2")
        assertFalse(tampered.verify(agentPub), "changing the signed conditions must break verification")
    }

    @Test
    fun selfAttestationRejected() {
        assertFailsWith<IllegalArgumentException> {
            OwnerAttestation.sign(owner.pubKey.toHexKey(), "kind=1", owner.privKey!!)
        }
    }

    @Test
    fun nonCanonicalConditionsRejectedAtSigning() {
        assertFailsWith<IllegalArgumentException> {
            OwnerAttestation.sign(agentPub, "kind=01", owner.privKey!!)
        }
    }

    @Test
    fun authTagRoundTrip() {
        val att = OwnerAttestation.sign(agentPub, "kind=1&created_at>1700000000", owner.privKey!!)
        val tag = att.toTag()

        assertEquals(AuthTag.TAG_NAME, tag[0])
        assertEquals(4, tag.size)

        val parsed = OwnerAttestation.parse(tag)
        assertEquals(att, parsed)
        assertTrue(parsed!!.verify(agentPub))
    }

    @Test
    fun authTagParseRejectsMalformed() {
        assertNull(OwnerAttestation.parse(arrayOf("auth", "nothex", "kind=1", "00")))
        assertNull(OwnerAttestation.parse(arrayOf("auth", owner.pubKey.toHexKey(), "kind=1"))) // too short
        assertNull(OwnerAttestation.parse(arrayOf("p", owner.pubKey.toHexKey(), "kind=1", "0".repeat(128))))
    }

    @Test
    fun conditionsGrammar() {
        assertTrue(AttestationConditions.isValid(""))
        assertTrue(AttestationConditions.isValid("kind=1"))
        assertTrue(AttestationConditions.isValid("kind=0"))
        assertTrue(AttestationConditions.isValid("kind=65535"))
        assertTrue(AttestationConditions.isValid("kind=1&created_at<1713957000"))
        assertTrue(AttestationConditions.isValid("created_at>10&created_at<20"))

        assertFalse(AttestationConditions.isValid("kind=01")) // leading zero
        assertFalse(AttestationConditions.isValid("kind=65536")) // > u16
        assertFalse(AttestationConditions.isValid("kind=1&")) // trailing &
        assertFalse(AttestationConditions.isValid("&kind=1")) // leading &
        assertFalse(AttestationConditions.isValid("kind=1&&kind=2")) // double &
        assertFalse(AttestationConditions.isValid("kind = 1")) // whitespace
        assertFalse(AttestationConditions.isValid("expires=1")) // unknown clause
    }

    /**
     * Cross-implementation compliance: the known-answer vector published in Buzz's own
     * `buzz-sdk/src/nip_oa.rs` tests. Both assertions are independent of Schnorr's
     * auxiliary randomness (a fresh signature would differ but still verify), so they
     * pin exact interop: our preimage hash must equal Buzz's, and our verifier must
     * accept Buzz's reference signature.
     */
    @Test
    fun compliesWithBuzzReferenceVector() {
        val ownerPub = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        val agentPubHex = "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
        val conditions = "kind=1&created_at<1713957000"
        val specSig =
            "8b7df2575caf0a108374f8471722b233c53f9ff827a8b0f91861966c3b9dd5cb" +
                "2e189eae9f49d72187674c2f5bd244145e10ff86c9f257ffe65a1ee5f108b369"

        val att = OwnerAttestation(ownerPub, conditions, specSig)

        assertEquals(
            "nostr:agent-auth:$agentPubHex:$conditions",
            att.commitment(agentPubHex),
        )
        assertEquals(
            "08cdecd55af4c28d3801fd69615dcf5cc04fab3bc134b38a840bf157197069a6",
            OwnerAttestation.commitmentHash(agentPubHex, conditions).toHexKey(),
        )
        assertTrue(
            att.verify(agentPubHex),
            "Buzz's own reference signature must verify under the Quartz implementation",
        )
    }

    @Test
    fun conditionsParseExtractsValues() {
        val parsed = AttestationConditions.parse("kind=7&created_at<1713957000&created_at>1700000000")
        assertEquals(7, parsed?.kind)
        assertEquals(1713957000L, parsed?.createdAtBefore)
        assertEquals(1700000000L, parsed?.createdAtAfter)
    }
}
