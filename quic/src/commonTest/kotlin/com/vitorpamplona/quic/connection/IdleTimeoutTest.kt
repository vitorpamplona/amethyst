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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RFC 9000 §10.1 idle-timeout enforcement.
 *
 *  - §10.1: effective timeout = min of local + peer advertisements
 *    (skipping any side that advertised 0). Floored at 3 * PTO so loss
 *    recovery has time to fire before the connection times out.
 *  - §10.1.1: idle timer resets on (a) any successfully processed
 *    inbound packet, (b) outbound ack-eliciting packet emission.
 *  - §10.2.1: on expiry the connection enters CLOSED silently — no
 *    CONNECTION_CLOSE frame.
 *
 * Pre-fix `max_idle_timeout` was decoded into config + advertised in
 * the ClientHello transport-params extension, but never enforced — a
 * black-holed connection lived forever.
 */
class IdleTimeoutTest {
    private fun newConnection(
        nowMillis: () -> Long,
        localIdleMs: Long,
    ): QuicConnection =
        QuicConnection(
            serverName = "example.test",
            config =
                QuicConnectionConfig(
                    maxIdleTimeoutMillis = localIdleMs,
                ),
            tlsCertificateValidator = PermissiveCertificateValidator(),
            nowMillis = nowMillis,
        )

    @Test
    fun effective_timeout_uses_min_of_local_and_peer() {
        val conn = newConnection(nowMillis = { 0L }, localIdleMs = 30_000L)
        // No peer params yet → only local advertised.
        assertEquals(30_000L, conn.effectiveIdleTimeoutMs())

        // Peer advertises smaller — min wins.
        conn.peerTransportParameters = TransportParameters(maxIdleTimeoutMillis = 10_000L)
        assertEquals(10_000L, conn.effectiveIdleTimeoutMs())

        // Peer advertises larger — local still smaller, local wins.
        conn.peerTransportParameters = TransportParameters(maxIdleTimeoutMillis = 60_000L)
        assertEquals(30_000L, conn.effectiveIdleTimeoutMs())
    }

    @Test
    fun effective_timeout_skips_zero_advertisements() {
        // Local 0, peer 30s → peer wins (RFC 9000 §18.2: 0 means disabled).
        val conn = newConnection(nowMillis = { 0L }, localIdleMs = 0L)
        conn.peerTransportParameters = TransportParameters(maxIdleTimeoutMillis = 30_000L)
        assertEquals(30_000L, conn.effectiveIdleTimeoutMs())

        // Both 0 / unset → disabled (null).
        val conn2 = newConnection(nowMillis = { 0L }, localIdleMs = 0L)
        assertNull(conn2.effectiveIdleTimeoutMs())

        val conn3 = newConnection(nowMillis = { 0L }, localIdleMs = 0L)
        conn3.peerTransportParameters = TransportParameters(maxIdleTimeoutMillis = 0L)
        assertNull(conn3.effectiveIdleTimeoutMs())
    }

    @Test
    fun effective_timeout_floored_at_three_PTO() {
        // §10.1: floor = 3 * PTO. Pre-handshake PTO base ≈
        //   smoothed_rtt(100ms) + max(4*rttvar(50ms), 1ms) + max_ack_delay(0)
        //   = 300 ms; 3 * 300 = 900 ms.
        // A configured 200 ms idle timeout MUST be floored, not honored
        // verbatim, otherwise a one-RTT loss recovery period would
        // outlast the connection.
        val conn = newConnection(nowMillis = { 0L }, localIdleMs = 200L)
        val effective = conn.effectiveIdleTimeoutMs()
        assertNotNull(effective)
        assertTrue(effective >= 900L, "expected floor of 900 ms, got $effective")
    }

    @Test
    fun isIdleTimedOut_false_at_construction() {
        val now = mutableLongOf(0L)
        val conn = newConnection(nowMillis = now::get, localIdleMs = 10_000L)
        assertFalse(conn.isIdleTimedOut(now.get()))
    }

    @Test
    fun isIdleTimedOut_false_before_deadline() {
        val now = mutableLongOf(0L)
        val conn = newConnection(nowMillis = now::get, localIdleMs = 10_000L)
        // Just under the deadline.
        now.set(9_999L)
        assertFalse(conn.isIdleTimedOut(now.get()))
    }

    @Test
    fun isIdleTimedOut_true_at_deadline() {
        val now = mutableLongOf(0L)
        val conn = newConnection(nowMillis = now::get, localIdleMs = 10_000L)
        now.set(10_000L)
        assertTrue(conn.isIdleTimedOut(now.get()))
    }

    @Test
    fun lastActivityMs_bump_postpones_deadline() {
        val now = mutableLongOf(0L)
        val conn = newConnection(nowMillis = now::get, localIdleMs = 10_000L)
        // Halfway through the idle window an inbound packet arrives.
        now.set(5_000L)
        conn.lastActivityMs = now.get()
        // Deadline shifts to 15_000 — still inside at 14_000.
        now.set(14_000L)
        assertFalse(conn.isIdleTimedOut(now.get()))
        // Past the new deadline.
        now.set(15_000L)
        assertTrue(conn.isIdleTimedOut(now.get()))
    }

    @Test
    fun isIdleTimedOut_never_when_disabled() {
        val now = mutableLongOf(0L)
        val conn = newConnection(nowMillis = now::get, localIdleMs = 0L)
        // Even at simulated wallclock infinity, no timeout is enforced.
        now.set(Long.MAX_VALUE / 2)
        assertFalse(conn.isIdleTimedOut(now.get()))
    }
}

/**
 * Minimal mutable Long holder for clock injection. Avoids depending on
 * kotlinx.atomicfu in test sources where atomicity isn't needed (the
 * clock is bumped from the test thread, read from the same thread).
 */
private class MutableLong(
    initial: Long,
) {
    private var value = initial

    fun get(): Long = value

    fun set(v: Long) {
        value = v
    }
}

private fun mutableLongOf(initial: Long): MutableLong = MutableLong(initial)
