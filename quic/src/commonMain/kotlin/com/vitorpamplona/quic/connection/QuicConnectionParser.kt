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

import com.vitorpamplona.quic.QuicCodecException
import com.vitorpamplona.quic.connection.recovery.drainAckedSentPackets
import com.vitorpamplona.quic.frame.AckFrame
import com.vitorpamplona.quic.frame.ConnectionCloseFrame
import com.vitorpamplona.quic.frame.CryptoFrame
import com.vitorpamplona.quic.frame.DatagramFrame
import com.vitorpamplona.quic.frame.HandshakeDoneFrame
import com.vitorpamplona.quic.frame.MaxDataFrame
import com.vitorpamplona.quic.frame.MaxStreamDataFrame
import com.vitorpamplona.quic.frame.MaxStreamsFrame
import com.vitorpamplona.quic.frame.NewConnectionIdFrame
import com.vitorpamplona.quic.frame.NewTokenFrame
import com.vitorpamplona.quic.frame.PathChallengeFrame
import com.vitorpamplona.quic.frame.PathResponseFrame
import com.vitorpamplona.quic.frame.PingFrame
import com.vitorpamplona.quic.frame.ResetStreamFrame
import com.vitorpamplona.quic.frame.RetireConnectionIdFrame
import com.vitorpamplona.quic.frame.StopSendingFrame
import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.frame.decodeFrames
import com.vitorpamplona.quic.observability.qlogFrameName
import com.vitorpamplona.quic.packet.LongHeaderPacket
import com.vitorpamplona.quic.packet.LongHeaderType
import com.vitorpamplona.quic.packet.QuicVersion
import com.vitorpamplona.quic.packet.RetryPacket
import com.vitorpamplona.quic.packet.ShortHeaderPacket
import com.vitorpamplona.quic.stream.StreamId
import com.vitorpamplona.quic.tls.TlsClient

/** RFC 9000 §16: maximum varint value, also the per-stream offset ceiling. */
private const val MAX_QUIC_OFFSET: Long = (1L shl 62) - 1L

/**
 * Decode every QUIC packet inside a single inbound UDP datagram and dispatch
 * its frames to [conn]'s state.
 *
 * QUIC permits *coalescing* multiple packets into one datagram (RFC 9000 §12.2)
 * — typically Initial + Handshake from the server in the same datagram during
 * the handshake. We loop until the datagram is fully consumed or a packet
 * fails to parse (which we drop silently per RFC 9001 §5.5).
 *
 * Lock-split refactor (2026-05-08): caller must hold
 * [QuicConnection.streamsLock]. The driver wraps its read loop in
 * `streamsLock.withLock { feedDatagram(...) }`. Test harnesses that drive
 * single-threaded packet flow (no concurrent app code) may invoke this
 * directly without lock acquisition; the runtime invariants still hold
 * because there's no contending thread. Phase 1 wraps the whole feed
 * under streamsLock so frame-dispatch / stream creation / level state
 * remains a single critical section.
 */
fun feedDatagram(
    conn: QuicConnection,
    datagram: ByteArray,
    nowMillis: Long,
) {
    try {
        feedDatagramInner(conn, datagram, nowMillis)
    } catch (e: com.vitorpamplona.quic.QuicProtocolViolationException) {
        // RFC 9000 §17.2 / §17.3.1: peer set reserved bits in the
        // header after HP unmask (or a similar invariant violation
        // bubbled out of the parse path). Spec MUST close with
        // PROTOCOL_VIOLATION.
        conn.markClosedExternally(e.message ?: "PROTOCOL_VIOLATION")
    }
}

private fun feedDatagramInner(
    conn: QuicConnection,
    datagram: ByteArray,
    nowMillis: Long,
) {
    var offset = 0
    while (offset < datagram.size) {
        val first = datagram[offset].toInt() and 0xFF
        val isLong = (first and 0x80) != 0
        if (isLong) {
            // RFC 9000 §17.2.1: a Version Negotiation packet has the form
            // bit set but version=0. Detect it BEFORE peekHeader, which
            // assumes a v1-shaped layout (token, length fields).
            if (offset + 5 <= datagram.size) {
                val version =
                    ((datagram[offset + 1].toInt() and 0xFF) shl 24) or
                        ((datagram[offset + 2].toInt() and 0xFF) shl 16) or
                        ((datagram[offset + 3].toInt() and 0xFF) shl 8) or
                        (datagram[offset + 4].toInt() and 0xFF)
                if (version == QuicVersion.VERSION_NEGOTIATION) {
                    feedVersionNegotiationPacket(conn, datagram, offset)
                    // VN packets MUST be the only packet in their datagram
                    // (RFC 9000 §17.2.1: no length field, body is rest of
                    // datagram). Stop walking.
                    return
                }
            }
            // Per RFC 9001 §5.5, drop ONLY the failing packet, not subsequent
            // coalesced ones. Use peekHeader to advance over a packet whose
            // payload we couldn't decrypt; only break the loop on a header
            // that's totally unparseable (then we don't know where the next
            // packet starts).
            val peeked = LongHeaderPacket.peekHeader(datagram, offset) ?: break
            val consumed = feedLongHeaderPacket(conn, datagram, offset, nowMillis)
            offset += consumed ?: peeked.totalLength
        } else {
            // Short-header — consumes the rest of the datagram.
            feedShortHeaderPacket(conn, datagram, offset, nowMillis)
            return
        }
    }
}

