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
package com.vitorpamplona.quic.observability

import com.vitorpamplona.quic.connection.ConnectionId
import com.vitorpamplona.quic.connection.EncryptionLevel
import com.vitorpamplona.quic.connection.InMemoryQuicPipe
import com.vitorpamplona.quic.connection.QuicConnection
import com.vitorpamplona.quic.connection.QuicConnectionConfig
import com.vitorpamplona.quic.connection.TransportParameters
import com.vitorpamplona.quic.connection.feedDatagram
import com.vitorpamplona.quic.tls.InProcessTlsServer
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end smoke for [QlogObserver]:
 *
 *  1. NoOp safety — a connection driven through start → handshake →
 *     close with [QlogObserver.NoOp] must complete without throwing.
 *  2. RecordingQlogObserver captures the expected events (start,
 *     local transport_params, packet_sent for ClientHello, close).
 *  3. Malformed inbound datagram surfaces a `packet_dropped` event.
 *
 * The recorded-event list documented in the implementation report
 * comes from the second test below.
 */
class QlogObserverTest {
    @Test
    fun noOpObserver_handshakeAndCloseDoNotThrow(): Unit =
        runBlocking {
            val client = handshakedClient(QlogObserver.NoOp)
            // Drive a tiny operation post-handshake.
            client.lock.withLock {
                // No-op — just confirm we can still take the lock.
                assertEquals(QuicConnection.Status.CONNECTED, client.status)
            }
            client.close(0L, "done")
            // close() flips status to CLOSING; the writer's next
            // drainOutbound emits CONNECTION_CLOSE and transitions to
            // CLOSED. We don't run the driver here, so stop at CLOSING
            // and assert that — the point of this test is just that
            // the no-op observer doesn't throw on any call site.
            assertTrue(
                client.status == QuicConnection.Status.CLOSING ||
                    client.status == QuicConnection.Status.CLOSED,
                "expected CLOSING or CLOSED, was ${client.status}",
            )
        }

    @Test
    fun recordingObserver_capturesStartParametersSentAndClose(): Unit =
        runBlocking {
            val recorder = RecordingQlogObserver()
            val client = handshakedClient(recorder)
            client.close(0L, "shutdown")

            val names = recorder.events.map { it.name }
            assertTrue(
                names.contains("connectionStarted"),
                "expected connectionStarted in $names",
            )
            // Local transport parameters set at start.
            val localTps =
                recorder.events
                    .filter { it.name == "transportParametersSet" }
                    .map { it.payload["initiator"] }
            assertTrue(
                localTps.contains("local"),
                "expected local transportParametersSet in $localTps",
            )
            // At least one packet_sent for the ClientHello at INITIAL level.
            val initialSends =
                recorder.events.filter {
                    it.name == "packetSent" && it.payload["level"] == EncryptionLevel.INITIAL
                }
            assertTrue(
                initialSends.isNotEmpty(),
                "expected at least one INITIAL-level packetSent (ClientHello) " +
                    "but saw ${recorder.events.map { it.name to it.payload["level"] }}",
            )
            assertTrue(
                names.contains("connectionClosed"),
                "expected connectionClosed in $names",
            )
        }

