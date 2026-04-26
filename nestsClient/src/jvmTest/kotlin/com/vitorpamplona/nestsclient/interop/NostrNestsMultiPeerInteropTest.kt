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
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.NestsSpeaker
import com.vitorpamplona.nestsclient.NestsSpeakerState
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.moq.MoqObject
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
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
 * Phase-4 multi-peer interop tests. Each test brings up a real
 * nostrnests stack, exercises a multi-publisher / multi-subscriber
 * scenario through the production [connectNestsSpeaker] +
 * [connectNestsListener] entry points, and asserts the relay's fan-out
 * + ordering guarantees end-to-end.
 *
 * Coverage:
 *   - **Multi-listener fan-out** — 1 speaker, 2 listeners; each listener
 *     receives the full frame stream.
 *   - **Multi-speaker** — 2 speakers under the same room; one listener
 *     subscribes to each by pubkey hex; both subscriptions deliver
 *     their respective speaker's frames with no cross-talk.
 *   - **Subscribe-before-announce** — listener subscribes before the
 *     speaker has announced; per moq-rs the relay holds the subscribe
 *     and resolves it once a publisher arrives.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsMultiPeerInteropTest {
    @Test
    fun one_speaker_fans_out_to_two_listeners() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val room = freshRoom(harness, hostPubkey = "0".repeat(64))
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val speakerSigner = NostrSignerInternal(KeyPair())
            val speakerRoom = room.copy(hostPubkey = speakerSigner.pubKey)
            val capture = DriverCapture()

            val listenerSignerA = NostrSignerInternal(KeyPair())
            val listenerSignerB = NostrSignerInternal(KeyPair())

            try {
                val speaker =
                    connectSpeaker(
                        scope = pumpScope,
                        room = speakerRoom,
                        signer = speakerSigner,
                        capture = capture,
                    )
                speaker.startBroadcasting()

                val listenerA = connectListener(pumpScope, speakerRoom, listenerSignerA)
                val listenerB = connectListener(pumpScope, speakerRoom, listenerSignerB)

                val subA = listenerA.subscribeSpeaker(speakerSigner.pubKey)
                val subB = listenerB.subscribeSpeaker(speakerSigner.pubKey)

                val collectedA = collectFrames(pumpScope, subA, FRAMES_FANOUT)
                val collectedB = collectFrames(pumpScope, subB, FRAMES_FANOUT)

                delay(SUBSCRIBE_SETTLE_MS)
                for (i in 0 until FRAMES_FANOUT) {
                    capture.push(shortArrayOf(i.toShort()))
                    delay(FRAME_SPACING_MS)
                }

                assertFrameSequence(collectedA.await(), FRAMES_FANOUT, "listener A")
                assertFrameSequence(collectedB.await(), FRAMES_FANOUT, "listener B")

                runCatching { subA.unsubscribe() }
                runCatching { subB.unsubscribe() }
                runCatching { listenerA.close() }
                runCatching { listenerB.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    @Test
    fun two_speakers_in_same_room_deliver_independently_to_one_listener() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            // Two speakers sharing the same room namespace must publish
            // under different sub-paths (relay enforces `put: [pubkey]`).
            // The room's hostPubkey gates `claims.root` — we use signer A's
            // pubkey as the hostPubkey for the namespace, but each speaker
            // mints their own JWT bound to their own pubkey via `put`.
            val signerA = NostrSignerInternal(KeyPair())
            val signerB = NostrSignerInternal(KeyPair())
            val listenerSigner = NostrSignerInternal(KeyPair())
            val room = freshRoom(harness, hostPubkey = signerA.pubKey)

            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val captureA = DriverCapture()
            val captureB = DriverCapture()

            try {
                val speakerA =
                    connectSpeaker(pumpScope, room, signerA, captureA, encoderPrefix = "A:")
                val speakerB =
                    connectSpeaker(pumpScope, room, signerB, captureB, encoderPrefix = "B:")
                speakerA.startBroadcasting()
                speakerB.startBroadcasting()

                val listener = connectListener(pumpScope, room, listenerSigner)
                val subA = listener.subscribeSpeaker(signerA.pubKey)
                val subB = listener.subscribeSpeaker(signerB.pubKey)

                val collectedA = collectFrames(pumpScope, subA, FRAMES_MULTI_SPEAKER)
                val collectedB = collectFrames(pumpScope, subB, FRAMES_MULTI_SPEAKER)

                delay(SUBSCRIBE_SETTLE_MS)
                for (i in 0 until FRAMES_MULTI_SPEAKER) {
                    captureA.push(shortArrayOf((i + 0).toShort()))
                    captureB.push(shortArrayOf((i + 100).toShort()))
                    delay(FRAME_SPACING_MS)
                }

                val resA = collectedA.await()
                val resB = collectedB.await()
                assertNotNull(resA, "listener never received speaker-A frames")
                assertNotNull(resB, "listener never received speaker-B frames")
                assertEquals(FRAMES_MULTI_SPEAKER, resA.size, "speaker A count")
                assertEquals(FRAMES_MULTI_SPEAKER, resB.size, "speaker B count")

                resA.forEachIndexed { idx, obj ->
                    assertContentEquals(
                        "A:".encodeToByteArray() + byteArrayOf(idx.toByte()),
                        obj.payload,
                        "speaker A frame $idx — must not contain B's payload (no cross-talk)",
                    )
                }
                resB.forEachIndexed { idx, obj ->
                    assertContentEquals(
                        "B:".encodeToByteArray() + byteArrayOf((idx + 100).toByte()),
                        obj.payload,
                        "speaker B frame $idx — must not contain A's payload (no cross-talk)",
                    )
                }

                runCatching { subA.unsubscribe() }
                runCatching { subB.unsubscribe() }
                runCatching { listener.close() }
                runCatching { speakerA.close() }
                runCatching { speakerB.close() }
            } finally {
                captureA.stop()
                captureB.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    @Test
    fun listener_subscribed_before_announce_receives_late_frames() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            // The relay holds the SUBSCRIBE open until a publisher
            // announces. Validates moq-rs's "subscribe before announce"
            // contract end-to-end through our client.
            val speakerSigner = NostrSignerInternal(KeyPair())
            val listenerSigner = NostrSignerInternal(KeyPair())
            val room = freshRoom(harness, hostPubkey = speakerSigner.pubKey)

            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)
            val capture = DriverCapture()

            try {
                val listener = connectListener(pumpScope, room, listenerSigner)
                val sub = listener.subscribeSpeaker(speakerSigner.pubKey)
                val collected = collectFrames(pumpScope, sub, FRAMES_PRESUB)

                // Wait briefly so the SUBSCRIBE is on the relay before
                // the publisher arrives — the relay should hold it.
                delay(SUBSCRIBE_SETTLE_MS)

                val speaker = connectSpeaker(pumpScope, room, speakerSigner, capture)
                speaker.startBroadcasting()

                // Give the announce + publisher-side subscribe matchup
                // time to plumb before pushing frames.
                delay(SUBSCRIBE_SETTLE_MS)
                for (i in 0 until FRAMES_PRESUB) {
                    capture.push(shortArrayOf(i.toShort()))
                    delay(FRAME_SPACING_MS)
                }

                assertFrameSequence(collected.await(), FRAMES_PRESUB, "pre-subscribed listener")

                runCatching { sub.unsubscribe() }
                runCatching { listener.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    // ---------- Helpers ----------

    private suspend fun connectSpeaker(
        scope: CoroutineScope,
        room: NestsRoomConfig,
        signer: NostrSignerInternal,
        capture: AudioCapture,
        encoderPrefix: String = "FRAME-",
    ): NestsSpeaker {
        val speaker =
            connectNestsSpeaker(
                httpClient = http,
                transport = transport,
                scope = scope,
                room = room,
                signer = signer,
                speakerPubkeyHex = signer.pubKey,
                captureFactory = { capture },
                encoderFactory = { StubEncoder(encoderPrefix.encodeToByteArray()) },
            )
        assertTrue(
            speaker.state.value is NestsSpeakerState.Connected,
            "speaker did not reach Connected — was ${speaker.state.value}",
        )
        return speaker
    }

    private suspend fun connectListener(
        scope: CoroutineScope,
        room: NestsRoomConfig,
        signer: NostrSignerInternal,
    ): NestsListener {
        val listener =
            connectNestsListener(
                httpClient = http,
                transport = transport,
                scope = scope,
                room = room,
                signer = signer,
            )
        assertTrue(
            listener.state.value is NestsListenerState.Connected,
            "listener did not reach Connected — was ${listener.state.value}",
        )
        return listener
    }

    private fun collectFrames(
        scope: CoroutineScope,
        sub: SubscribeHandle,
        count: Int,
    ) = scope.async {
        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
            sub.objects.take(count).toList()
        }
    }

    private fun assertFrameSequence(
        objs: List<MoqObject>?,
        expectedCount: Int,
        who: String,
    ) {
        assertNotNull(objs, "$who did not receive $expectedCount frames within ${RECEIVE_TIMEOUT_MS}ms")
        assertEquals(expectedCount, objs.size, "$who frame count")
        objs.forEachIndexed { idx, obj ->
            assertEquals(idx.toLong(), obj.objectId, "$who object id at index $idx")
            assertContentEquals(
                "FRAME-".encodeToByteArray() + byteArrayOf(idx.toByte()),
                obj.payload,
                "$who payload at index $idx",
            )
        }
    }

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
        private const val FRAMES_FANOUT = 6
        private const val FRAMES_MULTI_SPEAKER = 6
        private const val FRAMES_PRESUB = 4
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val FRAME_SPACING_MS = 25L
        private const val RECEIVE_TIMEOUT_MS = 15_000L

        private val http = OkHttpNestsClient()
        private val transport =
            QuicWebTransportFactory(certificateValidator = PermissiveCertificateValidator())
        private var harnessOrNull: NostrNestsHarness? = null

        private fun freshRoom(
            harness: NostrNestsHarness,
            hostPubkey: String,
        ) = NestsRoomConfig(
            authBaseUrl = harness.authBaseUrl,
            endpoint = harness.moqEndpoint,
            hostPubkey = hostPubkey,
            roomId = "mp-${System.nanoTime()}",
        )

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
