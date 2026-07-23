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
package com.vitorpamplona.quartz.nipXXBolt12Zaps

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12Bech32
import com.vitorpamplona.quartz.nipXXBolt12Zaps.intent.Bolt12ZapIntentEvent
import com.vitorpamplona.quartz.nipXXBolt12Zaps.zap.Bolt12ZapEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Bolt12ZapEventTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val recipient = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val offer = Bolt12Bech32.encode(Bolt12Bech32.OFFER_HRP, byteArrayOf(1, 2, 3, 4, 5, 6))
    private val proof = Bolt12Bech32.encode(Bolt12Bech32.PAYER_PROOF_HRP, byteArrayOf(7, 8, 9, 10))
    private val zapId = "ab".repeat(16)

    private fun Array<Array<String>>.tag(name: String) = firstOrNull { it.isNotEmpty() && it[0] == name }

    @Test
    fun profileZapIntentCarriesTheRequiredTags() {
        val template = Bolt12ZapIntentEvent.buildProfileZap(recipient, 21_000L, offer, zapId, comment = "excellent note")

        assertEquals(Bolt12ZapIntentEvent.KIND, template.kind)
        assertEquals("excellent note", template.content)
        assertEquals(listOf("p", recipient), template.tags.tag("p")?.toList())
        assertEquals(listOf("amount", "21000"), template.tags.tag("amount")?.toList())
        assertEquals(listOf("offer", offer), template.tags.tag("offer")?.toList())
        assertEquals(listOf("zap_id", zapId), template.tags.tag("zap_id")?.toList())
        assertNull(template.tags.tag("e"))
        assertNull(template.tags.tag("a"))
    }

    @Test
    fun eventTargetedIntentCarriesEAndKTags() {
        val zapped =
            Event("b".repeat(64), "c".repeat(64), 1_700_000_000L, 1, emptyArray(), "hi", "d".repeat(128))
        val template = Bolt12ZapIntentEvent.build(recipient, 21_000L, offer, zapId, EventHintBundle(zapped))

        val eTag = template.tags.tag("e")
        assertTrue(eTag != null && eTag[1] == "b".repeat(64))
        assertEquals(listOf("k", "1"), template.tags.tag("k")?.toList())
        assertNull(template.tags.tag("a"))
    }

    @Test
    fun zapEventEmbedsIntentAndCopiesFields() =
        runTest {
            val intent = signer.sign(Bolt12ZapIntentEvent.buildProfileZap(recipient, 21_000L, offer, zapId, comment = "nice"))
            val template = Bolt12ZapEvent.build(intent, proof, payerPubKey = signer.pubKey)

            assertEquals(Bolt12ZapEvent.KIND, template.kind)
            assertEquals("nice", template.content)
            assertEquals(listOf("description", intent.toJson()), template.tags.tag("description")?.toList())
            assertEquals(listOf("p", recipient), template.tags.tag("p")?.toList())
            assertEquals(listOf("amount", "21000"), template.tags.tag("amount")?.toList())
            assertEquals(listOf("offer", offer), template.tags.tag("offer")?.toList())
            assertEquals(listOf("proof", proof), template.tags.tag("proof")?.toList())
            assertEquals(listOf("P", signer.pubKey), template.tags.tag("P")?.toList())
        }

    @Test
    fun anonymousZapOmitsThePayerTag() =
        runTest {
            val intent = signer.sign(Bolt12ZapIntentEvent.buildProfileZap(recipient, 1_000L, offer, zapId))
            val template = Bolt12ZapEvent.build(intent, proof, payerPubKey = null)
            assertNull(template.tags.tag("P"))
        }

    @Test
    fun parsedBackAccessorsAndFactoryTypesAreCorrect() =
        runTest {
            val intent = signer.sign(Bolt12ZapIntentEvent.buildProfileZap(recipient, 21_000L, offer, zapId, comment = "nice"))
            val zap = signer.sign(Bolt12ZapEvent.build(intent, proof, payerPubKey = signer.pubKey))

            assertEquals(recipient, zap.recipient())
            assertEquals(21_000L, zap.amount())
            assertEquals(offer, zap.offer())
            assertEquals(proof, zap.payerProof())
            assertEquals(signer.pubKey, zap.payer())
            assertTrue(zap.isProfileZap())
            assertEquals(intent.id, zap.zapIntent?.id)

            // The factory (used by fromJson / relay ingestion) resolves the right types.
            assertTrue(Event.fromJson(zap.toJson()) is Bolt12ZapEvent)
            assertTrue(Event.fromJson(intent.toJson()) is Bolt12ZapIntentEvent)
        }
}
