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
package com.vitorpamplona.quartz.buzz.pairing

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairingEventTest {
    private val source = NostrSignerInternal(KeyPair())
    private val target = NostrSignerInternal(KeyPair())

    @Test
    fun payloadRoundTripsBothWays() =
        runTest {
            val message = PairingMessage.Payload(PayloadType.NSEC, "nsec1test")
            val event = PairingEvent.create(message, target.pubKey, source)

            assertEquals(24134, event.kind)
            assertTrue(PairingEvent.KIND in 20000..29999, "24134 must be ephemeral")
            assertEquals(source.pubKey, event.pubKey)
            assertEquals(target.pubKey, event.recipientPubKey())

            // Symmetric NIP-44 key: both sender and recipient can decrypt.
            assertEquals(message, event.decrypt(source))
            assertEquals(message, event.decrypt(target))
        }

    @Test
    fun offerAndAbortSerialiseWithWireDiscriminants() =
        runTest {
            val offer = PairingMessage.Offer(sessionId = "de".repeat(32))
            val offerEvent = PairingEvent.create(offer, target.pubKey, source)
            assertEquals(offer, offerEvent.decrypt(target))

            val abort = PairingMessage.Abort(AbortReason.SAS_MISMATCH)
            assertEquals("sas_mismatch", abort.reason)
            assertEquals(AbortReason.SAS_MISMATCH, abort.reasonOrUnknown())

            // Kebab-case type discriminant + snake_case reason on the wire.
            val json = abort.encodeToJson()
            assertTrue(json.contains("\"type\":\"abort\""), json)
            assertTrue(json.contains("\"reason\":\"sas_mismatch\""), json)
        }

    @Test
    fun unknownAbortReasonMapsToUnknown() {
        val decoded = PairingMessage.decodeFromJson("""{"type":"abort","reason":"solar_flare"}""")
        assertTrue(decoded is PairingMessage.Abort)
        assertEquals(AbortReason.UNKNOWN, (decoded as PairingMessage.Abort).reasonOrUnknown())
    }
}
