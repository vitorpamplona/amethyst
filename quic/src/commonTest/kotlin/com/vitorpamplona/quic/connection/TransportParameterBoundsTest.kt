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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9000 §18.2 transport-parameter bounds checks. These values are
 * advertised by the peer in their EncryptedExtensions
 * `quic_transport_parameters` extension; out-of-range values MUST close
 * the connection with TRANSPORT_PARAMETER_ERROR.
 *
 *  - `max_udp_payload_size` minimum 1200 (the §14 datagram floor).
 *  - `ack_delay_exponent` maximum 20.
 *  - `active_connection_id_limit` minimum 2.
 *
 * Pre-fix the values were decoded into [TransportParameters] but no
 * runtime check enforced the spec ranges — a hostile peer could ship a
 * value of e.g. `ack_delay_exponent = 60` and our parser's
 * `ackDelay << 60` shift would have desynced RTT (the parser still
 * caps the exponent defensively, but the connection should not have
 * been allowed in the first place).
 */
class TransportParameterBoundsTest {
    private fun runHandshake(serverParams: TransportParameters): QuicConnection.Status =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
                )
            val serverScid = ConnectionId.random(8)
            val tlsServer =
                InProcessTlsServer(
                    transportParameters =
                        serverParams
                            .copy(
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
            // Drive enough rounds for ServerHello + EE + handshake completion.
            // Once peer params are applied, `applyPeerTransportParameters`
            // runs the §18.2 bounds checks which (on violation) flip the
            // connection to CLOSED.
            pipe.drive(maxRounds = 16)
            client.status
        }

    private fun baselineLegalParams() =
        TransportParameters(
            initialMaxData = 1L * 1024 * 1024,
            initialMaxStreamDataBidiLocal = 64L * 1024,
            initialMaxStreamDataBidiRemote = 64L * 1024,
            initialMaxStreamDataUni = 64L * 1024,
            initialMaxStreamsBidi = 16,
            initialMaxStreamsUni = 16,
        )

    @Test
    fun max_udp_payload_size_below_1200_closes() {
        val status = runHandshake(baselineLegalParams().copy(maxUdpPayloadSize = 1199L))
        assertEquals(
            QuicConnection.Status.CLOSED,
            status,
            "RFC 9000 §18.2: max_udp_payload_size < 1200 MUST be TRANSPORT_PARAMETER_ERROR",
        )
    }

    @Test
    fun max_udp_payload_size_at_1200_accepted() {
        val status = runHandshake(baselineLegalParams().copy(maxUdpPayloadSize = 1200L))
        assertEquals(QuicConnection.Status.CONNECTED, status)
    }

    @Test
    fun ack_delay_exponent_above_20_closes() {
        val status = runHandshake(baselineLegalParams().copy(ackDelayExponent = 21L))
        assertEquals(
            QuicConnection.Status.CLOSED,
            status,
            "RFC 9000 §18.2: ack_delay_exponent > 20 MUST be TRANSPORT_PARAMETER_ERROR",
        )
    }

    @Test
    fun ack_delay_exponent_at_20_accepted() {
        val status = runHandshake(baselineLegalParams().copy(ackDelayExponent = 20L))
        assertEquals(QuicConnection.Status.CONNECTED, status)
    }

    @Test
    fun active_connection_id_limit_below_2_closes() {
        val status = runHandshake(baselineLegalParams().copy(activeConnectionIdLimit = 1L))
        assertEquals(
            QuicConnection.Status.CLOSED,
            status,
            "RFC 9000 §18.2: active_connection_id_limit < 2 MUST be TRANSPORT_PARAMETER_ERROR",
        )
    }

    @Test
    fun active_connection_id_limit_at_2_accepted() {
        val status = runHandshake(baselineLegalParams().copy(activeConnectionIdLimit = 2L))
        assertEquals(QuicConnection.Status.CONNECTED, status)
    }

    @Test
    fun missing_optional_params_accepted() {
        // No bounds-checked params advertised → fall back to defaults,
        // connection completes normally.
        val status = runHandshake(baselineLegalParams())
        assertEquals(QuicConnection.Status.CONNECTED, status)
    }

    @Test
    fun close_reason_mentions_specific_violated_param() {
        // Capture which bound was reported so future audit rounds can
        // confirm the message contains a usable diagnostic, not just a
        // generic "transport params bad".
        val client =
            runBlocking {
                val client =
                    QuicConnection(
                        serverName = "example.test",
                        config = QuicConnectionConfig(),
                        tlsCertificateValidator = PermissiveCertificateValidator(),
                    )
                val serverScid = ConnectionId.random(8)
                val tlsServer =
                    InProcessTlsServer(
                        transportParameters =
                            baselineLegalParams()
                                .copy(
                                    initialSourceConnectionId = serverScid.bytes,
                                    originalDestinationConnectionId = client.destinationConnectionId.bytes,
                                    ackDelayExponent = 25L,
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
                pipe.drive(maxRounds = 16)
                client
            }
        val reason = client.closeReason
        assertNotNull(reason)
        assertTrue(reason.contains("ack_delay_exponent"), "reason should name the violated param: $reason")
    }
}
