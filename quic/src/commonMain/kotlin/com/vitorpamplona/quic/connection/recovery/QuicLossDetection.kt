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

import kotlin.math.abs

/**
 * RFC 9002 §5–§6 loss detection — RTT estimation + packet/time
 * threshold loss declaration. Mirrors the algorithm Firefox neqo
 * implements at `neqo-transport/src/recovery/mod.rs` and `rtt.rs`.
 *
 * State is per-connection (RTT estimates are per-path; we model a
 * single path). Per-encryption-space state — `largestAckedPn`,
 * `largestAckedSentTime` — lives on [com.vitorpamplona.quic.connection.LevelState].
 *
 * Step 5 of `quic/plans/2026-05-04-control-frame-retransmit.md`: this
 * file implements the algorithm; the parser invokes it on every
 * inbound ACK; step 6 dispatches the returned lost-token list to
 * the `pending*` setters.
 */
class QuicLossDetection {
    /**
     * RFC 9002 §5.3. Smoothed RTT estimate. Initialised to
     * [INITIAL_RTT_MS] until the first sample arrives. Updated as
     * `smoothed_rtt = (7/8) * smoothed_rtt + (1/8) * adjusted_rtt`.
     */
    var smoothedRttMs: Long = INITIAL_RTT_MS
        private set

    /**
     * RFC 9002 §5.3. RTT variance estimate. Initialised to half the
     * initial RTT. Updated as
     * `rttvar = (3/4) * rttvar + (1/4) * |smoothed_rtt - adjusted_rtt|`.
     */
    var rttVarMs: Long = INITIAL_RTT_MS / 2
        private set

    /**
     * RFC 9002 §5.2. Latest RTT sample, before ack-delay adjustment.
     * Tracks the most recent observation so callers can compute
     * `max(latestRtt, smoothedRtt)` for the time-threshold loss check.
     */
    var latestRttMs: Long = INITIAL_RTT_MS
        private set

    /**
     * RFC 9002 §5.2. Minimum RTT observed on this path. Used to
     * clamp ack-delay so a peer can't artificially inflate our RTT
     * estimate by reporting a large ack-delay.
     */
    var minRttMs: Long = Long.MAX_VALUE
        private set

    /** True after the first valid RTT sample has been processed. */
    var hasFirstRttSample: Boolean = false
        private set

    /**
     * Update RTT estimates from a new ACK that newly-acknowledged
     * the largest packet number. Per RFC 9002 §5.2, an RTT sample
     * is only generated when:
     *   - the largest acknowledged packet number is newly acknowledged, and
     *   - at least one of the newly acknowledged packets was ack-eliciting.
     *
     * The caller must enforce both conditions before calling this.
     */
    fun onRttSample(
        largestAckedSentTimeMs: Long,
        ackDelayMs: Long,
        nowMs: Long,
    ) {
        val rawSampleMs = nowMs - largestAckedSentTimeMs
        if (rawSampleMs < 0L) return // clock skew; ignore
        if (!hasFirstRttSample) {
            minRttMs = rawSampleMs
            smoothedRttMs = rawSampleMs
            rttVarMs = rawSampleMs / 2
            latestRttMs = rawSampleMs
            hasFirstRttSample = true
            return
        }
        if (rawSampleMs < minRttMs) minRttMs = rawSampleMs
        // Adjust by ack-delay, clamped so that adjusted_rtt >= min_rtt.
        // Without the clamp, a peer reporting a fake high ack-delay can
        // push smoothed_rtt below min_rtt (RFC 9002 §5.3).
        val adjusted =
            if (rawSampleMs - ackDelayMs >= minRttMs) {
                rawSampleMs - ackDelayMs
            } else {
                rawSampleMs
            }
        latestRttMs = adjusted
        rttVarMs = (3L * rttVarMs + abs(smoothedRttMs - adjusted)) / 4L
        smoothedRttMs = (7L * smoothedRttMs + adjusted) / 8L
    }

