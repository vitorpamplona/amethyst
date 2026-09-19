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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.spec00Coordinator.ICoordinator
import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * One session per coordinator, for one account.
 *
 * The rules under test are all consequences of one fact: a `gid`, a `kp_ref`
 * and a cursor mean nothing outside the coordinator that issued them. Break
 * that and the failures are not loud — one group answers for another, or two
 * managers fork the same ratchet tree — so each rule gets a test that fails
 * for the right reason.
 */
@OptIn(ExperimentalEncodingApi::class)
class CordnCoordinatorRegistryTest : CordnTransportHarness() {
    private val gid = "1c4d3e2f-5a6b-4c7d-8e9f-0a1b2c3d4e5f"

    /** One identity across every coordinator, as an account really is. */
    private val account = Account()

    /** Counts opens, so "the same session" can be told from "an equal one". */
    private inner class CountingFactory : CordnCoordinatorScopeFactory {
        var opened = 0
        var closed = 0
        private val groupStores = mutableMapOf<HexKey, CordnGroupStore>()
        private val keyStores = mutableMapOf<HexKey, CordnKeyPackageStore>()

        override suspend fun open(
            accountPubKey: HexKey,
            config: CoordinatorConfig,
        ): CordnCoordinatorScope {
            // Opening really suspends: production reaches a relay and an
            // encrypted store. Without this the in-memory fixture runs to
            // completion atomically and no concurrency test can observe
            // anything — a second caller never gets to look at the map.
            yield()
            opened++
            return object : CordnCoordinatorScope {
                override val coordinator: ICoordinator = account.clientFor(config.pubKey)

                // Keyed by coordinator, and kept across a reopen: the stores are
                // what survives a transport change, which is the whole reason a
                // relay edit does not cost the user their groups.
                override val groupStore = groupStores.getOrPut(config.pubKey) { InMemoryCordnGroupStore() }
                override val keyPackageStore = keyStores.getOrPut(config.pubKey) { InMemoryCordnKeyPackageStore() }

                override suspend fun close() {
                    closed++
                }
            }
        }
    }

    private fun registry(factory: CordnCoordinatorScopeFactory) = CordnCoordinatorRegistry(account.pubKey, factory)

    @Test
    fun `asking twice for one coordinator returns the same session`() =
        runTest {
            // Not a cache: a second CordnGroupManager would deserialise its own
            // copy of every MlsGroup from the same store, and both would commit
            // against the same epoch. MLS does not recover from a forked
            // ratchet tree — every later message simply fails to decrypt.
            val factory = CountingFactory()
            val registry = registry(factory)

            val first = driving { registry.session(config) }
            val second = driving { registry.session(config) }

            assertSame(first, second)
            assertSame(first.manager, second.manager)
            assertEquals(1, factory.opened, "the transport must not be opened twice")
        }

    @Test
    fun `concurrent callers do not get two sessions`() =
        runTest {
            // The ordinary case, not a race worth dismissing: the group list and
            // the sync loop both reach for a session at launch.
            val factory = CountingFactory()
            val registry = registry(factory)

            val sessions =
                driving {
                    coroutineScope {
                        List(8) { async { registry.session(config) } }.awaitAll()
                    }
                }

            sessions.forEach { assertSame(sessions.first(), it) }
            assertEquals(1, factory.opened)
        }

    @Test
    fun `the same gid on two coordinators is two groups`() =
        runTest {
            // spec/00.md §4: a gid is the caller's to choose and unique only
            // within one coordinator. Two of them picking "abc" is expected, and
            // they are unrelated groups. A registry keyed by gid instead of by
            // coordinator would let one answer for the other.
            val factory = CountingFactory()
            val registry = registry(factory)
            val other = config.copy(pubKey = secondCoordinatorPubKey)

            val here = driving { registry.session(config) }
            val there = driving { registry.session(other) }

            driving { here.manager.createGroup(gid, CordnGroupMetadata(name = "Ours", adminPubkeys = listOf(registry.accountPubKey))) }

            assertEquals(setOf(gid), here.manager.gids.value)
            assertTrue(
                there.manager.gids.value
                    .isEmpty(),
                "the other coordinator's session must not see it",
            )
            assertNull(there.manager.group(gid))
            assertEquals(2, factory.opened)
        }

