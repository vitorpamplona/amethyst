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
package com.vitorpamplona.quartz.experimental.clink

import com.vitorpamplona.quartz.experimental.clink.client.DebitClient
import com.vitorpamplona.quartz.experimental.clink.client.OfferClient
import com.vitorpamplona.quartz.experimental.clink.debits.DebitEvent
import com.vitorpamplona.quartz.experimental.clink.debits.DebitFrequency
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferReceipt
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.experimental.clink.pointers.OfferPriceType
import com.vitorpamplona.quartz.experimental.clink.server.ClinkServer
import com.vitorpamplona.quartz.experimental.clink.server.K1Tracker
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClinkClientServerTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val servicePubKey = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.shocknet.dev")!!

    @Test
    fun offerClientExposesPointerRoutingAndResponseFilter() {
        val client = OfferClient(NOffer(servicePubKey, listOf(relay), "offer-id", OfferPriceType.SPONTANEOUS, null), signer)

        assertEquals(servicePubKey, client.servicePubKey)
        assertEquals(listOf(relay), client.relays)

        val filter = client.responseFilter("d".repeat(64))
        assertEquals(listOf(OfferEvent.KIND), filter.kinds)
        assertEquals(listOf(servicePubKey), filter.authors)
        assertEquals(listOf("d".repeat(64)), filter.tags?.get("e"))
    }

    @Test
    fun offerReceiptRoundTripsFromServiceToPayer() =
        kotlinx.coroutines.test.runTest {
            val payer = NostrSignerInternal(KeyPair())
            val service = NostrSignerInternal(KeyPair())
            val offer = NOffer(service.pubKey, listOf(relay), "offer-id", OfferPriceType.SPONTANEOUS, null)
            val client = OfferClient(offer, payer)

            // payer asks, service settles and sends a receipt referencing the request
            val request = client.requestInvoice(amountSats = 1000)
            val receiptEvent = OfferEvent.createReceipt(OfferReceipt(res = OfferReceipt.OK, preimage = "ab".repeat(32)), request, service)

            assertEquals(request.id, receiptEvent.requestId())
            val receipt = client.parseReceipt(receiptEvent)
            assertTrue(receipt.isOk())
            assertEquals("ab".repeat(32), receipt.preimage)
        }

    @Test
    fun requestBudgetRejectsInvalidFrequencyUnit() =
        kotlinx.coroutines.test.runTest {
            val client = DebitClient(NDebit(servicePubKey, listOf(relay), null, null), signer)
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                client.requestBudget(1000, DebitFrequency(1, "fortnight"))
            }
            // valid units do not throw
            client.requestBudget(1000, DebitFrequency(1, DebitFrequency.UNIT_MONTH))
        }

    @Test
    fun offerRequestTruncatesDescriptionTo100Chars() =
        kotlinx.coroutines.test.runTest {
            val service = NostrSignerInternal(KeyPair())
            val client = OfferClient(NOffer(service.pubKey, listOf(relay), "o", OfferPriceType.SPONTANEOUS, null), signer)
            val event = client.requestInvoice(amountSats = 100, description = "x".repeat(150))
            val decrypted = event.decryptRequest(service)
            assertEquals(100, decrypted.description?.length)
        }

    @Test
    fun serverRequestFilterTargetsRecipientByPTag() {
        val filter = ClinkServer.debitRequestFilter(servicePubKey, since = 100L)

        assertEquals(listOf(DebitEvent.KIND), filter.kinds)
        assertEquals(listOf(servicePubKey), filter.tags?.get("p"))
        assertEquals(100L, filter.since)
    }

    @Test
    fun freshnessHonors30SecondWindow() {
        assertTrue(ClinkServer.isFresh(requestCreatedAt = 1000, now = 1000))
        assertTrue(ClinkServer.isFresh(requestCreatedAt = 1000, now = 1030))
        assertTrue(ClinkServer.isFresh(requestCreatedAt = 1030, now = 1000))
        assertFalse(ClinkServer.isFresh(requestCreatedAt = 1000, now = 1031))
    }

    @Test
    fun k1TrackerIsSingleUsePerScope() {
        val tracker = K1Tracker()
        val k1 = "4caa9ee5f0f0a0b1c2d3e4f5061728394a5b6c7d8e9f00112233445566778899"

        assertFalse(tracker.isConsumed("pointer-7", k1))
        assertTrue(tracker.consume("pointer-7", k1))
        assertTrue(tracker.isConsumed("pointer-7", k1))
        // second consume of the same scope+k1 fails
        assertFalse(tracker.consume("pointer-7", k1))
        // same k1 under a different scope is independent
        assertTrue(tracker.consume("pointer-8", k1))
    }
}