    @Test
    fun malformedDatagram_recordsPacketDropped(): Unit =
        runBlocking {
            val recorder = RecordingQlogObserver()
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
                    qlogObserver = recorder,
                )
            client.start()
            // Long-header packet bytes that look parseable enough to peek
            // but won't decrypt — the receive keys for HANDSHAKE/APPLICATION
            // aren't installed yet, so feedDatagram drops with "no receive
            // keys at level …". 0xC0 = long header, INITIAL with the
            // smallest valid layout: 0xC0 | version(0x00000001) | dcil=0
            // | scil=0 | token_len=0 | length=2 | pn=0x00 | one byte of
            // garbage. AEAD-decrypt will fail on this Initial too, since
            // the keys are derived from a different DCID.
            val garbage =
                byteArrayOf(
                    0xC0.toByte(), // long header initial
                    0x00,
                    0x00,
                    0x00,
                    0x01, // version v1
                    0x00, // dcil = 0
                    0x00, // scil = 0
                    0x00, // token length varint = 0
                    0x02, // length = 2
                    0x00, // pn byte
                    0x00, // payload byte (will fail AEAD)
                )
            client.lock.withLock {
                feedDatagram(client, garbage, nowMillis = 1L)
            }
            val drops = recorder.events.filter { it.name == "packetDropped" }
            assertTrue(
                drops.isNotEmpty(),
                "expected at least one packetDropped event but saw ${recorder.events.map { it.name }}",
            )
        }

    private fun handshakedClient(observer: QlogObserver): QuicConnection =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
                    qlogObserver = observer,
                )
            val serverScid = ConnectionId.random(8)
            val tlsServer =
                InProcessTlsServer(
                    transportParameters =
                        TransportParameters(
                            initialMaxData = 1_000_000,
                            initialMaxStreamDataBidiLocal = 100_000,
                            initialMaxStreamDataBidiRemote = 100_000,
                            initialMaxStreamDataUni = 100_000,
                            initialMaxStreamsBidi = 100,
                            initialMaxStreamsUni = 100,
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
            pipe.drive(maxRounds = 16)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
            client
        }
}

/**
 * Test-only [QlogObserver] that appends every callback into a list,
 * with a tiny serialized-payload shape so assertions read like
 * structured pattern-matches rather than a soup of positional args.
 */
internal class RecordingQlogObserver : QlogObserver {
    data class Event(
        val name: String,
        val payload: Map<String, Any?>,
    )

    val events: MutableList<Event> = mutableListOf()

    private fun add(
        name: String,
        payload: Map<String, Any?>,
    ) {
        events += Event(name, payload)
    }

    override fun onConnectionStarted(
        serverName: String,
        dcid: ByteArray,
        scid: ByteArray,
    ) = add(
        "connectionStarted",
        mapOf("serverName" to serverName, "dcid_size" to dcid.size, "scid_size" to scid.size),
    )

    override fun onConnectionClosed(
        initiator: String,
        errorCode: Long,
        reason: String,
    ) = add(
        "connectionClosed",
        mapOf("initiator" to initiator, "errorCode" to errorCode, "reason" to reason),
    )

    override fun onPacketSent(
        level: EncryptionLevel,
        packetNumber: Long,
        sizeBytes: Int,
        frames: List<String>,
    ) = add(
        "packetSent",
        mapOf("level" to level, "pn" to packetNumber, "size" to sizeBytes, "frames" to frames),
    )

    override fun onPacketReceived(
        level: EncryptionLevel,
        packetNumber: Long,
        sizeBytes: Int,
        frames: List<String>,
    ) = add(
        "packetReceived",
        mapOf("level" to level, "pn" to packetNumber, "size" to sizeBytes, "frames" to frames),
    )

    override fun onPacketDropped(
        reason: String,
        sizeBytes: Int,
    ) = add("packetDropped", mapOf("reason" to reason, "size" to sizeBytes))

    override fun onKeyUpdated(
        keyType: String,
        level: EncryptionLevel,
    ) = add("keyUpdated", mapOf("keyType" to keyType, "level" to level))

    override fun onLossDetected(
        level: EncryptionLevel,
        lostPacketNumbers: List<Long>,
    ) = add("lossDetected", mapOf("level" to level, "lost" to lostPacketNumbers))

    override fun onPtoFired(
        consecutivePtoCount: Int,
        ptoMillis: Long,
    ) = add("ptoFired", mapOf("count" to consecutivePtoCount, "ptoMillis" to ptoMillis))

    override fun onCongestionStateUpdated(newState: String) = add("congestionStateUpdated", mapOf("newState" to newState))

    override fun onTransportParametersSet(
        initiator: String,
        params: Map<String, String>,
    ) = add("transportParametersSet", mapOf("initiator" to initiator, "params" to params))

    override fun onAlpnNegotiated(alpn: String) = add("alpnNegotiated", mapOf("alpn" to alpn))

    override fun onVersionInformation(
        chosenVersion: String,
        otherVersionsOffered: List<String>,
    ) = add(
        "versionInformation",
        mapOf("chosen" to chosenVersion, "offered" to otherVersionsOffered),
    )
}