/**
 * RFC 9000 §17.2.1 / §6: parse a Version Negotiation packet and dispatch
 * to [QuicConnection.applyVersionNegotiation].
 *
 * Wire layout:
 *
 *   first byte (form=1, unused 4 bits — server fills with random)
 *   version    (4 bytes, fixed at 0x00000000)
 *   dcid_len   (1 byte) + dcid (dcid_len bytes)
 *   scid_len   (1 byte) + scid (scid_len bytes)
 *   supported_versions: sequence of 32-bit big-endian version numbers,
 *     consuming the rest of the UDP datagram.
 *
 * VN packets are NOT AEAD-protected — there's nothing to decrypt. We do
 * a minimal sanity check (DCID matches our SCID) and then hand the
 * version list off. Malformed packets are dropped silently per RFC 9000
 * §17.2.1 ("an endpoint MUST NOT send … in response to a Version
 * Negotiation packet").
 */
private fun feedVersionNegotiationPacket(
    conn: QuicConnection,
    datagram: ByteArray,
    offset: Int,
) {
    // Layout fields above; bail early if any read would run past the
    // end of the datagram (truncated VN — drop silently).
    var pos = offset + 5
    if (pos >= datagram.size) return
    val dcidLen = datagram[pos].toInt() and 0xFF
    pos += 1
    if (dcidLen > 20 || pos + dcidLen > datagram.size) return
    val dcid = datagram.copyOfRange(pos, pos + dcidLen)
    pos += dcidLen
    if (pos >= datagram.size) return
    val scidLen = datagram[pos].toInt() and 0xFF
    pos += 1
    if (scidLen > 20 || pos + scidLen > datagram.size) return
    pos += scidLen // SCID body — not validated; servers may pick anything.

    // RFC 9000 §6.1: the VN packet's destination CID MUST equal the SCID
    // the client put in its first Initial. Mismatch ⇒ probable spoof
    // from an off-path attacker; drop without state change.
    if (!dcid.contentEquals(conn.sourceConnectionId.bytes)) return

    val versionsRegion = datagram.size - pos
    if (versionsRegion <= 0 || versionsRegion % 4 != 0) return // malformed

    val supportedVersions = ArrayList<Int>(versionsRegion / 4)
    while (pos + 4 <= datagram.size) {
        val v =
            ((datagram[pos].toInt() and 0xFF) shl 24) or
                ((datagram[pos + 1].toInt() and 0xFF) shl 16) or
                ((datagram[pos + 2].toInt() and 0xFF) shl 8) or
                (datagram[pos + 3].toInt() and 0xFF)
        supportedVersions += v
        pos += 4
    }
    conn.applyVersionNegotiation(supportedVersions)
}

private fun feedLongHeaderPacket(
    conn: QuicConnection,
    datagram: ByteArray,
    offset: Int,
    nowMillis: Long,
): Int? {
    val peeked = LongHeaderPacket.peekHeader(datagram, offset) ?: return null
    // RFC 9000 §17.2.5 + RFC 9001 §5.8: Retry has no PN space, no AEAD
    // payload protection, and cannot be coalesced with anything else
    // (it consumes the rest of the datagram via peekHeader.totalLength).
    // Branch out before the standard parse-and-decrypt path.
    if (peeked.type == LongHeaderType.RETRY) {
        val retryBytes = datagram.copyOfRange(offset, offset + peeked.totalLength)
        val retryPacket = RetryPacket.parse(retryBytes)
        if (retryPacket != null) {
            // applyRetry returns false on bad-tag / second-Retry / pre-start —
            // in all of those cases we silently drop without advancing state.
            conn.applyRetry(retryPacket, retryBytes)
        }
        return peeked.totalLength
    }
    val level =
        when (peeked.type) {
            LongHeaderType.INITIAL -> {
                EncryptionLevel.INITIAL
            }

            LongHeaderType.HANDSHAKE -> {
                EncryptionLevel.HANDSHAKE
            }

            LongHeaderType.ZERO_RTT, LongHeaderType.RETRY -> {
                // Not supported by client; surface as a drop so qvis can
                // see we ignored a packet rather than silently moving on.
                conn.qlogObserver.onPacketDropped(
                    "unsupported long-header type ${peeked.type}",
                    peeked.totalLength,
                )
                return null
            }
        }
    val state = conn.levelState(level)
    val proto = state.receiveProtection
    if (proto == null) {
        conn.qlogObserver.onPacketDropped(
            "no receive keys at level $level",
            peeked.totalLength,
        )
        return null
    }
    val parsed =
        LongHeaderPacket.parseAndDecrypt(
            bytes = datagram,
            offset = offset,
            aead = proto.aead,
            key = proto.key,
            iv = proto.iv,
            hp = proto.hp,
            hpKey = proto.hpKey,
            largestReceivedInSpace = state.pnSpace.largestReceived,
        )
    if (parsed == null) {
        conn.qlogObserver.onPacketDropped(
            "AEAD auth failed or header parse failed at level $level",
            peeked.totalLength,
        )
        return null
    }

    state.pnSpace.observeInbound(parsed.packet.packetNumber, nowMillis)
    // RFC 9000 §10.1.1: any successfully processed inbound packet
    // resets the idle timer.
    conn.lastActivityMs = nowMillis

    // The server's source CID becomes our destination CID for subsequent packets.
    if (level == EncryptionLevel.INITIAL) {
        conn.destinationConnectionId = parsed.packet.scid
    }

    if (conn.qlogObserver !== com.vitorpamplona.quic.observability.QlogObserver.NoOp) {
        conn.qlogObserver.onPacketReceived(
            level = level,
            packetNumber = parsed.packet.packetNumber,
            sizeBytes = parsed.consumed,
            frames = peekFrameNames(parsed.packet.payload),
        )
    }
    dispatchFrames(conn, level, parsed.packet.payload, parsed.packet.packetNumber, nowMillis)
    return parsed.consumed
}

