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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2PKindFixtures
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2POrderEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MostroDevFeePaymentEventTest {
    private fun parse(json: String): Event = Event.fromJson(json)

    private fun build(vararg tags: Array<String>): Event = EventFactory.create("0".repeat(64), "1".repeat(64), 1L, MostroDevFeePaymentEvent.KIND, arrayOf(*tags), "", "")

    @Test
    fun kindIsKnown() {
        assertTrue(EventFactory.isKnownKind(MostroDevFeePaymentEvent.KIND))
        assertIs<MostroDevFeePaymentEvent>(build())
    }

    @Test
    fun realReceiptAccessors() {
        val receipt = assertIs<MostroDevFeePaymentEvent>(parse(P2PKindFixtures.REAL_MOSTRO_DEV_FEE))
        assertEquals("a5febfc3-a23c-47b9-a205-c44f919d7ea8", receipt.orderId())
        assertEquals(348L, receipt.amount())
        assertEquals("85bfe082db1a3414d130ac7757be018ec810cd1bca341218f94eae5e933a98f5", receipt.paymentHash())
        assertEquals("dev@pay.mostro.foundation", receipt.destination())
        assertEquals("mainnet", receipt.network())
        assertEquals("mostro", receipt.platform())
        assertEquals("Mostro", receipt.instanceName())
        assertEquals(1822958826L, receipt.expiration())
        assertFalse(parse(P2PKindFixtures.REAL_MOSTRO_DEV_FEE) is SearchableEvent)
    }

    @Test
    fun theOrderIsTheOnlyEdgeAndAWellFormedAddress() {
        val receipt = assertIs<MostroDevFeePaymentEvent>(parse(P2PKindFixtures.REAL_MOSTRO_DEV_FEE))
        val expected = "38383:82fa8cb978b43c79b2156585bac2c011176a21d2aead6d9f7c575c005be88390:a5febfc3-a23c-47b9-a205-c44f919d7ea8"
        assertEquals(listOf(expected), receipt.linkedAddressIds())
        assertEquals(emptyList(), receipt.addressHints())

        val address = assertNotNull(Address.parse(expected))
        assertEquals(P2POrderEvent.KIND, address.kind)
        assertEquals(receipt.pubKey, address.pubKeyHex)
        assertEquals(receipt.orderId(), address.dTag)
    }

    @Test
    fun anOrderIdThatIsNotAUuidIsNoEdge() {
        val receipt = assertIs<MostroDevFeePaymentEvent>(build(arrayOf("order-id", "not a uuid:with:colons")))
        assertEquals("not a uuid:with:colons", receipt.orderId())
        assertNull(receipt.orderAddressId())
        assertEquals(emptyList(), receipt.linkedAddressIds())
    }

    @Test
    fun malformedTagsReadAsNull() {
        val receipt = assertIs<MostroDevFeePaymentEvent>(build(arrayOf("order-id"), arrayOf("amount", "8 sats"), arrayOf("hash", ""), arrayOf("destination")))
        assertNull(receipt.orderId())
        assertNull(receipt.amount())
        assertNull(receipt.paymentHash())
        assertNull(receipt.destination())
        assertEquals(emptyList(), receipt.linkedAddressIds())
    }

    @Test
    fun buildRoundTrips() {
        val template =
            MostroDevFeePaymentEvent.build(
                orderId = "550e8400-e29b-41d4-a716-446655440000",
                amountSats = 8,
                paymentHash = "ca2f47b7c2169b8c42ef135e8ee32706e1fd3722b65e5a16f21ce675d2affb6b",
                destination = "dev@mostro.network",
                network = "mainnet",
                expiration = 1822958826L,
                instanceName = "Test Instance",
                createdAt = 1768256716L,
            )
        val receipt = assertIs<MostroDevFeePaymentEvent>(EventFactory.create<Event>("0".repeat(64), "1".repeat(64), template.createdAt, template.kind, template.tags, template.content, ""))
        assertEquals("550e8400-e29b-41d4-a716-446655440000", receipt.orderId())
        assertEquals(8L, receipt.amount())
        assertEquals("ca2f47b7c2169b8c42ef135e8ee32706e1fd3722b65e5a16f21ce675d2affb6b", receipt.paymentHash())
        assertEquals("dev@mostro.network", receipt.destination())
        assertEquals("mainnet", receipt.network())
        assertEquals(1822958826L, receipt.expiration())
        assertEquals("Test Instance", receipt.instanceName())
        assertEquals("dev-fee-payment", template.tags.first { it[0] == "z" }[1])
        assertEquals(listOf("38383:${"1".repeat(64)}:550e8400-e29b-41d4-a716-446655440000"), receipt.linkedAddressIds())
    }
}