    @Test
    fun `editing a coordinator's relays keeps its groups`() =
        runTest {
            // A user correcting a relay or renaming a coordinator has not made
            // it a different coordinator. The transport is bound to the relays
            // so it reopens; the stores are not, so the groups come back.
            val factory = CountingFactory()
            val registry = registry(factory)

            val before = driving { registry.session(config) }
            driving { before.manager.createGroup(gid, CordnGroupMetadata(name = "Kept", adminPubkeys = listOf(registry.accountPubKey))) }

            val moved = config.copy(relays = listOf(RelayUrlNormalizer.normalizeOrNull("wss://other.example.com")!!), label = "Renamed")
            val after = driving { registry.session(moved) }

            assertEquals(2, factory.opened, "the transport is bound to the relays, so it reopens")
            assertEquals(1, factory.closed, "and the old one is released, not leaked")
            assertEquals(setOf(gid), after.manager.gids.value, "but the group state survived")
            assertNotNull(after.manager.group(gid))
            assertEquals(
                "Renamed",
                registry.coordinators.value
                    .single()
                    .label,
            )
        }

    @Test
    fun `forgetting a coordinator closes it and keeps its state`() =
        runTest {
            // cordn-web separates removing a coordinator from purging it. Wiping
            // the stores here would make the undo impossible, so forget only
            // closes the wire.
            val factory = CountingFactory()
            val registry = registry(factory)

            val session = driving { registry.session(config) }
            driving { session.manager.createGroup(gid, CordnGroupMetadata(name = "Undo me", adminPubkeys = listOf(registry.accountPubKey))) }

            registry.forget(config.pubKey)

            assertEquals(1, factory.closed)
            assertNull(registry.sessionOrNull(config.pubKey))
            assertTrue(registry.coordinators.value.isEmpty())

            val readded = driving { registry.session(config) }
            assertEquals(setOf(gid), readded.manager.gids.value, "re-adding must find the groups again")
        }

    @Test
    fun `restore closes the coordinators that are no longer configured`() =
        runTest {
            // Otherwise a coordinator the user removed on another launch comes
            // back to life because its session happened to still be open.
            val factory = CountingFactory()
            val registry = registry(factory)
            val other = config.copy(pubKey = secondCoordinatorPubKey)

            driving { registry.restore(listOf(config, other)) }
            assertEquals(2, registry.coordinators.value.size)

            driving { registry.restore(listOf(other)) }

            assertEquals(listOf(other.pubKey), registry.coordinators.value.map { it.pubKey })
            assertNull(registry.sessionOrNull(config.pubKey))
            assertEquals(1, factory.closed)
        }

    @Test
    fun `a session knows its groups without being told to restore`() =
        runTest {
            // A session that reports no groups is indistinguishable from a
            // failure, and the first commit it makes for a group it forgot
            // starts from epoch zero. Leaving restore() to the caller makes
            // that a one-line omission away.
            val factory = CountingFactory()
            val first = registry(factory)
            val second = CordnCoordinatorRegistry(first.accountPubKey, factory)

            val opened = driving { first.session(config) }
            driving { opened.manager.createGroup(gid, CordnGroupMetadata(name = "Found", adminPubkeys = listOf(first.accountPubKey))) }
            driving { opened.keyPackages.publishNew() }
            val refs = opened.keyPackages.published.value

            // A fresh registry over the same stores — what a relaunch looks like.
            val relaunched = driving { second.session(config) }

            assertEquals(setOf(gid), relaunched.manager.gids.value)
            assertEquals(refs, relaunched.keyPackages.published.value)
        }

    @Test
    fun `logging out closes every coordinator`() =
        runTest {
            val factory = CountingFactory()
            val registry = registry(factory)

            driving { registry.restore(listOf(config, config.copy(pubKey = secondCoordinatorPubKey))) }
            registry.close()

            assertEquals(2, factory.closed)
            assertTrue(registry.coordinators.value.isEmpty())
            assertNull(registry.sessionOrNull(config.pubKey))
        }
}
