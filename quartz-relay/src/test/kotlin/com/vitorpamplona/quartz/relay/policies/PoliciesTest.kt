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
package com.vitorpamplona.quartz.relay.policies

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult
import com.vitorpamplona.quartz.relay.fixtures.SyntheticEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Per-policy unit tests. Each policy gets a small, focused suite that
 * proves accept/reject behaviour at the boundaries (empty config,
 * single hit, collision between allow + deny, etc.).
 *
 * The end-to-end "policy is applied through the Ktor server" coverage
 * lives in `LocalRelayServerTest` / `Nip01ComplianceTest` — these tests
 * just exercise the policy in isolation.
 */
class PoliciesTest {
    private fun event(
        kind: Int = 1,
        pubKey: String = SyntheticEvents.hexId(1),
        createdAt: Long = 1000L,
        content: String = "",
    ) = SyntheticEvents.fakeEvent(idSeed = 1, kind = kind, pubKey = pubKey, createdAt = createdAt, content = content)

    private fun assertAccepted(result: PolicyResult<*>) {
        if (result is PolicyResult.Rejected) fail("expected Accepted, got Rejected: ${result.reason}")
    }

    private fun assertRejected(
        result: PolicyResult<*>,
        reasonContains: String? = null,
    ) {
        when (result) {
            is PolicyResult.Accepted -> {
                fail("expected Rejected, got Accepted")
            }

            is PolicyResult.Rejected -> {
                reasonContains?.let {
                    assertTrue(
                        result.reason.contains(it),
                        "expected reason to contain '$it', got '${result.reason}'",
                    )
                }
            }
        }
    }

    // -- KindAllowDenyPolicy -------------------------------------------------

    @Test
    fun kindPolicyEmptyListsAreNoOp() {
        val p = KindAllowDenyPolicy()
        assertAccepted(p.accept(EventCmd(event(kind = 1))))
        assertAccepted(p.accept(EventCmd(event(kind = 99))))
    }

    @Test
    fun kindAllowListExcludesEverythingElse() {
        val p = KindAllowDenyPolicy(allow = setOf(1, 7))
        assertAccepted(p.accept(EventCmd(event(kind = 1))))
        assertAccepted(p.accept(EventCmd(event(kind = 7))))
        assertRejected(p.accept(EventCmd(event(kind = 4))), reasonContains = "kind 4 not allowed")
    }

    @Test
    fun kindDenyListBlocksLastWordOverAllowList() {
        // When both lists are set, allow is a permissive ceiling and
        // deny still removes specific kinds inside it.
        val p = KindAllowDenyPolicy(allow = setOf(1, 4, 7), deny = setOf(4))
        assertAccepted(p.accept(EventCmd(event(kind = 1))))
        assertRejected(p.accept(EventCmd(event(kind = 4))), reasonContains = "kind 4 denied")
        assertRejected(p.accept(EventCmd(event(kind = 999))), reasonContains = "not allowed")
    }

    // -- PubkeyAllowDenyPolicy ----------------------------------------------

    @Test
    fun pubkeyAllowList() {
        val alice = SyntheticEvents.hexId(101)
        val mallory = SyntheticEvents.hexId(102)
        val p = PubkeyAllowDenyPolicy(allow = setOf(alice))
        assertAccepted(p.accept(EventCmd(event(pubKey = alice))))
        assertRejected(p.accept(EventCmd(event(pubKey = mallory))), reasonContains = "not on allow")
    }

    @Test
    fun pubkeyDenyList() {
        val alice = SyntheticEvents.hexId(101)
        val mallory = SyntheticEvents.hexId(102)
        val p = PubkeyAllowDenyPolicy(deny = setOf(mallory))
        assertAccepted(p.accept(EventCmd(event(pubKey = alice))))
        assertRejected(p.accept(EventCmd(event(pubKey = mallory))), reasonContains = "denied")
    }

    @Test
    fun pubkeyMatchIsCaseInsensitive() {
        val pk = "ABCDEF".padEnd(64, '0')
        val p = PubkeyAllowDenyPolicy(deny = setOf(pk.lowercase()))
        // Event arrives with the upper-case form; policy must match.
        assertRejected(p.accept(EventCmd(event(pubKey = pk))))
    }

    // -- RejectFutureEventsPolicy -------------------------------------------

    @Test
    fun futureEventsBeyondSkewAreRejected() {
        val now = 1_000_000L
        val p = RejectFutureEventsPolicy(maxFutureSeconds = 60, now = { now })
        assertAccepted(p.accept(EventCmd(event(createdAt = now + 60))))
        assertAccepted(p.accept(EventCmd(event(createdAt = now))))
        assertAccepted(p.accept(EventCmd(event(createdAt = now - 9999)))) // past is fine
        assertRejected(p.accept(EventCmd(event(createdAt = now + 61))), reasonContains = "future")
    }

