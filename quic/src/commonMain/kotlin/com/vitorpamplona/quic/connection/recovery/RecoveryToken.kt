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

/**
 * RFC 9002 §6 retransmit token. One token is recorded per ack-eliciting
 * frame at send time; the [SentPacket] carrying it is held in
 * [com.vitorpamplona.quic.connection.QuicConnection]'s sent-packet map
 * keyed by packet number.
 *
 * On ACK, the carrying packet's tokens are dropped silently — the peer
 * has confirmed receipt. On loss declaration, each token is dispatched
 * to its `onLost` handler, which decides whether the frame still needs
 * to be re-emitted (e.g. a `MAX_STREAMS_UNI` whose value has since
 * been superseded by a higher extension is not re-sent).
 *
 * Mirrors Firefox neqo's `StreamRecoveryToken` enum at
 * `neqo-transport/src/recovery/token.rs:21`. Scope (per
 * `quic/plans/2026-05-04-control-frame-retransmit.md`) is the
 * receive-side flow-control extensions that today silently wedge the
 * connection on packet loss against moq-rs:
 *
 *   - `MAX_STREAMS_UNI` / `MAX_STREAMS_BIDI` (RFC 9000 §19.11)
 *   - `MAX_DATA` (§19.9)
 *   - `MAX_STREAM_DATA` (§19.10)
 *
 * [Ack] is tracked but never retransmitted — ACK frames are not
 * ack-eliciting per RFC 9000 §13.2.1, so their loss is recovered by
 * the peer's own ACK retransmission of newer ranges.
 *
 * Out of scope (separate follow-ups, see plan):
 *   - STREAM data retransmit
 *   - CRYPTO data retransmit
 *   - RESET_STREAM, STOP_SENDING, NEW_CONNECTION_ID, etc.
 */
sealed class RecoveryToken {
    /**
     * Marks an ACK frame in the carrying packet. Recorded so the
     * sent-packet map's invariant ("every retained packet has a
     * non-empty `tokens` list") holds for ACK-only packets too, but
     * never re-emitted on loss.
     */
    data object Ack : RecoveryToken()

    /**
     * `MAX_STREAMS_UNI` extension we sent. On loss, only re-emit if
     * [maxStreams] still equals the connection's current
     * `advertisedMaxStreamsUni` — otherwise a newer extension has
     * already gone out and supersedes this one.
     */
    data class MaxStreamsUni(
        val maxStreams: Long,
    ) : RecoveryToken()

    /** Bidi counterpart of [MaxStreamsUni]. */
    data class MaxStreamsBidi(
        val maxStreams: Long,
    ) : RecoveryToken()

    /**
     * Connection-level `MAX_DATA` extension. On loss, only re-emit
     * if [maxData] still equals the connection's current
     * `advertisedMaxData` — same supersede semantics as
     * [MaxStreamsUni].
     */
    data class MaxData(
        val maxData: Long,
    ) : RecoveryToken()

    /**
     * Per-stream `MAX_STREAM_DATA` extension. On loss, the dispatcher
     * looks up the stream by [streamId]; if the stream has since been
     * closed or its advertised cap has moved past [maxData], the
     * token is dropped without re-emit.
     */
    data class MaxStreamData(
        val streamId: Long,
        val maxData: Long,
    ) : RecoveryToken()
}
