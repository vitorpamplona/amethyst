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
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicException
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicStream
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicTransport
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
import kotlinx.coroutines.delay
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
 * One stream per delivery: a publisher's uni stream, a direct sender's uni
 * stream, or a subscriber's bidi stream owns its connection and closes it on
 * [MarmotQuicStream.close]. That
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

    /**
     * The direct path: dial the receiver, open one uni stream, write records.
     *
     * Two things separate it from [publish] beyond the ALPN. There is no
     * control envelope — the dialed endpoint is already the one receiver, so
     * there is no room to name, and the first bytes on the stream are a record
     * frame. And [startEventId] never leaves this process: it is validated for
     * shape so a caller cannot pass a placeholder that would later disagree
     * with the record key and transcript hash it is bound into, but nothing is
     * written for it. A direct endpoint learns it only if the out-of-band
     * setup supplied it separately.
     *
     * Only the SENDER half lives here. The receiver half has to listen, and
     * `:quic` is a client stack with no server role — so a direct-path
     * receiver is not something this module can offer yet.
     */
    override suspend fun sendDirect(
        candidate: String,
        streamId: ByteArray,
        startEventId: ByteArray,
    ): MarmotQuicStream = open(candidate, streamId, startEventId, role = null)

    private suspend fun open(
        candidate: String,
        streamId: ByteArray,
        startEventId: ByteArray,
        /** The broker role to claim, or null for the envelope-less direct path. */
        role: BrokerControlType?,
    ): MarmotQuicStream {
        val endpoint =
            QuicEndpointCandidate.parse(candidate)
                ?: throw MarmotQuicException(MarmotQuicException.Kind.BadCandidate, "unusable quic:// candidate")

        val alpn = if (role == null) MarmotQuicAlpn.DIRECT else MarmotQuicAlpn.BROKER
        if (role == null) {
            // Same bounds the broker envelope enforces, applied even though
            // nothing is encoded: a stream id or start event id this layer
            // would refuse to route is one the record key and transcript hash
            // should not be built on either.
            require(streamId.size in 1..QuicBrokerControlEnvelopeV1.MAX_ID_LEN) {
                "direct stream_id must be 1..${QuicBrokerControlEnvelopeV1.MAX_ID_LEN} bytes"
            }
            require(startEventId.size in 1..QuicBrokerControlEnvelopeV1.MAX_ID_LEN) {
                "direct start_event_id must be 1..${QuicBrokerControlEnvelopeV1.MAX_ID_LEN} bytes"
            }
        }

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
                alpnList = listOf(alpn),
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
            val negotiated = connection.tls.negotiatedAlpn
            if (negotiated == null || !negotiated.contentEquals(alpn)) {
                throw MarmotQuicException(
                    MarmotQuicException.Kind.AlpnRejected,
                    "endpoint negotiated ${negotiated?.decodeToString()} instead of ${alpn.decodeToString()}",
                )
            }

            // Stream direction IS the role: a publisher claims the room on a
            // uni stream, a subscriber needs the return direction of a bidi
            // one. A broker rejects the wrong pairing.
            val stream =
                when (role) {
                    BrokerControlType.PUBLISH, null -> connection.openUniStream()
                    BrokerControlType.SUBSCRIBE -> connection.openBidiStream()
                }

            if (role != null) {
                // The control envelope is the first frame, framed exactly like
                // a record frame — length-prefixed the same way, so a broker
                // reads both with one framer. The direct path writes none: its
                // stream opens straight into records.
                stream.send.enqueue(frameEnvelope(QuicBrokerControlEnvelopeV1(role, streamId, startEventId)))
                driver.wakeup()
            }

            return QuicStreamDelivery(stream, driver, maxPlaintextFrameLen)
        } catch (t: Throwable) {
            driver.close()
            if (t is MarmotQuicException) throw t
            // A connection that never reached CONNECTED did not fail as a
            // peer closing on us mid-stream — it failed to be established at
            // all, and that is a different decision for a caller walking its
            // candidate list. Certificate rejection lands here: our own
            // validator refuses, we send a TLS alert, and the connection
            // closes before the handshake ever completes.
            val kind =
                if (connection.status == QuicConnection.Status.CONNECTED) {
                    MarmotQuicException.Kind.PeerClosed
                } else {
                    MarmotQuicException.Kind.HandshakeFailed
                }
            throw MarmotQuicException(kind, "${t.message}", t)
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
    private val flushTimeoutMillis: Long = DEFAULT_FLUSH_TIMEOUT_MILLIS,
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

    /**
     * FIN our write side and wait until the peer acknowledges it.
     *
     * The wait is the point. `enqueue` only puts bytes in the send buffer; the
     * driver still has to put them on the wire and the peer still has to ACK
     * them. Returning before that and letting the caller [close] tears the
     * connection down with records still buffered, and they are simply lost —
     * silently, because the publisher already counted them. QUIC only ACKs a
     * FIN once everything ahead of it arrived, so `finAcked` is exactly the
     * "the broker has all of it" signal.
     */
    override suspend fun finish() {
        stream.send.finish()
        driver.wakeup()
        withTimeoutOrNull(flushTimeoutMillis) {
            while (!stream.send.finAcked) {
                driver.wakeup()
                delay(FLUSH_POLL_MILLIS)
            }
        }
    }

    /**
     * Tear down the connection. A caller that wrote records is expected to
     * [finish] first; this still gives an unacknowledged FIN a bounded moment
     * rather than dropping the tail of a stream on the floor.
     */
    override suspend fun close() {
        if (stream.send.finSent && !stream.send.finAcked) {
            withTimeoutOrNull(flushTimeoutMillis) {
                while (!stream.send.finAcked) {
                    driver.wakeup()
                    delay(FLUSH_POLL_MILLIS)
                }
            }
        }
        driver.close()
    }
}

private const val DEFAULT_FLUSH_TIMEOUT_MILLIS = 10_000L
private const val FLUSH_POLL_MILLIS = 20L
