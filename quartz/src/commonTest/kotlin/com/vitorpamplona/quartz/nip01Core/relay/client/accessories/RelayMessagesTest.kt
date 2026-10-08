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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every relay wording the RelayMessages matchers read, as it was captured, and what each one
 * must mean. Sources: the 2026-10 surveys (743 crawled relays; 246 relays, one or two per relay
 * software; 21 relays for COUNT) and the relays named beside each line. Add a new wording here
 * before changing a matcher.
 */
class RelayMessagesTest {
    private val budget = "ERROR: read bandwidth budget exhausted (1048570 bytes/min per IP) (retry in 30ms)"

    @Test
    fun aRefusalOfTheEventLimitIsReadForTheCapItStates() {
        // purplepag.es
        assertEquals(500, lowerLimitAfterRefusal("blocked: limit too high: 50000 (max 500)", 50_000))
        assertEquals(25_000, lowerLimitAfterRefusal("blocked: limit too high", 50_000))
        assertEquals(1_000, lowerLimitAfterRefusal("error: limit exceeds max_limit of 1000", 5_000))
        // relay.cxplay.org
        assertEquals(1_000, lowerLimitAfterRefusal("invalid: limitation.max_limit 1000", 5_000))
        // relay.wavefunc.live: read as "no max stated", it was halved four times.
        assertEquals(500, lowerLimitAfterRefusal("restricted: limit must not exceed 500", 5_000))
        assertEquals(200, lowerLimitAfterRefusal("error: limit exceeds 200", 5_000))
        // A stated max that is not lower than what was sent cannot be the reason: halve.
        assertEquals(250, lowerLimitAfterRefusal("blocked: limit too high (max 500)", 500))
        assertNull(lowerLimitAfterRefusal("blocked: limit too high", 1), "nothing lower to ask for")
        assertNull(lowerLimitAfterRefusal(null, 500))
    }

    @Test
    fun limitWordingAboutSomethingElseLowersNothing() {
        assertNull(lowerLimitAfterRefusal("rate-limited: slow down, limit 10 REQs a second", 500), "throttling, not a cap")
        assertNull(lowerLimitAfterRefusal("error: rate limit exceeded", 500))
        assertNull(lowerLimitAfterRefusal("ERROR: rate limit exceeded, slow down", 5_000))
        assertNull(lowerLimitAfterRefusal("error: request limit exceeded, too many requests", 5_000))
        assertNull(lowerLimitAfterRefusal("auth-required: we only serve members", 500))
        assertNull(lowerLimitAfterRefusal("error: number of subscriptions exceeds limit", 5_000), "too many open subscriptions")
        assertNull(lowerLimitAfterRefusal("blocked: subscription limit reached (20)", 5_000))
        assertNull(lowerLimitAfterRefusal("error: max subscriptions limit of 10 reached", 5_000))
        assertNull(lowerLimitAfterRefusal("error: too many filters, limit is 10", 5_000), "too many filters per REQ")
        assertNull(lowerLimitAfterRefusal("blocked: REQ contains 12 filters, maximum is 10 (limit)", 5_000))
    }

    @Test
    fun severalFiltersComeDownOnlyAsFarAsTheRefusalSays() {
        assertEquals(listOf<Int?>(500, 100), lowerLimitsAfterRefusal("blocked: limit too high: 1000 (max 500)", listOf(1_000, 100)))
        assertEquals(listOf<Int?>(500, null), lowerLimitsAfterRefusal("blocked: limit too high", listOf(1_000, null)))
        // Every filter already at or under the stated max: the largest is halved.
        assertEquals(listOf<Int?>(250, 100), lowerLimitsAfterRefusal("blocked: limit too high (max 500)", listOf(500, 100)))
        assertNull(lowerLimitsAfterRefusal("blocked: limit too high", listOf(null, null)), "no limit to lower")
        assertNull(lowerLimitsAfterRefusal("error: rate limit exceeded", listOf(1_000)))
    }

    @Test
    fun throttlingIsToldApartFromARefusal() {
        // relay.damus.io and the bostr proxies in front of it
        assertTrue(isThrottleMessage(budget))
        assertTrue(isThrottleMessage("rate-limited: slow down"))
        assertTrue(isThrottleMessage("error: too many requests"))
        assertFalse(isThrottleMessage("blocked: limit too high: 5000 (max 500)"))
        assertFalse(isThrottleMessage("blocked: too much"))
        assertFalse(isThrottleMessage("auth-required: members only"))
    }

    @Test
    fun subscriptionCapsAndRateLimitsAreToldApart() {
        assertTrue(isSubscriptionLimitMessage("error: number of subscriptions exceeds limit"))
        assertTrue(isSubscriptionLimitMessage("ERROR: too many concurrent REQs"))
        assertFalse(isSubscriptionLimitMessage("rate-limited: slow down"))
        assertTrue(isRateLimitMessage("rate-limited: slow down"))
        assertTrue(isRateLimitMessage("error: too many requests"))
        assertFalse(isRateLimitMessage("error: number of subscriptions exceeds limit"))
    }

    @Test
    fun aNoticeRefusingTheCountVerbIsRecognised() {
        assertTrue(isCountRejectionNotice("ERROR: bad msg: unknown cmd")) // strfry builds without NIP-45
        assertTrue(isCountRejectionNotice("Unknown message type: COUNT")) // relay.snort.social
        assertTrue(isCountRejectionNotice("ERROR: bad msg: invalid message: {'message_type': ['Invalid enum value COUNT']}")) // nostr.wine
        assertTrue(isCountRejectionNotice("invalid message")) // relay.conduit.market
        assertFalse(isCountRejectionNotice("rate-limited: slow down"))
        assertFalse(isCountRejectionNotice("ERROR: bad msg: invalid message: event too large"))
        assertFalse(isCountRejectionNotice("could not parse filter"))
        // About one query on a relay that does count: not a refusal of the verb.
        assertFalse(isCountRejectionNotice("rate-limited: too many concurrent COUNT requests"))
    }
}
