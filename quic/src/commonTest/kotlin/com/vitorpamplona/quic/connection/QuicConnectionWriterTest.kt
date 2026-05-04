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

import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Coverage for [QuicConnectionWriter] paths that the survey flagged as
 * untested:
 *
 *  * `appendFlowControlUpdates` MAX_STREAM_DATA emission once consumption
 *    crosses half-window — pre-fix the writer never emitted these on its own,
 *    leaving peer credit pinned at the initial value.
 *  * `buildBestLevelPacket` CLOSING-status branch — the only path that
 *    emits CONNECTION_CLOSE; pre-fix nothing exercised it.
 *  * Connection-level send-credit enforcement (audit-4 #9) — the writer
 *    must skip stream chunks once `sendConnectionFlowConsumed` reaches the
 *    cap.
 */
class QuicConnectionWriterTest {
    private fun connectedClient(config: QuicConnectionConfig = QuicConnectionConfig()): Pair<QuicConnection, InMemoryQuicPipe> {
        val client =
            QuicConnection(
                serverName = "example.test",
                config = config,
                tlsCertificateValidator = PermissiveCertificateValidator(),
            )
        val pipe = InMemoryQuicPipe(client, client.destinationConnectionId.bytes)
        client.start()
        pipe.drive(maxRounds = 16)
        check(client.status == QuicConnection.Status.CONNECTED)
        return client to pipe
    }

    @Test
    fun draining_in_closing_status_emits_connection_close() {
        // Audit-4 #15 + survey HIGH-#4: drainOutbound's CLOSING branch was
        // never asserted. After connection.close() the next drain MUST
        // produce a packet (the CONNECTION_CLOSE), and the connection's
        // status must be CLOSING. We assert the side-effect rather than
        // re-decrypting because peer-side keys aren't reachable here.
        runBlocking {
            val (client, _) = connectedClient()
            client.close(errorCode = 7, reason = "buh bye")
            assertEquals(QuicConnection.Status.CLOSING, client.status)
            val packet = drainOutbound(client, nowMillis = 0L)
            assertNotNull(packet, "CLOSING-status drain must produce a CONNECTION_CLOSE packet")
            // The packet exists; details are exercised end-to-end by interop tests.
        }
    }

    @Test
    fun max_stream_data_emitted_after_consumer_drains_half_window() {
        // Open a peer-initiated stream by faking a STREAM frame from server,
        // then drain the consumer. The writer's appendFlowControlUpdates
        // should issue a MAX_STREAM_DATA frame raising the limit once
        // received >= half the window. We assert via stream.receiveLimit
        // (which the writer bumps in-place) rather than re-decrypting the
        // packet — the side-effect on connection state is the contract.
        runBlocking {
            val (client, pipe) =
                connectedClient(
                    QuicConnectionConfig(
                        initialMaxStreamDataBidiRemote = 64,
                        initialMaxStreamDataBidiLocal = 64,
                    ),
                )
            val streamId = 1L
            val data = ByteArray(40) { it.toByte() }
            val packet = pipe.buildServerApplicationDatagram(listOf(StreamFrame(streamId, 0L, data, false)))!!
            feedDatagram(client, packet, nowMillis = 0L)

            val stream = client.streamById(streamId)!!
            val initialLimit = stream.receiveLimit
            // Drain the data so contiguousEnd advances past half-window.
            kotlinx.coroutines.withTimeoutOrNull(2_000L) {
                stream.incoming.collect { /* consume */ }
            }
            // Drain outbound — writer should bump stream.receiveLimit upward
            // as part of appendFlowControlUpdates (and emit MAX_STREAM_DATA).
            assertNotNull(drainOutbound(client, nowMillis = 0L))
            assertTrue(
                stream.receiveLimit > initialLimit,
                "appendFlowControlUpdates must raise stream.receiveLimit; was $initialLimit, now ${stream.receiveLimit}",
            )
        }
    }

    @Test
    fun writer_respects_connection_level_send_credit_cap() {
        // Audit-4 #9: pre-fix the writer ignored sendConnectionFlowCredit
        // and would happily send past the peer's initial_max_data, ending
        // in FLOW_CONTROL_ERROR. Post-fix, once sendConnectionFlowConsumed
        // reaches the cap the writer skips the stream entirely.
        runBlocking {
            val (client, _) = connectedClient()
            client.sendConnectionFlowCredit = 100L
            client.sendConnectionFlowConsumed = 0L

            val stream = client.openBidiStream()
            stream.send.enqueue(ByteArray(500))
            // Drain repeatedly until output stalls. The internal counter
            // sendConnectionFlowConsumed is the contract — it must equal
            // the cap and never exceed it.
            for (round in 0 until 20) {
                drainOutbound(client, nowMillis = 0L) ?: break
            }
            assertEquals(
                100L,
                client.sendConnectionFlowConsumed,
                "writer must emit exactly sendConnectionFlowCredit bytes, no more",
            )
        }
    }
}
