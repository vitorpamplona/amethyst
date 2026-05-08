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

import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quic.connection.recovery.RecoveryToken
import com.vitorpamplona.quic.connection.recovery.SentPacket
import com.vitorpamplona.quic.frame.AckFrame
import com.vitorpamplona.quic.frame.ConnectionCloseFrame
import com.vitorpamplona.quic.frame.CryptoFrame
import com.vitorpamplona.quic.frame.DatagramFrame
import com.vitorpamplona.quic.frame.Frame
import com.vitorpamplona.quic.frame.MaxDataFrame
import com.vitorpamplona.quic.frame.MaxStreamDataFrame
import com.vitorpamplona.quic.frame.MaxStreamsFrame
import com.vitorpamplona.quic.frame.NewConnectionIdFrame
import com.vitorpamplona.quic.frame.PathChallengeFrame
import com.vitorpamplona.quic.frame.PathResponseFrame
import com.vitorpamplona.quic.frame.PingFrame
import com.vitorpamplona.quic.frame.ResetStreamFrame
import com.vitorpamplona.quic.frame.RetireConnectionIdFrame
import com.vitorpamplona.quic.frame.StopSendingFrame
import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.frame.encodeFrames
import com.vitorpamplona.quic.observability.QlogObserver
import com.vitorpamplona.quic.observability.qlogFrameName
import com.vitorpamplona.quic.packet.LongHeaderPacket
import com.vitorpamplona.quic.packet.LongHeaderPlaintextPacket
import com.vitorpamplona.quic.packet.LongHeaderType
import com.vitorpamplona.quic.packet.QuicVersion
import com.vitorpamplona.quic.packet.ShortHeaderPacket
import com.vitorpamplona.quic.packet.ShortHeaderPlaintextPacket

/**
 * Build the next UDP datagram to send for [conn], or return null if there's
 * nothing to send right now.
 *
 * The datagram coalesces (in order):
 *   - Initial packet (if Initial-level CRYPTO has unsent bytes or a pending ACK)
 *   - Handshake packet (likewise)
 *   - 1-RTT packet (CRYPTO from NewSessionTicket, STREAM, DATAGRAM, ACK, etc.)
 *
 * RFC 9000 §14: any datagram containing an Initial packet from the client
 * MUST be padded to at least 1200 bytes total.
 *
 * Lock-split refactor (2026-05-08): caller must hold
 * [QuicConnection.streamsLock]. Phase 1 keeps level-state mutation
 * inline under the same critical section as the streams-domain work
 * the writer needs — the win comes from `lifecycleLock`-only callers
 * (close(), status reads, PTO bookkeeping) no longer fighting this lock.
 * The driver wraps `streamsLock.withLock { drainOutbound(...) }`; tests
 * that drive single-threaded send paths can call this without holding
 * the lock — there's no contending thread.
 */
fun drainOutbound(
    conn: QuicConnection,
    nowMillis: Long,
): ByteArray? {
    val parts = mutableListOf<ByteArray>()

    // Closing — emit a CONNECTION_CLOSE at the highest available level. The
    // datagram-build paths below MUST satisfy two RFC 9000 constraints we
    // got wrong before:
    //  - §10.2.3: at Initial / Handshake levels, only CONNECTION_CLOSE
    //    (Transport, 0x1c) is allowed; the application-level close (0x1d) is
    //    forbidden because app state would leak before the handshake is
    //    encrypted with the application key.
    //  - §14.1: a client datagram containing an Initial MUST be ≥ 1200 bytes
    //    in UDP-payload terms, even when carrying only a CONNECTION_CLOSE.
    if (conn.status == QuicConnection.Status.CLOSING) {
        val datagram = buildClosingDatagram(conn, nowMillis)
        conn.status = QuicConnection.Status.CLOSED
        return datagram
    }

    // Bug-A fix: drive the §8.2.4 3*PTO validation budget on every
    // drain, not just on PTO expiration. The peer ACKs PATH_CHALLENGE
    // (it's ack-eliciting), and ACK arrival resets consecutivePtoCount —
    // so the PTO timer that previously hosted [checkPathValidationTimeoutLocked]
    // can stop firing before the budget elapses, leaving validation
    // hung indefinitely. Drain happens at least every PTO_BASE
    // (~30-100ms) even when the application is idle, which covers the
    // budget-elapsed condition reliably. Cheap (one type-cast +
    // time comparison) when the validator is in [PathValidationState.Idle].
    conn.checkPathValidationTimeoutLocked(nowMillis)

    // Drain destructive frame sources into local lists, ONCE.
    val initialContents = collectHandshakeLevelFrames(conn, EncryptionLevel.INITIAL, nowMillis)
    val handshakeContents = collectHandshakeLevelFrames(conn, EncryptionLevel.HANDSHAKE, nowMillis)
    val applicationPkt = buildApplicationPacket(conn, nowMillis)

    val initialState = conn.initial
    val handshakeState = conn.handshake
    val initialHasContent = initialContents != null && initialState.sendProtection != null
    val handshakeHasContent = handshakeContents != null && handshakeState.sendProtection != null

    // Build natural-size first.
    val initialNatural =
        if (initialContents != null && initialState.sendProtection != null) {
            buildLongHeaderFromFrames(
                conn = conn,
                level = EncryptionLevel.INITIAL,
                frames = initialContents.frames,
                tokens = initialContents.tokens,
                nowMillis = nowMillis,
                padBytes = 0,
            )
        } else {
            null
        }
    val handshakeNatural =
        if (handshakeContents != null && handshakeState.sendProtection != null) {
            buildLongHeaderFromFrames(
                conn = conn,
                level = EncryptionLevel.HANDSHAKE,
                frames = handshakeContents.frames,
                tokens = handshakeContents.tokens,
                nowMillis = nowMillis,
                padBytes = 0,
            )
        } else {
            null
        }

    val firstPass = listOfNotNull(initialNatural, handshakeNatural, applicationPkt)
    if (firstPass.isEmpty()) return null

    // RFC 9000 §14.1: client datagrams containing Initial MUST pad to ≥ 1200,
    // via PADDING frames inside the Initial's encryption envelope.
    val datagram =
        if (initialNatural != null) {
            var natural = 0
            for (p in firstPass) natural += p.size
            if (natural < 1200) {
                // Padding rebuild: re-issue the Initial with PADDING frames inside
                // the AEAD envelope so the on-wire datagram clears the §14.1 floor.
                //
                // Off-by-one trap: the QUIC long-header Length field is a varint
                // (RFC 9000 §16). When the natural-size payload is tiny enough
                // for Length to fit in 1 byte (body ≤ 63 bytes), the rebuild's
                // larger body crosses the 64-byte threshold and Length grows to
                // 2 bytes — adding 1 wire byte that wasn't in `natural`. Same
                // shape at 16384 bytes (2 → 4) and 2^30 (4 → 8). A naive
                // `padBytes = 1200 - natural` then produces a 1199-byte
                // datagram for PING-only Initials.
                //
                // Solution: rebuild with the initial deficit, measure, and if we
                // still fall short, bump by the residual and rebuild once more.
                // Each iteration adds PADDING bytes 1:1 to the wire size; the
                // varint grows monotonically so this terminates in ≤ 2 rounds
                // for any reachable payload size.
                var padBytes = 1200 - natural
                var paddedInitial: ByteArray
                while (true) {
                    initialState.pnSpace.rewindOutboundForRebuild()
                    paddedInitial =
                        buildLongHeaderFromFrames(
                            conn = conn,
                            level = EncryptionLevel.INITIAL,
                            frames = initialContents!!.frames, // null-safe: gated by `initialNatural != null` above
                            tokens = initialContents.tokens,
                            nowMillis = nowMillis,
                            padBytes = padBytes,
                        )
                    var totalAfterRebuild = paddedInitial.size
                    if (handshakeNatural != null) totalAfterRebuild += handshakeNatural.size
                    if (applicationPkt != null) totalAfterRebuild += applicationPkt.size
                    if (totalAfterRebuild >= 1200) break
                    padBytes += 1200 - totalAfterRebuild
                }
                concat(listOfNotNull(paddedInitial, handshakeNatural, applicationPkt))
            } else {
                concat(firstPass)
            }
        } else {
            concat(firstPass)
        }

    // RFC 9001 §4.9.1: a client MUST discard Initial keys when it first
    // sends a Handshake packet. We just built one (handshakeNatural !=
    // null), so the next drainOutbound MUST NOT touch the Initial level.
    // After this point any retransmitted Initial from the peer is silently
    // dropped (initial.receiveProtection == null), which is correct per
    // the same RFC: by the time the client sends a Handshake packet, the
    // server has already moved up encryption levels too.
    if (handshakeNatural != null && !conn.initial.keysDiscarded) {
        conn.initial.discardKeys()
    }
    return datagram
}

