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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

            cache.consume(event, relayUrl)

            val stored = cache.getOrCreateAddressableNote(event.address()).event as? BlossomServersEvent
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

            cache.consume(newer, relayUrl)
            cache.consume(older, relayUrl)

            val stored = cache.getOrCreateAddressableNote(newer.address()).event as? BlossomServersEvent
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
            cache.consume(event, relayUrl)

            val scope = CoroutineScope(SupervisorJob())
            try {
                val state =
                    BlossomServerListState(
                        signer = signer,
                        cache = cache,
                        scope = scope,
                    )

                // Await the IO-backed stateIn subscription first: the flow settling proves the
                // state finished wiring, after which the synchronous getter must agree.
                withTimeout(30_000) {
                    assertEquals(servers, state.flow.first { it.isNotEmpty() })
                }
                assertEquals(servers, state.getBlossomServersList()?.servers())
            } finally {
                scope.cancel()
            }
        }
}
