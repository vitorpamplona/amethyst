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
package com.vitorpamplona.nestsclient.interop

import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.NestsSpeakerState
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase-3 interop test. Drives the production [connectNestsSpeaker] +
 * [connectNestsListener] entry points end-to-end against the real
 * nostrnests Docker stack: the speaker announces a track, the listener
 * subscribes by speaker pubkey, the speaker pushes deterministic Opus-
 * shaped payloads through the [com.vitorpamplona.nestsclient.audio.AudioRoomBroadcaster]
 * pipeline, and the listener collects them via the [com.vitorpamplona.nestsclient.moq.SubscribeHandle.objects]
 * flow.
 *
 * Verifies:
 *   - QuicWebTransportFactory + PermissiveCertificateValidator can speak
 *     to the relay's self-signed cert
 *   - JWT minting + WebTransport CONNECT + MoQ SETUP all succeed against
 *     the reference relay (not a unit-test mock)
 *   - The single-segment TrackNamespace shape (`nests/<kind>:<host>:<room>`)
 *     matches the relay's `root` JWT claim — chosen in Phase 2 without
 *     wire confirmation; this test is the confirmation.
 *   - OBJECT_DATAGRAMs round-trip with payload integrity + monotonic
 *     object ids.
 *
 * One keypair drives both the speaker and the listener — that sidesteps
 * the question of how nostrnests's auth sidecar gates speaker vs. audience
 * (kind 30312 hostlist, NIP-65, etc.) so this test focuses on the
 * transport + MoQ protocol, not the authorisation policy. A future
 * dual-keypair test can layer permission checks on top.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsRoundTripInteropTest {
    @Test
    fun production_speaker_broadcasts_to_production_listener_via_real_relay() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val signer = NostrSignerInternal(KeyPair())
            val pubkey = signer.pubKey
            val room =
                NestsRoomConfig(
                    authBaseUrl = harness.authBaseUrl,
                    endpoint = harness.moqEndpoint,
                    hostPubkey = pubkey,
                    roomId = "rt-${System.currentTimeMillis()}",
                )

            val httpClient = OkHttpNestsClient()
            // Self-signed dev cert on the relay — production uses
            // JdkCertificateValidator; the type system forces us to pass a
            // permissive validator explicitly here.
            val transport =
                QuicWebTransportFactory(
                    certificateValidator = PermissiveCertificateValidator(),
                )

            // Independent supervisor so a pump failure on one side doesn't
            // tear down the test scope before assertions can run.
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val capture = DriverCapture()
            val encoder = StubEncoder(prefix = "FRAME-".encodeToByteArray())

            try {
                val speaker =
                    connectNestsSpeaker(
                        httpClient = httpClient,
                        transport = transport,
                        scope = pumpScope,
                        room = room,
                        signer = signer,
                        speakerPubkeyHex = pubkey,
                        captureFactory = { capture },
                        encoderFactory = { encoder },
                    )
                assertTrue(
                    speaker.state.value is NestsSpeakerState.Connected,
                    "speaker did not reach Connected — was ${speaker.state.value}",
                )

                val broadcast = speaker.startBroadcasting()
                assertTrue(
                    speaker.state.value is NestsSpeakerState.Broadcasting,
                    "speaker did not reach Broadcasting — was ${speaker.state.value}",
                )

                val listener =
                    connectNestsListener(
                        httpClient = httpClient,
                        transport = transport,
                        scope = pumpScope,
                        room = room,
                        signer = signer,
                    )
                assertTrue(
                    listener.state.value is NestsListenerState.Connected,
                    "listener did not reach Connected — was ${listener.state.value}",
                )

                val subscription = listener.subscribeSpeaker(pubkey)

                // Start collecting before the speaker publishes anything —
                // OBJECT_DATAGRAMs that arrive before .objects.collect runs
                // are dropped by the per-subscription buffer.
                val received =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            subscription.objects.take(N_FRAMES).toList()
                        }
                    }

                // Tiny breathing room so the SUBSCRIBE_OK lands and the
                // publisher knows there's an inbound subscriber before the
                // first send (otherwise TrackPublisher.send returns false
                // and rolls back the object id without queuing a datagram).
                delay(SUBSCRIBE_SETTLE_MS)

                for (i in 0 until N_FRAMES) {
                    capture.push(shortArrayOf(i.toShort()))
                    // Pace frames slightly so a transient datagram drop
                    // doesn't cluster the loss window onto a single burst.
                    delay(FRAME_SPACING_MS)
                }

                val datagrams = received.await()
                assertNotNull(
                    datagrams,
                    "Did not receive $N_FRAMES OBJECT_DATAGRAMs within ${RECEIVE_TIMEOUT_MS}ms",
                )
                assertEquals(N_FRAMES, datagrams.size, "expected exactly $N_FRAMES objects")

                datagrams.forEachIndexed { idx, obj ->
                    assertEquals(
                        idx.toLong(),
                        obj.objectId,
                        "object id at index $idx — MoQ requires monotonic ids per group",
                    )
                    assertEquals(0L, obj.groupId, "single-group track per audio-rooms NIP draft")
                    assertContentEquals(
                        "FRAME-".encodeToByteArray() + byteArrayOf(idx.toByte()),
                        obj.payload,
                        "payload at index $idx round-tripped through encoder + relay",
                    )
                }

                // Tear down in reverse order. unsubscribe first so the
                // publisher doesn't fan out into a closed listener.
                runCatching { subscription.unsubscribe() }
                runCatching { broadcast.close() }
                runCatching { listener.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            // Force the runBlocking → method return type to Unit; JUnit 4
            // rejects test methods that return anything else.
            Unit
        }

    /**
     * Capture seam driven directly by the test. [push] hands one frame to
     * the broadcaster's pump; [stop] signals end-of-stream so the pump
     * exits cleanly during teardown.
     */
    private class DriverCapture : AudioCapture {
        private val frames = Channel<ShortArray>(capacity = Channel.UNLIMITED)

        @Volatile private var started: Boolean = false

        override fun start() {
            started = true
        }

        override suspend fun readFrame(): ShortArray? {
            if (!started) return null
            return frames.receiveCatching().getOrNull()
        }

        override fun stop() {
            frames.close()
        }

        fun push(pcm: ShortArray) {
            frames.trySend(pcm)
        }
    }

    /**
     * Encoder that emits `<prefix><lo-byte-of-first-pcm-sample>` for each
     * frame. Lets the test assert byte-for-byte payload integrity through
     * the relay without pulling a real Opus encoder into the JVM build.
     */
    private class StubEncoder(
        private val prefix: ByteArray,
    ) : OpusEncoder {
        override fun encode(pcm: ShortArray): ByteArray = prefix + byteArrayOf(pcm.first().toByte())

        override fun release() = Unit
    }

    companion object {
        private const val N_FRAMES = 8
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val FRAME_SPACING_MS = 25L
        private const val RECEIVE_TIMEOUT_MS = 15_000L

        private var harnessOrNull: NostrNestsHarness? = null

        @BeforeClass
        @JvmStatic
        fun setUpHarness() {
            if (NostrNestsHarness.isEnabled()) {
                harnessOrNull = NostrNestsHarness.start()
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownHarness() {
            harnessOrNull?.close()
            harnessOrNull = null
        }
    }
}
