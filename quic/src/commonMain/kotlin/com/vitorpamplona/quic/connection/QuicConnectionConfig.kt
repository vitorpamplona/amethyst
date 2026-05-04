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
     * Initial peer-initiated unidirectional stream limit. Sized to never
     * trip the rolling [QuicConnectionWriter.appendFlowControlUpdates]
     * extension path during a realistic audio-rooms broadcast — see
     * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`.
     *
     * Why a fixed-and-large initial value instead of relying on rolling
     * extension: production tracing against `moq.nostrnests.com` showed
     * that emitting `MAX_STREAMS_UNI` mid-connection silently breaks the
     * relay's send path. The listener receives one more uni stream after
     * the bump, then UDP goes dead at the kernel level
     * (`udpRecvDatagrams` frozen) while our QUIC state still believes
     * the connection is alive. The relay propagates the listener's
     * disconnect to the publisher (`inbound SUBSCRIBE FIN'd` on the
     * publisher side), but our stack never observes it — split-brain.
     * Reproducible across runs. We don't yet know whether our frame is
     * malformed, mis-sequenced relative to other frames in the packet,
     * or hitting a moq-rs / Quinn bug — but we do know that not emitting
     * the extension keeps the connection healthy.
     *
     * Capacity math, with [NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP]
     * = 5 and Opus 20 ms: each group is one peer-initiated uni stream,
     * so the relay opens ~10 streams/sec. The half-window threshold
     * (`count + initialMaxStreamsUni/2 >= advertisedMaxStreamsUni`)
     * trips at count = 500 000 streams, i.e. ~13.9 hours of continuous
     * audio. Well past any realistic Nest duration.
     *
     * Memory cost: the [QuicConnection.streams] map currently grows for
     * the connection's lifetime. At 10 streams/sec a 2-hour Nest leaves
     * ~72k entries; per-stream overhead is small but unbounded growth
     * over many hours is a known follow-up. For now this is a tolerable
     * trade in exchange for not tripping the relay-side bug.
     */
    val initialMaxStreamsUni: Long = 1_000_000L,
    val maxIdleTimeoutMillis: Long = 30_000L,
    val maxUdpPayloadSize: Long = 1452L,
    val activeConnectionIdLimit: Long = 4L,
    val maxDatagramFrameSize: Long = 1200L,
    val ackDelayExponent: Long = 3L,
    val maxAckDelay: Long = 25L,
)
