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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelayLimitsTest {
    private val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val sig = "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce"

    private fun event(
        createdAt: Long = 1000L,
        content: String = "hi",
        tags: Array<Array<String>> = emptyArray(),
    ) = Event("a".repeat(64), pubkey, createdAt, 1, tags, content, sig)

    @Test
    fun toNip11LimitationMirrorsTheEnforcedNumbers() {
        val limits =
            RelayLimits(
                maxMessageLength = 65536,
                maxSubscriptions = 20,
                maxFilters = 10,
                maxLimit = 500,
                maxContentLength = 8196,
                maxEventTags = 2000,
                createdAtLowerLimit = 1577836800L,
                createdAtUpperLimit = 1893456000L,
                authRequired = true,
            )
        val lim = limits.toNip11Limitation()
        assertEquals(65536, lim.max_message_length)
        assertEquals(20, lim.max_subscriptions)
        assertEquals(10, lim.max_filters)
        assertEquals(500, lim.max_limit)
        assertEquals(8196, lim.max_content_length)
        assertEquals(2000, lim.max_event_tags)
        assertEquals(1577836800, lim.created_at_lower_limit)
        assertEquals(1893456000, lim.created_at_upper_limit)
        assertEquals(true, lim.auth_required)
    }

    // -- LimitsPolicy: EVENT ---------------------------------------------------

    @Test
    fun rejectsOversizedContent() {
        val policy = LimitsPolicy(RelayLimits(maxContentLength = 5))
        val result = policy.accept(EventCmd(event(content = "way too long")))
        assertTrue(result is PolicyResult.Rejected)
        assertTrue(result.reason.startsWith("invalid:"))
    }

    @Test
    fun rejectsTooManyTags() {
        val tags = Array(4) { arrayOf("t", "x$it") }
        val policy = LimitsPolicy(RelayLimits(maxEventTags = 3))
        assertTrue(policy.accept(EventCmd(event(tags = tags))) is PolicyResult.Rejected)
    }

    @Test
    fun rejectsCreatedAtOutOfRange() {
        val policy = LimitsPolicy(RelayLimits(createdAtLowerLimit = 100L, createdAtUpperLimit = 200L))
        assertTrue(policy.accept(EventCmd(event(createdAt = 50L))) is PolicyResult.Rejected)
        assertTrue(policy.accept(EventCmd(event(createdAt = 250L))) is PolicyResult.Rejected)
        assertTrue(policy.accept(EventCmd(event(createdAt = 150L))) is PolicyResult.Accepted)
    }

    // -- LimitsPolicy: session-level hooks -------------------------------------

    @Test
    fun acceptMessageRejectsOversizedFrame() {
        val policy = LimitsPolicy(RelayLimits(maxMessageLength = 10))
        assertEquals(null, policy.acceptMessage("short"))
        val reason = policy.acceptMessage("this is way too long")
        assertTrue(reason != null && reason.startsWith("invalid:"))
    }

    @Test
    fun acceptMessageIsNoopWhenUnset() {
        assertEquals(null, LimitsPolicy(RelayLimits()).acceptMessage("anything at all, no cap"))
    }

    @Test
    fun acceptSubscriptionRejectsAtCap() {
        val policy = LimitsPolicy(RelayLimits(maxSubscriptions = 3))
        assertEquals(null, policy.acceptSubscription("s", openSubscriptions = 2))
        val reason = policy.acceptSubscription("s", openSubscriptions = 3)
        assertTrue(reason != null && reason.startsWith("rate-limited:"))
    }

    // -- LimitsPolicy: REQ / COUNT ---------------------------------------------

    @Test
    fun rejectsTooManyFilters() {
        val policy = LimitsPolicy(RelayLimits(maxFilters = 2))
        val req = ReqCmd("s", listOf(Filter(kinds = listOf(1)), Filter(kinds = listOf(2)), Filter(kinds = listOf(3))))
        assertTrue(policy.accept(req) is PolicyResult.Rejected)
    }

    @Test
    fun rejectsLongSubscriptionId() {
        val policy = LimitsPolicy(RelayLimits(maxSubidLength = 4))
        assertTrue(policy.accept(ReqCmd("toolong", listOf(Filter(kinds = listOf(1))))) is PolicyResult.Rejected)
        assertTrue(policy.accept(CountCmd("toolong", listOf(Filter(kinds = listOf(1))))) is PolicyResult.Rejected)
    }

    @Test
    fun clampsLimitDownToMax() {
        val policy = LimitsPolicy(RelayLimits(maxLimit = 100))
        val result = policy.accept(ReqCmd("s", listOf(Filter(kinds = listOf(1), limit = 5000))))
        assertTrue(result is PolicyResult.Accepted)
        assertEquals(
            100,
            result.cmd.filters
                .single()
                .limit,
        )
    }

    @Test
    fun appliesDefaultLimitWhenUnset() {
        val policy = LimitsPolicy(RelayLimits(defaultLimit = 50, maxLimit = 100))
        val result = policy.accept(ReqCmd("s", listOf(Filter(kinds = listOf(1))))) as PolicyResult.Accepted
        assertEquals(
            50,
            result.cmd.filters
                .single()
                .limit,
        )
    }

    @Test
    fun leavesAcceptableRequestUnchanged() {
        val policy = LimitsPolicy(RelayLimits(maxLimit = 100))
        val req = ReqCmd("s", listOf(Filter(kinds = listOf(1), limit = 10)))
        val result = policy.accept(req) as PolicyResult.Accepted
        // No clamp needed -> the same command instance is returned.
        assertTrue(result.cmd === req)
    }
}
