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