private fun feedShortHeaderPacket(
    conn: QuicConnection,
    datagram: ByteArray,
    offset: Int,
    nowMillis: Long,
) {
    val state = conn.levelState(EncryptionLevel.APPLICATION)
    val live = state.receiveProtection
    if (live == null) {
        conn.qlogObserver.onPacketDropped(
            "no application receive keys",
            datagram.size - offset,
        )
        return
    }

    // RFC 9001 §6 — pick the right keys BEFORE running AEAD by peeking at
    // the protected first byte's key-phase bit. Three cases:
    //   1. Wire phase == current phase → use current (live) keys.
    //   2. Wire phase != current phase but matches the previously-current
    //      phase (we already rotated past it) → use the retained
    //      [previousReceiveProtection] for reordered packets.
    //   3. Wire phase != current phase and != previous → peer has rotated
    //      to the next phase; derive next-phase keys, attempt AEAD, on
    //      success commit the rotation.
    // Without this dance, every post-rotation packet AEAD-fails silently
    // (qlog drops) and the connection wedges (no ACKs to peer, peer falls
    // into PTO mode → throughput collapse). Surfaced by quic-go
    // transferloss interop, which initiates a key update around server
    // pn=100 by default.
    val peek =
        ShortHeaderPacket.peekKeyPhase(
            bytes = datagram,
            offset = offset,
            dcidLen = conn.sourceConnectionId.length,
            hp = live.hp,
            hpKey = live.hpKey,
        )
    if (peek == null) {
        conn.qlogObserver.onPacketDropped(
            "AEAD auth failed or header parse failed at level APPLICATION",
            datagram.size - offset,
        )
        return
    }

    // Build an ordered list of (keys, rotateOnSuccess) candidates and
    // try AEAD against each in order. The list shape depends on whether
    // the peer's KEY_PHASE bit matches our current phase:
    //
    //   match → [current keys] (no rotation possible)
    //   mismatch → [previous keys (if any), next-phase keys (if derivable)]
    //
    // The mismatch path tries `previousReceiveProtection` FIRST because
    // a reordered packet from before the last rotation is by far the
    // common case (the reorder window is short, on the order of a few
    // RTTs). If those keys decrypt the packet, we use them and don't
    // commit. If they fail (genuine consecutive rotation — peer
    // rotated AGAIN, KEY_PHASE wraps back to its prior value), we
    // fall through to deriving next-phase keys against the
    // already-rolled-forward `appReceiveSecret`. Two AEAD attempts on
    // a mismatched-phase packet are OK; KEY_PHASE mismatch is rare
    // (at most once per a-billion-packets in normal usage), and the
    // failed first attempt is cheap (single AEAD seal-verify on a
    // small payload).
    //
    // Pre-fix the parser routed every mismatch packet UNCONDITIONALLY
    // through previousReceiveProtection if non-null, with no
    // fallback. After two consecutive rotations the prior-keys path
    // would always be the wrong keys, AEAD-fail, and the connection
    // would silently wedge — `KeyUpdatePeerInitiatedTest`'s
    // `twoConsecutiveRotationsCommitCorrectly` was disabled with a
    // KNOWN-LIMITATION comment for this reason.
    val parsed: ShortHeaderPacket.ParseResult?
    val rotateOnSuccess: PacketProtection?
    if (peek.keyPhase == conn.currentReceiveKeyPhase) {
        parsed =
            ShortHeaderPacket.parseAndDecrypt(
                bytes = datagram,
                offset = offset,
                dcidLen = conn.sourceConnectionId.length,
                aead = live.aead,
                key = live.key,
                iv = live.iv,
                hp = live.hp,
                hpKey = live.hpKey,
                largestReceivedInSpace = state.pnSpace.largestReceived,
            )
        rotateOnSuccess = null
    } else {
        // Try previous-phase first (reorder path). null result here
        // means the packet wasn't from before the last rotation —
        // fall through to next-phase derivation.
        val priorTry =
            conn.previousReceiveProtection?.let { prev ->
                ShortHeaderPacket.parseAndDecrypt(
                    bytes = datagram,
                    offset = offset,
                    dcidLen = conn.sourceConnectionId.length,
                    aead = prev.aead,
                    key = prev.key,
                    iv = prev.iv,
                    hp = prev.hp,
                    hpKey = prev.hpKey,
                    largestReceivedInSpace = state.pnSpace.largestReceived,
                )
            }
        if (priorTry != null) {
            parsed = priorTry
            rotateOnSuccess = null
        } else {
            // Peer rotated (possibly for a 2nd time). Derive next-phase
            // keys against the rolled-forward `appReceiveSecret` and
            // try AEAD. On success we commit the rotation; on failure
            // it's a bona-fide corrupt / unauthenticated packet.
            val nextPhase = conn.deriveNextPhaseReceiveKeys()
            if (nextPhase == null) {
                conn.qlogObserver.onPacketDropped(
                    "AEAD auth failed or header parse failed at level APPLICATION",
                    datagram.size - offset,
                )
                return
            }
            parsed =
                ShortHeaderPacket.parseAndDecrypt(
                    bytes = datagram,
                    offset = offset,
                    dcidLen = conn.sourceConnectionId.length,
                    aead = nextPhase.aead,
                    key = nextPhase.key,
                    iv = nextPhase.iv,
                    hp = nextPhase.hp,
                    hpKey = nextPhase.hpKey,
                    largestReceivedInSpace = state.pnSpace.largestReceived,
                )
            rotateOnSuccess = nextPhase
        }
    }
    if (parsed == null) {
        conn.qlogObserver.onPacketDropped(
            "AEAD auth failed or header parse failed at level APPLICATION",
            datagram.size - offset,
        )
        // RFC 9001 §6.6 / §B.1 integrity limit. A 1-RTT packet that
        // failed AEAD verification counts as a forgery attempt against
        // the active receive key. Closing here prevents an attacker
        // from grinding through the integrity-limit-many forgeries
        // searching for a key recovery on the underlying AEAD.
        conn.aeadDecryptFailureCount += 1L
        val limit = live.aead.integrityLimit
        if (conn.aeadDecryptFailureCount >= limit) {
            conn.markClosedExternally(
                "AEAD_LIMIT_REACHED: 1-RTT decrypt-failure count ${conn.aeadDecryptFailureCount} >= integrity limit $limit",
            )
        }
        return
    }
    // AEAD succeeded with the candidate next-phase keys → commit the
    // rotation. The commit installs them as live, demotes the prior keys
    // to [previousReceiveProtection], and rolls the send side forward so
    // the next outbound carries the matching KEY_PHASE bit (peer uses
    // that to confirm the rotation completed).
    //
    // RFC 9001 §6.1: a peer that initiated a key update MUST NOT send a
    // post-rotation packet with a smaller PN than any pre-rotation
    // packet. So a "next-phase" packet with PN <= largestReceived is
    // either a misbehaving peer or an off-path attempt to flip our key
    // state with a captured/forged packet. We refuse the commit in that
    // case — AEAD already cleared, so the frames are still delivered,
    // but we don't promote next-phase keys to current. Aligns with
    // quicly / quiche / s2n-quic behaviour.
    if (rotateOnSuccess != null && parsed.packet.packetNumber > state.pnSpace.largestReceived) {
        conn.commitKeyUpdate(rotateOnSuccess)
    }
    // RFC 9001 §6.4: clear the in-flight-rotation gate once an inbound
    // packet decrypts under the live (post-rotation) keys. That confirms
    // the peer has rolled forward in lockstep, so it's safe to permit
    // a subsequent [QuicConnection.initiateKeyUpdate]. The `rotateOnSuccess
    // == null` branch above is the live-keys path (peer's key_phase bit
    // matched our [currentReceiveKeyPhase]); reaching that with a
    // successful parse is the confirmation signal.
    if (rotateOnSuccess == null && conn.keyUpdateInProgress) {
        conn.keyUpdateInProgress = false
    }
    state.pnSpace.observeInbound(parsed.packet.packetNumber, nowMillis)
    // RFC 9000 §10.1.1: any successfully processed inbound packet
    // resets the idle timer.
    conn.lastActivityMs = nowMillis
    if (conn.qlogObserver !== com.vitorpamplona.quic.observability.QlogObserver.NoOp) {
        conn.qlogObserver.onPacketReceived(
            level = EncryptionLevel.APPLICATION,
            packetNumber = parsed.packet.packetNumber,
            sizeBytes = datagram.size - offset,
            frames = peekFrameNames(parsed.packet.payload),
        )
    }
    dispatchFrames(conn, EncryptionLevel.APPLICATION, parsed.packet.payload, parsed.packet.packetNumber, nowMillis)
}

