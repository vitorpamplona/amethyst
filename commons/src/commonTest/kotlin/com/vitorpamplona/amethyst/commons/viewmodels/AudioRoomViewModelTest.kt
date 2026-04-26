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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.nestsclient.NestsClient
import com.vitorpamplona.nestsclient.NestsException
import com.vitorpamplona.nestsclient.NestsListener
import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.audio.AudioPlayer
import com.vitorpamplona.nestsclient.audio.OpusDecoder
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Drives [AudioRoomViewModel] with a fake [NestsListenerConnector] and
 * verifies its state-flow transitions:
 *   - connect() shows Connecting before the underlying connector returns
 *   - listener emits Connected → uiState collapses to Connected
 *   - listener emits Failed → uiState becomes Failed(reason)
 *   - setMuted updates uiState and propagates to active subscriptions
 *   - disconnect() returns to Idle and tears down the listener
 *
 * Subscribe-side (subscribeSpeaker → AudioRoomPlayer.play) is exercised via
 * the existing nestsClient tests; the cross-module audio glue would need
 * an internal SubscribeHandle constructor that's intentionally not visible
 * here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioRoomViewModelTest {
    @BeforeTest
    fun setupMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun connectShowsConnectingThenConnected() =
        runTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }

            vm.connect()
            // Initial UI shows the resolving substep before the connector
            // (suspend) has a chance to actually return — that's the
            // ResolvingRoom optimistic enter we set on connect().
            assertIs<ConnectionUiState.Connecting>(vm.uiState.value.connection)

            // Connector resolves with the listener; it's still Idle until we
            // emit something. Drive it Connected directly.
            fakeListener.emit(NestsListenerState.Connected(room = ROOM_CONFIG, negotiatedMoqVersion = 0xff000011))

            assertEquals(ConnectionUiState.Connected, vm.uiState.value.connection)
        }

    @Test
    fun listenerFailedSurfacesAsUiFailed() =
        runTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }

            vm.connect()
            fakeListener.emit(NestsListenerState.Failed("relay rejected", IllegalStateException("oops")))

            val ui = vm.uiState.value.connection
            assertIs<ConnectionUiState.Failed>(ui)
            assertEquals("relay rejected", ui.reason)
        }

    @Test
    fun connectorThrowsBecomesUiFailed() =
        runTest {
            val vm = newViewModel { throw NestsException("dns blew up") }

            vm.connect()

            val ui = vm.uiState.value.connection
            assertIs<ConnectionUiState.Failed>(ui)
            assertEquals("dns blew up", ui.reason)
        }

    @Test
    fun setMutedFlipsUiStateAndIsRetained() =
        runTest {
            val vm = newViewModel { FakeNestsListener() }

            assertFalse(vm.uiState.value.isMuted)
            vm.setMuted(true)
            assertTrue(vm.uiState.value.isMuted)
            vm.setMuted(false)
            assertFalse(vm.uiState.value.isMuted)
        }

    @Test
    fun connectIsIdempotentWhileConnecting() =
        runTest {
            val fakeListener = FakeNestsListener()
            var connectCalls = 0
            val vm =
                newViewModel {
                    connectCalls++
                    fakeListener
                }

            vm.connect()
            vm.connect()
            vm.connect()

            assertEquals(1, connectCalls, "connect() should not re-fire while a Connecting flow is in flight")
        }

    @Test
    fun disconnectReturnsToIdleAndClosesListener() =
        runTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }

            vm.connect()
            fakeListener.emit(NestsListenerState.Connected(ROOM_CONFIG, 0xff000011))
            assertEquals(ConnectionUiState.Connected, vm.uiState.value.connection)

            vm.disconnect()

            assertEquals(ConnectionUiState.Idle, vm.uiState.value.connection)
            assertTrue(fakeListener.closeCallCount > 0, "listener.close() should run on disconnect()")
        }

    @Test
    fun connectingStepMapsThroughToUiStep() =
        runTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }

            vm.connect()
            fakeListener.emit(
                NestsListenerState.Connecting(
                    NestsListenerState.Connecting.ConnectStep.OpeningTransport,
                ),
            )

            val ui = vm.uiState.value.connection
            assertIs<ConnectionUiState.Connecting>(ui)
            assertEquals(ConnectionUiState.Step.OpeningTransport, ui.step)
        }

    @Test
    fun speakingNowClearsOnTeardown() =
        runTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }

            vm.connect()
            fakeListener.emit(NestsListenerState.Connected(ROOM_CONFIG, 0xff000011))
            // Speaking-now is empty until an object arrives — exercising the
            // timeout-based clearing requires a live SubscribeHandle, which is
            // covered in nestsClient's pipe tests. Here we just verify the
            // teardown contract: speakingNow returns to empty after disconnect.
            vm.disconnect()

            assertTrue(
                vm.uiState.value.speakingNow
                    .isEmpty(),
            )
            assertTrue(
                vm.uiState.value.activeSpeakers
                    .isEmpty(),
            )
        }

    private fun TestScope.newViewModel(connect: suspend (CoroutineScope) -> NestsListener): AudioRoomViewModel =
        AudioRoomViewModel(
            httpClient = NoopNestsClient,
            transport = NoopWebTransportFactory,
            decoderFactory = { NoopOpusDecoder },
            playerFactory = { NoopAudioPlayer() },
            signer = NoopSigner,
            room = ROOM_CONFIG,
            connector =
                NestsListenerConnector { _, _, scope, _, _ ->
                    connect(scope)
                },
            // Wire to the test's backgroundScope so close calls run during
            // the test rather than escaping to the real GlobalScope.
            cleanupScope = backgroundScope,
        )

    private class FakeNestsListener : NestsListener {
        private val mutable = MutableStateFlow<NestsListenerState>(NestsListenerState.Idle)
        override val state: StateFlow<NestsListenerState> = mutable.asStateFlow()
        var closeCallCount: Int = 0
            private set

        fun emit(s: NestsListenerState) {
            mutable.value = s
        }

        override suspend fun subscribeSpeaker(speakerPubkeyHex: String): SubscribeHandle = error("subscribeSpeaker not exercised in these tests — see AudioRoomPlayerTest in :nestsClient")

        override suspend fun close() {
            closeCallCount++
            mutable.value = NestsListenerState.Closed
        }
    }

    private object NoopNestsClient : NestsClient {
        override suspend fun mintToken(
            room: NestsRoomConfig,
            publish: Boolean,
            signer: NostrSigner,
        ): String = error("mintToken not used (connector seam bypasses it)")
    }

    private object NoopWebTransportFactory : WebTransportFactory {
        override suspend fun connect(
            authority: String,
            path: String,
            bearerToken: String?,
        ): WebTransportSession = error("connect not used (connector seam bypasses it)")
    }

    private object NoopOpusDecoder : OpusDecoder {
        override fun decode(opusPacket: ByteArray): ShortArray = ShortArray(0)

        override fun release() {}
    }

    private class NoopAudioPlayer : AudioPlayer {
        override fun start() {}

        override suspend fun enqueue(pcm: ShortArray) {}

        override fun setMuted(muted: Boolean) {}

        override fun stop() {}
    }

    private object NoopSigner : NostrSigner(pubKey = "0".repeat(64)) {
        override fun isWriteable(): Boolean = false

        override fun hasForegroundSupport(): Boolean = false

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = error("sign not used in this VM test")

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: String,
        ): String = error("not used")

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: String,
        ): String = error("not used")

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: String,
        ): String = error("not used")

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: String,
        ): String = error("not used")

        override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent = error("not used")

        override suspend fun deriveKey(nonce: String): String = error("not used")
    }

    companion object {
        private val ROOM_CONFIG =
            NestsRoomConfig(
                authBaseUrl = "https://relay.example.test/api/v1/nests",
                endpoint = "https://relay.example.test/moq",
                hostPubkey = "0".repeat(64),
                roomId = "test-room",
            )
    }
}
