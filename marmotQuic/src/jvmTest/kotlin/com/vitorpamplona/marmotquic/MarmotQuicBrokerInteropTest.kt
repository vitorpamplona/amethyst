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
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamTranscriptV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.InMemoryAgentTextStreamSequenceStore
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicException
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives our `transports/quic.md` client against MDK's own
 * `marmot-quic-broker`, the reference implementation of the other side.
 *
 * This is the only way to know the binding is right. Everything it exercises
 * is a place where two implementations have to agree byte for byte and where
 * our own tests would happily agree with themselves: the ALPN string, the
 * control envelope's layout and its literal protocol name, which stream
 * direction each role uses, and the 4-byte frame prefix. The records
 * themselves stay opaque to the broker — it fans out ciphertext and never
 * holds a key.
 *
 * Opt in with `-DmarmotQuicBroker=127.0.0.1:4450` after starting:
 *
 * ```
 * cargo build --release --bin marmot-quic-broker   # in the MDK checkout
 * ./target/release/marmot-quic-broker --bind 127.0.0.1:4450 --json
 * ```
 *
 * Without the property the test skips, so a normal `./gradlew test` never
 * needs a broker on the machine.
 */
class MarmotQuicBrokerInteropTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val brokerAuthority: String? = System.getProperty("marmotQuicBroker")

    /**
     * Report "no broker configured" as a JUnit skip rather than a silent pass,
     * so a run that was meant to exercise the broker cannot look green because
     * the property never reached the worker.
     */
    private fun requireBroker(): String {
        Assume.assumeTrue(
            "set -DmarmotQuicBroker=host:port and start MDK's marmot-quic-broker to run the interop cases",
            brokerAuthority != null,
        )
        return brokerAuthority!!
    }

    private val candidate get() = "quic://$brokerAuthority"

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun transport() =
        QuicAgentTextStreamTransport(
            parentScope = scope,
            // The broker generates a self-signed certificate on startup. The
            // binding expects exactly that ("preview endpoints and brokers may
            // be self-signed") and says a client MAY pin it locally; a test
            // against a throwaway broker accepts it outright.
            certificateValidator = PermissiveCertificateValidator(),
        )

    private fun keyContext(
        streamId: ByteArray,
        startEventId: ByteArray,
    ) = AgentTextStreamKeyContextV1(
        groupId = ByteArray(32) { 0x01 },
        streamId = streamId,
        mlsEpoch = 3,
        senderId = ByteArray(32) { 0x02 },
        startEventId = startEventId,
    )

    @Test
    fun ourPublisherAndSubscriberMeetInsideTheReferenceBroker() {
        requireBroker()
        runBlocking {
            val streamId = Random.nextBytes(32)
            val startEventId = Random.nextBytes(32)
            val secret = ByteArray(32) { 0x77 }
            val crypto = AgentTextStreamCrypto(secret, keyContext(streamId, startEventId))

            val transport = transport()
            // Subscribe first: the broker's replay window is 0 by default, so
            // a subscriber that arrives after the records were pushed sees
            // nothing — which is the binding working as specified, not a bug.
            val subscriber = transport.subscribe(candidate, streamId, startEventId)
            val received = async { withTimeout(30_000) { subscriber.incoming().take(3).toList() } }
            delay(500)

            val publisher = transport.publish(candidate, streamId, startEventId)
            val sender = AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore())
            val plaintexts = listOf("the ", "quick ", "brown fox")
            for (text in plaintexts) {
                publisher.send(sender.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, text.encodeToByteArray()))
            }
            publisher.finish()

            val records = received.await()

            assertEquals(listOf(1L, 2L, 3L), records.map { it.seq })
            assertEquals(
                plaintexts.joinToString(""),
                records.joinToString("") { crypto.open(it).frame.decodeToString() },
                "the broker relays ciphertext and cannot read a byte of it, so what comes back must open " +
                    "under the same group-derived key",
            )

            // A receiver that folded every record must agree with the
            // publisher's transcript, which is what the final kind:9 carries.
            val fold = AgentTextStreamTranscriptV1.start(streamId, startEventId)
            records.forEach { fold.append(crypto.open(it)) }
            assertContentEquals(sender.transcript.hash, fold.hash)
            assertEquals(sender.transcript.chunkCount, fold.chunkCount)

            publisher.close()
            subscriber.close()
        }
    }

    @Test
    fun theBrokerKeepsRoomsApart() {
        requireBroker()
        runBlocking {
            val startEventId = Random.nextBytes(32)
            val mine = Random.nextBytes(32)
            val theirs = Random.nextBytes(32)
            val transport = transport()

            val subscriber = transport.subscribe(candidate, mine, startEventId)
            val received = async { withTimeout(15_000) { subscriber.incoming().take(1).toList() } }
            delay(500)

            // Same start event, different stream id: a different room. "A
            // broker MUST NOT merge or cross-deliver records between different
            // rooms."
            val wrongRoom = transport.publish(candidate, theirs, startEventId)
            val strayCrypto = AgentTextStreamCrypto(ByteArray(32) { 0x66 }, keyContext(theirs, startEventId))
            val stray = AgentTextStreamPublisher.open(strayCrypto, InMemoryAgentTextStreamSequenceStore())
            wrongRoom.send(stray.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "not for you".encodeToByteArray()))
            wrongRoom.finish()

            // Now the right room, so the test finishes on a positive signal
            // rather than a timeout we cannot distinguish from a hang.
            val rightRoom = transport.publish(candidate, mine, startEventId)
            val mineCrypto = AgentTextStreamCrypto(ByteArray(32) { 0x77 }, keyContext(mine, startEventId))
            val ours = AgentTextStreamPublisher.open(mineCrypto, InMemoryAgentTextStreamSequenceStore())
            rightRoom.send(ours.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "for you".encodeToByteArray()))
            rightRoom.finish()

            val records = received.await()
            assertEquals(1, records.size)
            assertContentEquals(mine, records.single().streamId)
            assertEquals("for you", mineCrypto.open(records.single()).frame.decodeToString())

            wrongRoom.close()
            rightRoom.close()
            subscriber.close()
        }
    }

    @Test
    fun theBrokerRefusesAnEndpointThatDoesNotSpeakOurAlpn() {
        assertTrue(requireBroker().isNotEmpty())
        // Sanity: a candidate that parses but points nowhere must fail as a
        // handshake, not hang or throw something unclassified.
        runBlocking {
            val failure =
                runCatching {
                    withTimeout(30_000) {
                        transport().subscribe("quic://127.0.0.1:1", Random.nextBytes(32), Random.nextBytes(32))
                    }
                }.exceptionOrNull()
            assertTrue(failure is MarmotQuicException, "expected a MarmotQuicException, got $failure")
        }
    }
}
