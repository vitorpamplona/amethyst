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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke test for the in-memory client+server QUIC pipe, modeled on
 * Cloudflare quiche's `Pipe` pattern. Drives a real [QuicConnection]
 * through the full handshake (Initial → Handshake → 1-RTT) without
 * touching the network, then verifies the connection reaches
 * [QuicConnection.Status.CONNECTED] and that application keys are
 * installed in both directions.
 *
 * This complements [com.vitorpamplona.quic.tls.TlsRoundTripTest] (which
 * exercises only the TLS layer) by routing all CRYPTO bytes through the
 * full QUIC packet protection path.
 */
class InMemoryQuicPipeTest {
    @Test
    fun client_connection_reaches_connected_via_in_memory_pipe() {
        val client =
            QuicConnection(
                serverName = "example.test",
                config = QuicConnectionConfig(),
                tlsCertificateValidator = null,
            )
        val pipe = InMemoryQuicPipe(client = client, initialDcid = client.destinationConnectionId.bytes)

        // The connection auto-installs Initial keys on construction; start the
        // handshake by emitting the ClientHello and driving the pipe.
        client.start()
        pipe.drive(maxRounds = 16)

        assertEquals(
            QuicConnection.Status.CONNECTED,
            client.status,
            "client should reach CONNECTED after pipe handshake",
        )
        assertTrue(client.handshakeComplete, "handshakeComplete must be flipped")
        assertNotNull(client.application.sendProtection, "1-RTT send keys must be installed")
        assertNotNull(client.application.receiveProtection, "1-RTT receive keys must be installed")
    }
}
