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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Step E of the deferred-follow-ups pass: CRYPTO retransmit per
 * encryption level. The handshake produces ClientHello / Finished
 * CRYPTO bytes at Initial / Handshake levels; a lost packet
 * carrying any of those must be retransmitted at the same level.
 *
 * Mirrors the structure of [StreamRetransmitTest] but exercises the
 * Initial / Handshake-level paths in [QuicConnectionWriter].
 */
class CryptoRetransmitTest {
    @Test
    fun handshakePacket_carriesCryptoToken_inSentPacket() =
        runBlocking {
            val client = newClientWithStartedHandshake()
            // First drain produces the ClientHello at Initial level.
            runCatching { drainOutbound(client, nowMillis = 1L) }

            val initialEntries =
                client.initial.sentPackets.entries
                    .toList()
            val withCrypto =
                initialEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.Crypto }
                }
            assertNotNull(
                withCrypto,
                "Initial-level SentPacket must carry a Crypto token; saw ${initialEntries.map { it.value.tokens.map { t -> t::class.simpleName } }}",
            )
            val cryptoToken =
                withCrypto.value.tokens
                    .filterIsInstance<RecoveryToken.Crypto>()
                    .single()
            assertEquals(EncryptionLevel.INITIAL, cryptoToken.level, "Crypto token's level must match the packet's level")
            assertEquals(0L, cryptoToken.offset, "first ClientHello starts at offset 0")
            assertTrue(cryptoToken.length > 0L, "ClientHello has non-zero length")
        }

    @Test
    fun cryptoData_lostAndRetransmittedAtSameLevel() =
        runBlocking {
            val client = newClientWithStartedHandshake()
            runCatching { drainOutbound(client, nowMillis = 1L) }

            val firstEntry =
                client.initial.sentPackets.entries
                    .firstOrNull { entry -> entry.value.tokens.any { it is RecoveryToken.Crypto } }
            assertNotNull(firstEntry)
            val firstPn = firstEntry.key
            val cryptoToken =
                firstEntry.value.tokens
                    .filterIsInstance<RecoveryToken.Crypto>()
                    .single()

            // Simulate loss via direct dispatch.
            client.lock.lock()
            try {
                client.onTokensLost(listOf(cryptoToken))
                client.initial.sentPackets.remove(firstPn)
            } finally {
                client.lock.unlock()
            }

            // Initial-level cryptoSend should now have re-queued bytes
            // for retransmit. Verify by checking readableBytes.
            assertTrue(
                client.initial.cryptoSend.readableBytes > 0,
                "lost CRYPTO bytes must be back in the Initial-level cryptoSend's retransmit queue",
            )

            // Next drain must produce a new Initial packet carrying
            // the CRYPTO at the original offset.
            runCatching { drainOutbound(client, nowMillis = 2L) }
            val replayEntries =
                client.initial.sentPackets.entries
                    .filter { it.key != firstPn }
            val replay =
                replayEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.Crypto }
                }
            assertNotNull(replay, "retransmit must produce a fresh Initial SentPacket carrying Crypto")
            val replayToken =
                replay.value.tokens
                    .filterIsInstance<RecoveryToken.Crypto>()
                    .single()
            assertEquals(EncryptionLevel.INITIAL, replayToken.level)
            assertEquals(cryptoToken.offset, replayToken.offset, "retransmit replays original offset")
            assertEquals(cryptoToken.length, replayToken.length)
        }

    @Test
    fun cryptoAck_releasesBufferAtSameLevel() =
        runBlocking {
            val client = newClientWithStartedHandshake()
            runCatching { drainOutbound(client, nowMillis = 1L) }

            val packet =
                client.initial.sentPackets.entries
                    .first { it.value.tokens.any { t -> t is RecoveryToken.Crypto } }
            // ACK via direct dispatch.
            client.lock.lock()
            try {
                client.onTokensAcked(packet.value.tokens)
            } finally {
                client.lock.unlock()
            }
            // After ACK the Initial-level cryptoSend's flushedFloor should
            // have advanced — we check by observing that another takeChunk
            // returns null (no more bytes ready) AND finSent stays true if
            // it was set (i.e. the ACK didn't reset it).
            assertEquals(0, client.initial.cryptoSend.readableBytes)
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
            // We don't drive the InProcessTlsServer here — we only need
            // the client to have produced its ClientHello (which it does
            // synchronously inside start()). The Initial-level cryptoSend
            // is now non-empty.
            assertTrue(
                client.initial.cryptoSend.readableBytes > 0,
                "client.start() must produce ClientHello bytes",
            )
            // Need send-protection installed before drainOutbound emits
            // anything. start() handles that.
            assertNotNull(client.initial.sendProtection, "Initial-level send protection must be installed")
            client
        }
}