/**
 * Decode the payload's frames just to surface their qlog names. Reuses
 * the same [com.vitorpamplona.quic.frame.decodeFrames] path as
 * [dispatchFrames]; if it throws (malformed peer payload), we return
 * an empty list — the dispatch path will catch the same exception
 * and surface the close via `markClosedExternally`.
 */
private fun peekFrameNames(payload: ByteArray): List<String> =
    try {
        com.vitorpamplona.quic.frame
            .decodeFrames(payload)
            .map { qlogFrameName(it::class.simpleName ?: "frame") }
    } catch (_: QuicCodecException) {
        emptyList()
    }

private fun dispatchFrames(
    conn: QuicConnection,
    level: EncryptionLevel,
    payload: ByteArray,
    packetNumber: Long,
    nowMillis: Long,
) {
    // Audit-4 #1: malformed frames in an otherwise-AEAD-validated payload (or
    // unknown frame types from a future-extension peer) used to throw straight
    // through the read loop's `finally` block, dropping the connection without
    // ever sending CONNECTION_CLOSE. Catch decode exceptions and turn them
    // into a graceful close so the peer learns why we tore down.
    val frames =
        try {
            decodeFrames(payload)
        } catch (e: QuicCodecException) {
            conn.markClosedExternally("frame decode failed: ${e.message}")
            return
        }
    val state = conn.levelState(level)
    var ackEliciting = false
    for (frame in frames) {
        when (frame) {
            is AckFrame -> {
                // The peer's `frame.largestAcknowledged` is in OUR
                // outbound PN space — the inbound `state.ackTracker`
                // tracks PNs WE RECEIVED from the peer. The previous
                // purge here was conceptually wrong (mixed two
                // unrelated number spaces) but happened to mostly
                // work because both PN spaces grow at similar rates.
                // The correct purge is now driven by the
                // ACK-of-ACK dispatch in [QuicConnection.onTokensAcked]
                // for [RecoveryToken.Ack]: when the peer ACKs the
                // packet that carried our outbound ACK frame, we
                // know the peer received our ACK and we can drop
                // the corresponding inbound PNs from the tracker.
                // Step 5 of `quic/plans/2026-05-04-control-frame-retransmit.md`:
                // (a) snapshot the send time of the largest-acked PN
                // BEFORE drain so we can update RTT, (b) drain ACK'd
                // packets, (c) if the ACK advanced largestAckedPn AND
                // any drained packet was ack-eliciting, update RTT,
                // (d) detect-and-remove lost packets. The lost-token
                // dispatch (step 6) is a TODO — for step 5 we drop the
                // returned list on the floor.
                val largestSentTime = state.sentPackets[frame.largestAcknowledged]?.sentAtMillis
                val drained = drainAckedSentPackets(state.sentPackets, frame)
                // Step C of the deferred-follow-ups pass: dispatch ACK
                // to per-buffer markAcked for Stream / Crypto tokens.
                // Releases SendBuffer memory and advances its
                // flushedFloor as low-end ACKs accumulate.
                for (drainedPacket in drained) {
                    conn.onTokensAcked(drainedPacket.tokens)
                }
                val advancedLargest =
                    state.largestAckedPn?.let { it < frame.largestAcknowledged } ?: true
                if (advancedLargest) {
                    state.largestAckedPn = frame.largestAcknowledged
                    state.largestAckedSentTimeMs = largestSentTime
                }
                if (advancedLargest && largestSentTime != null && drained.any { it.ackEliciting }) {
                    // Peer-controlled `frame.ackDelay` is a varint up to 2^62-1.
                    // Naive `shl ackDelayExponent` overflows Long for crafted
                    // values (negative result → poisoned RTT sample inflating
                    // smoothed_rtt). Clamp the exponent and the shift result
                    // before consuming. Per RFC 9000 §18.2 the exponent is
                    // 0..20; we further clamp ackDelay so the shift never
                    // overflows (`ackDelay <= Long.MAX_VALUE >>> exponent`).
                    //
                    // RFC 9000 §13.2.5: a received ACK is decoded using the
                    // PEER's `ack_delay_exponent`, not ours. Pre-handshake
                    // peer params haven't arrived yet — fall back to the
                    // default of 3 per §18.2. Coercion below ensures even
                    // a malicious peer that smuggles a >20 value through
                    // the §18.2 bounds check (e.g. on a connection where
                    // the peer-params bounds enforcement is bypassed by a
                    // race) can't desync our RTT estimator.
                    val peerExponent =
                        conn.peerTransportParameters?.ackDelayExponent
                            ?: TransportParameterDefaults.ACK_DELAY_EXPONENT
                    val exponent = peerExponent.coerceIn(0L, 20L).toInt()
                    val maxAckDelayPreShift =
                        if (exponent == 0) Long.MAX_VALUE else Long.MAX_VALUE ushr exponent
                    val safeAckDelay = frame.ackDelay.coerceIn(0L, maxAckDelayPreShift)
                    val ackDelayUs = safeAckDelay shl exponent
                    val ackDelayMs = ackDelayUs / 1_000L
                    conn.lossDetection.onRttSample(
                        largestAckedSentTimeMs = largestSentTime,
                        ackDelayMs = ackDelayMs,
                        nowMs = nowMillis,
                    )
                    // Step 7: reset PTO count on any new ack-eliciting
                    // ACK (RFC 9002 §6.2.1). The peer responded, so the
                    // exponential backoff resets.
                    conn.consecutivePtoCount = 0
                }
                state.largestAckedPn?.let { largestAckedPn ->
                    val result =
                        conn.lossDetection.detectAndRemoveLost(
                            sentPackets = state.sentPackets,
                            largestAckedPn = largestAckedPn,
                            nowMs = nowMillis,
                        )
                    // Step 6: dispatch each lost packet's tokens to the
                    // matching pending* field. The supersede check (lost
                    // value still == advertised) lives inside onTokensLost.
                    for (lostPacket in result.lost) {
                        conn.onTokensLost(lostPacket.tokens)
                    }
                    if (result.lost.isNotEmpty()) {
                        conn.qlogObserver.onLossDetected(level, result.lost.map { it.packetNumber })
                    }
                    // RFC 9002 §6.1.2 timer-driven loss detection. Record
                    // the earliest pending time-threshold deadline so the
                    // driver's send loop can wake at that instant rather
                    // than waiting for the next inbound ACK or PTO. Set
                    // null when no sub-largest packets remain in flight.
                    state.nextLossTimeMs = result.nextLossTimeMs
                }
            }

            is CryptoFrame -> {
                ackEliciting = true
                state.cryptoReceive.insert(frame.offset, frame.data)
                val contiguous = state.cryptoReceive.readContiguous()
                if (contiguous.isNotEmpty()) {
                    val tlsLevel =
                        when (level) {
                            EncryptionLevel.INITIAL -> TlsClient.Level.INITIAL
                            EncryptionLevel.HANDSHAKE -> TlsClient.Level.HANDSHAKE
                            EncryptionLevel.APPLICATION -> TlsClient.Level.APPLICATION
                        }
                    conn.tls.pushHandshakeBytes(tlsLevel, contiguous)
                    // Pull any new outbound CRYPTO bytes out of TLS into our
                    // send queue at the matching encryption level.
                    drainTlsOutbound(conn)
                }
            }

            is StreamFrame -> {
                ackEliciting = true
                // Audit-4 #5: reject peer-attempted CLIENT_BIDI / CLIENT_UNI
                // stream IDs that don't match a stream we've opened. Per RFC
                // 9000 §19.8, only the side that owns the parity may open;
                // a server squatting on a CLIENT_* id is a protocol violation
                // and could otherwise inject phantom streams into newPeerStreams.
                //
                // Exempt retired ids: msquic-style aggressive loss recovery
                // can retransmit a STREAM frame on a stream we've fully
                // retired before the peer sees our FIN-ACK. That's a
                // legitimate retransmit on a stream WE opened, not squatting
                // — the next guard drops it silently. Without this exemption
                // the close fires on the parallel `transfer` interop test
                // (3 GETs on streams 0/4/8) and truncates whichever stream
                // is still mid-receive when the close lands.
                if (StreamId.isClientInitiated(frame.streamId) &&
                    conn.streamByIdLocked(frame.streamId) == null &&
                    !conn.isStreamIdRetiredLocked(frame.streamId)
                ) {
                    conn.markClosedExternally(
                        "peer opened stream ${frame.streamId} on client-initiated id space (STREAM_STATE_ERROR)",
                    )
                    return
                }
                // Phantom-stream guard: drop STREAM frames the peer
                // retransmitted on a stream we've already retired. Without
                // this, the next line would mint a fresh QuicStream object
                // and re-deliver duplicate bytes to the application
                // (worse, on a SERVER_UNI stream it would also bump
                // peerInitiatedUniCount and trigger a spurious
                // MAX_STREAMS_UNI emission). The frame is still
                // ack-eliciting — we set ackEliciting above — so our
                // ACK goes out and the peer's loss-detector backs off.
                // Stays inside the StreamFrame handler so other frame
                // types (RESET_STREAM, MAX_STREAM_DATA, STOP_SENDING)
                // on retired ids gracefully fall through their existing
                // streamByIdLocked == null branches as no-ops.
                if (conn.isStreamIdRetiredLocked(frame.streamId) &&
                    conn.streamByIdLocked(frame.streamId) == null
                ) {
                    continue
                }
                val stream = conn.getOrCreatePeerStreamLocked(frame.streamId)
                // RFC 9000 §3.2: once the peer has sent RESET_STREAM the
                // receive side is in the "Reset Recvd" terminal state.
                // Any subsequent STREAM frame on this id is a peer
                // protocol violation — close with STREAM_STATE_ERROR.
                // Pre-fix the bytes were silently absorbed, leaving the
                // application with a phantom mid-reset stream that the
                // peer believed was already dead.
                if (stream.peerResetReceived) {
                    conn.markClosedExternally(
                        "STREAM_STATE_ERROR: stream ${frame.streamId} STREAM after RESET_STREAM",
                    )
                    return
                }
                // RFC 9000 §4.1: peer MUST NOT send beyond the limit we advertised.
                // The connection-level kill protects against unbounded memory
                // growth from a misbehaving peer.
                val frameEnd = frame.offset + frame.data.size
                if (frameEnd > stream.receiveLimit) {
                    conn.markClosedExternally(
                        "peer exceeded stream ${frame.streamId} receive limit ($frameEnd > ${stream.receiveLimit})",
                    )
                    return
                }
                // RFC 9000 §4.1 connection-level enforcement: the SUM
                // across all streams of "largest stream offset received"
                // MUST NOT exceed the most recently advertised MAX_DATA.
                // We update [conn.connectionInboundOffsetSum] only when
                // this frame's end-offset advances the per-stream
                // high-water mark, so duplicate / reordered frames don't
                // double-count.
                if (frameEnd > stream.receiveHighestOffset) {
                    val delta = frameEnd - stream.receiveHighestOffset
                    stream.receiveHighestOffset = frameEnd
                    conn.connectionInboundOffsetSum += delta
                    if (conn.connectionInboundOffsetSum > conn.advertisedMaxData) {
                        conn.markClosedExternally(
                            "FLOW_CONTROL_ERROR: peer exceeded connection MAX_DATA " +
                                "(${conn.connectionInboundOffsetSum} > ${conn.advertisedMaxData})",
                        )
                        return
                    }
                }
                // RFC 9000 §4.5: enforce final-size invariants. The
                // [com.vitorpamplona.quic.stream.ReceiveBuffer.insert]
                // surface returns a typed result; map any non-OK result
                // to a connection close with FINAL_SIZE_ERROR so the
                // peer knows it just violated the spec instead of
                // having its bytes silently dropped.
                when (stream.receive.insert(frame.offset, frame.data, frame.fin)) {
                    com.vitorpamplona.quic.stream.ReceiveBuffer.InsertResult.OK -> {
                        Unit
                    }

                    com.vitorpamplona.quic.stream.ReceiveBuffer.InsertResult.OFFSET_PAST_FIN -> {
                        conn.markClosedExternally(
                            "FINAL_SIZE_ERROR: stream ${frame.streamId} frame ends at " +
                                "${frame.offset + frame.data.size} past final size ${stream.receive.finOffset}",
                        )
                        return
                    }

                    com.vitorpamplona.quic.stream.ReceiveBuffer.InsertResult.FIN_CONFLICTS_WITH_PRIOR_FIN -> {
                        conn.markClosedExternally(
                            "FINAL_SIZE_ERROR: stream ${frame.streamId} second FIN at " +
                                "${frame.offset + frame.data.size} disagrees with prior final size ${stream.receive.finOffset}",
                        )
                        return
                    }
                }
                val data = stream.receive.readContiguous()
                if (data.isNotEmpty()) {
                    // Round-4 perf #9: mark the stream as needing a flow-
                    // control re-credit check. Writer's
                    // appendFlowControlUpdates consults this flag instead of
                    // walking every open stream on every drain.
                    stream.receiveDirtyForFlowControl = true
                    val delivered = stream.deliverIncoming(data)
                    if (!delivered) {
                        // Audit-4 #3: incoming channel saturated. Closing the
                        // connection beats silently dropping bytes — a stalled
                        // consumer is better surfaced as an error than as a
                        // mysterious hole in the application's data. Use
                        // INTERNAL_ERROR (RFC 9000 §20.1).
                        conn.markClosedExternally(
                            "INTERNAL_ERROR: stream ${frame.streamId} consumer overflowed " +
                                "incoming channel (slow consumer)",
                        )
                        return
                    }
                }
                // Audit-4 #4: only close the incoming channel once the
                // contiguous read frontier has actually reached the FIN
                // offset. Closing on FIN-arrival drops any later-arriving
                // fill chunks silently because trySend on a closed channel
                // returns failure — the application would see a truncated
                // stream with no error signal.
                if (stream.receive.finReceived && stream.receive.isFullyRead()) {
                    stream.closeIncoming()
                }
            }

            is DatagramFrame -> {
                ackEliciting = true
                // Audit-4 #8: cap the inbound datagram queue. RFC 9221
                // datagrams are outside connection flow control; a peer can
                // otherwise pin arbitrary memory by spamming DATAGRAM frames.
                // We drop the OLDEST queued datagram when full — preferable
                // for audio rooms (live streams) over rejecting fresh ones.
                val queue = conn.incomingDatagramsLocked()
                if (queue.size >= QuicConnection.MAX_INCOMING_DATAGRAM_QUEUE) {
                    queue.removeFirst()
                }
                queue.addLast(frame.data)
                conn.signalIncomingDatagram()
            }

            is MaxDataFrame -> {
                // RFC 9000 §13.2.1: MAX_DATA is ack-eliciting. Without this,
                // a packet carrying only MAX_DATA would record the PN but
                // never trigger an ACK (round-4 ACK gating regression).
                ackEliciting = true
                // RFC 9000 §19.9: MAX_DATA only ever raises the cap.
                if (frame.maxData > conn.sendConnectionFlowCredit) {
                    conn.sendConnectionFlowCredit = frame.maxData
                }
            }

            is MaxStreamDataFrame -> {
                ackEliciting = true
                conn.streamByIdLocked(frame.streamId)?.let {
                    if (frame.maxStreamData > it.sendCredit) it.sendCredit = frame.maxStreamData
                }
            }

            is MaxStreamsFrame -> {
                ackEliciting = true
                // RFC 9000 §19.11: MAX_STREAMS only ever raises the cap.
                // Frames with values smaller than the current cap are ignored.
                // Bidi vs uni is signaled via the frame's `bidi` flag.
                if (frame.bidi) {
                    if (frame.maxStreams > conn.peerMaxStreamsBidi) {
                        conn.peerMaxStreamsBidi = frame.maxStreams
                    }
                } else {
                    if (frame.maxStreams > conn.peerMaxStreamsUni) {
                        conn.peerMaxStreamsUni = frame.maxStreams
                    }
                }
            }

            is ResetStreamFrame -> {
                // RFC 9000 §3.5: RESET_STREAM is the peer aborting THEIR send
                // side of the stream. Round-5 #2: it's only legal on streams
                // where the peer owns a send side (server-initiated streams,
                // or our own bidi). A peer RESETting one of OUR uni streams
                // (CLIENT_UNI = id%4==2) is STREAM_STATE_ERROR — they don't
                // have a send side to abort.
                ackEliciting = true
                if (StreamId.kindOf(frame.streamId) == StreamId.Kind.CLIENT_UNI) {
                    conn.markClosedExternally(
                        "STREAM_STATE_ERROR: peer RESET_STREAM on client-uni id ${frame.streamId} (peer has no send side)",
                    )
                    return
                }
                // RFC 9000 §4.5: the [finalSize] in RESET_STREAM MUST agree
                // with any final size implied by previously-received STREAM
                // frames AND MUST be ≥ the highest offset already observed.
                // §4.5 also caps final size at 2^62-1 (the QUIC offset field
                // ceiling). A peer that violates any of these is closed with
                // FINAL_SIZE_ERROR — pre-fix we accepted any value silently,
                // letting a buggy peer drift our state.
                if (frame.finalSize < 0L || frame.finalSize > MAX_QUIC_OFFSET) {
                    conn.markClosedExternally(
                        "FINAL_SIZE_ERROR: stream ${frame.streamId} RESET_STREAM finalSize " +
                            "${frame.finalSize} outside [0, 2^62-1]",
                    )
                    return
                }
                val target = conn.streamByIdLocked(frame.streamId)
                if (target != null) {
                    val priorFin = target.receive.finOffset
                    val highestSeen = target.receive.highestObservedOffset()
                    if (priorFin != null && frame.finalSize != priorFin) {
                        conn.markClosedExternally(
                            "FINAL_SIZE_ERROR: stream ${frame.streamId} RESET_STREAM finalSize " +
                                "${frame.finalSize} disagrees with prior FIN size $priorFin",
                        )
                        return
                    }
                    if (frame.finalSize < highestSeen) {
                        conn.markClosedExternally(
                            "FINAL_SIZE_ERROR: stream ${frame.streamId} RESET_STREAM finalSize " +
                                "${frame.finalSize} below already-observed offset $highestSeen",
                        )
                        return
                    }
                }
                // RFC 9000 §4.5: a RESET_STREAM's finalSize counts toward
                // connection-level flow control even though no STREAM frame
                // ever delivered those bytes — it represents what the peer
                // committed to send before aborting. Top up
                // [conn.connectionInboundOffsetSum] when finalSize advances
                // beyond what STREAM frames already counted, then re-check
                // against the connection cap.
                if (target != null && frame.finalSize > target.receiveHighestOffset) {
                    val delta = frame.finalSize - target.receiveHighestOffset
                    target.receiveHighestOffset = frame.finalSize
                    conn.connectionInboundOffsetSum += delta
                    if (conn.connectionInboundOffsetSum > conn.advertisedMaxData) {
                        conn.markClosedExternally(
                            "FLOW_CONTROL_ERROR: peer RESET_STREAM finalSize pushed connection past MAX_DATA " +
                                "(${conn.connectionInboundOffsetSum} > ${conn.advertisedMaxData})",
                        )
                        return
                    }
                }
                // RFC 9000 §3.2: latch the receive-side terminal state so
                // any subsequent STREAM frame on this id closes the
                // connection with STREAM_STATE_ERROR (handled in the
                // STREAM branch above).
                target?.peerResetReceived = true
                // Mark the peer's stream aborted and close our read side; the
                // application sees a truncated incoming flow.
                target?.closeIncoming()
            }

            is StopSendingFrame -> {
                // RFC 9000 §3.5: peer asks us to stop sending on its read side.
                // We MUST respond with RESET_STREAM carrying the same
                // application error code so the peer can free its receive
                // resources. Pre-fix this was silently dropped, so the peer
                // would keep buffering bytes we kept emitting and the
                // application kept paying CPU on a stream the peer no
                // longer cared about.
                //
                // resetStream() is "first-call wins" so a duplicate
                // STOP_SENDING (peer retransmit) doesn't redundantly mutate
                // state. Only triggers for streams with an outgoing side —
                // peer-uni (CLIENT_UNI from peer's perspective = SERVER_UNI
                // here) has none, so the call is a defensive no-op.
                ackEliciting = true
                conn.streamByIdLocked(frame.streamId)?.let { stream ->
                    val kind = StreamId.kindOf(frame.streamId)
                    val hasLocalSend =
                        kind == StreamId.Kind.CLIENT_BIDI ||
                            kind == StreamId.Kind.SERVER_BIDI ||
                            kind == StreamId.Kind.CLIENT_UNI
                    if (hasLocalSend) {
                        stream.resetStream(frame.applicationErrorCode)
                    }
                }
            }

            is NewTokenFrame -> {
                // Round-4 #2: 0-RTT/resumption token. Out-of-scope; drop.
                ackEliciting = true
            }

            is NewConnectionIdFrame -> {
                // RFC 9000 §13.2.1: NEW_CONNECTION_ID is ack-eliciting.
                // §19.15 + §5.1.2: store the offered CID + stateless
                // reset token in the [PathValidator] pool so the
                // client-initiated migration path (RFC 9000 §9) can
                // pick one when the active path stops receiving
                // ACKs. Also enforces `retire_prior_to` semantics
                // and queues RETIRE_CONNECTION_ID for any cached
                // entry whose sequence number falls below the new
                // watermark. Peer protocol violations close the
                // connection upstream.
                ackEliciting = true
                conn.applyPeerNewConnectionIdLocked(
                    sequenceNumber = frame.sequenceNumber,
                    retirePriorTo = frame.retirePriorTo,
                    connectionId = frame.connectionId,
                    statelessResetToken = frame.statelessResetToken,
                )
            }

            is PathChallengeFrame -> {
                // RFC 9000 §8.2.2 — peer is validating that a path is
                // alive. We MUST echo the SAME 8-byte payload in a
                // PATH_RESPONSE on the path the challenge arrived on.
                // The writer drains [pendingPathChallengePayloads] on the next
                // application-level packet build.
                //
                // Common practical trigger: server-side path
                // validation after our connection-id rotation, OR
                // post-NAT-rebind probing. Without responding the
                // server may declare the path dead within a few RTTs
                // and tear the connection down — visible to users as
                // a sudden audio cut on a phone that briefly
                // switched cells.
                //
                // RFC 9000 §13.2.1: PATH_CHALLENGE is ack-eliciting.
                // The PATH_RESPONSE we queue here is itself
                // ack-eliciting; the regular ACK path covers both.
                ackEliciting = true
                conn.queuePathResponseLocked(frame.data)
            }

            is PathResponseFrame -> {
                // RFC 9000 §13.2.1: PATH_RESPONSE is ack-eliciting.
                // §8.2.2: if the payload matches our outstanding
                // PATH_CHALLENGE, validation succeeded — the writer
                // is told to start stamping the new DCID and a
                // RETIRE_CONNECTION_ID for the old sequence number
                // is queued (RFC 9000 §9.5). Mismatched payloads
                // (no outstanding challenge OR an attacker echoing
                // random bytes) are silently ignored after the ACK.
                ackEliciting = true
                conn.applyPeerPathResponseLocked(frame.data)
            }

            is RetireConnectionIdFrame -> {
                // RFC 9000 §13.2.1: RETIRE_CONNECTION_ID is
                // ack-eliciting. §19.16: peer is asking us to
                // retire one of OUR source CIDs. We only ever
                // issued sequence 0 (the SCID we picked at
                // connection start); any seq > 0 is a protocol
                // violation per §19.16, closes the connection.
                ackEliciting = true
                conn.applyPeerRetireConnectionIdLocked(frame.sequenceNumber)
            }

            is ConnectionCloseFrame -> {
                // Audit-4 #13: any frames following CONNECTION_CLOSE in the
                // same payload MUST NOT be dispatched — they could create
                // streams or deliver bytes on an already-closed connection.
                conn.markClosedExternally("peer CONNECTION_CLOSE: ${frame.reason}")
                return
            }

            is HandshakeDoneFrame -> {
                // Audit-4 #14: HANDSHAKE_DONE is permitted ONLY at Application
                // level (RFC 9000 §19.20). Anywhere else is PROTOCOL_VIOLATION.
                if (level != EncryptionLevel.APPLICATION) {
                    conn.markClosedExternally(
                        "HANDSHAKE_DONE at $level (PROTOCOL_VIOLATION; allowed only at APPLICATION)",
                    )
                    return
                }
                ackEliciting = true
                // Round-5 #13: only flip to CONNECTED if we're still in
                // HANDSHAKING. Pre-fix this unconditionally overwrote the
                // status, which would resurrect a connection that
                // applyPeerTransportParameters had just closed via
                // markClosedExternally because of a CID validation failure.
                if (conn.status == QuicConnection.Status.HANDSHAKING) {
                    conn.status = QuicConnection.Status.CONNECTED
                }
                // RFC 9001 §4.9.2 + §4.1.2: a client confirms the handshake
                // on receipt of HANDSHAKE_DONE; once confirmed, MUST discard
                // Handshake keys. Frees the AEAD cipher state and any
                // residual handshake CRYPTO bookkeeping that's no longer
                // needed for the lifetime of the connection.
                conn.handshake.discardKeys()
            }

            is PingFrame -> {
                ackEliciting = true
            }

            else -> {
                // PADDING + DATA_BLOCKED + STREAM_DATA_BLOCKED + STREAMS_BLOCKED
                // are non-eliciting / cleared during decode.
            }
        }
    }
    // Always record the packet's actual PN — even non-ack-eliciting packets
    // need to appear in our ACK ranges so the peer's loss-recovery sees a
    // contiguous picture of what we received.
    state.ackTracker.receivedPacket(packetNumber, ackEliciting = ackEliciting, receivedAtMillis = nowMillis)
}

private fun drainTlsOutbound(conn: QuicConnection) {
    for (lvl in TlsClient.Level.entries) {
        while (true) {
            val bytes = conn.tls.pollOutbound(lvl) ?: break
            val state =
                when (lvl) {
                    TlsClient.Level.INITIAL -> conn.initial
                    TlsClient.Level.HANDSHAKE -> conn.handshake
                    TlsClient.Level.APPLICATION -> conn.application
                }
            state.cryptoSend.enqueue(bytes)
        }
    }
}