private fun concat(parts: List<ByteArray>): ByteArray {
    var totalLen = 0
    for (p in parts) totalLen += p.size
    val out = ByteArray(totalLen)
    var pos = 0
    for (p in parts) {
        p.copyInto(out, pos)
        pos += p.size
    }
    return out
}

/**
 * Build a CONNECTION_CLOSE-only datagram at the highest encryption level we
 * have keys for. Two RFC 9000 constraints make this trickier than a normal
 * packet build:
 *
 *  - §10.2.3 — at Initial / Handshake levels, only CONNECTION_CLOSE
 *    (Transport, 0x1c) is allowed. An application-level close is replaced
 *    with `APPLICATION_ERROR (0x0c)` + frameType=0 + empty reason so we don't
 *    leak app state pre-handshake.
 *  - §14.1 — a client datagram containing an Initial MUST be ≥ 1200 bytes,
 *    even a close-only one. We do this by building once at natural size and,
 *    if short, rewinding the PN and rebuilding with a PADDING-frame deficit
 *    inside the AEAD envelope. The rebuild loops because the long-header
 *    Length varint (RFC 9000 §16) can grow by 1 byte once the body crosses
 *    the 64-byte threshold, so a single-shot deficit can land 1 byte short
 *    of 1200.
 */
private fun buildClosingDatagram(
    conn: QuicConnection,
    nowMillis: Long,
): ByteArray? {
    val app = conn.application
    if (app.sendProtection != null) {
        // 1-RTT level reached: app close (0x1d) is allowed and carries the
        // original error code + reason.
        val frame = ConnectionCloseFrame(conn.closeErrorCode, null, conn.closeReason ?: "")
        return buildBestLevelPacket(conn, listOf(frame))
    }

    // Pre-1-RTT: must use transport-encoded close. RFC 9000 §20.1
    // APPLICATION_ERROR = 0x0c — "the application or application protocol
    // caused the connection to be closed during the handshake".
    val transportFrame =
        ConnectionCloseFrame(
            errorCode = APPLICATION_ERROR,
            frameType = 0L,
            reason = "",
        )

    val hs = conn.handshake
    if (hs.sendProtection != null) {
        return buildLongHeaderFromFrames(
            conn = conn,
            level = EncryptionLevel.HANDSHAKE,
            frames = listOf(transportFrame),
            tokens = emptyList(),
            nowMillis = nowMillis,
            padBytes = 0,
        )
    }

    val init = conn.initial
    if (init.sendProtection != null && !init.keysDiscarded) {
        val natural =
            buildLongHeaderFromFrames(
                conn = conn,
                level = EncryptionLevel.INITIAL,
                frames = listOf(transportFrame),
                tokens = emptyList(),
                nowMillis = nowMillis,
                padBytes = 0,
            )
        if (natural.size >= 1200) return natural
        var padBytes = 1200 - natural.size
        var padded: ByteArray
        do {
            init.pnSpace.rewindOutboundForRebuild()
            padded =
                buildLongHeaderFromFrames(
                    conn = conn,
                    level = EncryptionLevel.INITIAL,
                    frames = listOf(transportFrame),
                    tokens = emptyList(),
                    nowMillis = nowMillis,
                    padBytes = padBytes,
                )
            if (padded.size >= 1200) break
            padBytes += 1200 - padded.size
        } while (true)
        return padded
    }
    return null
}

