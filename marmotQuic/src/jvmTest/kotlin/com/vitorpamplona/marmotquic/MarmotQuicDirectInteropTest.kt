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

import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamCrypto
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamKeyContextV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamPublisher
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRecordV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.InMemoryAgentTextStreamSequenceStore
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicException
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import com.vitorpamplona.quic.tls.PinnedCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import java.io.File
import java.net.DatagramSocket
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Drives our direct-path sender against MDK's own direct-path receiver
 * (`wn stream receive`), the reference implementation of the listening half.
 *
 * The direct path inverts the broker path's connection direction — the
 * RECEIVER listens and the SENDER dials — and drops the control envelope
 * entirely, so the very first bytes on the stream are a record frame. Both of
 * those are exactly the kind of thing an implementation happily agrees with
 * itself about: a self-test would pass with an envelope still on the wire, or
 * with the wrong ALPN, as long as both ends made the same mistake. Only the
 * reference receiver can say otherwise.
 *
 * `:quic` is a client stack with no server role, so we can only drive the
 * sender half here. That is also the half the spec makes usable in v1: there
 * is no start-payload candidate by which a direct receiver advertises its own
 * endpoint, so the sender always has the address from somewhere else.
 *
 * Opt in with `-DmarmotWn=/path/to/wn` (MDK's CLI, `cargo build --release
 * --bin wn`). Without it the cases skip, so an ordinary `./gradlew test`
 * never needs the reference implementation on the machine.
 */
class MarmotQuicDirectInteropTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val wnPath: String? = System.getProperty("marmotWn")

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun requireWn(): File {
        val file = wnPath?.let { File(it) }
        Assume.assumeTrue(
            "set -DmarmotWn=/path/to/wn (MDK's CLI) to run the direct-path interop cases",
            file != null && file.canExecute(),
        )
        return file!!
    }

    /** A UDP port nothing is listening on right now. */
    private fun freeUdpPort(): Int = DatagramSocket(0).use { it.localPort }

    /**
     * One run of `wn stream receive`: it binds, waits for a single direct
     * stream, and prints its JSON result when the stream finishes.
     */
    private class ReferenceReceiver(
        val process: Process,
        val port: Int,
    ) {
        fun awaitResult(timeoutSeconds: Long): String {
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            val out = process.inputStream.readBytes().decodeToString()
            val err = process.errorStream.readBytes().decodeToString()
            if (!finished) {
                process.destroyForcibly()
                throw AssertionError("the reference receiver never finished. stdout=$out stderr=$err")
            }
            return out.ifBlank { throw AssertionError("the reference receiver printed nothing. stderr=$err") }
        }
    }

    private fun startReceiver(
        wn: File,
        startEventId: ByteArray,
    ): ReferenceReceiver {
        val port = freeUdpPort()
        val process =
            ProcessBuilder(
                wn.absolutePath,
                "--json",
                "stream",
                "receive",
                "--bind",
                "127.0.0.1:$port",
                "--start-event-id",
                startEventId.toHex(),
            ).start()
        return ReferenceReceiver(process, port)
    }

    private fun crypto(
        streamId: ByteArray,
        startEventId: ByteArray,
    ) = AgentTextStreamCrypto(
        ByteArray(32) { 0x77 },
        AgentTextStreamKeyContextV1(
            groupId = ByteArray(32) { 0x01 },
            streamId = streamId,
            mlsEpoch = 3,
            senderId = ByteArray(32) { 0x02 },
            startEventId = startEventId,
        ),
    )

    @Test
    fun ourDirectSenderReachesTheReferenceReceiver() {
        val wn = requireWn()
        runBlocking {
            val streamId = Random.nextBytes(32)
            val startEventId = Random.nextBytes(32)
            val receiver = startReceiver(wn, startEventId)
            // The receiver binds before it accepts; give it a moment so the
            // dial does not race the bind and report the port as unreachable.
            delay(1_000)

            val transport =
                QuicAgentTextStreamTransport(
                    parentScope = scope,
                    // `wn stream receive` mints a throwaway self-signed
                    // certificate per run and only prints it in its final
                    // JSON, so there is nothing to pin ahead of the dial. The
                    // pin is enforced in its own case below.
                    certificateValidator = PermissiveCertificateValidator(),
                )
            val stream = transport.sendDirect("quic://127.0.0.1:${receiver.port}", streamId, startEventId)
            val sender = AgentTextStreamPublisher.open(crypto(streamId, startEventId), InMemoryAgentTextStreamSequenceStore())
            val chunks = listOf("direct ", "path ", "records")
            for (text in chunks) {
                stream.send(sender.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, text.encodeToByteArray()))
            }
            stream.finish()
            stream.close()

            val json = receiver.awaitResult(30)
            // The reference receiver read our frames, so the ALPN, the
            // stream direction, the absence of a control envelope and the
            // 4-byte frame prefix all matched. It was given no key, so the
            // payloads stay ciphertext to it — what it can confirm is the
            // routing identity and the sequence.
            assertTrue(json.contains("\"stream_id\":\"${streamId.toHex()}\""), json)
            assertTrue(json.contains("\"chunk_count\":${chunks.size}"), json)
            assertTrue(json.contains("\"seq\":1"), json)
            assertTrue(json.contains("\"seq\":${chunks.size}"), json)
        }
    }

    @Test
    fun aWrongPinIsRefusedAgainstARealHandshake() {
        val wn = requireWn()
        runBlocking {
            val streamId = Random.nextBytes(32)
            val startEventId = Random.nextBytes(32)
            val receiver = startReceiver(wn, startEventId)
            delay(1_000)

            // A pin over a certificate the receiver is not holding. The
            // handshake has to fail here rather than at the first record:
            // once bytes are flowing, "pinned" would have meant nothing.
            val transport =
                QuicAgentTextStreamTransport(
                    parentScope = scope,
                    certificateValidator = PinnedCertificateValidator.ofSha256Hex("00".repeat(32)),
                )
            val failure =
                assertFailsWith<MarmotQuicException> {
                    transport.sendDirect("quic://127.0.0.1:${receiver.port}", streamId, startEventId)
                }
            assertEquals(MarmotQuicException.Kind.HandshakeFailed, failure.kind, "${failure.message}")

            receiver.process.destroyForcibly()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
