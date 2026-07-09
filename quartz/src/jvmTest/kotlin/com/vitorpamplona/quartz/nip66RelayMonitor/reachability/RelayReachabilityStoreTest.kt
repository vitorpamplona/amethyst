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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.NetworkType
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayReachabilityStoreTest {
    private fun store() =
        EventStore(
            dbName = null,
            indexStrategy = DefaultIndexingStrategy(),
        )

    private fun cache(store: EventStore) =
        RelayReachabilityStore(
            store = store,
            signer = NostrSignerInternal(KeyPair()),
            ttlSeconds = 3600,
        )

    private val live1 = RelayUrlNormalizer.normalize("wss://alive.example.com")
    private val live2 = RelayUrlNormalizer.normalize("wss://also-alive.example.com")
    private val dead1 = RelayUrlNormalizer.normalize("wss://dead.example.com")
    private val dead2 = RelayUrlNormalizer.normalize("wss://gone.example.com")
    private val onion = RelayUrlNormalizer.normalize("wss://abc.onion")
    private val onionPath = RelayUrlNormalizer.normalize("wss://abc.onion/npub1x")

    // Contains the literal ".onion" as a substring but is NOT a Tor host — a loose
    // `contains(".onion")` would misclassify it; the normalizer's isOnion must not.
    private val fakeOnion = RelayUrlNormalizer.normalize("wss://relay.onionfake.com")

    @Test
    fun recordsAndReloadsReachability() =
        runBlocking {
            Secp256k1Instance
            val store = store()
            val cache = cache(store)
            val now = 1_000_000L

            cache.record(reachable = setOf(live1, live2), dead = setOf(dead1, dead2), now = now)

            val snap = cache.snapshot(now = now)
            assertEquals(setOf(live1, live2), snap.live)
            assertEquals(setOf(dead1, dead2), snap.dead)
            assertTrue(snap.isKnownDead(dead1))
            assertFalse(snap.isKnownDead(live1))
        }

    @Test
    fun aFreshSuccessfulOpenOverridesAnEarlierDeadMark() =
        runBlocking {
            Secp256k1Instance
            val store = store()
            val cache = cache(store)

            // Marked dead first, then seen alive a second later (addressable replace).
            cache.record(reachable = emptySet(), dead = setOf(dead1), now = 1_000L)
            cache.record(reachable = setOf(dead1), dead = emptySet(), now = 1_001L)

            val snap = cache.snapshot(now = 1_001L)
            assertTrue(dead1 in snap.live)
            assertFalse(snap.isKnownDead(dead1))
        }

    @Test
    fun recordsOlderThanTheTtlAreIgnored() =
        runBlocking {
            Secp256k1Instance
            val store = store()
            val cache = cache(store) // ttl = 3600s

            cache.record(reachable = emptySet(), dead = setOf(dead1), now = 1_000L)

            // "now" is well past the 1h TTL from when dead1 was recorded.
            val snap = cache.snapshot(now = 1_000L + 3601L)
            assertFalse(snap.isKnownDead(dead1))
            assertEquals(0, snap.size)
        }

    @Test
    fun onionRelayIsTaggedTorNetwork() {
        assertEquals(NetworkType.TOR, RelayReachabilityStore.networkTypeOf(onion))
        assertEquals(NetworkType.TOR, RelayReachabilityStore.networkTypeOf(onionPath))
        assertEquals(NetworkType.CLEARNET, RelayReachabilityStore.networkTypeOf(live1))
        // A host that merely contains ".onion" as a substring is clearnet, not Tor.
        assertEquals(NetworkType.CLEARNET, RelayReachabilityStore.networkTypeOf(fakeOnion))
    }
}
