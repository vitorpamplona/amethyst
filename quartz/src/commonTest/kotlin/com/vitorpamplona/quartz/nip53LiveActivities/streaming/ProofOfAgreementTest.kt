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
package com.vitorpamplona.quartz.nip53LiveActivities.streaming

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProofOfAgreementTest {
    private val dummySig = "0".repeat(128)

    @Test
    fun roundTripSignAndVerify() {
        val host = KeyPair()
        val participant = KeyPair()
        val hostHex = host.pubKey.toHexKey()
        val participantHex = participant.pubKey.toHexKey()
        val dTag = "my-stream"

        val proof =
            ProofOfAgreement.sign(
                participantPrivKey = participant.privKey!!,
                eventKind = LiveActivitiesEvent.KIND,
                eventAuthor = hostHex,
                eventDTag = dTag,
            )

        assertTrue(
            ProofOfAgreement.verify(
                proof = proof,
                participantPubKey = participantHex,
                eventKind = LiveActivitiesEvent.KIND,
                eventAuthor = hostHex,
                eventDTag = dTag,
            ),
        )
    }

    @Test
    fun verifyFailsForDifferentAddress() {
        val host = KeyPair()
        val participant = KeyPair()
        val hostHex = host.pubKey.toHexKey()
        val participantHex = participant.pubKey.toHexKey()

        val proof =
            ProofOfAgreement.sign(
                participantPrivKey = participant.privKey!!,
                eventKind = LiveActivitiesEvent.KIND,
                eventAuthor = hostHex,
                eventDTag = "one",
            )

        assertFalse(
            ProofOfAgreement.verify(
                proof = proof,
                participantPubKey = participantHex,
                eventKind = LiveActivitiesEvent.KIND,
                eventAuthor = hostHex,
                eventDTag = "another",
            ),
        )
    }

    @Test
    fun verifyFailsForWrongParticipantKey() {
        val host = KeyPair()
        val participant = KeyPair()
        val imposter = KeyPair()
        val hostHex = host.pubKey.toHexKey()

        val proof =
            ProofOfAgreement.sign(
                participantPrivKey = participant.privKey!!,
                eventKind = LiveActivitiesEvent.KIND,
                eventAuthor = hostHex,
                eventDTag = "stream",
            )

        assertFalse(
            ProofOfAgreement.verify(
                proof = proof,
                participantPubKey = imposter.pubKey.toHexKey(),
                eventKind = LiveActivitiesEvent.KIND,
                eventAuthor = hostHex,
                eventDTag = "stream",
            ),
        )
    }

    @Test
    fun verifyFailsForMalformedProof() {
        assertFalse(
            ProofOfAgreement.verify(
                proof = "not-hex",
                participantPubKey = "a".repeat(64),
                eventKind = LiveActivitiesEvent.KIND,
                eventAuthor = "a".repeat(64),
                eventDTag = "x",
            ),
        )
        assertFalse(
            ProofOfAgreement.verify(
                proof = "ab",
                participantPubKey = "a".repeat(64),
                eventKind = LiveActivitiesEvent.KIND,
                eventAuthor = "a".repeat(64),
                eventDTag = "x",
            ),
        )
    }

    @Test
    fun verifyAgainstLiveActivitiesEvent() {
        val host = KeyPair()
        val participant = KeyPair()
        val hostHex = host.pubKey.toHexKey()
        val participantHex = participant.pubKey.toHexKey()
        val dTag = "event-d"

        val proof =
            ProofOfAgreement.sign(
                participantPrivKey = participant.privKey!!,
                eventKind = LiveActivitiesEvent.KIND,
                eventAuthor = hostHex,
                eventDTag = dTag,
            )

        val event =
            LiveActivitiesEvent(
                id = "0".repeat(64),
                pubKey = hostHex,
                createdAt = 1_700_000_000L,
                tags =
                    arrayOf(
                        arrayOf("d", dTag),
                        arrayOf("p", participantHex, "", "Speaker", proof),
                        arrayOf("p", "c".repeat(64), "", "Speaker"),
                    ),
                content = "",
                sig = dummySig,
            )

        val verifiedTag = event.participants().first { it.pubKey == participantHex }
        val unverifiedTag = event.participants().first { it.pubKey == "c".repeat(64) }

        assertTrue(event.hasValidProof(verifiedTag))
        assertFalse(event.hasValidProof(unverifiedTag), "missing proof must not be treated as valid")
    }

    @Test
    fun digestMatchesAddressForm() {
        // digest(kind, pubkey, dtag) must agree with digest(Address)
        val hostHex = "a".repeat(64)
        val d1 = ProofOfAgreement.digest(LiveActivitiesEvent.KIND, hostHex, "abc")
        val d2 = ProofOfAgreement.digest(Address(LiveActivitiesEvent.KIND, hostHex, "abc"))
        assertTrue(d1.contentEquals(d2))
    }

    @Test
    fun tagWithoutProofFailsVerification() {
        val host = KeyPair()
        val participant = KeyPair()

        val event =
            LiveActivitiesEvent(
                id = "0".repeat(64),
                pubKey = host.pubKey.toHexKey(),
                createdAt = 1_700_000_000L,
                tags = arrayOf(arrayOf("d", "d"), arrayOf("p", participant.pubKey.toHexKey())),
                content = "",
                sig = dummySig,
            )

        assertFalse(
            ProofOfAgreement.verify(
                tag = ParticipantTag(participant.pubKey.toHexKey(), null, null, null),
                event = event,
            ),
        )
    }
}