/** RFC 9000 §20.1 — application-or-protocol caused close during handshake. */
private const val APPLICATION_ERROR: Long = 0x0cL

private fun buildBestLevelPacket(
    conn: QuicConnection,
    frames: List<Frame>,
): ByteArray? {
    // Prefer 1-RTT > Handshake > Initial.
    val payload = encodeFrames(frames)
    val app = conn.application
    if (app.sendProtection != null) {
        val proto = app.sendProtection!!
        val pn = app.pnSpace.allocateOutbound()
        val built =
            ShortHeaderPacket.build(
                ShortHeaderPlaintextPacket(
                    conn.destinationConnectionId,
                    pn,
                    payload,
                    keyPhase = conn.currentSendKeyPhase,
                ),
                proto.aead,
                proto.key,
                proto.iv,
                proto.hp,
                proto.hpKey,
                largestAckedInSpace = -1L,
            )
        emitQlogSent(conn, EncryptionLevel.APPLICATION, pn, built.size, frames)
        return built
    }
    val hs = conn.handshake
    if (hs.sendProtection != null) {
        return buildLongHeaderPacket(conn, EncryptionLevel.HANDSHAKE, payload, frames)
    }
    val init = conn.initial
    if (init.sendProtection != null) {
        return buildLongHeaderPacket(conn, EncryptionLevel.INITIAL, payload, frames)
    }
    return null
}

private fun buildLongHeaderPacket(
    conn: QuicConnection,
    level: EncryptionLevel,
    payload: ByteArray,
    frames: List<Frame>,
): ByteArray {
    val state = conn.levelState(level)
    val proto = state.sendProtection!!
    val pn = state.pnSpace.allocateOutbound()
    val type =
        when (level) {
            EncryptionLevel.INITIAL -> LongHeaderType.INITIAL
            EncryptionLevel.HANDSHAKE -> LongHeaderType.HANDSHAKE
            EncryptionLevel.APPLICATION -> error("APPLICATION uses short-header packets")
        }
    val built =
        LongHeaderPacket.build(
            LongHeaderPlaintextPacket(
                type = type,
                version = QuicVersion.V1,
                dcid = conn.destinationConnectionId,
                scid = conn.sourceConnectionId,
                packetNumber = pn,
                payload = payload,
            ),
            proto.aead,
            proto.key,
            proto.iv,
            proto.hp,
            proto.hpKey,
            largestAckedInSpace = -1L,
        )
    emitQlogSent(conn, level, pn, built.size, frames)
    return built
}

/**
 * Frames + the matching retransmit-tokens collected for a single
 * encryption-level packet build. Carried out of
 * [collectHandshakeLevelFrames] so the caller can pass the tokens
 * to the SentPacket retention recorded in
 * [buildLongHeaderFromFrames].
 */
private data class HandshakeLevelContents(
    val frames: List<Frame>,
    val tokens: List<RecoveryToken>,
)

/**
 * Drain ACK + CRYPTO frames for [level] into a fresh list. Destructive on
 * the CRYPTO send buffer (which now retains bytes until ACK per
 * [com.vitorpamplona.quic.stream.SendBuffer]'s retain-until-ACK
 * semantics — see commit B). Returns null if there are no frames to
 * send and the level has no protection installed.
 *
 * Each emitted frame contributes a matching [RecoveryToken] so the
 * caller can record a [com.vitorpamplona.quic.connection.recovery.SentPacket]
 * keyed by packet number, enabling RFC 9002 retransmit at this
 * encryption level (CRYPTO bytes are reliable per RFC 9000 §13.3).
 */
private fun collectHandshakeLevelFrames(
    conn: QuicConnection,
    level: EncryptionLevel,
    nowMillis: Long,
): HandshakeLevelContents? {
    val state = conn.levelState(level)
    if (state.sendProtection == null) return null
    val frames = mutableListOf<Frame>()
    val tokens = mutableListOf<RecoveryToken>()
    state.ackTracker.buildAckFrame(nowMillis, conn.config.ackDelayExponent.toInt())?.let {
        frames += it
        tokens += RecoveryToken.Ack(level = level, largestAcked = it.largestAcknowledged)
    }
    val cryptoChunk = state.cryptoSend.takeChunk(maxBytes = 1100)
    if (cryptoChunk != null && cryptoChunk.data.isNotEmpty()) {
        frames += CryptoFrame(cryptoChunk.offset, cryptoChunk.data)
        // Step E: record a Crypto retransmit token at the matching
        // encryption level so a lost handshake packet's CRYPTO bytes
        // get re-emitted at the same offset on next drain (RFC 9000
        // §13.3 — handshake bytes are reliable).
        tokens +=
            RecoveryToken.Crypto(
                level = level,
                offset = cryptoChunk.offset,
                length = cryptoChunk.data.size.toLong(),
            )
    }
    // RFC 9002 §6.2.4: PTO probe MUST be ack-eliciting at the
    // encryption level with unacknowledged data. `buildApplicationPacket`
    // consumes [pendingPing] when 1-RTT keys exist; pre-1-RTT we
    // honor it here at the highest active level (Handshake > Initial).
    //
    // The driver's PTO branch also calls
    // [QuicConnection.requeueAllInflightCrypto], which moves any
    // unacknowledged ClientHello / ClientFinished bytes back onto
    // the cryptoSend retransmit queue — so the takeChunk above
    // already produced a CRYPTO frame in that case, and the PING
    // would be redundant. We only emit a bare PING when there's no
    // CRYPTO retransmit available (e.g. the unacknowledged data was
    // already sitting in cryptoSend's retransmit queue from a
    // previous PTO and got drained, or the original Initial was
    // never sent at all). This preserves the "send something
    // ack-eliciting on every PTO" contract without wasting a frame.
    if (conn.pendingPing &&
        conn.application.sendProtection == null &&
        level == highestPreApplicationLevel(conn)
    ) {
        if (frames.none { it is CryptoFrame }) {
            frames += PingFrame
        }
        // Either the CRYPTO frame already covers the ack-eliciting
        // requirement at this level, or we just appended a PING.
        // Clear the flag so the next drain doesn't double-fire.
        conn.pendingPing = false
    }
    if (frames.isEmpty()) return null
    return HandshakeLevelContents(frames = frames, tokens = tokens)
}

