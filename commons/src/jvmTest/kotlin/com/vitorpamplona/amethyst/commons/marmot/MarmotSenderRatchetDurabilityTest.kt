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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.groups.MlsGroupStateStore
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.mls.group.OwnSenderRatchet
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The sender ratchet position is stored as its own small record instead of
 * rewriting the whole group state on every send. That is a pure optimisation
 * only if it keeps one property exactly: **the position never goes backwards**.
 *
 * A rewind is not a lost message. The SecretTree derives the AEAD key and nonce
 * from (secret, generation), so re-emitting a generation this leaf has already
 * published encrypts a new plaintext under a key+nonce pair already used — which
 * for an AEAD is a break, not a glitch. Everything here exists to pin that edge.
 */
class MarmotSenderRatchetDurabilityTest {
    private val nostrGroupId = "f".repeat(64)

    private class Fixture(
        val store: MlsGroupStateStore = SnapshotStateStore(),
        val signer: NostrSignerInternal = NostrSignerInternal(KeyPair()),
    ) {
        val manager = MarmotManager(signer, store, SnapshotMessageStore(), SnapshotBundleStore(), publisher = ACCEPTING_RELAY)
    }

    private suspend fun Fixture.createGroup() =
        manager.createGroup(
            nostrGroupId,
            MarmotGroupData(nostrGroupId = nostrGroupId, name = "ratchet", relays = listOf("wss://relay.invalid")),
        )

    /** Where this leaf's application ratchet currently is, in memory. */
    private fun Fixture.generation(): Int =
        manager.groupManager
            .getGroup(nostrGroupId)
            ?.exportOwnSenderRatchet()
            ?.applicationGeneration ?: -1

    private suspend fun Fixture.send(text: String) = manager.buildGroupMessage(nostrGroupId, manager.buildTextRumor(text))

    @Test
    fun `a send records its position without rewriting the group state`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            val stateAfterCreate = f.store.load(nostrGroupId)

            f.send("one")

            // The state blob is untouched; the small record carries the advance.
            assertTrue(stateAfterCreate.contentEquals(f.store.load(nostrGroupId)), "a send must not rewrite the group state")

