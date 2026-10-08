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
package com.vitorpamplona.quartz.nip69P2pOrderEvents

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.BondTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.FiatAmountTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderStatus
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderType
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.utils.compareToValue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kind 38383 is NIP-69's P2P order, but Paygress (a Cashu-paid compute marketplace) publishes its
 * provider offers on it too: `t`, `d`, `v` tags and a JSON body. There is no tag split here —
 * NIP-69 owns the number and the class reads only NIP-69 tags — so the class must take a Paygress
 * offer without throwing and without inventing order data from it.
 */
class P2POrderEventTest {
    private fun parse(json: String): Event = Event.fromJson(json)

    @Test
    fun realFixturesVerify() {
        P2PKindFixtures.REAL_SIGNED.forEach { json ->
            val event = parse(json)
            assertTrue(event.verify(), "fixture ${event.id} (kind ${event.kind}) does not verify")
        }
    }

    @Test
    fun paygressOfferParsesWithoutFabricatingAnOrder() {
        val offer = assertIs<P2POrderEvent>(parse(P2PKindFixtures.SYNTHETIC_PAYGRESS_OFFER))

        // Every accessor runs; none throws, and none reads an order out of an offer.
        assertNull(offer.orderType())
        assertNull(offer.currency())
        assertNull(offer.status())
        assertNull(offer.amount())
        assertNull(offer.fiatAmount())
        assertNull(offer.paymentMethods())
        assertNull(offer.premium())
        assertNull(offer.expiresAt())
        assertNull(offer.platform())
        assertNull(offer.documentType())
        assertNull(offer.source())
        assertNull(offer.rating())
        assertNull(offer.network())
        assertNull(offer.layer())
        assertNull(offer.makerName())
        assertNull(offer.bond())
        assertNull(offer.tags.instanceName())

        // It is still addressable by its own `d`.
        assertEquals("paygress:offer:v1:c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3", offer.dTag())

        // Nothing from the JSON body (host names, spec descriptions) reaches the index.
        assertEquals("", offer.indexableContent())
        val fields = mutableListOf<String?>()
        offer.forEachIndexableField {
            fields.add(it)
            true
        }
        assertEquals(listOf<String?>(null, null), fields)
        // Only its own `t` hashtags reach the tiered extraction; no name or keyword is made up.
        val tiered = assertIs<IndexableFields.Tiered>(SearchFieldExtractor.extract(offer))
        assertEquals(emptyList(), tiered.primary)
        assertEquals(emptyList(), tiered.secondary)
        assertNull(tiered.text)
    }

    @Test
    fun mostroOrderAccessors() {
        val order = assertIs<P2POrderEvent>(parse(P2PKindFixtures.SYNTHETIC_MOSTRO_ORDER))
        assertEquals(OrderType.BUY, order.orderType())
        assertEquals("MXN", order.currency())
        assertEquals(OrderStatus.CANCELED, order.status())
        assertEquals(0L, order.amount())
        assertEquals("0", order.fiatAmount()?.min)
        assertNull(order.fiatAmount()?.max)
        assertEquals(listOf("SPEI"), order.paymentMethods())
        assertEquals("-4", order.premium())
        assertEquals(1791517310L, order.expiresAt())
        assertEquals("mostro", order.platform())
        assertEquals("Mostro México", order.tags.instanceName())
        assertEquals("order", order.documentType())
        assertEquals("mainnet", order.network())
        assertEquals("lightning", order.layer())
    }

    @Test
    fun roboSatsOrderAccessors() {
        val order = assertIs<P2POrderEvent>(parse(P2PKindFixtures.REAL_ROBOSATS_ORDER))
        assertEquals(OrderType.SELL, order.orderType())
        assertEquals("50.00000000", order.fiatAmount()?.min)
        assertEquals("500.00000000", order.fiatAmount()?.max)
        assertEquals("FreePort", order.makerName())
        assertEquals("robosats", order.platform())
        assertNull(order.tags.instanceName())
        // RoboSats writes its bond as a percentage of the trade ("3.00"), a decimal.
        assertEquals(0, order.bond()?.compareToValue(BigDecimal("3.00")))
        assertEquals("FreePort USD CashApp", order.indexableContent())
    }

    @Test
    fun malformedOrderTagsReadAsNull() {
        val event =
            EventFactory.create<Event>(
                "0".repeat(64),
                "1".repeat(64),
                1L,
                P2POrderEvent.KIND,
                arrayOf(
                    arrayOf("k"),
                    arrayOf("k", "rent"),
                    arrayOf("s", "unknown"),
                    arrayOf("amt", "lots"),
                    arrayOf("fa", ""),
                    arrayOf("pm"),
                    arrayOf("expires_at", "soon"),
                    arrayOf("y"),
                    arrayOf("y", "mostro", " "),
                ),
                "",
                "",
            )
        val order = assertIs<P2POrderEvent>(event)
        assertNull(order.orderType())
        assertNull(order.status())
        assertNull(order.amount())
        assertNull(order.fiatAmount())
        assertNull(order.paymentMethods())
        assertNull(order.expiresAt())
        assertEquals("mostro", order.platform())
        assertNull(order.tags.instanceName())
    }

    @Test
    fun bondReadsDecimalsAndWholeNumbers() {
        assertEquals(0, BondTag.parse(arrayOf("bond", "0"))?.compareToValue(BigDecimal(0)))
        assertEquals(0, BondTag.parse(arrayOf("bond", "2.50"))?.compareToValue(BigDecimal("2.5")))
        assertNull(BondTag.parse(arrayOf("bond", "3%")))
        assertNull(BondTag.parse(arrayOf("bond", "")))
        assertNull(BondTag.parse(arrayOf("bond")))
        assertContentEquals(arrayOf("bond", "2.50"), BondTag.assemble(BigDecimal("2.50")))
        assertContentEquals(arrayOf("bond", "0"), BondTag.assemble(0L))
    }

    @Test
    fun buildRoundTrips() {
        val template =
            P2POrderEvent.build(
                orderType = OrderType.SELL,
                currency = "EUR",
                status = OrderStatus.PENDING,
                amount = 0,
                fiatAmount = FiatAmountTag("10", "100"),
                paymentMethods = listOf("SEPA"),
                premium = "1",
                platform = "mostro",
                expiresAt = 1800000000L,
                bond = BigDecimal("3.00"),
                dTag = "order-1",
                createdAt = 1L,
            )
        val order = assertIs<P2POrderEvent>(EventFactory.create<Event>("0".repeat(64), "1".repeat(64), 1L, template.kind, template.tags, template.content, ""))
        assertEquals(OrderType.SELL, order.orderType())
        assertEquals("EUR", order.currency())
        assertEquals("100", order.fiatAmount()?.max)
        assertEquals("order", order.documentType())
        assertEquals(0, order.bond()?.compareToValue(BigDecimal("3")))
    }
}
