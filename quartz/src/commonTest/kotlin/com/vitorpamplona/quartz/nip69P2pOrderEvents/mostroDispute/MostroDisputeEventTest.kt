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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDispute

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2PKindFixtures
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MostroDisputeEventTest {
    private fun parse(json: String): Event = Event.fromJson(json)

    private fun build(
        createdAt: Long,
        vararg tags: Array<String>,
    ): Event = EventFactory.create("0".repeat(64), "1".repeat(64), createdAt, MostroDisputeEvent.KIND, arrayOf(*tags), "", "")

    @Test
    fun kindIsKnownAndProbesAsTheDispute() {
        assertTrue(EventFactory.isKnownKind(MostroDisputeEvent.KIND))
        assertIs<MostroDisputeEvent>(EventFactory.probe(MostroDisputeEvent.KIND))
    }

    @Test
    fun realDisputeAccessors() {
        val dispute = assertIs<MostroDisputeEvent>(parse(P2PKindFixtures.REAL_MOSTRO_DISPUTE))
        assertEquals("0ab33c54-d9ad-4f7c-b9f3-3257ad81aab6", dispute.disputeId())
        assertEquals("seller-refunded", dispute.status())
        assertEquals("seller", dispute.initiator())
        assertEquals(1791395845L, dispute.publishedAt())
        assertEquals(1791395845L, dispute.openedAt())
        assertEquals(1799173413L, dispute.expiration())
        assertEquals("mostro", dispute.platform())
        assertEquals("NostroMostro 🇪🇸", dispute.instanceName())
    }

    @Test
    fun aDisputeNamesNothingElse() {
        val dispute = parse(P2PKindFixtures.REAL_MOSTRO_DISPUTE)
        assertFalse(dispute is PubKeyHintProvider)
        assertFalse(dispute is EventHintProvider)
        assertFalse(dispute is AddressHintProvider)
        assertFalse(dispute is SearchableEvent)
    }

    @Test
    fun openedAtFallsBackToTheLegacyTagThenTheEventTime() {
        val legacy = assertIs<MostroDisputeEvent>(build(2000L, arrayOf("z", "dispute"), arrayOf("created_at", "1500")))
        assertNull(legacy.publishedAt())
        assertEquals(1500L, legacy.openedAt())

        val bare = assertIs<MostroDisputeEvent>(build(2000L, arrayOf("z", "dispute")))
        assertEquals(2000L, bare.openedAt())

        val both = assertIs<MostroDisputeEvent>(build(2000L, arrayOf("z", "dispute"), arrayOf("created_at", "1500"), arrayOf("published_at", "1600")))
        assertEquals(1600L, both.openedAt())
    }

    @Test
    fun malformedTagsReadAsNull() {
        val dispute =
            assertIs<MostroDisputeEvent>(
                build(2000L, arrayOf("z", "dispute"), arrayOf("s"), arrayOf("initiator", ""), arrayOf("published_at", "noon"), arrayOf("created_at", "")),
            )
        assertNull(dispute.status())
        assertNull(dispute.initiator())
        assertNull(dispute.publishedAt())
        assertEquals(2000L, dispute.openedAt())
        assertEquals("", dispute.disputeId())
    }

    @Test
    fun otherAppsOn38386AreNotDisputes() {
        val promotion = parse(P2PKindFixtures.SYNTHETIC_PAYGRESS_PROMOTION)
        assertIs<UnrecognizedKind38386Event>(promotion)
        assertIs<AddressableEvent>(promotion)

        // A bondtrade settlement: `t` holds a block height, no `z`.
        assertEquals(UnrecognizedKind38386Event::class, build(1L, arrayOf("d", "46288cbac33bb018"), arrayOf("t", "3450178"))::class)
        assertEquals(UnrecognizedKind38386Event::class, build(1L, arrayOf("z", "order"))::class)
    }

    @Test
    fun buildRoundTrips() {
        val template =
            MostroDisputeEvent.build(
                disputeId = "660e8400-e29b-41d4-a716-446655440001",
                status = "initiated",
                initiator = "buyer",
                openedAt = 1700000100L,
                expiration = 1800000000L,
                instanceName = "Test Instance",
                createdAt = 1700000200L,
            )
        val dispute = assertIs<MostroDisputeEvent>(EventFactory.create<Event>("0".repeat(64), "1".repeat(64), template.createdAt, template.kind, template.tags, template.content, ""))
        assertEquals("660e8400-e29b-41d4-a716-446655440001", dispute.disputeId())
        assertEquals("initiated", dispute.status())
        assertEquals("buyer", dispute.initiator())
        assertEquals(1700000100L, dispute.openedAt())
        assertEquals(1800000000L, dispute.expiration())
        assertEquals("Test Instance", dispute.instanceName())
        assertEquals("dispute", template.tags.first { it[0] == "z" }[1])
    }
}
