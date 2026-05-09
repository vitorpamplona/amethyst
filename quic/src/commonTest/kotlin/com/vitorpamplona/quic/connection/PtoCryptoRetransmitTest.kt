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

import com.vitorpamplona.quic.connection.recovery.RecoveryToken
import com.vitorpamplona.quic.frame.CryptoFrame
import com.vitorpamplona.quic.frame.Frame
import com.vitorpamplona.quic.packet.LongHeaderPacket
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9002 §6.2.4 spec-correct PTO probe behavior: when the timer fires
 * pre-handshake the client MUST send an ack-eliciting packet at the
 * encryption level with unacknowledged data, and SHOULD retransmit the
 * lost data rather than emit a bare PING.
 *
 * Regression scenario from the quic-interop-runner ns-3 transfer test
 * against aioquic: the first ClientHello datagram is dropped because
 * the simulated server isn't fully started at t≈0.5s. Our PTO at
 * t≈1.5s previously sent only a PING with the same DCID; the server
 * had no state for that DCID (never saw the original Initial), so
 * the PING was silently ignored and the connection died.
 *
 * The fix has two cooperating pieces:
 *   1. [com.vitorpamplona.quic.stream.SendBuffer.requeueAllInflight]
 *      moves every sent-but-not-ACK'd byte range from the inflight
 *      list back onto the retransmit queue.
 *   2. [QuicConnection.requeueAllInflightCrypto] exposes that to the
 *      driver, which calls it from the PTO branch when 1-RTT keys
 *      aren't installed yet (i.e. handshake not done).
 *
 * After the call, the next [drainOutbound] naturally emits a CRYPTO
 * frame at the original offset — the server actually sees a fresh
 * ClientHello attempt and can advance the handshake.
 */
