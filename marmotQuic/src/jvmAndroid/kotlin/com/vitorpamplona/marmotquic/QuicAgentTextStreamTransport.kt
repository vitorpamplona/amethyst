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
package com.vitorpamplona.marmotquic

import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRecordV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.AgentTextStreamFraming
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.BrokerControlType
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicAlpn
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.QuicBrokerControlEnvelopeV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.QuicEndpointCandidate
import com.vitorpamplona.quic.connection.QuicConnection
import com.vitorpamplona.quic.connection.QuicConnectionConfig
import com.vitorpamplona.quic.connection.QuicConnectionDriver
import com.vitorpamplona.quic.stream.QuicStream
import com.vitorpamplona.quic.tls.CertificateValidator
import com.vitorpamplona.quic.transport.UdpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `transports/quic.md` on top of the repo's own pure-Kotlin `:quic` stack.
 *
 * Marmot's binding is RAW QUIC, not WebTransport: it negotiates its own ALPN
 * (`marmot.quic_broker.v1` / `marmot.quic_stream.v1`) and writes frames
 * straight onto QUIC streams. So this deliberately does not reuse
 * `nestsClient`'s `WebTransportSession` — that abstraction begins above HTTP/3
 * Extended CONNECT, which this binding has no part of. What it does reuse is
 * everything under that: the QUIC connection, TLS 1.3, ALPN negotiation,
 * stream multiplexing and the UDP socket.
 *
 * One stream per delivery: a publisher's uni stream or a subscriber's bidi
 * stream owns its connection and closes it on [MarmotQuicStream.close]. That
 * is the shape the binding describes — a room is a stream — and it keeps a
 * failed candidate from leaving a connection behind.
 */
class QuicAgentTextStreamTransport(
    private val parentScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /**
     * Preview endpoints and brokers are commonly self-signed, and the binding
     * says so: a client MAY pin by DER or SHA-256 fingerprint through local
     * configuration instead of the system trust store. That choice is the
     * caller's, so the validator is required rather than defaulted — the type
     * system should not let "forgot to decide" compile.
     */
    private val certificateValidator: CertificateValidator,
    private val handshakeTimeoutMillis: Long = 10_000L,
    /**
     * The group's `max_plaintext_frame_len`, when the caller knows it. A
     * receiver that knows the policy must reject a frame above it; without one
     * the broker's blind cap applies.
     */
    private val maxPlaintextFrameLen: Long? = null,
) : MarmotQuicTransport {
    override suspend fun publish(
        candidate: String,
        streamId: ByteArray,
        startEventId: ByteArray,
    ): MarmotQuicStream = open(candidate, streamId, startEventId, BrokerControlType.PUBLISH)

    override suspend fun subscribe(
        candidate: String,
        streamId: ByteArray,
        startEventId: ByteArray,
    ): MarmotQuicStream = open(candidate, streamId, startEventId, BrokerControlType.SUBSCRIBE)

    private suspend fun open(
        candidate: String,
        streamId: ByteArray,
        startEventId: ByteArray,
        role: BrokerControlType,
    ): MarmotQuicStream {
        val endpoint =
            QuicEndpointCandidate.parse(candidate)
                ?: throw MarmotQuicException(MarmotQuicException.Kind.BadCandidate, "unusable quic:// candidate")

        val socket =
            try {
                UdpSocket.connect(endpoint.host, endpoint.port)
            } catch (t: Throwable) {
                throw MarmotQuicException(MarmotQuicException.Kind.HandshakeFailed, "cannot reach the candidate", t)
            }

        val connection =
            QuicConnection(
                // An IP literal is matched against an iPAddress SAN and never
                // sent as SNI; `serverName` is only meaningful for a DNS name.
                serverName = endpoint.serverNameIndication ?: endpoint.host,
                config = QuicConnectionConfig(),
                tlsCertificateValidator = certificateValidator,
                alpnList = listOf(MarmotQuicAlpn.BROKER),
            )
        val driver = QuicConnectionDriver(connection, socket, parentScope)
        driver.start()

        try {
            val completed =
                withTimeoutOrNull(handshakeTimeoutMillis) {
                    connection.awaitHandshake()
                    true
                }
            if (completed == null || connection.status != QuicConnection.Status.CONNECTED) {
                throw MarmotQuicException(
                    MarmotQuicException.Kind.HandshakeFailed,
                    "QUIC handshake did not complete (status=${connection.status})",
                )
            }
            // An endpoint that did not take our ALPN is not a Marmot endpoint,
            // whatever else it may be. Fail here so the caller moves to the
            // next candidate rather than waiting on records that never come.
            val alpn = connection.tls.negotiatedAlpn
            if (alpn == null || !alpn.contentEquals(MarmotQuicAlpn.BROKER)) {
                throw MarmotQuicException(
                    MarmotQuicException.Kind.AlpnRejected,
                    "endpoint negotiated ${alpn?.decodeToString()} instead of ${MarmotQuicAlpn.BROKER.decodeToString()}",
                )
            }

            // Stream direction IS the role: a publisher claims the room on a
            // uni stream, a subscriber needs the return direction of a bidi
            // one. A broker rejects the wrong pairing.
            val stream =
                when (role) {
                    BrokerControlType.PUBLISH -> connection.openUniStream()
                    BrokerControlType.SUBSCRIBE -> connection.openBidiStream()
                }

            // The control envelope is the first frame, framed exactly like a
            // record frame — length-prefixed the same way, so a broker reads
            // both with one framer.
            stream.send.enqueue(frameEnvelope(QuicBrokerControlEnvelopeV1(role, streamId, startEventId)))
            driver.wakeup()

            return QuicStreamDelivery(stream, driver, maxPlaintextFrameLen)
        } catch (t: Throwable) {
            driver.close()
            throw if (t is MarmotQuicException) t else MarmotQuicException(MarmotQuicException.Kind.PeerClosed, "${t.message}", t)
        }
    }

    private fun frameEnvelope(envelope: QuicBrokerControlEnvelopeV1): ByteArray {
        val encoded = envelope.encode()
        return byteArrayOf(
            ((encoded.size shr 24) and 0xff).toByte(),
            ((encoded.size shr 16) and 0xff).toByte(),
            ((encoded.size shr 8) and 0xff).toByte(),
            (encoded.size and 0xff).toByte(),
        ) + encoded
    }
}

/** One QUIC stream carrying framed records, plus the connection it rides on. */
private class QuicStreamDelivery(
    private val stream: QuicStream,
    private val driver: QuicConnectionDriver,
    maxPlaintextFrameLen: Long?,
) : MarmotQuicStream {
    private val reader = AgentTextStreamFraming.Reader(maxPlaintextFrameLen)

    override suspend fun send(record: AgentTextStreamRecordV1) {
        stream.send.enqueue(AgentTextStreamFraming.frame(record))
        driver.wakeup()
    }

    override fun incoming(): Flow<AgentTextStreamRecordV1> =
        flow {
            stream.incoming.collect { chunk ->
                for (record in reader.push(chunk)) emit(record)
            }
        }

    override suspend fun finish() {
        stream.send.finish()
        driver.wakeup()
    }

    override suspend fun close() {
        driver.close()
    }
}
