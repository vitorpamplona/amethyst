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
package com.vitorpamplona.nestsclient.interop.native

import com.vitorpamplona.nestsclient.NestsClient
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.audio.JvmOpusEncoder
import com.vitorpamplona.nestsclient.audio.SineWaveAudioCapture
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Kotlin speaker → Kotlin listener through [NativeMoqRelayHarness].
 *
 * Diagnostic for the I1 forward-direction gap. Isolates whether
 * the audio uni stream issue is Kotlin-side (bug in `:nestsClient`'s
 * publisher framing) or interop-specific (kotlin's frames are RFC
 * shaped but Rust's parser interpretation differs). If both ends are
 * Kotlin and the listener still doesn't see frames, the producer
 * is at fault. If both ends are Kotlin and frames flow, the
 * Kotlin↔Rust gap is on the reader side or in a subtle frame-vs-
 * datagram-vs-control-stream encoding mismatch.
 */
class KotlinSpeakerKotlinListenerThroughNativeRelayTest {
    @BeforeTest
    fun gate() {
        NativeMoqRelayHarness.assumeHangInterop()
    }

    @Test
    fun kotlin_speaker_to_kotlin_listener_round_trip_through_native_relay() =
        runBlocking {
            val harness = NativeMoqRelayHarness.shared()

            val signer: NostrSigner = NostrSignerInternal(KeyPair())
            val pubkey = signer.pubKey
            val room =
                NestsRoomConfig(
                    authBaseUrl = "<unused-public-relay>",
                    endpoint = harness.relayUrl,
                    hostPubkey = pubkey,
                    roomId = "rt-${UUID.randomUUID()}",
                )

            val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val transport =
                QuicWebTransportFactory(
                    certificateValidator = PermissiveCertificateValidator(),
                )

            try {
                val speaker =
                    connectNestsSpeaker(
                        httpClient = StaticTokenNestsClient,
                        transport = transport,
                        scope = pumpScope,
                        room = room,
                        signer = signer,
                        speakerPubkeyHex = pubkey,
                        captureFactory = { SineWaveAudioCapture(freqHz = 440) },
                        encoderFactory = { JvmOpusEncoder() },
                        // 5 frames per group matches the cliff-
                        // investigation plan's recommended default
                        // (`nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`)
                        // and the equivalent group cardinality in
                        // hang-publish. The repo's current
                        // `NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP = 50`
                        // is what's deployed, but with multi-frame
                        // uni streams Kotlin's audio data doesn't
                        // reach the relay's downstream subscribers.
                        framesPerGroup = 5,
                    )
                val handle = speaker.startBroadcasting()
                // Tiny breathing room so the announce and
                // setOnNewSubscriber hook are both in place.
                delay(150)

                val listener =
                    connectNestsListener(
                        httpClient = StaticTokenNestsClient,
                        transport = transport,
                        scope = pumpScope,
                        room = room,
                        signer = signer,
                    )
                val subscription = listener.subscribeSpeaker(pubkey)

                // Collect the next 50 audio frames (~1 s of audio).
                // 8 s wallclock budget covers the harness handshake +
                // any catalog hook race + small gradle worker startup
                // overhead when this test runs after HangInteropTest
                // in the same JVM.
                val received =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(8_000L) {
                            subscription.objects.take(50).toList()
                        }
                    }

                val frames = received.await()
                handle.close()
                speaker.close()
                listener.close()

                checkNotNull(frames) {
                    "Kotlin listener received no frames within 8 s — the audio " +
                        "uni stream is broken on the Kotlin side too, not just on the " +
                        "hang-listen interop path."
                }
                check(frames.size == 50) {
                    "expected exactly 50 frames, got ${frames.size}"
                }
            } finally {
                pumpScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }
        }

    private object StaticTokenNestsClient : NestsClient {
        override suspend fun mintToken(
            room: NestsRoomConfig,
            publish: Boolean,
            signer: NostrSigner,
        ): String = ""
    }
}