/**
 * Highest encryption level for which we currently hold send keys, given
 * 1-RTT keys are NOT installed. Used by [collectHandshakeLevelFrames]
 * to place a PTO PING (RFC 9002 §6.2.4) at the right level when the
 * application path can't carry it.
 */
private fun highestPreApplicationLevel(conn: QuicConnection): EncryptionLevel =
    when {
        conn.handshake.sendProtection != null -> EncryptionLevel.HANDSHAKE
        conn.initial.sendProtection != null && !conn.initial.keysDiscarded -> EncryptionLevel.INITIAL
        else -> EncryptionLevel.INITIAL // collectHandshakeLevelFrames already returned null on no keys
    }

/**
 * Build a long-header packet from already-collected frames, with optional
 * trailing PADDING (0x00) bytes inside the encryption envelope. RFC 9000
 * §14.1 mandates this for client-Initial datagrams; PADDING is a one-byte
 * frame so concatenating N zero bytes after the encoded frames yields N
 * valid PADDING frames, all collapsed to nothing on decode.
 */
private fun buildLongHeaderFromFrames(
    conn: QuicConnection,
    level: EncryptionLevel,
    frames: List<Frame>,
    tokens: List<RecoveryToken>,
    nowMillis: Long,
    padBytes: Int,
): ByteArray {
    val state = conn.levelState(level)
    val proto = state.sendProtection!!
    val basePayload = encodeFrames(frames)
    val payload =
        if (padBytes > 0) {
            ByteArray(basePayload.size + padBytes).also { basePayload.copyInto(it, 0) }
        } else {
            basePayload
        }
    val pn = state.pnSpace.allocateOutbound()
    val type = if (level == EncryptionLevel.INITIAL) LongHeaderType.INITIAL else LongHeaderType.HANDSHAKE
    // RFC 9000 §17.2.5.1: after a Retry, every Initial we send MUST carry
    // the server-issued Retry token verbatim in the Initial header's Token
    // field. Handshake packets have no Token field, so this only affects
    // Initial-level builds.
    val token =
        if (type == LongHeaderType.INITIAL) {
            conn.retryToken ?: ByteArray(0)
        } else {
            ByteArray(0)
        }
    val packet =
        LongHeaderPacket.build(
            LongHeaderPlaintextPacket(
                type = type,
                version = conn.currentVersion,
                dcid = conn.destinationConnectionId,
                scid = conn.sourceConnectionId,
                token = token,
                packetNumber = pn,
                payload = payload,
            ),
            proto.aead,
            proto.key,
            proto.iv,
            proto.hp,
            proto.hpKey,
            largestAckedInSpace = -1L,
        )
    // Step E: retain the packet for RFC 9002 retransmit. Initial /
    // Handshake packets carry CRYPTO frames; loss detection runs at
    // each level and routes lost Crypto tokens back to the level's
    // cryptoSend buffer via QuicConnection.onTokensLost (commit A).
    //
    // Initial-level rebuilds with padding (RFC 9000 §14.1) call
    // pnSpace.rewindOutboundForRebuild() to reuse the same PN. The
    // map insert below overwrites the prior entry, so the recorded
    // SentPacket reflects the final padded packet's size — correct
    // for retransmit purposes.
    state.sentPackets[pn] =
        SentPacket(
            packetNumber = pn,
            sentAtMillis = nowMillis,
            ackEliciting = isAckEliciting(frames),
            sizeBytes = packet.size,
            tokens = tokens,
        )
    emitQlogSent(conn, level, pn, packet.size, frames)
    return packet
}

/**
 * Fire [QlogObserver.onPacketSent] for one outbound packet. Skipped
 * fast for the [QlogObserver.NoOp] default so production callers pay
 * only one identity comparison + one virtual call.
 */
private fun emitQlogSent(
    conn: QuicConnection,
    level: EncryptionLevel,
    packetNumber: Long,
    sizeBytes: Int,
    frames: List<com.vitorpamplona.quic.frame.Frame>,
) {
    val observer = conn.qlogObserver
    if (observer === QlogObserver.NoOp) return
    val frameNames = ArrayList<String>(frames.size)
    for (f in frames) frameNames += qlogFrameName(f::class.simpleName ?: "frame")
    observer.onPacketSent(
        level = level,
        packetNumber = packetNumber,
        sizeBytes = sizeBytes,
        frames = frameNames,
    )
}

