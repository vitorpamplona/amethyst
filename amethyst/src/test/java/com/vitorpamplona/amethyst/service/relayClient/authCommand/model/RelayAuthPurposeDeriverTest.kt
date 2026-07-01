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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class RelayAuthPurposeDeriverTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    private fun event(
        kind: Int,
        pTags: List<String> = emptyList(),
    ) = Event(
        id = "00".repeat(32),
        pubKey = "11".repeat(32),
        createdAt = 1_700_000_000L,
        kind = kind,
        tags = pTags.map { arrayOf("p", it) }.toTypedArray(),
        content = "",
        sig = "22".repeat(64),
    )

    @Test
    fun giftWrapBecomesSendDmToItsRecipient() {
        val purposes = RelayAuthPurposeDeriver.derive(listOf(event(GiftWrapEvent.KIND, listOf(alice))), emptyMap())

        assertEquals(1, purposes.size)
        assertEquals(AuthPurposeKind.SEND_DM, purposes[0].kind)
        assertEquals(setOf(alice), purposes[0].counterparties)
    }

    @Test
    fun nonGiftWrapWithPTagsBecomesNotifyInbox() {
        val purposes = RelayAuthPurposeDeriver.derive(listOf(event(1, listOf(alice, bob))), emptyMap())

        assertEquals(1, purposes.size)
        assertEquals(AuthPurposeKind.NOTIFY_INBOX, purposes[0].kind)
        assertEquals(setOf(alice, bob), purposes[0].counterparties)
    }

    @Test
    fun notifyExcludesTheEventsOwnAuthor() {
        val author = "11".repeat(32) // matches event()'s pubKey
        val purposes = RelayAuthPurposeDeriver.derive(listOf(event(1, listOf(author, alice))), emptyMap())

        assertEquals(1, purposes.size)
        assertEquals(AuthPurposeKind.NOTIFY_INBOX, purposes[0].kind)
        assertEquals(setOf(alice), purposes[0].counterparties)
    }

    @Test
    fun subscriptionAuthorsBecomeReadOutbox() {
        val purposes = RelayAuthPurposeDeriver.derive(emptyList(), mapOf("sub1" to listOf(Filter(authors = listOf(alice, bob)))))

        assertEquals(1, purposes.size)
        assertEquals(AuthPurposeKind.READ_OUTBOX, purposes[0].kind)
        assertEquals(setOf(alice, bob), purposes[0].counterparties)
    }

    @Test
    fun mixedPendingWorkYieldsAllPurposes() {
        val purposes =
            RelayAuthPurposeDeriver.derive(
                pendingEvents = listOf(event(GiftWrapEvent.KIND, listOf(alice)), event(1, listOf(bob))),
                activeFilters = mapOf("sub1" to listOf(Filter(authors = listOf(alice)))),
            )

        assertEquals(
            setOf(AuthPurposeKind.SEND_DM, AuthPurposeKind.NOTIFY_INBOX, AuthPurposeKind.READ_OUTBOX),
            purposes.map { it.kind }.toSet(),
        )
    }

    @Test
    fun noAttributableWorkYieldsNoPurposes() {
        assertEquals(emptyList<AuthPurpose>(), RelayAuthPurposeDeriver.derive(emptyList(), emptyMap()))
        // an event with no p tags gives nothing to attribute a notification to
        assertEquals(emptyList<AuthPurpose>(), RelayAuthPurposeDeriver.derive(listOf(event(1)), emptyMap()))
    }
}
