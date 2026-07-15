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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-account NIP-42 signing gate: on the shared client, an account signs a relay's AUTH only
 * when the relay is in its own relay list, or it is publishing its own event there. The bug these
 * lock down: an unpaid/bystander account being AUTH'd (and billed by inbox.nostr.wine) purely
 * because another account uses that relay — including via a merged filter that names it.
 */
class RelayAuthFirstPartyTest {
    private val relay = NormalizedRelayUrl("wss://inbox.nostr.wine/")
    private val me = "a".repeat(64)
    private val other = "b".repeat(64)

    private fun event(pubkey: String) =
        Event(
            id = "0".repeat(64),
            pubKey = pubkey,
            createdAt = 0,
            kind = 1059,
            tags = emptyArray(),
            content = "",
            sig = "",
        )

    @Test
    fun aRelayNotInMyListWithNothingOfMineIsNotFirstParty() {
        // The exact inbox.nostr.wine case: a relay another account uses, which a merged read filter
        // names me on, but which is in none of my lists and where I publish nothing → must NOT sign.
        assertFalse(RelayAuthFirstParty.hasReason(me, relay, emptyList(), emptySet()))
    }

    @Test
    fun publishingSomeoneElsesEventIsNotFirstParty() {
        assertFalse(RelayAuthFirstParty.hasReason(me, relay, listOf(event(other)), emptySet()))
    }

    @Test
    fun aRelayIConfiguredIsFirstParty() {
        // Own inbox/outbox reads qualify this way: the relay serving them is in my own relay list.
        assertTrue(RelayAuthFirstParty.hasReason(me, relay, emptyList(), setOf(relay)))
    }

    @Test
    fun publishingMyOwnEventIsFirstParty() {
        // Delivering my own DM/post to the recipient's relay, even one not in my list.
        assertTrue(RelayAuthFirstParty.hasReason(me, relay, listOf(event(me)), emptySet()))
    }
}
