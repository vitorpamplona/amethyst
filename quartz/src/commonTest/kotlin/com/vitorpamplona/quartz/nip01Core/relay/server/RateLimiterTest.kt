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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.RetryAfter
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RateLimitPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RateLimiter
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RateLimiterTest {
    private var now = 1_000_000L
    private val clock = { now }

    @Test
    fun aBurstIsSpentThenEachTokenCostsItsShareOfAMinute() {
        val limiter = RateLimiter(perMinute = 60, burst = 3, clock = clock)
        repeat(3) { assertEquals(0L, limiter.acquire("a"), "token #$it") }
        // 60 a minute is one a second: the fourth waits a whole second.
        assertEquals(1_000L, limiter.acquire("a"))
        now += 400
        assertEquals(600L, limiter.acquire("a"), "the wait shrinks as the bucket refills")
        now += 600
        assertEquals(0L, limiter.acquire("a"), "the stated wait is exactly enough")
        assertEquals(1_000L, limiter.acquire("a"))
    }

    @Test
    fun aRefusalSpendsNothing() {
        val limiter = RateLimiter(perMinute = 60, burst = 1, clock = clock)
        assertEquals(0L, limiter.acquire("a"))
        repeat(50) { limiter.acquire("a") }
        now += 1_000
        assertEquals(0L, limiter.acquire("a"), "hammering while refused must not push the next token further out")
    }

    @Test
    fun aQuietClientRefillsOnlyUpToItsBurst() {
        val limiter = RateLimiter(perMinute = 60, burst = 2, clock = clock)
        repeat(2) { limiter.acquire("a") }
        now += 3_600_000
        assertEquals(0L, limiter.acquire("a"))
        assertEquals(0L, limiter.acquire("a"))
        assertTrue(limiter.acquire("a") > 0, "an hour of quiet still buys only the burst")
    }

    @Test
    fun clientsHaveTheirOwnBuckets() {
        val limiter = RateLimiter(perMinute = 60, burst = 1, clock = clock)
        assertEquals(0L, limiter.acquire("a"))
        assertTrue(limiter.acquire("a") > 0)
        assertEquals(0L, limiter.acquire("b"))
    }

    @Test
    fun theWaitIsNeverZeroForARefusal() {
        // 600 000 a minute is 10 a millisecond: the true wait rounds below 1 ms.
        val limiter = RateLimiter(perMinute = 600_000, burst = 1, clock = clock)
        assertEquals(0L, limiter.acquire("a"))
        assertEquals(1L, limiter.acquire("a"))
    }

    @Test
    fun idleBucketsAreSweptAndBusyOnesKept() {
        val limiter = RateLimiter(perMinute = 60, burst = 2, clock = clock)
        limiter.acquire("idle")
        now += 500
        limiter.acquire("busy")
        // "idle" has had 2 s to refill its burst of 2; "busy" only 1.5 s.
        now += 1_500
        limiter.sweep()
        assertEquals(1, limiter.trackedClients())
        assertTrue(limiter.acquire("busy") == 0L)
    }

    @Test
    fun invalidRatesAreRefused() {
        assertFailsWith<IllegalArgumentException> { RateLimiter(perMinute = 0) }
        assertFailsWith<IllegalArgumentException> { RateLimiter(perMinute = 10, burst = 0) }
    }
}

class RateLimitPolicyTest {
    private var now = 0L
    private val events = RateLimiter(perMinute = 60, burst = 1) { now }
    private val reqs = RateLimiter(perMinute = 30, burst = 1) { now }

    private val event =
        Event(
            id = "e".repeat(64),
            pubKey = "p".repeat(64),
            createdAt = 0,
            kind = 1,
            tags = emptyArray(),
            content = "",
            sig = "s".repeat(128),
        )

    private fun connect(
        address: String?,
        id: Long,
    ): IRelayPolicy =
        RateLimitPolicy(events, reqs).also { policy ->
            policy.onConnect(
                object : RequestContext {
                    override val connectionId = id
                    override val policy = policy
                    override val authenticatedUsers = emptySet<String>()
                    override val clientAddress = address
                },
            ) { _: Message -> }
        }

    private fun assertRateLimited(
        result: PolicyResult<*>,
        expectedWaitMs: Long,
    ) {
        assertIs<PolicyResult.Rejected<*>>(result)
        assertEquals(MachineReadablePrefix.RATE_LIMITED, MachineReadablePrefix.parse(result.reason))
        assertEquals(expectedWaitMs, RetryAfter.parse(result.reason, result.params))
    }

    @Test
    fun anEventPastTheRateIsRefusedWithItsBackOff() {
        val policy = connect("10.0.0.1", 1)
        assertIs<PolicyResult.Accepted<*>>(policy.accept(EventCmd(event)))
        assertRateLimited(policy.accept(EventCmd(event)), 1_000)
    }

    @Test
    fun socketsFromOneAddressShareAnAllowance() {
        assertIs<PolicyResult.Accepted<*>>(connect("10.0.0.1", 1).accept(EventCmd(event)))
        assertRateLimited(connect("10.0.0.1", 2).accept(EventCmd(event)), 1_000)
        assertIs<PolicyResult.Accepted<*>>(connect("10.0.0.2", 3).accept(EventCmd(event)))
    }

    @Test
    fun withoutAnAddressEachConnectionCountsAlone() {
        assertIs<PolicyResult.Accepted<*>>(connect(null, 1).accept(EventCmd(event)))
        assertIs<PolicyResult.Accepted<*>>(connect(null, 2).accept(EventCmd(event)))
    }

    @Test
    fun reqsCountsAndNegOpensShareTheReqAllowance() {
        val policy = connect("10.0.0.1", 1)
        val filter = Filter(kinds = listOf(1))
        assertIs<PolicyResult.Accepted<*>>(policy.accept(ReqCmd("a", listOf(filter))))
        assertRateLimited(policy.accept(CountCmd("b", listOf(filter))), 2_000)
        assertIs<PolicyResult.Rejected<*>>(policy.accept(NegOpenCmd("c", filter, "61")))
        // ...and spend nothing of the event allowance.
        assertIs<PolicyResult.Accepted<*>>(policy.accept(EventCmd(event)))
    }

    @Test
    fun authIsNeverLimited() {
        val policy = connect("10.0.0.1", 1)
        val auth = RelayAuthEvent(event.id, event.pubKey, 0, emptyArray(), "", event.sig)
        repeat(5) { assertIs<PolicyResult.Accepted<*>>(policy.accept(AuthCmd(auth))) }
    }

    @Test
    fun aNullLimiterLeavesThatTypeOpen() {
        val policy = RateLimitPolicy(events = null, reqs = reqs)
        repeat(5) { assertIs<PolicyResult.Accepted<*>>(policy.accept(EventCmd(event))) }
    }
}