private fun buildApplicationPacket(
    conn: QuicConnection,
    nowMillis: Long,
): ByteArray? {
    val state = conn.application
    // Prefer 1-RTT keys when available; fall back to 0-RTT keys for the
    // pre-handshake-confirmed window on a resumption connection. Once
    // 1-RTT lands, [QuicConnection.zeroRttSendProtection] is cleared per
    // RFC 9001 §4.10 — so the fallback only ever fires before
    // ServerHello has been processed.
    val use1Rtt = state.sendProtection != null
    val proto = state.sendProtection ?: conn.zeroRttSendProtection ?: return null
    // Drop fully-settled streams BEFORE iterating so we don't waste a
    // round-robin slot on a stream with nothing to send and no receive
    // bookkeeping pending. Run this once per drain (at the top of the
    // application-level path, the only level where streams exist) under
    // the same `streamsLock` the rest of the drain holds — see
    // [QuicConnection.retireFullyDoneStreamsLocked] for the safety
    // argument. The cumulative receive bytes from removed streams fold
    // into `appendFlowControlUpdates` below so MAX_DATA stays correct.
    // Safe to call on the 0-RTT path too: no stream can be fully
    // settled mid-handshake (peer hasn't ACK'd anything yet), so the
    // walk is a no-op.
    conn.retireFullyDoneStreamsLocked()
    val frames = mutableListOf<Frame>()
    // Tokens collected in lock-step with [frames]: each retransmittable
    // frame contributes a [RecoveryToken] so the [SentPacket] recorded
    // at the bottom of this function can drive RFC 9002 loss
    // detection + retransmit (steps 3–6 of
    // `quic/plans/2026-05-04-control-frame-retransmit.md`). Frames that
    // are not retransmittable (DatagramFrame, StreamFrame for now) do
    // not contribute a token; the packet is still ack-eliciting and
    // tracked for loss-detection timing.
    val tokens = mutableListOf<RecoveryToken>()

    // RFC 9000 §17.2.3 — 0-RTT packets MUST NOT contain ACK frames. The
    // server cannot ACK 0-RTT-level packets because it has no way to
    // signal which encryption level the ACK targets without the
    // application packet number space being established by 1-RTT keys.
    // Skip ACK building when we're about to emit a 0-RTT packet.
    if (use1Rtt) {
        state.ackTracker.buildAckFrame(nowMillis, conn.config.ackDelayExponent.toInt())?.let { plainAck ->
            // RFC 9000 §13.4.2: an endpoint that USES ECN on outbound
            // packets (we set ECT(0) on every datagram via the socket's
            // IP_TOS option) MUST report ECN counts in 1-RTT ACK frames so
            // the peer can detect path congestion. We don't currently
            // read inbound TOS bits — JDK's DatagramChannel doesn't expose
            // them without JNI — so the counts are all-zero. The interop
            // runner's `ecn` testcase only checks for the field's presence
            // (`hasattr(p["quic"], "ack.ect0_count")`); strict peers that
            // cross-validate counts would reject this, but aioquic /
            // picoquic / quic-go all tolerate it. Initial / Handshake-space
            // ACKs stay plain (ecnCounts=null) — the spec allows ECN counts
            // there too, but interop implementations don't always handle
            // them and we'd gain nothing.
            val ackWithEcn =
                AckFrame(
                    largestAcknowledged = plainAck.largestAcknowledged,
                    ackDelay = plainAck.ackDelay,
                    firstAckRange = plainAck.firstAckRange,
                    additionalRanges = plainAck.additionalRanges,
                    ecnCounts =
                        com.vitorpamplona.quic.frame
                            .AckEcnCounts(0L, 0L, 0L),
                )
            frames += ackWithEcn
            tokens += RecoveryToken.Ack(level = EncryptionLevel.APPLICATION, largestAcked = ackWithEcn.largestAcknowledged)
        }
    }

    // Step 7: PTO probe. The driver sets `pendingPing` when its
    // PTO timer fires without intervening ACKs. A PING is the
    // smallest ack-eliciting frame (RFC 9000 §19.2) — its only
    // purpose is to provoke an ACK from the peer, which then runs
    // through loss detection (steps 5–6) and surfaces any
    // outstanding losses for retransmit. The PING itself is not
    // retransmitted on loss (RFC 9002 §A.9 PROBE_TIMEOUT skipped).
    if (conn.pendingPing) {
        frames += PingFrame
        conn.pendingPing = false
    }

    // Re-credit the peer's send window when our receive offset has advanced
    // beyond half the previously-advertised limit. Emits MAX_STREAM_DATA per
    // stream and MAX_DATA at the connection level.
    appendFlowControlUpdates(conn, frames, tokens)

    // Pending datagrams
    while (conn.pendingDatagramsLocked().isNotEmpty()) {
        val payload = conn.pendingDatagramsLocked().removeFirst()
        frames += DatagramFrame(payload, explicitLength = true)
        if (frames.size >= 16) break
    }

    // Drain stream send buffers — round-robin starting from a rotating index
    // so streams created earlier don't starve streams created later under MTU
    // pressure. Honors per-stream send credit (RFC 9000 §4) AND connection-
    // level send credit (audit-4 #9: previously the writer ignored it
    // entirely; the peer's initial_max_data was decoded then forgotten,
    // causing the connection to be torn down with FLOW_CONTROL_ERROR once
    // we cumulatively sent past the cap).
    var packetBudget = 1100
    val connRemaining =
        (conn.sendConnectionFlowCredit - conn.sendConnectionFlowConsumed).coerceAtLeast(0L)
    var connBudget = connRemaining
    // Round-4 perf #10: use the connection's pre-built list view instead of
    // allocating a fresh `entries.toList()` per drain. The list is
    // insertion-ordered and stays in sync with the streams map.
    val streamsView = conn.streamsListLocked()
    if (streamsView.isNotEmpty()) {
        // Strict priority across tiers, round-robin within each tier.
        // Higher-priority streams (e.g. moq-lite newer-sequence group
        // streams) ALWAYS drain ahead of lower-priority ones; the
        // rotating start-index only rotates among same-priority peers.
        // This is the spec-aligned shape — applying the rotation
        // globally over the sorted list would flip cross-tier order on
        // alternating drains, defeating the priority hint entirely.
        //
        // Default priority is 0; if every stream is at the default, all
        // streams form a single tier and iteration order matches the
        // pre-priority round-robin behaviour exactly.
        //
        // Cost: O(N log N) per drain pass plus one transient sorted
        // list. N is small (1–10 in the moq-lite audio path) but the
        // multiplexing interop testcase pushes N to ~2000, so we
        // optimize:
        //   1. filter out streams that are fully closed (no data + no
        //      retransmits coming) — drops "done" streams from the
        //      walk, which is most of them under bursty interop loads.
        //   2. skip the sort entirely when every stream has the
        //      default priority (the common case) — preserving the
        //      insertion-ordered round-robin shape.
        // The combination drops drainOutbound's per-call cost from
        // O(N log N) where N=total-streams to roughly O(active) under
        // realistic loads.
        val active = streamsView.filter { !it.isClosed }
        val sorted =
            when {
                active.size <= 1 -> active
                active.all { it.priority == 0 } -> active
                else -> active.sortedByDescending { it.priority }
            }
        val rotation = conn.streamRoundRobinStart
        var tierStart = 0
        outer@ while (tierStart < sorted.size) {
            // Walk the contiguous run of same-priority streams.
            val tierPriority = sorted[tierStart].priority
            var tierEnd = tierStart + 1
            while (tierEnd < sorted.size && sorted[tierEnd].priority == tierPriority) tierEnd++
            val tierSize = tierEnd - tierStart
            val tierRotation = if (tierSize > 1) rotation % tierSize else 0
            for (k in 0 until tierSize) {
                if (packetBudget <= 64) break@outer
                val stream = sorted[tierStart + ((tierRotation + k) % tierSize)]
                val streamRemaining = (stream.sendCredit - stream.send.sentOffset).coerceAtLeast(0L)
                // Skip if both stream and connection have no credit; FIN-only
                // (zero-byte) chunks may still go through because they don't
                // consume credit.
                if (streamRemaining <= 0L && connBudget <= 0L && !stream.send.finPending) continue
                val effectiveCap = minOf(streamRemaining, connBudget)
                val maxBytes =
                    minOf(packetBudget - 32, effectiveCap.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                val chunk = stream.send.takeChunk(maxBytes = maxBytes) ?: continue
                if (chunk.data.isNotEmpty() || chunk.fin) {
                    frames +=
                        StreamFrame(
                            streamId = stream.streamId,
                            offset = chunk.offset,
                            data = chunk.data,
                            fin = chunk.fin,
                            explicitLength = true,
                        )
                    // Step C of the deferred-follow-ups pass: track this
                    // STREAM emission so RFC 9002 retransmit can re-queue
                    // the byte range on loss. SendBuffer.markLost (commit B)
                    // moves the range from in-flight back to the retransmit
                    // queue, and the next takeChunk replays it.
                    tokens +=
                        RecoveryToken.Stream(
                            streamId = stream.streamId,
                            offset = chunk.offset,
                            length = chunk.data.size.toLong(),
                            fin = chunk.fin,
                        )
                    packetBudget -= chunk.data.size + 32
                    connBudget -= chunk.data.size
                    conn.sendConnectionFlowConsumed += chunk.data.size
                }
            }
            tierStart = tierEnd
        }
        conn.streamRoundRobinStart = (rotation + 1) % streamsView.size
    }

    if (frames.isEmpty()) return null

    // Diagnostic: gated by QUIC_INTEROP_DEBUG=1 so prod is silent. Tells
    // us exactly what state the writer was in when it built this packet.
    // Hunting the multiplexing-on-the-wire bug where MultiplexingCoalescing
    // Test passes (~9 streams/packet) but the live driver emits 1 STREAM
    // per packet against aioquic.
    if (com.vitorpamplona.quic.connection.writerDebugEnabled) {
        val streamFrameCount = frames.count { it is StreamFrame }
        val totalFrames = frames.size
        val streamsViewSize = conn.streamsListLocked().size
        val activeSize = conn.streamsListLocked().count { !it.isClosed }
        System.err.println(
            "[writer.app] frames=$totalFrames stream_frames=$streamFrameCount " +
                "streamsView=$streamsViewSize active=$activeSize " +
                "packetBudget_remaining=${1100 - (frames.filterIsInstance<StreamFrame>().sumOf { it.data.size + 32 })} " +
                "connBudget_initial=${(conn.sendConnectionFlowCredit - conn.sendConnectionFlowConsumed).coerceAtLeast(0L)}",
        )
    }

    val payload = encodeFrames(frames)
    val pn = state.pnSpace.allocateOutbound()
    // Retain the packet for RFC 9002 loss detection BEFORE the encrypt
    // step so the bookkeeping survives a build/encrypt throw (the PN
    // was already consumed at allocateOutbound; if we don't track it,
    // the gap is silent). Step 2 only populates the map; step 3 drains
    // on ACK; step 5 declares loss. Until step 5 lands, the map can
    // only grow — but step 2 ships dormant, no reads, no behavior
    // change visible to the peer.
    val sizeBytes =
        runCatching {
            if (use1Rtt) {
                ShortHeaderPacket.build(
                    ShortHeaderPlaintextPacket(
                        conn.destinationConnectionId,
                        pn,
                        payload,
                        keyPhase = conn.currentSendKeyPhase,
                    ),
                    proto.aead,
                    proto.key,
                    proto.iv,
                    proto.hp,
                    proto.hpKey,
                    largestAckedInSpace = -1L,
                )
            } else {
                // 0-RTT — long header type=0x01. Same Application packet
                // number space (RFC 9000 §17.2.3), same DCID/SCID. Token
                // is empty (only Initial carries a token) — the
                // LongHeaderPacket builder special-cases that.
                LongHeaderPacket.build(
                    LongHeaderPlaintextPacket(
                        type = LongHeaderType.ZERO_RTT,
                        version = conn.currentVersion,
                        dcid = conn.destinationConnectionId,
                        scid = conn.sourceConnectionId,
                        packetNumber = pn,
                        payload = payload,
                    ),
                    proto.aead,
                    proto.key,
                    proto.iv,
                    proto.hp,
                    proto.hpKey,
                    largestAckedInSpace = -1L,
                )
            }
        }
    state.sentPackets[pn] =
        SentPacket(
            packetNumber = pn,
            sentAtMillis = nowMillis,
            ackEliciting = isAckEliciting(frames),
            sizeBytes = sizeBytes.getOrNull()?.size ?: 0,
            tokens = tokens.toList(),
        )
    sizeBytes.getOrNull()?.let { built ->
        emitQlogSent(conn, EncryptionLevel.APPLICATION, pn, built.size, frames)
    }
    // Re-throw on encrypt failure so callers (driver loop) see the
    // same exception they did before this change. The bookkeeping
    // entry is still in place; if the throw was transient, retransmit
    // logic in step 6 picks up the slack on the next outbound.
    return sizeBytes.getOrThrow()
}

/**
 * RFC 9000 §13.2.1: a packet is ack-eliciting if it contains any frame
 * other than ACK, PADDING, or CONNECTION_CLOSE. Loss detection (step 5)
 * only fires for ack-eliciting packets — non-eliciting ones are tracked
 * for timing but never retransmitted.
 *
 * `:quic` doesn't currently emit PADDING-only or CONNECTION_CLOSE-only
 * frames mid-connection through this path, so the ack-eliciting set
 * is "anything that isn't a pure ACK". Listed explicitly anyway so the
 * RFC reference stays close to the implementation.
 */
private fun isAckEliciting(frames: List<Frame>): Boolean = frames.any { it !is AckFrame && it !is ConnectionCloseFrame }

/**
 * Append MAX_STREAM_DATA / MAX_DATA frames re-crediting the peer when our
 * receive cursor has advanced past half the previously-advertised window.
 *
 * Each emitted frame contributes a matching [RecoveryToken] to [tokens]
 * so the [SentPacket] recorded by the caller can drive RFC 9002
 * retransmit on loss (step 6 of
 * `quic/plans/2026-05-04-control-frame-retransmit.md`).
 *
 * Caller must hold [QuicConnection.lock].
 */
private fun appendFlowControlUpdates(
    conn: QuicConnection,
    frames: MutableList<Frame>,
    tokens: MutableList<RecoveryToken>,
) {
    // Step 4: re-emit any retransmittable extension whose carrying
    // packet was declared lost (the `pending*` fields are populated
    // by step 6's loss dispatcher; for now they default to null).
    // Each pending entry is consumed by emitting a fresh frame with
    // the same value plus a fresh token, so the new packet's loss
    // can be re-detected if the wire drops this one too. The
    // supersede-check (`token.value == advertised*`) lives on the
    // setter side in step 6 — by the time `pending*` is set, the
    // value is known to still match the current advertised cap.
    val pendingUni = conn.pendingMaxStreamsUni
    if (pendingUni != null) {
        frames += MaxStreamsFrame(bidi = false, maxStreams = pendingUni)
        tokens += RecoveryToken.MaxStreamsUni(maxStreams = pendingUni)
        conn.pendingMaxStreamsUni = null
    }
    val pendingBidi = conn.pendingMaxStreamsBidi
    if (pendingBidi != null) {
        frames += MaxStreamsFrame(bidi = true, maxStreams = pendingBidi)
        tokens += RecoveryToken.MaxStreamsBidi(maxStreams = pendingBidi)
        conn.pendingMaxStreamsBidi = null
    }
    val pendingData = conn.pendingMaxData
    if (pendingData != null) {
        frames += MaxDataFrame(pendingData)
        tokens += RecoveryToken.MaxData(maxData = pendingData)
        conn.pendingMaxData = null
    }
    if (conn.pendingMaxStreamData.isNotEmpty()) {
        // Iterate over a snapshot so we can mutate the map safely.
        val pendingStreamEntries = conn.pendingMaxStreamData.entries.toList()
        for ((streamId, maxData) in pendingStreamEntries) {
            frames += MaxStreamDataFrame(streamId, maxData)
            tokens += RecoveryToken.MaxStreamData(streamId = streamId, maxData = maxData)
        }
        conn.pendingMaxStreamData.clear()
    }

    // Per-stream RESET_STREAM / STOP_SENDING. Both are reliable per
    // RFC 9000 §13.3 — the writer drains the per-stream emit-pending
    // flag, attaches the corresponding RecoveryToken, and clears the
    // flag. The loss dispatcher re-flags on packet loss; ACK latches
    // resetAcked / stopSendingAcked so subsequent stale loss tokens
    // are dropped.
    for (stream in conn.streamsListLocked()) {
        val resetState = stream.resetState
        if (resetState != null && stream.resetEmitPending && !stream.resetAcked) {
            frames +=
                ResetStreamFrame(
                    streamId = stream.streamId,
                    applicationErrorCode = resetState.errorCode,
                    finalSize = resetState.finalSize,
                )
            tokens +=
                RecoveryToken.ResetStream(
                    streamId = stream.streamId,
                    errorCode = resetState.errorCode,
                    finalSize = resetState.finalSize,
                )
            stream.resetEmitPending = false
        }
        val stopSendingState = stream.stopSendingState
        if (stopSendingState != null && stream.stopSendingEmitPending && !stream.stopSendingAcked) {
            frames +=
                StopSendingFrame(
                    streamId = stream.streamId,
                    applicationErrorCode = stopSendingState.errorCode,
                )
            tokens +=
                RecoveryToken.StopSending(
                    streamId = stream.streamId,
                    errorCode = stopSendingState.errorCode,
                )
            stream.stopSendingEmitPending = false
        }
    }

    // PATH_RESPONSE — drain every pending challenge response in one
    // pass. RFC 9000 §13.3 is silent on retransmission for
    // PATH_RESPONSE: it's NOT in the ack-eliciting-and-retransmittable
    // class, so we don't track tokens for these. If the response
    // packet is lost, the peer's next PATH_CHALLENGE retry queues a
    // fresh entry here and we respond again. The peer is responsible
    // for retrying its challenge until it sees a matching response.
    while (conn.pendingPathChallengePayloads.isNotEmpty()) {
        val data = conn.pendingPathChallengePayloads.removeFirst()
        frames += PathResponseFrame(data)
    }

    // RFC 9000 §9 client-initiated path validation. Drain any
    // PATH_CHALLENGE the [PathValidator] has queued — including
    // re-queued ones from the loss dispatcher. Each emission
    // records a [RecoveryToken.PathChallenge] so loss recovery
    // can decide to retransmit (the validator's own 3 * PTO
    // budget is the ultimate timeout per RFC 9000 §8.2.4).
    while (conn.pathValidator.pendingChallenges.isNotEmpty()) {
        val payload = conn.pathValidator.pendingChallenges.removeFirst()
        frames += PathChallengeFrame(payload)
        tokens += RecoveryToken.PathChallenge(payload)
    }

    // RFC 9000 §19.16 RETIRE_CONNECTION_ID. Drain pending retires;
    // the dispatcher re-queues on loss until the peer ACKs.
    while (conn.pathValidator.pendingRetireSequences.isNotEmpty()) {
        val seq = conn.pathValidator.pendingRetireSequences.removeFirst()
        frames += RetireConnectionIdFrame(seq)
        tokens += RecoveryToken.RetireConnectionId(seq)
    }

    // NEW_CONNECTION_ID retransmits. No application path emits these
    // initially today (connection-ID rotation isn't wired); the map
    // is populated only by the loss dispatcher, so this branch only
    // fires for genuine retransmits.
    if (conn.pendingNewConnectionId.isNotEmpty()) {
        val pendingNewCidEntries = conn.pendingNewConnectionId.entries.toList()
        for ((_, token) in pendingNewCidEntries) {
            frames +=
                NewConnectionIdFrame(
                    sequenceNumber = token.sequenceNumber,
                    retirePriorTo = token.retirePriorTo,
                    connectionId = token.connectionId,
                    statelessResetToken = token.statelessResetToken,
                )
            tokens += token
        }
        conn.pendingNewConnectionId.clear()
    }

    val cfg = conn.config
    // Seed with the cumulative receive high-water mark from streams that
    // [QuicConnection.retireFullyDoneStreamsLocked] has already dropped
    // — the writer's connection-level MAX_DATA threshold uses the
    // *lifetime* total, so retired streams must keep contributing even
    // after their per-object `receive.contiguousEnd()` is no longer
    // reachable. Without this seed, retiring K bytes of streams would
    // silently regress the advertised credit by K and eventually starve
    // the peer once the running total fell behind `advertisedMaxData`.
    var totalRecvAdvanced = conn.retiredStreamsRecvBytes
    // Round-4 perf #9 + round-5 #9: walk the streams via the index-friendly
    // list view (no `entries.toList()` allocation), and only do per-stream
    // window/threshold work for streams flagged by the parser since the last
    // drain. Streams whose receive frontier hasn't advanced cannot need a
    // new MAX_STREAM_DATA frame.
    for (stream in conn.streamsListLocked()) {
        val id = stream.streamId
        val rcv = stream.receive.contiguousEnd()
        if (rcv == 0L) continue
        if (!stream.receiveDirtyForFlowControl) {
            // Stream has data but nothing changed since last drain — skip the
            // per-direction window lookup and the comparison.
            totalRecvAdvanced += rcv
            continue
        }
        // Pick the per-direction window matching the stream kind so a
        // uni-only deployment with bidi=0 doesn't accidentally use the bidi
        // window and vice versa.
        val window =
            when (
                com.vitorpamplona.quic.stream.StreamId
                    .kindOf(id)
            ) {
                com.vitorpamplona.quic.stream.StreamId.Kind.SERVER_UNI -> cfg.initialMaxStreamDataUni
                com.vitorpamplona.quic.stream.StreamId.Kind.SERVER_BIDI -> cfg.initialMaxStreamDataBidiRemote
                com.vitorpamplona.quic.stream.StreamId.Kind.CLIENT_BIDI -> cfg.initialMaxStreamDataBidiLocal
                com.vitorpamplona.quic.stream.StreamId.Kind.CLIENT_UNI -> cfg.initialMaxStreamDataUni
            }
        // Re-credit when consumed > half the advertised window.
        if (rcv >= stream.receiveLimit - window / 2) {
            val newLimit = rcv + window
            if (newLimit > stream.receiveLimit) {
                stream.receiveLimit = newLimit
                frames += MaxStreamDataFrame(id, newLimit)
                tokens += RecoveryToken.MaxStreamData(streamId = id, maxData = newLimit)
            }
        }
        // Clear the dirty flag once we've considered this stream — even if we
        // didn't emit a new MAX_STREAM_DATA, the threshold-check work doesn't
        // need to repeat until more bytes arrive.
        stream.receiveDirtyForFlowControl = false
        totalRecvAdvanced += rcv
    }
    // Connection-level: only re-grant when the new total would exceed our
    // currently-advertised limit. Without this we'd emit a fresh MaxDataFrame
    // on every outbound packet once the threshold was crossed.
    if (totalRecvAdvanced > 0L && totalRecvAdvanced + cfg.initialMaxData / 2 >= conn.advertisedMaxData) {
        val newLimit = totalRecvAdvanced + cfg.initialMaxData
        if (newLimit > conn.advertisedMaxData) {
            conn.advertisedMaxData = newLimit
            frames += MaxDataFrame(newLimit)
            tokens += RecoveryToken.MaxData(maxData = newLimit)
        }
    }
    // Peer-initiated stream-id concurrency (RFC 9000 §19.11). MoQ over
    // WebTransport opens one uni stream per group per audience-side
    // subscription, so the lifetime peer-uni-stream count grows
    // monotonically with the broadcast. Without periodic
    // MAX_STREAMS_UNI extensions, the peer eventually hits our
    // initial cap (`config.initialMaxStreamsUni`, default 100) and
    // any further peer uni-streams are silently rejected on their
    // side — visible to the application as "audio cuts out at frame
    // ~99". See
    // `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`.
    if (conn.peerInitiatedUniCount + cfg.initialMaxStreamsUni / 2 >= conn.advertisedMaxStreamsUni) {
        val newCap = conn.peerInitiatedUniCount + cfg.initialMaxStreamsUni
        if (newCap > conn.advertisedMaxStreamsUni) {
            val oldCap = conn.advertisedMaxStreamsUni
            conn.advertisedMaxStreamsUni = newCap
            frames += MaxStreamsFrame(bidi = false, maxStreams = newCap)
            tokens += RecoveryToken.MaxStreamsUni(maxStreams = newCap)
            Log.d("NestQuic") {
                "MAX_STREAMS_UNI emit oldCap=$oldCap → newCap=$newCap " +
                    "peerInitiatedUniCount=${conn.peerInitiatedUniCount}"
            }
        }
    }
    if (conn.peerInitiatedBidiCount + cfg.initialMaxStreamsBidi / 2 >= conn.advertisedMaxStreamsBidi) {
        val newCap = conn.peerInitiatedBidiCount + cfg.initialMaxStreamsBidi
        if (newCap > conn.advertisedMaxStreamsBidi) {
            val oldCap = conn.advertisedMaxStreamsBidi
            conn.advertisedMaxStreamsBidi = newCap
            frames += MaxStreamsFrame(bidi = true, maxStreams = newCap)
            tokens += RecoveryToken.MaxStreamsBidi(maxStreams = newCap)
            Log.d("NestQuic") {
                "MAX_STREAMS_BIDI emit oldCap=$oldCap → newCap=$newCap " +
                    "peerInitiatedBidiCount=${conn.peerInitiatedBidiCount}"
            }
        }
    }
}
