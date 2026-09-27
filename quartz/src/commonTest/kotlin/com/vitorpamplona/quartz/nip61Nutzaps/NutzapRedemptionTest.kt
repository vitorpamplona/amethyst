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
package com.vitorpamplona.quartz.nip61Nutzaps

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.redemption.buildNutzapRedemption
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * NIP-61 reuses the NIP-60 wallet kinds: redeemed tokens are kind 7375 wallet token
 * events and redemptions are kind 7376 spending history events. Each kind has a
 * single class, so what EventFactory builds must expose the NIP-61 fields.
 */
class NutzapRedemptionTest {
    private val sender = "1".repeat(64)
    private val nutzap = NutzapEvent("a".repeat(64), sender, 1, emptyArray(), "", "b".repeat(128))

    @Test
    fun redemptionIsParsedAsSpendingHistory() {
        val template = CashuSpendingHistoryEvent.buildNutzapRedemption(EventHintBundle(nutzap), "encrypted")
        assertEquals(CashuSpendingHistoryEvent.KIND, template.kind)

        val event = EventFactory.create<Event>("c".repeat(64), "2".repeat(64), 2, template.kind, template.tags, template.content, "d".repeat(128))
        val history = assertIs<CashuSpendingHistoryEvent>(event)

        assertEquals(listOf(nutzap.id), history.redeemedNutzaps().map { it.eventId })
        assertEquals(listOf(nutzap.id), history.redeemedReferences().map { it.eventId })
        assertEquals(listOf(sender), history.linkedPubKeys())
    }

    @Test
    fun walletTokenKindHasOneClass() {
        val event = EventFactory.create<Event>("c".repeat(64), "2".repeat(64), 2, CashuTokenEvent.KIND, emptyArray(), "", "d".repeat(128))
        assertIs<CashuTokenEvent>(event)
    }
}
