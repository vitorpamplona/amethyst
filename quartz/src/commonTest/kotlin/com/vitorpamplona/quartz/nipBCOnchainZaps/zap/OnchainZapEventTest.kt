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
package com.vitorpamplona.quartz.nipBCOnchainZaps.zap

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Asserts the on-the-wire tag structure of kind:8333 events against the
 * NIP-BC spec, so cross-app interoperability doesn't drift.
 */
class OnchainZapEventTest {
    private val txid = "a".repeat(64)
    private val recipient = "8b34731183a85a4fc1a3ea9e8caa14e72ff31716bf6dd0d2f8c93f5b14e44f5d"

    private fun Array<Array<String>>.tag(name: String) = firstOrNull { it.isNotEmpty() && it[0] == name }

    @Test
    fun profileZapHasExactlyTheSpecTags() {
        val template = OnchainZapEvent.buildProfileZap(txid, recipient, 25_000L)

        assertEquals(OnchainZapEvent.KIND, template.kind)
        val tags = template.tags

        // Required NIP-BC tags, exact values.
        assertEquals(listOf("alt", "Onchain zap: 25000 sats"), tags.tag("alt")?.toList())
        assertEquals(listOf("i", "bitcoin:tx:$txid"), tags.tag("i")?.toList())
        assertEquals(listOf("p", recipient), tags.tag("p")?.toList())
        assertEquals(listOf("amount", "25000"), tags.tag("amount")?.toList())

        // A profile zap targets no event — no e / a / k tags.
        assertNull(tags.tag("e"), "profile zap must not carry an e tag")
        assertNull(tags.tag("a"), "profile zap must not carry an a tag")
        assertNull(tags.tag("k"), "profile zap must not carry a k tag")
    }

    @Test
    fun eventZapCarriesEventAndKindTags() {
        val zapped =
            Event(
                id = "b".repeat(64),
                pubKey = "c".repeat(64),
                createdAt = 1_700_000_000L,
                kind = 1,
                tags = emptyArray(),
                content = "hello",
                sig = "d".repeat(128),
            )
        val template =
            OnchainZapEvent.build(txid, recipient, 21_000L, EventHintBundle(zapped))
        val tags = template.tags

        assertEquals(listOf("i", "bitcoin:tx:$txid"), tags.tag("i")?.toList())
        assertEquals(listOf("p", recipient), tags.tag("p")?.toList())
        assertEquals(listOf("amount", "21000"), tags.tag("amount")?.toList())
        assertEquals("Onchain zap: 21000 sats", tags.tag("alt")?.get(1))

        // The zapped event is referenced by an `e` tag and its kind by a `k` tag.
        val eTag = tags.tag("e")
        assertTrue(eTag != null && eTag[1] == "b".repeat(64), "e tag must reference the zapped event id")
        assertEquals(listOf("k", "1"), tags.tag("k")?.toList())
        // Kind 1 is not addressable — no a tag.
        assertNull(tags.tag("a"))
    }

    @Test
    fun parsedBackEventExposesTheSameFields() {
        // The receipt must round-trip through the event accessors used by the
        // verifier and feed code.
        val template = OnchainZapEvent.buildProfileZap(txid, recipient, 25_000L)
        val event =
            OnchainZapEvent(
                id = "e".repeat(64),
                pubKey = "f".repeat(64),
                createdAt = template.createdAt,
                tags = template.tags,
                content = template.content,
                sig = "0".repeat(128),
            )
        assertEquals(txid, event.txid())
        assertEquals(recipient, event.recipient())
        assertEquals(25_000L, event.claimedAmountInSats())
        assertTrue(event.isProfileZap())
    }
}
