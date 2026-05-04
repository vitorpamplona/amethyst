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
package com.vitorpamplona.quic.connection.recovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Step 7 of `quic/plans/2026-05-04-control-frame-retransmit.md`:
 * RFC 9002 §6.2 Probe Timeout duration calculation. Mirrors neqo's
 * PTO tests (`pto_works_basic`, `pto_state_count`, etc.) at the
 * algorithm level — the driver-loop integration is exercised by
 * the existing connection tests.
 */
class PtoTest {
    @Test
    fun ptoBeforeFirstRttSample_usesInitialDefault() {
        val ld = QuicLossDetection()
        // Before any sample: smoothed_rtt = 333, rttvar = 333/2 = 166.
        // PTO = 333 + max(4*166, 1) + 0 = 333 + 664 = 997.
        assertEquals(997L, ld.ptoBaseMs(maxAckDelayMs = 0L))
    }

    @Test
    fun ptoIncludesMaxAckDelay() {
        val ld = QuicLossDetection()
        assertEquals(997L + 25L, ld.ptoBaseMs(maxAckDelayMs = 25L))
    }

    @Test
    fun ptoAfterRttSample_usesNewSmoothedRtt() {
        val ld = QuicLossDetection()
        ld.onRttSample(largestAckedSentTimeMs = 0L, ackDelayMs = 0L, nowMs = 100L)
        // After first sample: smoothed = 100, rttvar = 50.
        // PTO = 100 + max(4*50, 1) + 0 = 100 + 200 = 300.
        assertEquals(300L, ld.ptoBaseMs(maxAckDelayMs = 0L))
    }

    @Test
    fun ptoFloors4RttVarAtGranularity() {
        val ld = QuicLossDetection()
        // Two identical samples drive rttvar toward 0. Then PTO should
        // floor `4 * rttvar` at 1 ms granularity.
        ld.onRttSample(largestAckedSentTimeMs = 0L, ackDelayMs = 0L, nowMs = 100L)
        // rttvar after first sample = 50.
        ld.onRttSample(largestAckedSentTimeMs = 100L, ackDelayMs = 0L, nowMs = 200L)
        // smoothed = (7*100 + 100)/8 = 100. rttvar = (3*50 + 0)/4 = 37.
        // 4*37 = 148, > 1ms. OK floor not exercised yet.
        // Many identical samples drive rttvar → 0:
        var t = 200L
        repeat(40) {
            ld.onRttSample(largestAckedSentTimeMs = t, ackDelayMs = 0L, nowMs = t + 100L)
            t += 100L
        }
        // After many samples: rttvar should be tiny but positive. PTO
        // contribution from 4*rttvar might fall below 1; check the
        // floor by comparing PTO to smoothed_rtt + max_ack_delay only
        // (i.e. the variance contribution is at least 1).
        val smoothed = ld.smoothedRttMs
        val pto = ld.ptoBaseMs(maxAckDelayMs = 0L)
        assertTrue(pto >= smoothed + 1L, "PTO must include at least 1ms of variance: pto=$pto smoothed=$smoothed")
    }
}
