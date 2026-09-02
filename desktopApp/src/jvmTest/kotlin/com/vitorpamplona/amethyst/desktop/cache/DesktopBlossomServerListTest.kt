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
package com.vitorpamplona.amethyst.desktop.cache

import com.vitorpamplona.amethyst.commons.model.nipB7Blossom.BlossomServerListState
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * The desktop app must load the user's media server list from the same event
 * kind the Amethyst mobile app uses — the NIP-B7 [BlossomServersEvent]
 * (kind 10063). These tests verify that an incoming kind-10063 event lands in
 * [DesktopLocalCache] and that the shared [BlossomServerListState] surfaces the
 * declared servers.
 */
class DesktopBlossomServerListTest {
    private val relayUrl = NormalizedRelayUrl("wss://relay.test/")

    private suspend fun signedServerList(
        servers: List<String>,
        signer: NostrSignerInternal,
        createdAt: Long = 1_700_000_000,
    ): BlossomServersEvent = BlossomServersEvent.create(servers, signer, createdAt)

    @Test
    fun `consume stores an incoming kind 10063 event in the addressable cache`() =
        runTest {
            val cache = DesktopLocalCache()
            val signer = NostrSignerInternal(KeyPair())
            val servers = listOf("https://blossom.example.com", "https://cdn.example.org")
            val event = signedServerList(servers, signer)

            // Pin the note across consume -> read: the cache holds notes via
            // SoftReference (LargeSoftCache), so without a strong reference a GC
            // between the two calls evicts the note and the re-fetch mints an
            // empty one (seen on memory-tight CI runners).
            val pinned = cache.getOrCreateAddressableNote(event.address())
            cache.consume(event, relayUrl)

            val stored = pinned.event as? BlossomServersEvent
            assertNotNull(stored, "kind 10063 event must be stored in the addressable cache")
            assertEquals(servers, stored.servers())
        }

    @Test
    fun `an older event does not overwrite a newer one`() =
        runTest {
            val cache = DesktopLocalCache()
            val signer = NostrSignerInternal(KeyPair())
            val newer = signedServerList(listOf("https://new.example.com"), signer, createdAt = 2_000)
            val older = signedServerList(listOf("https://old.example.com"), signer, createdAt = 1_000)

            val pinned = cache.getOrCreateAddressableNote(newer.address())
            cache.consume(newer, relayUrl)
            cache.consume(older, relayUrl)

            val stored = pinned.event as? BlossomServersEvent
            assertEquals(listOf("https://new.example.com"), stored?.servers())
        }

    @Test
    fun `BlossomServerListState surfaces the servers from the cached event`() =
        // Deliberately runBlocking, not runTest: BlossomServerListState.flow hops through
        // real Dispatchers.IO (flowOn) into a stateIn collector. Under runTest the awaiting
        // coroutine and the stateIn scope sit on the virtual-time scheduler, and the IO
        // handoff can park while that scheduler is idle — runTest then aborts with
        // UncompletedCoroutinesError (seen on CI). Real dispatchers end-to-end make the
        // await deterministic; withTimeout keeps a hang from stalling the suite.
        runBlocking {
            val cache = DesktopLocalCache()
            val signer = NostrSignerInternal(KeyPair())
            val servers = listOf("https://blossom.example.com")
            val event = signedServerList(servers, signer)
            // Pin the note across the consume -> construct window: the cache holds
            // notes via SoftReference (LargeSoftCache), and a GC under CI memory
            // pressure evicted the consumed note before the state's
            // getOrCreateAddressableNote re-fetched it, minting a fresh EMPTY note
            // (observed as flow=[] / getter=null on the macOS runner). Production
            // is immune the same way: BlossomServerListState pins blossomListNote
            // as a field for its lifetime.
            val pinned = cache.getOrCreateAddressableNote(event.address())
            cache.consume(event, relayUrl)
            assertNotNull(pinned.event, "consume must store the event before the state is built")

            // Unconfined on purpose: the state's stateIn(Eagerly) collector then starts
            // synchronously and resumes directly on the flowOn(IO) producer thread, so the
            // test depends only on the 64+-thread IO pool. With the default scope the
            // collector needs a Dispatchers.Default worker (4 on CI), and any of the other
            // ~330 desktop tests in this JVM leaking a blocked Default thread starved it —
            // that is the 30s timeout this test hit on CI after surviving local runs.
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val state =
                    BlossomServerListState(
                        signer = signer,
                        cache = cache,
                        scope = scope,
                    )

                // Await the IO-backed stateIn subscription first: the flow settling proves the
                // state finished wiring, after which the synchronous getter must agree.
                try {
                    withTimeout(30_000) {
                        assertEquals(servers, state.flow.first { it.isNotEmpty() })
                    }
                } catch (e: TimeoutCancellationException) {
                    fail(
                        "flow never surfaced the servers: flow.value=${state.flow.value}, " +
                            "getter=${state.getBlossomServersList()?.servers()}",
                        e,
                    )
                }
                assertEquals(servers, state.getBlossomServersList()?.servers())
            } finally {
                scope.cancel()
            }
        }
}
