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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.NetworkType
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RequirementTag
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RttType
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
    // ---- one address, more than one writer ---------------------------------

    private suspend fun tagsOf(
        store: EventStore,
        signer: NostrSignerInternal,
        relay: NormalizedRelayUrl,
    ): List<Array<String>> =
        store
            .query<RelayDiscoveryEvent>(
                Filter(kinds = listOf(RelayDiscoveryEvent.KIND), authors = listOf(signer.pubKey), tags = mapOf("d" to listOf(relay.url))),
            ).maxByOrNull { it.createdAt }
            ?.tags
            ?.toList()
            .orEmpty()

    private fun names(tags: List<Array<String>>) = tags.mapNotNull { it.firstOrNull() }.toSet()

    /**
     * A 30166 is addressable, so this monitor has one record per relay — and it
     * is not necessarily the only thing writing per-relay knowledge under that
     * identity. A record rebuilt from this writer's own tags deletes the rest,
     * and the loss is invisible: the event still signs and still parses.
     */
    @Test
    fun `an update keeps tags this writer does not own`() =
        runBlocking {
            val store = store()
            val signer = NostrSignerInternal(KeyPair())
            val cache = RelayReachabilityStore(store, signer, ttlSeconds = 3600)

            cache.recordProbed(mapOf(live1 to 120L), emptySet(), now = 1_700_000_000)
            // Something else records what it knows about the same relay,
            // keeping what the monitor already put there.
            val existing = tagsOf(store, signer, live1)
            val withExtra =
                RelayDiscoveryEvent.build(live1, "", createdAt = 1_700_000_001) {
                    existing.forEach { if (it.firstOrNull() != "d") add(it) }
                    add(arrayOf("redirect", "wss://canonical.example.com/"))
                }
            store.insert(signer.sign(withExtra))

            cache.recordProbed(mapOf(live1 to 131L), emptySet(), now = 1_700_000_002)

            val after = tagsOf(store, signer, live1)
            assertTrue("redirect" in names(after), "the update erased another writer's tag: ${names(after)}")
            // ...and still replaced what it does own.
            assertEquals("131", after.first { it[0] == RttType.OPEN.tagName }[1])
        }

    /**
     * A store enforcing replaceable semantics rejects a record that is not
     * strictly newer than the one it replaces. Two writers inside one second,
     * or a peer whose clock runs ahead, are ordinary — and an update lost that
     * way looks exactly like one that had nothing to say.
     */
    @Test
    fun `an update lands even when the record it replaces is newer than the clock`() =
        runBlocking {
            val store = store()
            val signer = NostrSignerInternal(KeyPair())
            val cache = RelayReachabilityStore(store, signer, ttlSeconds = 3600)

            // Ordinary skew: the record ahead of our clock by seconds, which is
            // what two writers or a slightly fast peer produce.
            cache.recordProbed(mapOf(live1 to 120L), emptySet(), now = 1_700_000_010)
            cache.recordProbed(mapOf(live1 to 131L), emptySet(), now = 1_700_000_000)

            assertEquals("131", tagsOf(store, signer, live1).first { it[0] == RttType.OPEN.tagName }[1])
        }

    /**
     * Without a ceiling the bump is sticky: a record that once landed in the
     * future is derived from forever, so it never ages out of the TTL window
     * and relays enforcing future-timestamp limits reject every publish.
     */
    @Test
    fun `a record already far in the future is never pushed further ahead`() =
        runBlocking {
            val store = store()
            val signer = NostrSignerInternal(KeyPair())
            val cache = RelayReachabilityStore(store, signer, ttlSeconds = 3600)
            val now = 1_700_000_000L

            cache.recordProbed(mapOf(live1 to 120L), emptySet(), now = now + 86_400)
            cache.recordProbed(mapOf(live1 to 131L), emptySet(), now = now)

            val held =
                store
                    .query<RelayDiscoveryEvent>(
                        Filter(kinds = listOf(RelayDiscoveryEvent.KIND), authors = listOf(signer.pubKey), tags = mapOf("d" to listOf(live1.url))),
                    ).maxByOrNull { it.createdAt }
            assertEquals(now + 86_400, held?.createdAt, "the pathological stamp was carried forward instead of capped")
        }

    /** writeOne measures rtt-open only; deleting a read/write latency it never took is the loss this guards. */
    @Test
    fun `a reachable update keeps latencies it did not measure`() =
        runBlocking {
            val store = store()
            val signer = NostrSignerInternal(KeyPair())
            val cache = RelayReachabilityStore(store, signer, ttlSeconds = 3600)

            val observed = RelayObserver()
            observed.record(live1, true, 100L, null)
            observed.observationOf(live1)?.rttReadMs = 55L
            cache.record(observed.collectUnreported(), now = 1_700_000_000)
            assertTrue(RttType.READ.tagName in names(tagsOf(store, signer, live1)))

            cache.recordProbed(mapOf(live1 to 120L), emptySet(), now = 1_700_000_100)

            val after = tagsOf(store, signer, live1)
            assertEquals("55", after.first { it[0] == RttType.READ.tagName }[1], "rtt-read was deleted by a writer that never measured it")
            assertEquals("120", after.first { it[0] == RttType.OPEN.tagName }[1])
        }

    /** An observation proves `R auth` and nothing else; other requirements belong to whoever measured them. */
    @Test
    fun `an observation clears only the auth requirement`() =
        runBlocking {
            val store = store()
            val signer = NostrSignerInternal(KeyPair())
            val cache = RelayReachabilityStore(store, signer, ttlSeconds = 3600)

            cache.recordProbed(mapOf(live1 to 120L), emptySet(), now = 1_700_000_000)
            val existing = tagsOf(store, signer, live1)
            val withReqs =
                RelayDiscoveryEvent.build(live1, "", createdAt = 1_700_000_001) {
                    existing.forEach { if (it.firstOrNull() != "d") add(it) }
                    add(arrayOf(RequirementTag.TAG_NAME, "pow"))
                    add(arrayOf(RequirementTag.TAG_NAME, RelayReachabilityStore.AUTH_REQUIREMENT))
                }
            store.insert(signer.sign(withReqs))

            // Reached without a challenge: auth no longer holds, pow was never ours.
            val observed = RelayObserver()
            observed.record(live1, true, 100L, null)
            cache.record(observed.collectUnreported(), now = 1_700_000_002)

            val after = tagsOf(store, signer, live1).filter { it[0] == RequirementTag.TAG_NAME }.map { it[1] }
            assertTrue("pow" in after, "another writer's requirement was erased: " + after)
            assertTrue(RelayReachabilityStore.AUTH_REQUIREMENT !in after, "auth should have been cleared: " + after)
        }

    /** One `d` filter binds one host parameter per url, and callers pass the whole relay universe. */
    @Test
    fun `a flush wider than one query chunk still writes every relay`() =
        runBlocking {
            val store = store()
            val signer = NostrSignerInternal(KeyPair())
            val cache = RelayReachabilityStore(store, signer, ttlSeconds = 3600)
            val many = (0 until RelayReachabilityStore.RELAYS_PER_QUERY * 2 + 7).map { RelayUrlNormalizer.normalize("wss://r" + it + ".example.com") }

            cache.record(reachable = many.toSet(), dead = emptySet(), now = 1_700_000_000)

            assertEquals(many.size, cache.snapshot(now = 1_700_000_100).live.size)
        }

    /** A relay that went down must lose its rtt, or it still reads as live. */
    @Test
    fun `a dead update clears the rtt it replaces`() =
        runBlocking {
            val store = store()
            val signer = NostrSignerInternal(KeyPair())
            val cache = RelayReachabilityStore(store, signer, ttlSeconds = 3600)

            cache.recordProbed(mapOf(live1 to 120L), emptySet(), now = 1_700_000_000)
            cache.record(reachable = emptySet(), dead = setOf(live1), now = 1_700_000_100)

            assertTrue(RttType.OPEN.tagName !in names(tagsOf(store, signer, live1)))
            assertTrue(cache.snapshot(now = 1_700_000_200).isKnownDead(live1))
        }

    /** Only OUR records merge: republishing another monitor's tags under this key would launder their claims. */
    @Test
    fun `another monitor's record is not merged into ours`() =
        runBlocking {
            val store = store()
            val signer = NostrSignerInternal(KeyPair())
            val other = NostrSignerInternal(KeyPair())
            val cache = RelayReachabilityStore(store, signer, ttlSeconds = 3600)

            val theirs =
                RelayDiscoveryEvent.build(live1, "", createdAt = 1_700_000_000) {
                    add(arrayOf("redirect", "wss://not-ours.example.com/"))
                }
            store.insert(other.sign(theirs))

            cache.recordProbed(mapOf(live1 to 120L), emptySet(), now = 1_700_000_100)

            assertTrue("redirect" !in names(tagsOf(store, signer, live1)))
        }
}
