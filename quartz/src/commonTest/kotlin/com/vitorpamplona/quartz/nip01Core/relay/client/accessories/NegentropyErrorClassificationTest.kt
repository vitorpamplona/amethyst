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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract for the two narrow NEG-ERR/NOTICE wording classifiers. Both feed
 * irreversible control flow — [isOverflow] triggers `created_at` window-splitting,
 * [isNegentropyRejectionNotice] aborts a reconcile to paging — so a false positive is
 * costly (a split storm, or a healthy sync abandoned). These lock the boundary.
 */
class NegentropyErrorClassificationTest {
    @Test
    fun overflowMatchesResultSetSizeErrors() {
        // strfry, current + older wording, and equivalents from other relays.
        assertTrue(isOverflow("blocked: query matches too many records (2988225 > 1000000)"))
        assertTrue(isOverflow("too many query results"))
        assertTrue(isOverflow("ERROR: too many results"))
        assertTrue(isOverflow("result set too large"))
        assertTrue(isOverflow("results too large to sync"))
        assertTrue(isOverflow("exceeds max_sync_events"))
    }

    @Test
    fun overflowRejectsRateAndRefusalErrors() {
        // These do NOT shrink when the window shrinks — treating them as overflow
        // would split forever toward 1-second leaves and storm the relay. They must
        // fail over to paging instead.
        assertFalse(isOverflow("rate-limited: too many requests"))
        assertFalse(isOverflow("error: too many concurrent subscriptions"))
        assertFalse(isOverflow("too many connections"))
        assertFalse(isOverflow("blocked: message too large"))
        assertFalse(isOverflow("blocked: negentropy disabled"))
        assertFalse(isOverflow("auth-required: restricted"))
    }

    @Test
    fun rejectionNoticeMatchesTheObservedRefusals() {
        assertTrue(isNegentropyRejectionNotice("ERROR: bad msg: negentropy disabled"))
        assertTrue(isNegentropyRejectionNotice("Negentropy sync is disabled"))
        assertTrue(isNegentropyRejectionNotice("failed to parse envelope: unknown envelope label"))
    }

    @Test
    fun rejectionNoticeIgnoresUnrelatedNotices() {
        // A NOTICE has no subId, so an over-broad matcher would let unrelated traffic
        // on the shared connection abort a healthy reconcile mid-handshake.
        assertFalse(isNegentropyRejectionNotice("rate-limited: slow down"))
        assertFalse(isNegentropyRejectionNotice("invalid: bad event envelope size"))
        assertFalse(isNegentropyRejectionNotice("could not parse REQ"))
        assertFalse(isNegentropyRejectionNotice("restricted: auth required"))
    }
}