class PtoCryptoRetransmitTest {
    @Test
    fun ptoPreHandshake_retransmitsClientHelloCryptoBytes() =
        runBlocking {
            val client = newClientWithStartedHandshake()

            // First drain — emits the ClientHello inside an Initial datagram.
            val firstDrain = drainOutbound(client, nowMillis = 1L)
            assertNotNull(firstDrain, "first drain must produce the ClientHello datagram")
            assertTrue(
                firstDrain.size >= 1200,
                "RFC 9000 §14.1 — Initial-bearing datagram pads to ≥ 1200 bytes (got ${firstDrain.size})",
            )

            // Capture the original ClientHello bytes and offset by
            // pulling them out of the SentPacket bookkeeping.
            val firstSent =
                client.initial.sentPackets.entries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.Crypto }
                }
            assertNotNull(firstSent, "Initial-level SentPacket must carry a Crypto token after first drain")
            val firstCrypto =
                firstSent.value.tokens
                    .filterIsInstance<RecoveryToken.Crypto>()
                    .single()
            assertEquals(0L, firstCrypto.offset, "ClientHello starts at offset 0")
            assertTrue(firstCrypto.length > 0L, "ClientHello has non-zero length")

            // Sanity: the second drain (immediately, no PTO yet) must
            // produce nothing — bytes are inflight, not unsent.
            val emptyDrain = drainOutbound(client, nowMillis = 2L)
            assertTrue(
                emptyDrain == null || emptyDrain.isEmpty(),
                "no PTO yet, no fresh data → second drain must be empty (got ${emptyDrain?.size} bytes)",
            )

            // Drive the EXACT helper QuicConnectionDriver.sendLoop
            // calls when its PTO timer fires. Earlier versions of
            // this test inlined the simulation (set pendingPing,
            // call requeueAllInflightCrypto manually) — but that
            // hid the regression where the driver itself stopped
            // calling requeueAllInflightCrypto. Calling
            // [handlePtoFired] keeps the test in lockstep with the
            // production code path: if anyone unwires the requeue
            // again, this test breaks.
            handlePtoFired(client)

            // Next drain must emit a fresh Initial packet carrying
            // the ClientHello CRYPTO at the original offset.
            val ptoDrain = drainOutbound(client, nowMillis = 3L)
            assertNotNull(ptoDrain, "PTO drain must produce a retransmit datagram")
            assertTrue(
                ptoDrain.size >= 1200,
                "RFC 9000 §14.1 — Initial-bearing datagram still pads to ≥ 1200 bytes on PTO retransmit (got ${ptoDrain.size})",
            )

            // The retransmit packet must carry a Crypto token at the
            // original offset and length — that's how we know the
            // CRYPTO frame went out (not a PING-only probe).
            val replayEntries =
                client.initial.sentPackets.entries
                    .filter { it.key != firstSent.key }
            val replaySent =
                replayEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.Crypto }
                }
            assertNotNull(
                replaySent,
                "PTO drain must produce a fresh Initial SentPacket carrying Crypto " +
                    "(saw ${replayEntries.map { it.value.tokens.map { t -> t::class.simpleName } }})",
            )
            val replayCrypto =
                replaySent.value.tokens
                    .filterIsInstance<RecoveryToken.Crypto>()
                    .single()
            assertEquals(EncryptionLevel.INITIAL, replayCrypto.level)
            assertEquals(firstCrypto.offset, replayCrypto.offset, "PTO retransmit replays original offset")
            assertEquals(firstCrypto.length, replayCrypto.length, "PTO retransmit replays original length")

            // Decode the retransmit packet and assert the CRYPTO
            // frame's payload bytes match the original.
            val firstFrames = decodeInitialFrames(firstDrain, client)
            val firstHello =
                firstFrames.filterIsInstance<CryptoFrame>().firstOrNull {
                    it.offset == firstCrypto.offset && it.data.size.toLong() == firstCrypto.length
                }
            assertNotNull(firstHello, "first drain's Initial must contain the ClientHello CRYPTO")

            val ptoFrames = decodeInitialFrames(ptoDrain, client)
            val replayHello =
                ptoFrames.filterIsInstance<CryptoFrame>().firstOrNull {
                    it.offset == firstCrypto.offset
                }
            assertNotNull(
                replayHello,
                "PTO drain's Initial must contain a CRYPTO frame at offset 0 — bare PING is not enough " +
                    "(saw frames ${ptoFrames.map { it::class.simpleName }})",
            )
            assertTrue(
                replayHello.data.contentEquals(firstHello.data),
                "PTO retransmit must carry the same ClientHello bytes (size first=${firstHello.data.size} replay=${replayHello.data.size})",
            )

            // pendingPing should be cleared since the CRYPTO frame
            // satisfied the ack-eliciting requirement at the level.
            assertEquals(
                false,
                client.pendingPing,
                "pendingPing must be consumed once the PTO emit went out (CRYPTO covered the probe)",
            )
        }

    @Test
    fun ptoEmitsTwoProbePacketsPerRfc9002() =
        runBlocking {
            // RFC 9002 §6.2.4: a PTO probe SHOULD send up to 2
            // ack-eliciting packets at the encryption level with
            // unacknowledged data. Halves recovery time across
            // consecutive drops — the `amplificationlimit` interop
            // scenario drops 6 client→server packets in a row, so
            // two probes per PTO collapse the recovery window from
            // ~19s (six PTO doublings, single packet each) to ~5s
            // (three rounds, two packets each). Strict server-side
            // handshake-progress watchdogs (quic-go, msquic) tear
            // the connection down at ~10s of silence regardless of
            // our budget, so the half-time matters for landing in
            // their tolerance.
            //
            // This test drives the EXACT helpers the send loop calls
            // — [handlePtoFired] (initial PTO requeue + budget=2)
            // followed by [requeueInflightForProbe] (the re-requeue
            // step the loop performs between probe-bearing drains).
            // If anyone unwires the budget or the re-requeue, this
            // test breaks.
            val client = newClientWithStartedHandshake()

            val firstDrain = drainOutbound(client, nowMillis = 1L)
            assertNotNull(firstDrain, "first drain emits the ClientHello datagram")

            val firstSent =
                client.initial.sentPackets.entries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.Crypto }
                }
            assertNotNull(firstSent, "Initial-level SentPacket must carry a Crypto token")
            val firstCrypto =
                firstSent.value.tokens
                    .filterIsInstance<RecoveryToken.Crypto>()
                    .single()

            // PTO fires.
            handlePtoFired(client)
            assertEquals(
                2,
                client.pendingProbePackets,
                "handlePtoFired must seed [pendingProbePackets]=2 per RFC 9002 §6.2.4",
            )

            // Probe 1: drain consumes the requeued CRYPTO. Send loop
            // decrements the budget and re-requeues for probe 2.
            val probe1 = drainOutbound(client, nowMillis = 2L)
            assertNotNull(probe1, "probe 1 must produce a datagram")
            assertTrue(
                probe1.size >= 1200,
                "probe 1 carries Initial → padded to ≥ 1200 (got ${probe1.size})",
            )
            client.pendingProbePackets--
            requeueInflightForProbe(client)

            // Probe 2: a SECOND independent datagram with the same
            // CRYPTO bytes at the same offset, allocated a fresh PN.
            val probe2 = drainOutbound(client, nowMillis = 3L)
            assertNotNull(probe2, "probe 2 must produce a SECOND datagram (RFC 9002 §6.2.4)")
            assertTrue(
                probe2.size >= 1200,
                "probe 2 also Initial-bearing → padded to ≥ 1200 (got ${probe2.size})",
            )
            client.pendingProbePackets--

            // Both probes must contain the original ClientHello CRYPTO
            // at offset 0. Their wire bytes will differ (different PN
            // → different AEAD nonce → different ciphertext) but the
            // decrypted CRYPTO payload matches.
            val firstFrames = decodeInitialFrames(firstDrain, client)
            val probe1Frames = decodeInitialFrames(probe1, client)
            val probe2Frames = decodeInitialFrames(probe2, client)
            val firstHello =
                firstFrames.filterIsInstance<CryptoFrame>().single { it.offset == firstCrypto.offset }
            val probe1Hello =
                probe1Frames.filterIsInstance<CryptoFrame>().firstOrNull { it.offset == firstCrypto.offset }
            val probe2Hello =
                probe2Frames.filterIsInstance<CryptoFrame>().firstOrNull { it.offset == firstCrypto.offset }
            assertNotNull(probe1Hello, "probe 1 must carry CRYPTO at offset 0 — bare PING is not enough")
            assertNotNull(probe2Hello, "probe 2 must ALSO carry CRYPTO at offset 0 (RFC 9002 §6.2.4)")
            assertTrue(
                probe1Hello.data.contentEquals(firstHello.data),
                "probe 1 replays the original ClientHello bytes",
            )
            assertTrue(
                probe2Hello.data.contentEquals(firstHello.data),
                "probe 2 replays the SAME original ClientHello bytes (re-requeue preserves them)",
            )

            // The two probes must use DISTINCT packet numbers — same
            // PN twice in the same encryption-level space would be
            // RFC 9000 §17.2.5.1 protocol violation, and was the
            // root cause of the picoquic-retry diagnostic earlier
            // this session.
            val sentPNs =
                client.initial.sentPackets.keys
                    .filter { it != firstSent.key }
                    .toSet()
            assertEquals(
                2,
                sentPNs.size,
                "two probes must consume two distinct PNs (got $sentPNs)",
            )

            // After both probes, no more inflight CRYPTO to re-requeue
            // → next drain returns null. The send loop's break path.
            val tail = drainOutbound(client, nowMillis = 4L)
            assertTrue(
                tail == null || tail.isEmpty(),
                "no third probe — budget exhausted (got ${tail?.size} bytes)",
            )
        }

    @Test
    fun consecutivePtoCountAdvancesByOnePerPtoFiringNotPerProbePacket() =
        runBlocking {
            // RFC 9002 §6.2.2: the consecutive-PTO count advances by
            // exactly ONE per PTO timer expiry (so the §6.2.1 backoff
            // is `pto_base * 2^count` per firing). Pre-2026-05-08 the
            // count incremented THREE times per firing — once in
            // `handlePtoFired`, once in `requeueInflightForProbe`,
            // and again from the send loop's between-probe re-requeue
            // — making the effective backoff `2^(3*N)` (1×, 8×, 64×
            // per PTO). Quic-go's `amplificationlimit` testcase
            // exposed this: PTO #3 didn't fire until ~11 s
            // post-handshake, well past quic-go's 5 s amp-limited
            // idle timeout. See the explicit kdoc in
            // [com.vitorpamplona.quic.connection.handlePtoFired].
            //
            // This test drives the EXACT helpers the production send
            // loop calls (handlePtoFired + the between-probe
            // requeueInflightForProbe) and asserts the count grows
            // by 1 per PTO firing, not per probe packet.
            val client = newClientWithStartedHandshake()
            drainOutbound(client, nowMillis = 1L)
            assertEquals(0, client.consecutivePtoCount, "fresh client starts at 0")

            // PTO #1 — full simulation including budget consumption.
            handlePtoFired(client)
            assertEquals(
                1,
                client.consecutivePtoCount,
                "handlePtoFired alone must take count from 0 to 1",
            )
            // First probe drained.
            drainOutbound(client, nowMillis = 2L)
            client.pendingProbePackets--
            // Send loop's between-probe re-requeue. Pre-fix this
            // bumped the count to 2 inside the same PTO firing.
            requeueInflightForProbe(client)
            assertEquals(
                1,
                client.consecutivePtoCount,
                "between-probe re-requeue must NOT bump the count — same PTO firing",
            )
            // Second probe drained.
            drainOutbound(client, nowMillis = 3L)
            client.pendingProbePackets--

            // PTO #2 — count must advance to 2, not 4.
            handlePtoFired(client)
            assertEquals(2, client.consecutivePtoCount, "PTO #2 takes count from 1 to 2")
            drainOutbound(client, nowMillis = 4L)
            client.pendingProbePackets--
            requeueInflightForProbe(client)
            assertEquals(2, client.consecutivePtoCount)
            drainOutbound(client, nowMillis = 5L)
            client.pendingProbePackets--

            // PTO #3 — count → 3, not 6 (capped).
            handlePtoFired(client)
            assertEquals(3, client.consecutivePtoCount, "PTO #3 takes count from 2 to 3")
        }

    /**
     * Decode an Initial-level long-header packet from a freshly-drained
     * datagram and return its frames. We open with the client's own
     * SEND protection (client-side keys) — symmetric AEAD opens with
     * the same key/iv that sealed it.
     */
    private fun decodeInitialFrames(
        datagram: ByteArray,
        client: QuicConnection,
    ): List<Frame> {
        val send = client.initial.sendProtection!!
        val parsed =
            LongHeaderPacket.parseAndDecrypt(
                bytes = datagram,
                offset = 0,
                aead = send.aead,
                key = send.key,
                iv = send.iv,
                hp = send.hp,
                hpKey = send.hpKey,
                largestReceivedInSpace = -1L,
            )
        assertNotNull(parsed, "must parse the Initial packet header (datagram size=${datagram.size})")
        return com.vitorpamplona.quic.frame
            .decodeFrames(parsed.packet.payload)
    }

    private fun newClientWithStartedHandshake(): QuicConnection =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator =
                        com.vitorpamplona.quic.tls
                            .PermissiveCertificateValidator(),
                )
            client.start()
            client
        }
}
