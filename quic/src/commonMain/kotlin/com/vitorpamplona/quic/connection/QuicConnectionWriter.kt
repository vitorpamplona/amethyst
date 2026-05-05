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
import com.vitorpamplona.quic.frame.PingFrame
import com.vitorpamplona.quic.frame.ResetStreamFrame
import com.vitorpamplona.quic.frame.StopSendingFrame
import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.frame.encodeFrames
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
 */
fun drainOutbound(
    conn: QuicConnection,
    nowMillis: Long,
): ByteArray? {
    val parts = mutableListOf<ByteArray>()

    // Closing — emit a CONNECTION_CLOSE at the highest available level.
    if (conn.status == QuicConnection.Status.CLOSING) {
        val frame = ConnectionCloseFrame(conn.closeErrorCode, null, conn.closeReason ?: "")
        val packet = buildBestLevelPacket(conn, listOf(frame)) ?: return null
        conn.status = QuicConnection.Status.CLOSED
        return packet
    }

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
    if (initialNatural != null) {
        var natural = 0
        for (p in firstPass) natural += p.size
        if (natural < 1200) {
            val deficit = 1200 - natural
            // Rewind the Initial PN — we'll reissue with the same PN and the
            // same captured frames plus padding. The SentPacket entry recorded
            // by the natural-size build will be overwritten by the rebuild
            // below since both use the same PN.
            initialState.pnSpace.rewindOutboundForRebuild()
            val paddedInitial =
                buildLongHeaderFromFrames(
                    conn = conn,
                    level = EncryptionLevel.INITIAL,
                    frames = initialContents!!.frames, // null-safe: gated by `initialNatural != null` above
                    tokens = initialContents.tokens,
                    nowMillis = nowMillis,
                    padBytes = deficit,
                )
            return concat(listOfNotNull(paddedInitial, handshakeNatural, applicationPkt))
        }
    }
    return concat(firstPass)
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
        return ShortHeaderPacket.build(
            ShortHeaderPlaintextPacket(conn.destinationConnectionId, pn, payload),
            proto.aead,
            proto.key,
            proto.iv,
            proto.hp,
            proto.hpKey,
            largestAckedInSpace = -1L,
        )
    }
    val hs = conn.handshake
    if (hs.sendProtection != null) {
        return buildLongHeaderPacket(conn, EncryptionLevel.HANDSHAKE, payload)
    }
    val init = conn.initial
    if (init.sendProtection != null) {
        return buildLongHeaderPacket(conn, EncryptionLevel.INITIAL, payload)
    }
    return null
}

private fun buildLongHeaderPacket(
    conn: QuicConnection,
    level: EncryptionLevel,
    payload: ByteArray,
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
    return LongHeaderPacket.build(
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
    if (frames.isEmpty()) return null
    return HandshakeLevelContents(frames = frames, tokens = tokens)
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
    val packet =
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
    return packet
}

private fun buildApplicationPacket(
    conn: QuicConnection,
    nowMillis: Long,
): ByteArray? {
    val state = conn.application
    val proto = state.sendProtection ?: return null
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

    state.ackTracker.buildAckFrame(nowMillis, conn.config.ackDelayExponent.toInt())?.let {
        frames += it
        tokens += RecoveryToken.Ack(level = EncryptionLevel.APPLICATION, largestAcked = it.largestAcknowledged)
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
        val start = conn.streamRoundRobinStart % streamsView.size
        for (i in streamsView.indices) {
            if (packetBudget <= 64) break
            val stream = streamsView[(start + i) % streamsView.size]
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
        conn.streamRoundRobinStart = (start + 1) % streamsView.size
    }

    if (frames.isEmpty()) return null
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
            ShortHeaderPacket.build(
                ShortHeaderPlaintextPacket(conn.destinationConnectionId, pn, payload),
                proto.aead,
                proto.key,
                proto.iv,
                proto.hp,
                proto.hpKey,
                largestAckedInSpace = -1L,
            )
        }
    state.sentPackets[pn] =
        SentPacket(
            packetNumber = pn,
            sentAtMillis = nowMillis,
            ackEliciting = isAckEliciting(frames),
            sizeBytes = sizeBytes.getOrNull()?.size ?: 0,
            tokens = tokens.toList(),
        )
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
    var totalRecvAdvanced = 0L
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