    /**
     * RFC 9002 §6.1.2. Loss-detection time threshold:
     * `loss_delay = max(latest_rtt, smoothed_rtt) * (9/8)`
     * clamped to at least [GRANULARITY_MS].
     */
    fun lossDelayMs(): Long {
        val maxRtt = if (latestRttMs > smoothedRttMs) latestRttMs else smoothedRttMs
        val scaled = (maxRtt * TIME_THRESHOLD_NUM) / TIME_THRESHOLD_DEN
        return if (scaled > GRANULARITY_MS) scaled else GRANULARITY_MS
    }

    /**
     * Detect packets lost in [sentPackets] and remove them. Called
     * after the parser drains ACK'd packets — the surviving entries
     * are the in-flight set, plus any older packets that haven't
     * been ACK'd yet. RFC 9002 §6.1 declares a packet lost iff:
     *
     *   - it has a smaller PN than [largestAckedPn] AND
     *     - it was sent at least [PACKET_THRESHOLD] PNs before
     *       [largestAckedPn], OR
     *     - it was sent more than [lossDelayMs] ago.
     *
     * Returns the list of lost packets in arbitrary order. Caller
     * dispatches their [SentPacket.tokens] (step 6).
     */
    fun detectAndRemoveLost(
        sentPackets: MutableMap<Long, SentPacket>,
        largestAckedPn: Long,
        nowMs: Long,
    ): List<SentPacket> {
        if (sentPackets.isEmpty()) return emptyList()
        val lossDelay = lossDelayMs()
        val lossThresholdSentMs = nowMs - lossDelay
        val lost = mutableListOf<SentPacket>()
        val it = sentPackets.entries.iterator()
        while (it.hasNext()) {
            val (pn, pkt) = it.next()
            if (pn >= largestAckedPn) continue
            val packetThresholdLost = pn < largestAckedPn - PACKET_THRESHOLD
            val timeThresholdLost = pkt.sentAtMillis <= lossThresholdSentMs
            if (packetThresholdLost || timeThresholdLost) {
                lost += pkt
                it.remove()
            }
        }
        return lost
    }

    /**
     * RFC 9002 §6.2.1 Probe Timeout duration:
     *
     *     PTO = smoothed_rtt + max(4 * rttvar, kGranularity) + max_ack_delay
     *
     * For Application packets only; Initial / Handshake spaces use
     * `max_ack_delay = 0` per §6.2.1. The connection passes the
     * peer's [maxAckDelayMs] (from its transport parameters) — pass
     * 0 if the peer hasn't advertised it yet.
     *
     * The result is doubled by the caller for each consecutive PTO
     * expiration (§6.2.2 exponential backoff), capped so a long-
     * silent connection doesn't wait forever between probes.
     */
    fun ptoBaseMs(maxAckDelayMs: Long): Long {
        val variancePart = (4L * rttVarMs).coerceAtLeast(GRANULARITY_MS)
        return smoothedRttMs + variancePart + maxAckDelayMs
    }

    companion object {
        /** RFC 9002 §6.2.2 default initial RTT before a sample arrives. */
        const val INITIAL_RTT_MS: Long = 333L

        /** RFC 9002 §6.1.1 packet-reordering threshold (number of PNs). */
        const val PACKET_THRESHOLD: Long = 3L

        /**
         * RFC 9002 §6.1.2 time-threshold multiplier. The scaled max
         * RTT is `max_rtt * NUM / DEN`; with NUM=9, DEN=8 the
         * threshold is `max_rtt * 9/8 = max_rtt + max_rtt/8`.
         */
        const val TIME_THRESHOLD_NUM: Long = 9L
        const val TIME_THRESHOLD_DEN: Long = 8L

        /** RFC 9002 §6.1.2 granularity floor. Most stacks use 1 ms. */
        const val GRANULARITY_MS: Long = 1L
    }
}
