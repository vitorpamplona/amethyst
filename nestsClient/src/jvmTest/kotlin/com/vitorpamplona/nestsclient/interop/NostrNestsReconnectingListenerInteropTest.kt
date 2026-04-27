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

import com.vitorpamplona.nestsclient.NestsListener
import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsReconnectPolicy
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.connectReconnectingNestsListener
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Interop test for [connectReconnectingNestsListener] against the real
 * nostrnests stack. Two scenarios:
 *
 *   1. Happy path — wrapper opens a session via the real auth +
 *      moq-relay, subscribes through the re-issuing pump, verifies
 *      that frames arrive on the consumer-facing [SubscribeHandle].
 *      This validates that the new MutableSharedFlow-backed pump
 *      doesn't drop frames when wired to real moq-lite framing.
 *
 *   2. Session swap — uses a custom `connector` that opens TWO real
 *      sessions in sequence; we close the first one mid-stream,
 *      observe the orchestrator's automatic reconnect, and confirm
 *      that the SAME SubscribeHandle the test is collecting keeps
 *      receiving frames published after the swap. This is the
 *      end-to-end version of [com.vitorpamplona.nestsclient.ReconnectingNestsListenerTest]
 *      against real wire semantics.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsReconnectingListenerInteropTest {
    @Test
    fun reconnecting_wrapper_round_trips_frames_via_real_relay() =
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
                    roomId = "rec-${System.currentTimeMillis()}",
                )

            val httpClient = OkHttpNestsClient()
            val transport =
                QuicWebTransportFactory(
                    certificateValidator = PermissiveCertificateValidator(),
                )
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val capture = DriverCapture()
            val encoder = StubEncoder(prefix = "REC-".encodeToByteArray())

            val scope = "reconnecting-happy-path"
            try {
                val speaker =
                    InteropDebug.stepSuspending(scope, "connectNestsSpeaker") {
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
                    }
                InteropDebug.assertSpeakerReached(scope, "Connected", speaker.state.value)

                val broadcast =
                    InteropDebug.stepSuspending(scope, "speaker.startBroadcasting") {
                        speaker.startBroadcasting()
                    }
                InteropDebug.assertSpeakerReached(scope, "Broadcasting", speaker.state.value)

                val reconnecting =
                    InteropDebug.stepSuspending(scope, "connectReconnectingNestsListener") {
                        connectReconnectingNestsListener(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = signer,
                            policy = NestsReconnectPolicy(initialDelayMs = 250L),
                        )
                    }

                // The wrapper's `state` mirrors the inner listener once
                // the orchestrator's first openOnce() resolves; wait
                // for it to land before subscribing.
                withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    reconnecting.state.first { it is NestsListenerState.Connected }
                } ?: fail("[$scope] reconnecting wrapper never reached Connected")

                val subscription =
                    InteropDebug.stepSuspending(scope, "reconnecting.subscribeSpeaker(pubkey)") {
                        reconnecting.subscribeSpeaker(pubkey)
                    }

                val received =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            subscription.objects.take(N_FRAMES).toList()
                        }
                    }

                // Same pacing as the round-trip test — gives the pump's
                // re-subscribe path time to land before the publisher
                // starts pushing.
                delay(SUBSCRIBE_SETTLE_MS)

                for (i in 0 until N_FRAMES) {
                    capture.push(shortArrayOf(i.toShort()))
                    delay(FRAME_SPACING_MS)
                }

                val datagrams = received.await()
                if (datagrams == null) {
                    fail(
                        "[$scope] Did not receive $N_FRAMES moq-lite frames within ${RECEIVE_TIMEOUT_MS}ms — " +
                            "wrapper=${InteropDebug.describe(reconnecting.state.value)}, " +
                            "speaker=${InteropDebug.describe(speaker.state.value)}",
                    )
                }
                assertEquals(N_FRAMES, datagrams.size, "expected exactly $N_FRAMES frames")
                datagrams.forEachIndexed { idx, obj ->
                    assertContentEquals(
                        "REC-".encodeToByteArray() + byteArrayOf(idx.toByte()),
                        obj.payload,
                        "wrapper-issued payload at index $idx round-tripped through real moq-lite relay",
                    )
                }

                runCatching { subscription.unsubscribe() }
                runCatching { broadcast.close() }
                runCatching { reconnecting.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    @Test
    fun reconnecting_wrapper_keeps_handle_alive_across_session_swap() =
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
                    roomId = "swap-${System.currentTimeMillis()}",
                )

            val httpClient = OkHttpNestsClient()
            val transport =
                QuicWebTransportFactory(
                    certificateValidator = PermissiveCertificateValidator(),
                )
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val capture = DriverCapture()
            val encoder = StubEncoder(prefix = "SWAP-".encodeToByteArray())

            val scope = "reconnecting-session-swap"
            // connector opens a fresh real listener every time the
            // orchestrator asks for one. The orchestrator only retries
            // after the inner listener emits Failed/Closed, so we
            // explicitly close the first one mid-stream below.
            val openCount = AtomicInteger(0)
            val openedListeners = mutableListOf<NestsListener>()
            val openedListenersLock = Any()

            try {
                val speaker =
                    InteropDebug.stepSuspending(scope, "connectNestsSpeaker") {
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
                    }
                val broadcast = speaker.startBroadcasting()

                val reconnecting =
                    InteropDebug.stepSuspending(scope, "connectReconnectingNestsListener (custom connector)") {
                        connectReconnectingNestsListener(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = signer,
                            policy = NestsReconnectPolicy(initialDelayMs = 500L),
                            connector = {
                                openCount.incrementAndGet()
                                val l =
                                    connectNestsListener(
                                        httpClient = httpClient,
                                        transport = transport,
                                        scope = pumpScope,
                                        room = room,
                                        signer = signer,
                                    )
                                synchronized(openedListenersLock) { openedListeners.add(l) }
                                l
                            },
                        )
                    }

                withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    reconnecting.state.first { it is NestsListenerState.Connected }
                } ?: fail("[$scope] never reached initial Connected")

                val subscription = reconnecting.subscribeSpeaker(pubkey)

                val received =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(SWAP_TIMEOUT_MS) {
                            subscription.objects.take(N_FRAMES_SWAP).toList()
                        }
                    }

                delay(SUBSCRIBE_SETTLE_MS)

                // First half-batch — these arrive on the FIRST real
                // session.
                for (i in 0 until HALF_FRAMES) {
                    capture.push(shortArrayOf(i.toShort()))
                    delay(FRAME_SPACING_MS)
                }

                // Force a reconnect by closing the first session out
                // from under the wrapper. The orchestrator observes
                // Closed → schedules a retry → connector returns a
                // freshly-opened second session.
                val firstListener =
                    synchronized(openedListenersLock) { openedListeners.firstOrNull() }
                        ?: fail("[$scope] expected at least one opened listener")
                InteropDebug.checkpoint(scope, "closing first inner listener to force reconnect")
                firstListener.close()

                // Wait for the wrapper to land Connected against the
                // SECOND real session before we publish more frames.
                withTimeoutOrNull(SWAP_TIMEOUT_MS) {
                    while (openCount.get() < 2) delay(50)
                    reconnecting.state.first { it is NestsListenerState.Connected }
                } ?: fail("[$scope] reconnecting wrapper did not reopen — opens=${openCount.get()}")

                // Some additional time for the moq-lite re-handshake
                // and re-subscription of the speaker track on the new
                // session before publishing more frames.
                delay(POST_SWAP_SETTLE_MS)

                // Second half — must arrive on the SAME SubscribeHandle
                // the consumer is still collecting from.
                for (i in HALF_FRAMES until N_FRAMES_SWAP) {
                    capture.push(shortArrayOf(i.toShort()))
                    delay(FRAME_SPACING_MS)
                }

                val datagrams = received.await()
                if (datagrams == null) {
                    fail(
                        "[$scope] subscription stopped emitting after the session swap — " +
                            "openCount=${openCount.get()}, " +
                            "wrapper=${InteropDebug.describe(reconnecting.state.value)}, " +
                            "speaker=${InteropDebug.describe(speaker.state.value)}",
                    )
                }
                assertEquals(N_FRAMES_SWAP, datagrams.size)
                assertTrue(
                    openCount.get() >= 2,
                    "expected at least 2 underlying sessions (one before, one after swap), got ${openCount.get()}",
                )

                runCatching { subscription.unsubscribe() }
                runCatching { broadcast.close() }
                runCatching { reconnecting.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    /** Channel-driven capture seam — same pattern as the round-trip test. */
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

    private class StubEncoder(
        private val prefix: ByteArray,
    ) : OpusEncoder {
        override fun encode(pcm: ShortArray): ByteArray = prefix + byteArrayOf(pcm.first().toByte())

        override fun release() = Unit
    }

    companion object {
        private const val N_FRAMES = 6
        private const val N_FRAMES_SWAP = 8
        private const val HALF_FRAMES = 3
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val POST_SWAP_SETTLE_MS = 1_000L
        private const val FRAME_SPACING_MS = 50L
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val RECEIVE_TIMEOUT_MS = 15_000L
        private const val SWAP_TIMEOUT_MS = 30_000L

        private var harnessOrNull: NostrNestsHarness? = null

        @BeforeClass
        @JvmStatic
        fun setUpHarness() {
            if (NostrNestsHarness.isEnabled()) {
                harnessOrNull = NostrNestsHarness.shared()
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownHarness() {
            harnessOrNull = null
        }
    }
}
