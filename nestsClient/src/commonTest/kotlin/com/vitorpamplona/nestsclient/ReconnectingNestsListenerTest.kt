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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.nestsclient.moq.MoqObject
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.moq.SubscribeOk
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconnectingNestsListenerTest {
    private val room =
        NestsRoomConfig(
            authBaseUrl = "https://relay.example.com/api/v1/nests",
            endpoint = "https://relay.example.com/moq",
            hostPubkey = "0".repeat(64),
            roomId = "abc",
        )
    private val signer = NostrSignerInternal(KeyPair())

    // The custom `connector` lambda below short-circuits the real
    // mintToken / WebTransport handshake, so these two sentinels
    // only satisfy the function signature — they're never invoked.
    private val httpClient =
        object : NestsClient {
            override suspend fun mintToken(
                room: NestsRoomConfig,
                publish: Boolean,
                signer: NostrSigner,
            ): String = error("not invoked — connector overrides")
        }
    private val transport =
        object : WebTransportFactory {
            override suspend fun connect(
                authority: String,
                path: String,
                bearerToken: String?,
            ): WebTransportSession = error("not invoked — connector overrides")
        }

    /**
     * Scripted [NestsListener] that opens in [NestsListenerState.Connected]
     * by default and exposes a [MutableSharedFlow] to push frames into
     * the next [SubscribeHandle.objects] returned from [subscribeSpeaker].
     */
    private class ScriptedListener(
        connectedRoom: NestsRoomConfig,
        moqVersion: Long = 1,
    ) : NestsListener {
        private val mutableState =
            MutableStateFlow<NestsListenerState>(
                NestsListenerState.Connected(connectedRoom, moqVersion),
            )
        override val state: StateFlow<NestsListenerState> = mutableState.asStateFlow()
        val frames = MutableSharedFlow<MoqObject>(extraBufferCapacity = 16)

        // Atomic counters — the wrapper's pump runs on Dispatchers.Default
        // while the test reads the count from the runBlocking thread, so
        // a plain `var subscribeCount` would risk stale reads.
        private val _subscribeCount =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        private val _unsubscribeCount =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        val subscribeCount: Int get() = _subscribeCount.get()
        val unsubscribeCount: Int get() = _unsubscribeCount.get()

        override suspend fun subscribeSpeaker(
            speakerPubkeyHex: String,
            maxLatencyMs: Long,
        ): SubscribeHandle {
            val id = _subscribeCount.incrementAndGet().toLong()
            return SubscribeHandle(
                subscribeId = id,
                trackAlias = id,
                ok =
                    SubscribeOk(
                        subscribeId = id,
                        expiresMs = 0L,
                        groupOrder = 0x01,
                        contentExists = false,
                    ),
                objects = frames,
                unsubscribeAction = { _unsubscribeCount.incrementAndGet() },
            )
        }

        override suspend fun close() {
            mutableState.value = NestsListenerState.Closed
        }

        fun fail(reason: String) {
            mutableState.value = NestsListenerState.Failed(reason)
        }
    }

    private fun frame(payload: ByteArray): MoqObject =
        MoqObject(
            trackAlias = 1L,
            groupId = 0L,
            objectId = 0L,
            publisherPriority = 0,
            payload = payload,
        )

    // The orchestrator + pump jobs are real coroutines on a real
    // dispatcher — runTest's virtual clock interacts badly with the
    // orchestrator's `delay(...)` reconnect loop (the test scheduler
    // doesn't auto-advance through the StateFlow + delay chain
    // under UnconfinedTestDispatcher). Use a dedicated supervisor
    // scope and real time; tests stay fast (single-digit ms) because
    // the policy's initialDelayMs is set to 1.
    @Test
    fun subscribeSpeaker_survives_session_swap() =
        runBlocking {
            // Plain SupervisorJob — NOT plus coroutineContext, otherwise
            // `scope.cancel()` in the finally block would propagate up
            // through the runBlocking parent and fail the test.
            val scope = CoroutineScope(SupervisorJob())
            try {
                val first = ScriptedListener(room)
                val second = ScriptedListener(room)
                val listenersInOrder = mutableListOf(first, second)

                val reconnecting =
                    connectReconnectingNestsListener(
                        httpClient = httpClient,
                        transport = transport,
                        scope = scope,
                        room = room,
                        signer = signer,
                        policy = NestsReconnectPolicy(initialDelayMs = 1L),
                        connector = { listenersInOrder.removeAt(0) },
                    )

                withTimeout(5_000L) {
                    reconnecting.state.first { it is NestsListenerState.Connected }
                }

                val handle = reconnecting.subscribeSpeaker("speaker-pubkey")

                // Signal when the consumer has actually subscribed to
                // the outer frames flow. The wrapper's frames SharedFlow
                // is replay=0, so an emit before the consumer registers
                // its subscription is silently dropped — observed on
                // macOS where the async coroutine isn't scheduled
                // before the test thread races into the first emit.
                val consumerSubscribed = CompletableDeferred<Unit>()

                // Counter for frames the consumer has actually observed.
                // Needed to break the FRAME1-delivery race: emit() into
                // first.frames is non-suspending and just enqueues into
                // the pump's slot. If we trigger a session swap before
                // the pump's collect lambda runs (`frames.emit(it)` to
                // the wrapper), collectLatest cancels iteration 1 mid-
                // resume and FRAME1 is lost — consumer ends with 1/2
                // frames and the async's withTimeout fires. The
                // collector is single-coroutine so a plain StateFlow
                // update is safe; the test thread reads it via .first.
                val consumerProgress = MutableStateFlow(0)

                // The wrapper backs handle.objects with a SharedFlow
                // (frames.asSharedFlow); the cast lets us use
                // SharedFlow.onSubscription, which fires AFTER the
                // collector is registered (Flow.onStart fires before).
                @Suppress("UNCHECKED_CAST")
                val objectsAsShared = handle.objects as SharedFlow<MoqObject>
                val collected =
                    scope.async {
                        withTimeout(5_000L) {
                            objectsAsShared
                                .onSubscription { consumerSubscribed.complete(Unit) }
                                .take(2)
                                .onEach { consumerProgress.value += 1 }
                                .toList()
                        }
                    }

                withTimeout(5_000L) { consumerSubscribed.await() }

                // Wait for the pump to actually subscribe to first.frames
                // before we emit upstream. first.subscribeCount is
                // incremented inside opener(first) — BEFORE the pump
                // reaches `handle.objects.collect` — so subscribeCount
                // alone doesn't prove the pump is listening.
                // subscriptionCount on first.frames flips to 1 only once
                // the pump's collect actually registers.
                withTimeout(5_000L) {
                    first.frames.subscriptionCount.first { it > 0 }
                }

                first.frames.emit(frame(byteArrayOf(0x01)))

                // Wait for FRAME1 to traverse pump → wrapper.frames →
                // consumer collector. Without this sync the next
                // `first.fail(...)` can race ahead and trigger
                // collectLatest cancellation of pump-iteration-1 while
                // FRAME1 is still queued in first.frames; the cancel
                // interrupts the pump's resume before its lambda runs
                // and FRAME1 never reaches the wrapper. Consumer then
                // observes only FRAME2 (1 frame ≠ take(2)) and the
                // async's withTimeout fires after 5 s.
                withTimeout(5_000L) {
                    consumerProgress.first { it >= 1 }
                }

                // Force a reconnect: fail the first listener, the
                // orchestrator opens the next one.
                first.fail("scripted-disconnect")
                // Both ScriptedListeners report the same `room` and
                // `negotiatedMoqVersion`, so we can't differentiate
                // first.Connected from second.Connected via state alone.
                // The pump re-issuing the subscription against the new
                // session is observable via second.frames.subscriptionCount
                // (same rationale as first.frames above — wait for the
                // collect, not just the subscribeSpeaker call).
                withTimeout(5_000L) {
                    second.frames.subscriptionCount.first { it > 0 }
                }

                second.frames.emit(frame(byteArrayOf(0x02)))

                val result = collected.await()
                assertEquals(2, result.size)
                assertEquals(0x01.toByte(), result[0].payload[0])
                assertEquals(0x02.toByte(), result[1].payload[0])

                // Both sessions saw exactly one subscribeSpeaker call —
                // the wrapper re-issues against the new session, not the
                // old one.
                assertEquals(1, first.subscribeCount)
                assertEquals(1, second.subscribeCount)

                handle.unsubscribe()
                assertEquals(1, second.unsubscribeCount)

                reconnecting.close()
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun unsubscribe_before_session_swap_releases_handle() =
        runBlocking {
            // Plain SupervisorJob — NOT plus coroutineContext, otherwise
            // `scope.cancel()` in the finally block would propagate up
            // through the runBlocking parent and fail the test.
            val scope = CoroutineScope(SupervisorJob())
            try {
                val first = ScriptedListener(room)
                val reconnecting =
                    connectReconnectingNestsListener(
                        httpClient = httpClient,
                        transport = transport,
                        scope = scope,
                        room = room,
                        signer = signer,
                        connector = { first },
                    )
                withTimeout(5_000L) {
                    reconnecting.state.first { it is NestsListenerState.Connected }
                }

                val handle = reconnecting.subscribeSpeaker("speaker-pubkey")

                // Wait until the pump opened the underlying subscription
                // AND committed liveHandleRef. subscribeCount alone
                // increments inside opener(first), which runs BEFORE
                // the pump sets liveHandleRef + starts collecting from
                // first.frames. If we unsubscribe while liveHandleRef
                // is still null the inner unsubscribe never fires and
                // the assertion below hangs/fails on macOS.
                // first.frames.subscriptionCount flips to 1 only once
                // the pump's collect actually registers, which is
                // strictly after liveHandleRef.set(handle).
                withTimeout(5_000L) {
                    first.frames.subscriptionCount.first { it > 0 }
                }
                assertTrue(first.subscribeCount >= 1, "pump should have opened the underlying sub")

                handle.unsubscribe()
                assertEquals(1, first.unsubscribeCount)

                reconnecting.close()
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun proactive_token_refresh_recycles_listener_without_failure_state() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            try {
                val first = ScriptedListener(room)
                val second = ScriptedListener(room)
                val third = ScriptedListener(room)
                val listenersInOrder = mutableListOf(first, second, third)

                // Track every state the wrapper surfaces. The contract
                // for proactive refresh is that we recycle WITHOUT
                // ever flipping to Reconnecting / Failed — the new
                // session is up before the old one's token would have
                // expired.
                val seenStates = mutableListOf<NestsListenerState>()

                val reconnecting =
                    connectReconnectingNestsListener(
                        httpClient = httpClient,
                        transport = transport,
                        scope = scope,
                        room = room,
                        signer = signer,
                        // 50 ms refresh window — small enough for the
                        // test to fire it immediately, large enough
                        // that the orchestrator's first Connected
                        // observation lands before the timeout.
                        tokenRefreshAfterMs = 50L,
                        connector = { listenersInOrder.removeAt(0) },
                    )

                // Watch state transitions — must NOT see Reconnecting
                // or Failed at any point during a clean refresh.
                val watcher =
                    scope.launch {
                        reconnecting.state.collect { seenStates += it }
                    }

                withTimeout(5_000L) {
                    reconnecting.state.first { it is NestsListenerState.Connected }
                }

                // Wait for at least 2 underlying listeners (one for
                // initial, one after first refresh).
                withTimeout(5_000L) {
                    while (listenersInOrder.size > 1) kotlinx.coroutines.delay(10)
                }

                // Verify: the wrapper's outward state never went to
                // Reconnecting or Failed during the refresh — the
                // user-visible UI stays Connected throughout.
                val sawReconnecting = seenStates.any { it is NestsListenerState.Reconnecting }
                val sawFailed = seenStates.any { it is NestsListenerState.Failed }
                assertTrue(
                    !sawReconnecting && !sawFailed,
                    "proactive refresh must not surface Reconnecting/Failed; saw=$seenStates",
                )

                // Verify: the previous listener was actually closed
                // (state.value should reach Closed for first/second
                // before the test ends).
                withTimeout(5_000L) {
                    first.state.first { it is NestsListenerState.Closed }
                }

                watcher.cancel()
                reconnecting.close()
            } finally {
                scope.cancel()
            }
        }
}
