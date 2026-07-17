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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayReqRefusalsTest {
    private val relay = NormalizedRelayUrl("wss://search.nos.today/")
    private val other = NormalizedRelayUrl("wss://relay.example/")

    private fun plain() = listOf(Filter(kinds = listOf(1), limit = 10))

    private fun search() = listOf(Filter(kinds = listOf(1), search = "bitcoin"))

    @Test
    fun searchOnlyBlocksPlainReqsButNotSearchReqsAfterThreshold() {
        val refusals = RelayReqRefusals(threshold = 2)

        assertFalse(refusals.shouldSuppress(relay, plain()), "no block before any refusal")

        // A search-only relay CLOSES plain feed REQs from different subscriptions.
        refusals.onRefused(relay, "error: search filter is required")
        assertFalse(refusals.shouldSuppress(relay, plain()), "one refusal is not enough to block")
        refusals.onRefused(relay, "error: search filter is required")

        assertTrue(refusals.shouldSuppress(relay, plain()), "a twice-refused search-only relay blocks plain REQs")
        assertFalse(refusals.shouldSuppress(relay, search()), "but a genuine search REQ is still allowed through")
        assertFalse(refusals.shouldSuppress(other, plain()), "the block is scoped to the offending relay")
        assertEquals(mapOf(relay to RelayReqRefusals.Policy.SEARCH_ONLY), refusals.blockedRelays())
    }

    @Test
    fun noReadsBlocksEveryReq() {
        val refusals = RelayReqRefusals(threshold = 2)

        refusals.onRefused(relay, "restricted: this relay does not accept REQs")
        refusals.onRefused(relay, "restricted: this relay does not accept REQs")

        assertTrue(refusals.shouldSuppress(relay, plain()), "a no-reads relay blocks plain REQs")
        assertTrue(refusals.shouldSuppress(relay, search()), "a no-reads relay blocks search REQs too")
    }

    @Test
    fun authRequiredAndUnknownReasonsNeverBlock() {
        val refusals = RelayReqRefusals(threshold = 2)

        // Auth gating is resolved by the auth subsystem, not by giving up on the relay.
        repeat(5) { refusals.onRefused(relay, "auth-required: this relay only serves private notes to authenticated authors or recipients") }
        repeat(5) { refusals.onRefused(relay, "rate-limited: slow down") }
        repeat(5) { refusals.onRefused(relay, "Subscription closed") }

        assertFalse(refusals.shouldSuppress(relay, plain()), "auth/rate/lifecycle reasons must never blanket-block a relay")
        assertTrue(refusals.blockedRelays().isEmpty())
    }

    @Test
    fun blockedFlowEmitsARelayWhenItBecomesBlocked() {
        val refusals = RelayReqRefusals(threshold = 2)
        assertTrue(refusals.blockedFlow.value.isEmpty(), "nothing blocked initially")

        refusals.onRefused(relay, "error: search filter is required")
        assertTrue(refusals.blockedFlow.value.isEmpty(), "one refusal doesn't publish a block")

        refusals.onRefused(relay, "error: search filter is required")
        assertEquals(setOf(relay), refusals.blockedFlow.value, "the relay is published to the flow once blocked")

        // Further refusals of the same relay don't churn the flow to a new (equal) set instance.
        val snapshot = refusals.blockedFlow.value
        refusals.onRefused(relay, "error: search filter is required")
        assertTrue(refusals.blockedFlow.value === snapshot, "an already-blocked relay doesn't re-emit")
    }

    @Test
    fun aFlippingReasonDoesNotAccumulateToABlock() {
        val refusals = RelayReqRefusals(threshold = 3)

        // Alternating unrelated capability complaints shouldn't cross the threshold for either class.
        refusals.onRefused(relay, "error: search filter is required")
        refusals.onRefused(relay, "queries not allowed")
        refusals.onRefused(relay, "error: search filter is required")

        assertFalse(refusals.shouldSuppress(relay, plain()), "no single class reached the threshold")
    }
}