    @Test
    fun futureEventsZeroSkewMeansOnlyPastOrPresent() {
        val now = 1_000L
        val p = RejectFutureEventsPolicy(maxFutureSeconds = 0, now = { now })
        assertAccepted(p.accept(EventCmd(event(createdAt = now))))
        assertRejected(p.accept(EventCmd(event(createdAt = now + 1))))
    }

    // -- MaxEventBytesPolicy ------------------------------------------------

    @Test
    fun maxBytesAllowsSmallEvents() {
        val small = event(content = "a")
        val limit = OptimizedJsonMapper.toJson(small).length + 100
        val p = MaxEventBytesPolicy(maxBytes = limit)
        assertAccepted(p.accept(EventCmd(small)))
    }

    @Test
    fun maxBytesRejectsOversizedEvents() {
        val big = event(content = "x".repeat(2_000))
        val p = MaxEventBytesPolicy(maxBytes = 500)
        assertRejected(p.accept(EventCmd(big)), reasonContains = "exceeds limit")
    }

    // -- RateLimitPolicy ----------------------------------------------------

    /** Helper that makes a clock we can advance in nanoseconds. */
    private class FakeClock {
        var nanos = 0L

        fun read(): Long = nanos

        fun advanceMillis(ms: Long) {
            nanos += ms * 1_000_000L
        }
    }

    @Test
    fun rateLimitMessagesPerSecond() {
        val clock = FakeClock()
        val p = RateLimitPolicy(messagesPerSec = 3, nowNanos = clock::read)

        // First three pass within the same instant.
        repeat(3) { assertAccepted(p.accept(EventCmd(event()))) }
        // Fourth is rate-limited.
        assertRejected(p.accept(EventCmd(event())), reasonContains = "messages per second")

        // After enough wall-time the bucket refills.
        clock.advanceMillis(400) // 1s/3 = 333ms per token; 400ms gives at least 1 token
        assertAccepted(p.accept(EventCmd(event())))
    }

    @Test
    fun rateLimitSubscriptionsPerMinute() {
        val clock = FakeClock()
        val p = RateLimitPolicy(subscriptionsPerMin = 2, nowNanos = clock::read)
        val req = ReqCmd("sub-1", listOf(Filter()))

        assertAccepted(p.accept(req))
        assertAccepted(p.accept(req))
        assertRejected(p.accept(req), reasonContains = "subscriptions per minute")

        // Refill after 30s for 2/min -> 1 token.
        clock.advanceMillis(31_000)
        assertAccepted(p.accept(req))
    }

    @Test
    fun rateLimitCountAlsoCountsAsSubscription() {
        val clock = FakeClock()
        val p = RateLimitPolicy(subscriptionsPerMin = 1, nowNanos = clock::read)
        val cnt = CountCmd("q1", listOf(Filter()))
        assertAccepted(p.accept(cnt))
        assertRejected(p.accept(cnt), reasonContains = "subscriptions per minute")
    }

    @Test
    fun rateLimitDisabledWhenBothLimitsAreNull() {
        val p = RateLimitPolicy()
        repeat(1000) { assertAccepted(p.accept(EventCmd(event()))) }
        repeat(1000) { assertAccepted(p.accept(ReqCmd("s", listOf(Filter())))) }
    }

    // -- Stack composition --------------------------------------------------

    /**
     * Verifies that policies compose via `IRelayPolicy.plus` so an
     * EVENT must clear every policy in the stack to be accepted.
     */
    @Test
    fun stackedPoliciesAllMustAccept() {
        val now = 1_000L
        val stack =
            (KindAllowDenyPolicy(allow = setOf(1)) as com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy) +
                RejectFutureEventsPolicy(maxFutureSeconds = 10, now = { now })

        // Allowed kind, in window — accepted.
        assertAccepted(stack.accept(EventCmd(event(kind = 1, createdAt = now))))
        // Allowed kind, future timestamp — rejected by RejectFuture.
        assertRejected(
            stack.accept(EventCmd(event(kind = 1, createdAt = now + 1000))),
            reasonContains = "future",
        )
        // Disallowed kind — rejected by KindPolicy regardless of timestamp.
        assertRejected(
            stack.accept(EventCmd(event(kind = 99, createdAt = now))),
            reasonContains = "not allowed",
        )
    }

    @Test
    fun rateLimitConstructorRejectsInvalidValues() {
        var threw = false
        try {
            RateLimitPolicy(messagesPerSec = 0)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertEquals(true, threw)
    }
}
