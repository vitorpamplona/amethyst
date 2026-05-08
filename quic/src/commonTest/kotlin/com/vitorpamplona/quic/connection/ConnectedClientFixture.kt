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

import com.vitorpamplona.quic.tls.InProcessTlsServer
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals

/**
 * Stand up a fresh [QuicConnection] wired through an
 * [InMemoryQuicPipe] and drive the handshake to CONNECTED. Most of
 * the audio-rooms tests start from this exact shape — extracted
 * here to keep each test file focused on its own assertions
 * rather than ~40 lines of identical fixture boilerplate.
 *
 * The transport-parameter knobs cover the only variation real tests
 * need:
 *   - moq-lite-shaped tests (many peer-uni streams, large data
 *     window): pass `maxStreamsUni = 65_536`, `maxData = 16 MiB`.
 *   - single-stream / control-frame tests (defaults are plenty).
 *
 * Both client and server advertise the same caps so flow-control
 * regressions surface as caps-mismatch failures rather than tests
 * passing accidentally because ONE side was generous.
 */
fun newConnectedClient(
    serverName: String = "example.test",
    maxStreamsBidi: Long = 16,
    maxStreamsUni: Long = 16,
    maxData: Long = 1L * 1024 * 1024,
    maxStreamData: Long = 64L * 1024,
    handshakeRounds: Int = 16,
): Pair<QuicConnection, InMemoryQuicPipe> =
    runBlocking {
        val client =
            QuicConnection(
                serverName = serverName,
                config =
                    QuicConnectionConfig(
                        initialMaxStreamsBidi = maxStreamsBidi,
                        initialMaxStreamsUni = maxStreamsUni,
                        initialMaxData = maxData,
                        initialMaxStreamDataBidiLocal = maxStreamData,
                        initialMaxStreamDataBidiRemote = maxStreamData,
                        initialMaxStreamDataUni = maxStreamData,
                    ),
                tlsCertificateValidator = PermissiveCertificateValidator(),
            )
        val serverScid = ConnectionId.random(8)
        val tlsServer =
            InProcessTlsServer(
                transportParameters =
                    TransportParameters(
                        initialMaxData = maxData,
                        initialMaxStreamDataBidiLocal = maxStreamData,
                        initialMaxStreamDataBidiRemote = maxStreamData,
                        initialMaxStreamDataUni = maxStreamData,
                        initialMaxStreamsBidi = maxStreamsBidi,
                        initialMaxStreamsUni = maxStreamsUni,
                        initialSourceConnectionId = serverScid.bytes,
                        originalDestinationConnectionId = client.destinationConnectionId.bytes,
                    ).encode(),
            )
        val pipe =
            InMemoryQuicPipe(
                client = client,
                initialDcid = client.destinationConnectionId.bytes,
                serverScid = serverScid,
                tlsServer = tlsServer,
            )
        client.start()
        pipe.drive(maxRounds = handshakeRounds)
        assertEquals(QuicConnection.Status.CONNECTED, client.status)
        client to pipe
    }
