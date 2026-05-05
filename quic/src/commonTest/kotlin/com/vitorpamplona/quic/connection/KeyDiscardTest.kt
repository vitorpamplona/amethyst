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

import com.vitorpamplona.quic.frame.HandshakeDoneFrame
import com.vitorpamplona.quic.frame.PingFrame
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RFC 9001 §4.9: Initial / Handshake keys MUST be discarded once the
 * level is no longer needed. Pre-fix `:quic` held both indefinitely,
 * causing a per-session memory leak (AEAD cipher state + handshake
 * CRYPTO buffers) for the lifetime of long connections.
 *
 *   - §4.9.1 client side: discard Initial keys when the client first
 *     sends a Handshake packet. Hooked from
 *     [QuicConnectionWriter.drainOutbound] after a Handshake packet is
 *     built into the outbound datagram.
 *   - §4.9.2 + §4.1.2 client side: handshake confirmation = receipt of
 *     HANDSHAKE_DONE; discard Handshake keys at that point. Hooked from
 *     [QuicConnectionParser]'s HandshakeDoneFrame branch.
 */
class KeyDiscardTest {
    private fun newConnectedClient(): Pair<QuicConnection, InMemoryQuicPipe> {
        val client =
            QuicConnection(
                serverName = "example.test",
                config = QuicConnectionConfig(),
                tlsCertificateValidator = PermissiveCertificateValidator(),
            )
        val pipe = InMemoryQuicPipe(client, client.destinationConnectionId.bytes)
        client.start()
        pipe.drive(maxRounds = 16)
        check(client.status == QuicConnection.Status.CONNECTED) {
            "handshake must succeed; status=${client.status}"
        }
        return client to pipe
    }

    @Test
    fun initialKeys_discardedAfterFirstHandshakePacket() {
        val (client, _) = newConnectedClient()
        // After the handshake, drive one more drainOutbound to flush
        // the client's Finished message — pipe.drive returns as soon as
        // status flips to CONNECTED, which happens after feedDatagram
        // processes the server's Finished but BEFORE the client emits
        // its own Finished.
        drainOutbound(client, nowMillis = 0L)
        assertTrue(client.initial.keysDiscarded, "Initial keys must be discarded after first Handshake packet")
        assertNull(client.initial.sendProtection, "Initial sendProtection nulled out")
        assertNull(client.initial.receiveProtection, "Initial receiveProtection nulled out")
        assertTrue(
            client.initial.sentPackets.isEmpty(),
            "Initial sentPackets cleared (no more loss-detection at this level)",
        )
    }

    @Test
    fun handshakeKeys_survivePastHandshakeUntilHandshakeDone() {
        val (client, _) = newConnectedClient()
        // Even though TLS finished, the client hasn't seen HANDSHAKE_DONE
        // yet. Per §4.1.2 the handshake isn't "confirmed" until then; the
        // Handshake keys must still be available for ACK exchange.
        assertFalse(client.handshake.keysDiscarded, "Handshake keys still live before HANDSHAKE_DONE")
        assertNotNull(client.handshake.sendProtection)
        assertNotNull(client.handshake.receiveProtection)
    }

    @Test
    fun handshakeKeys_discardedOnHandshakeDone() {
        val (client, pipe) = newConnectedClient()
        // Inject a HANDSHAKE_DONE at APPLICATION level. Pad with PINGs so
        // the encrypted payload is long enough for header-protection's
        // 16-byte sample window.
        val pings = List(40) { PingFrame }
        val packet = pipe.buildServerApplicationDatagram(listOf(HandshakeDoneFrame()) + pings)!!
        feedDatagram(client, packet, nowMillis = 0L)

        assertTrue(client.handshake.keysDiscarded, "HANDSHAKE_DONE must trigger Handshake key discard")
        assertNull(client.handshake.sendProtection)
        assertNull(client.handshake.receiveProtection)
        assertTrue(client.handshake.sentPackets.isEmpty())
        // Sanity: Application keys live on; the connection still works.
        assertNotNull(client.application.sendProtection, "Application keys untouched")
    }

    @Test
    fun discardKeys_isIdempotent() {
        val (client, _) = newConnectedClient()
        // Initial keys already discarded by the handshake exchange. A
        // second call must not throw and must not mutate any other state.
        client.initial.discardKeys()
        client.initial.discardKeys()
        assertTrue(client.initial.keysDiscarded)
        // Application level was never discarded; calling on a still-live
        // level is also valid (used in real life on connection close
        // for cleanup).
        assertFalse(client.application.keysDiscarded)
    }
}
