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

/**
 * Tunables for a [QuicConnection]. Defaults are appropriate for the MoQ +
 * WebTransport workload — small connection-level windows, a generous-enough
 * datagram size to fit Opus frames, idle timeout of 30 s.
 *
 * `maxUdpDatagramSize` caps both inbound and outbound UDP payload size. The
 * QUIC spec mandates that any client sending Initial packets MUST send
 * datagrams of at least 1200 bytes; we pad outbound Initials to satisfy this
 * regardless of how full the packet is.
 */
data class QuicConnectionConfig(
    val initialMaxData: Long = 16L * 1024 * 1024,
    val initialMaxStreamDataBidiLocal: Long = 1L * 1024 * 1024,
    val initialMaxStreamDataBidiRemote: Long = 1L * 1024 * 1024,
    val initialMaxStreamDataUni: Long = 1L * 1024 * 1024,
    val initialMaxStreamsBidi: Long = 100L,
    /**
     * Initial peer-initiated unidirectional stream limit. moq-rs's Quinn
     * stack advertises `max_concurrent_uni_streams = 10000` for the same
     * reason we now do: every Opus group is a fresh peer-initiated uni
     * stream, so a long broadcast accumulates many lifetime stream IDs.
     *
     * Production tracing on a listener phone (Phone B receiving a Phone-A
     * broadcast against `moq.nostrnests.com`) showed the cliff lands at
     * exactly the moment our writer emits its first `MAX_STREAMS_UNI`
     * extension at the half-window threshold (count=50 with cap=100):
     * the listener receives one more stream after the bump, then UDP
     * goes silent at the kernel level (`udpRecvDatagrams` frozen) while
     * our QUIC state still believes the connection is alive. The relay
     * propagates the listener's disconnect to the publisher (publisher
     * sees `inbound SUBSCRIBE FIN'd`), but our QUIC stack never observes
     * it — split-brain. The trace is reproducible across runs.
     *
     * The rolling-extension code path remains in place for correctness on
     * other peers (and so 100×fpg=5 broadcasts of audio-rooms longer than
     * ~50 s × 5 ≈ 250 s still extend cleanly), but with this cap raised
     * to 10 000 the listener won't actually need to extend until 5 000+
     * groups have flowed — well past any realistic Nest duration. That
     * sidesteps the malformed-or-mis-sequenced extension that's tripping
     * the relay today.
     */
    val initialMaxStreamsUni: Long = 10_000L,
    val maxIdleTimeoutMillis: Long = 30_000L,
    val maxUdpPayloadSize: Long = 1452L,
    val activeConnectionIdLimit: Long = 4L,
    val maxDatagramFrameSize: Long = 1200L,
    val ackDelayExponent: Long = 3L,
    val maxAckDelay: Long = 25L,
)