            val record = f.store.loadSenderRatchet(nostrGroupId)
            assertNotNull(record, "a send must persist its ratchet position somewhere")
            assertEquals(1, OwnSenderRatchet.decodeTls(record).applicationGeneration)
        }

    @Test
    fun `the record is far smaller than the state it replaces`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            f.send("one")

            val state = f.store.load(nostrGroupId)!!.size
            val record = f.store.loadSenderRatchet(nostrGroupId)!!.size
            assertTrue(record * 4 < state, "expected the per-send record ($record B) to be a fraction of the state ($state B)")
        }

    @Test
    fun `a restart between two sends does not reuse a generation`() =
        runBlocking {
            val store = SnapshotStateStore()
            val keys = KeyPair()

            val before = Fixture(store, NostrSignerInternal(keys))
            before.createGroup()
            before.send("one")
            before.send("two")
            val reached = before.generation()
            assertEquals(2, reached)

            // Restart: a fresh manager over the same store, as a cold process gets.
            val after = Fixture(store, NostrSignerInternal(keys))
            after.manager.groupManager.restoreAll()

            assertEquals(
                reached,
                after.generation(),
                "the restored ratchet must be where the last send left it, or the next send reuses a key+nonce",
            )

            after.send("three")
            assertEquals(reached + 1, after.generation())
        }

    @Test
    fun `a restart with no record falls back to the state's own position`() =
        runBlocking {
            val store = SnapshotStateStore()
            val keys = KeyPair()

            val before = Fixture(store, NostrSignerInternal(keys))
            before.createGroup()

            // A group that has never sent has nothing to record, and the state
            // alone is the whole truth.
            val after = Fixture(store, NostrSignerInternal(keys))
            after.manager.groupManager.restoreAll()
            assertEquals(0, after.generation().coerceAtLeast(0))

            after.send("first ever")
            assertEquals(1, after.generation())
        }

    @Test
    fun `a record from another epoch is ignored rather than applied`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            f.send("one")

            val group = f.manager.groupManager.getGroup(nostrGroupId)!!
            val current = group.exportOwnSenderRatchet()!!

            // Same leaf, same position, wrong epoch: the ratchet it describes no
            // longer exists, so applying it would install key material from a
            // key schedule this group has moved off.
            val stale = current.copy(epoch = current.epoch + 7, applicationGeneration = current.applicationGeneration + 50)
            assertTrue(!group.restoreOwnSenderRatchet(stale), "a record from another epoch must not be applied")
            assertEquals(current.applicationGeneration, f.generation())
        }

    @Test
    fun `a record from a rival state at the SAME epoch is ignored`() =
        runBlocking {
            // Convergence swaps one epoch-N state for a different epoch-N state
            // when it resolves a fork (MlsGroupManager.installState says so in
            // as many words). The epoch NUMBER therefore cannot be the whole
            // guard: a record left by the losing branch would otherwise install
            // key material from a key schedule this group has moved off.
            val f = Fixture()
            f.createGroup()
            f.send("one")

            val group = f.manager.groupManager.getGroup(nostrGroupId)!!
            val current = group.exportOwnSenderRatchet()!!

            val rival =
                current.copy(
                    treeBinder = current.treeBinder.copyOf().also { it[0] = (it[0] + 1).toByte() },
                    applicationGeneration = current.applicationGeneration + 9,
                )

            assertTrue(!group.restoreOwnSenderRatchet(rival), "a record bound to another SecretTree must not be applied")
            assertEquals(current.applicationGeneration, f.generation())
        }

    @Test
    fun `a record behind the live position never rewinds it`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            f.send("one")
            f.send("two")
            f.send("three")

            val group = f.manager.groupManager.getGroup(nostrGroupId)!!
            val current = group.exportOwnSenderRatchet()!!
            val behind = current.copy(applicationGeneration = 0)

            assertTrue(!group.restoreOwnSenderRatchet(behind), "an older record must not move the ratchet back")
            assertEquals(current.applicationGeneration, f.generation())
        }

    @Test
    fun `a record ahead of the live position skips forward`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            f.send("one")

            val group = f.manager.groupManager.getGroup(nostrGroupId)!!
            val current = group.exportOwnSenderRatchet()!!
            val ahead = current.copy(applicationGeneration = current.applicationGeneration + 5)

            // Skipping forward wastes key material and is always safe; it is the
            // direction a restore is allowed to move.
            assertTrue(group.restoreOwnSenderRatchet(ahead))
            assertEquals(current.applicationGeneration + 5, f.generation())
        }

    @Test
    fun `a store without the split still records every send`() =
        runBlocking {
            // Defaulting saveSenderRatchet to false must fall back to a full
            // state write, not silently drop the position.
            val store = FullWriteOnlyStore()
            val keys = KeyPair()

            val before = Fixture(store, NostrSignerInternal(keys))
            before.createGroup()
            before.send("one")
            before.send("two")
            val reached = before.generation()

            val after = Fixture(store, NostrSignerInternal(keys))
            after.manager.groupManager.restoreAll()
            assertEquals(reached, after.generation())
        }

    /** A store that has not adopted the split: the interface defaults apply. */
    private class FullWriteOnlyStore : MlsGroupStateStore {
        private val inner = SnapshotStateStore()

        override suspend fun save(
            nostrGroupId: String,
            state: ByteArray,
        ) = inner.save(nostrGroupId, state)

        override suspend fun load(nostrGroupId: String) = inner.load(nostrGroupId)

        override suspend fun delete(nostrGroupId: String) = inner.delete(nostrGroupId)

        override suspend fun listGroups() = inner.listGroups()

        override suspend fun saveRetainedEpochs(
            nostrGroupId: String,
            retainedSecrets: List<ByteArray>,
        ) = inner.saveRetainedEpochs(nostrGroupId, retainedSecrets)

        override suspend fun loadRetainedEpochs(nostrGroupId: String) = inner.loadRetainedEpochs(nostrGroupId)
    }
}
