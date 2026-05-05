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
     * Marks an ACK frame in the carrying packet. Carries enough
     * context for the dispatcher to purge our own [com.vitorpamplona.quic.recovery.AckTracker]
     * once the peer ACKs the carrying packet — i.e. an ACK-of-ACK.
     *
     * RFC 9000 §13.2.1: ACK frames are not ack-eliciting and not
     * retransmitted. But the ACK we sent IS itself acknowledgeable
     * (it rides on a packet whose other frames may be ack-eliciting),
     * and the peer's ACK of THAT packet means the peer has now
     * received our ACK for everything up to [largestAcked]. We can
     * stop including those PNs in subsequent outbound ACK frames.
     *
     * Pre-fix the parser purged on every inbound ACK using the
     * peer's `largestAcknowledged - firstAckRange` value — but that
     * is in OUR outbound PN space, not the inbound PN space the
     * tracker holds. The values happened to grow at similar rates so
     * the bug rarely manifested as outright wrongness, just
     * range-list bloat over long sessions where the two spaces drift
     * apart (e.g. listener receiving ~50 audio frames/sec while
     * sending ~1 ACK/sec).
     *
     * [level] routes the purge to the right per-space [com.vitorpamplona.quic.connection.LevelState.ackTracker]
     * since we track separately for Initial / Handshake / Application.
     */
    data class Ack(
        val level: com.vitorpamplona.quic.connection.EncryptionLevel,
        val largestAcked: Long,
    ) : RecoveryToken()

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

    /**
     * STREAM data range we sent. On loss, the dispatcher informs the
     * stream's [com.vitorpamplona.quic.stream.SendBuffer] that bytes
     * `[offset, offset + length)` need retransmission — the buffer
     * moves them from "sent" back to "needs retransmit", and the
     * next [com.vitorpamplona.quic.connection.QuicConnectionWriter]
     * drain re-emits them with the same offset (idempotent retransmit
     * per RFC 9000 §13.3).
     *
     * [fin] tracks whether this STREAM frame carried the FIN bit;
     * lost FIN frames must also be retransmitted (the FIN is part of
     * the stream's reliable byte sequence).
     */
    data class Stream(
        val streamId: Long,
        val offset: Long,
        val length: Long,
        val fin: Boolean,
    ) : RecoveryToken()

    /**
     * CRYPTO data range we sent at a specific encryption level.
     * RFC 9000 §17.2.5: handshake bytes are reliable per RFC 9000
     * §13.3; a lost CRYPTO frame must be retransmitted at the same
     * encryption level it was originally sent at. The dispatcher
     * consults [level] to route to the correct
     * [com.vitorpamplona.quic.connection.LevelState.cryptoSend].
     */
    data class Crypto(
        val level: com.vitorpamplona.quic.connection.EncryptionLevel,
        val offset: Long,
        val length: Long,
    ) : RecoveryToken()

    /**
     * `RESET_STREAM` frame we sent (RFC 9000 §19.4). Carries the
     * stream id, the application error code, and the final size.
     * On loss, retransmit verbatim — RESET_STREAM is reliable per
     * §13.3.
     *
     * `:quic` doesn't currently emit RESET_STREAM (no application
     * code triggers stream reset), so this variant is scaffolding for
     * future work. Listed in the enum so the dispatcher's `when`
     * stays exhaustive at compile time.
     */
    data class ResetStream(
        val streamId: Long,
        val errorCode: Long,
        val finalSize: Long,
    ) : RecoveryToken()

    /**
     * `STOP_SENDING` frame we sent (RFC 9000 §19.5). Reliable per
     * §13.3; retransmit verbatim on loss. Same scaffolding-only
     * status as [ResetStream] — `:quic` doesn't emit it today.
     */
    data class StopSending(
        val streamId: Long,
        val errorCode: Long,
    ) : RecoveryToken()

    /**
     * `NEW_CONNECTION_ID` frame we sent (RFC 9000 §19.15). Reliable
     * per §13.3. Same scaffolding-only status — connection-ID
     * rotation isn't wired today.
     */
    data class NewConnectionId(
        val sequenceNumber: Long,
        val retirePriorTo: Long,
        val connectionId: ByteArray,
        val statelessResetToken: ByteArray,
    ) : RecoveryToken() {
        // ByteArray fields require explicit equals/hashCode for data-class
        // contract correctness — Kotlin's data-class auto-equals does
        // identity compare on arrays.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NewConnectionId) return false
            return sequenceNumber == other.sequenceNumber &&
                retirePriorTo == other.retirePriorTo &&
                connectionId.contentEquals(other.connectionId) &&
                statelessResetToken.contentEquals(other.statelessResetToken)
        }

        override fun hashCode(): Int {
            var result = sequenceNumber.hashCode()
            result = 31 * result + retirePriorTo.hashCode()
            result = 31 * result + connectionId.contentHashCode()
            result = 31 * result + statelessResetToken.contentHashCode()
            return result
        }
    }
}
