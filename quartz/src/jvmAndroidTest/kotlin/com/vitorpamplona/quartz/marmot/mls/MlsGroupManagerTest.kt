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
package com.vitorpamplona.quartz.marmot.mls

import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * In-memory implementation of [MlsGroupStateStore] for testing.
 */
class InMemoryGroupStateStore : MlsGroupStateStore {
    private val states = mutableMapOf<String, ByteArray>()
    private val retainedEpochs = mutableMapOf<String, List<ByteArray>>()

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        states[nostrGroupId] = state
    }

    override suspend fun load(nostrGroupId: String): ByteArray? = states[nostrGroupId]

    override suspend fun delete(nostrGroupId: String) {
        states.remove(nostrGroupId)
        retainedEpochs.remove(nostrGroupId)
    }

    override suspend fun listGroups(): List<String> = states.keys.toList()

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) {
        retainedEpochs[nostrGroupId] = retainedSecrets
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> = retainedEpochs[nostrGroupId] ?: emptyList()
}

class MlsGroupManagerTest {
    private val groupId = "a".repeat(64) // 32-byte hex Nostr group ID

    @Test
    fun testCreateGroupAndPersist() {
        runBlocking {
            val store = InMemoryGroupStateStore()
            val manager = MlsGroupManager(store)

            val group = manager.createGroup(groupId, "alice".encodeToByteArray())

            assertNotNull(group)
            assertEquals(0L, group.epoch)
            assertTrue(manager.isMember(groupId))
            assertTrue(manager.activeGroupIds().contains(groupId))

            // State should be persisted
            assertNotNull(store.load(groupId))
        }
    }

    @Test
    fun testRestoreAfterRestart() {
        runBlocking {
            val store = InMemoryGroupStateStore()

            // Session 1: create group
            val manager1 = MlsGroupManager(store)
            manager1.createGroup(groupId, "alice".encodeToByteArray())

            // Session 2: restore
            val manager2 = MlsGroupManager(store)
            manager2.restoreAll()

            assertTrue(manager2.isMember(groupId))
            val group = manager2.getGroup(groupId)
            assertNotNull(group)
            assertEquals(0L, group.epoch)
        }
    }

    @Test
    fun testEncryptDecryptThroughManager() {
        runBlocking {
            val store = InMemoryGroupStateStore()
            val manager = MlsGroupManager(store)
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val plaintext = "Hello via manager".encodeToByteArray()
            val encrypted = manager.encrypt(groupId, plaintext)
            val decrypted = manager.decrypt(groupId, encrypted)

            assertContentEquals(plaintext, decrypted.content)
        }
    }

    @Test
    fun testAddMemberPersistsState() {
        runBlocking {
            val store = InMemoryGroupStateStore()
            val manager = MlsGroupManager(store)
            val group = manager.createGroup(groupId, "alice".encodeToByteArray())

            val bobBundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
            val result = manager.addMember(groupId, bobBundle.keyPackage.toTlsBytes())

            assertNotNull(result.welcomeBytes)

            // Restore and verify epoch advanced
            val manager2 = MlsGroupManager(store)
            manager2.restoreAll()
            val restored = manager2.getGroup(groupId)
            assertNotNull(restored)
            assertEquals(1L, restored.epoch)
            assertEquals(2, restored.memberCount)
        }
    }

    @Test
    fun testLeaveGroupDeletesState() {
        runBlocking {
            val store = InMemoryGroupStateStore()
            val manager = MlsGroupManager(store)
            manager.createGroup(groupId, "alice".encodeToByteArray())

            assertTrue(manager.isMember(groupId))

            manager.leaveGroup(groupId)

            assertTrue(!manager.isMember(groupId))
            assertNull(store.load(groupId))
        }
    }

    @Test
    fun testExporterSecret() {
        runBlocking {
            val store = InMemoryGroupStateStore()
            val manager = MlsGroupManager(store)
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val key = manager.exporterSecret(groupId)
            assertEquals(32, key.size)
        }
    }

    @Test
    fun testSigningKeyRotation() {
        runBlocking {
            val store = InMemoryGroupStateStore()
            val manager = MlsGroupManager(store)
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val keyBefore = manager.exporterSecret(groupId)

            val result = manager.rotateSigningKey(groupId)
            assertTrue(result.commitBytes.isNotEmpty())

            val keyAfter = manager.exporterSecret(groupId)
            assertTrue(!keyBefore.contentEquals(keyAfter))

            // Verify state persisted after rotation
            val manager2 = MlsGroupManager(store)
            manager2.restoreAll()
            val restoredKey = manager2.exporterSecret(groupId)
            assertContentEquals(keyAfter, restoredKey)
        }
    }

    @Test
    fun testMultipleGroupsIndependent() {
        runBlocking {
            val store = InMemoryGroupStateStore()
            val manager = MlsGroupManager(store)

            val groupId1 = "1".repeat(64)
            val groupId2 = "2".repeat(64)

            manager.createGroup(groupId1, "alice".encodeToByteArray())
            manager.createGroup(groupId2, "alice".encodeToByteArray())

            assertTrue(manager.isMember(groupId1))
            assertTrue(manager.isMember(groupId2))
            assertEquals(2, manager.activeGroupIds().size)

            // Leave one group
            manager.leaveGroup(groupId1)
            assertTrue(!manager.isMember(groupId1))
            assertTrue(manager.isMember(groupId2))

            // Restore
            val manager2 = MlsGroupManager(store)
            manager2.restoreAll()
            assertTrue(!manager2.isMember(groupId1))
            assertTrue(manager2.isMember(groupId2))
        }
    }

    /**
     * End-to-end amethyst ↔ amethyst leave + rejoin round-trip at the
     * [MlsGroupManager] layer. Covers the gaps left by
     * [MlsGroupLifecycleTest.testReaddAfterRemove_RejoinerCanEncryptAndDecrypt]
     * (which operates a layer below on raw [MlsGroup] instances):
     *
     *  1. Bob's [leaveGroup] must actually wipe his persisted state —
     *     `isMember` flips to false and the store no longer holds his group.
     *  2. Alice (admin) applying a Remove commit for the same leaf keeps
     *     her own group consistent afterwards.
     *  3. Bob can rejoin the SAME `nostrGroupId` with a fresh KeyPackage;
     *     the leave-side state cleanup must not leave any stale residue
     *     that would corrupt the new Welcome.
     *  4. Post-rejoin, bidirectional MLS encryption works in both
     *     directions — so the new epoch's tree, keying material and
     *     exporter secrets are all in lockstep across both managers.
     */
    @Test
    fun testLeaveAndRejoin_SameGroupIdEndToEnd() {
        runBlocking {
            val aliceStore = InMemoryGroupStateStore()
            val alice = MlsGroupManager(aliceStore)
            val bobStore = InMemoryGroupStateStore()
            val bob = MlsGroupManager(bobStore)

            // Seed with a NostrGroupData extension so Bob's processWelcome
            // can derive the nostrGroupId from the Welcome's GroupContext.
            // adminPubkeys is intentionally empty — the admin-depletion
            // guard (MlsGroup.enforceNoAdminDepletion) and MIP-03
            // committer-authority check both short-circuit when no admins
            // are configured, so Alice can drive all commits and Bob can
            // SelfRemove without tripping the admin gate.
            val groupData =
                MarmotGroupData(
                    nostrGroupId = groupId,
                    name = "leave-rejoin",
                )
            alice.createGroup(
                groupId,
                "alice".encodeToByteArray(),
                initialExtensions = listOf(groupData.toExtension()),
            )
            assertTrue(alice.isMember(groupId))

            // Bob publishes his first KeyPackage via a throwaway scratch
            // group (mirrors how a standalone KP is generated in
            // production before a Welcome has ever been seen).
            val bobBundle1 =
                MlsGroup
                    .create("bob".encodeToByteArray())
                    .createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
            val firstAdd = alice.addMember(groupId, bobBundle1.keyPackage.toTlsBytes())
            val firstWelcome = firstAdd.welcomeBytes ?: fail("Alice's first add must produce a Welcome")

            bob.processWelcome(firstWelcome, bobBundle1, hintNostrGroupId = groupId)
            assertTrue(bob.isMember(groupId))
            assertEquals(2, alice.getGroup(groupId)!!.memberCount)
            assertEquals(2, bob.getGroup(groupId)!!.memberCount)

            // Baseline round-trip both directions.
            val hello = "hello before leave".encodeToByteArray()
            assertContentEquals(
                hello,
                bob.getGroup(groupId)!!.decrypt(alice.getGroup(groupId)!!.encrypt(hello)).content,
            )
            val pong = "pong before leave".encodeToByteArray()
            assertContentEquals(
                pong,
                alice.getGroup(groupId)!!.decrypt(bob.getGroup(groupId)!!.encrypt(pong)).content,
            )

            // --- Leave: Bob's unilateral local teardown. -----------------
            // MlsGroupManager.leaveGroup returns the SelfRemove proposal
            // bytes (Alice would normally ingest these via the inbound
            // pipeline and fold them into a commit). It ALSO deletes Bob's
            // group from his store — the return contract is "your state
            // is gone now, the bytes are your farewell". Verify both.
            val (selfRemoveBytes, _) = bob.leaveGroup(groupId)
            assertTrue(selfRemoveBytes.isNotEmpty(), "SelfRemove bytes must be returned")
            assertFalse(bob.isMember(groupId), "Bob must no longer hold the group")
            assertNull(bobStore.load(groupId), "Bob's disk state for the group must be gone")
            assertEquals(
                emptyList(),
                bobStore.loadRetainedEpochs(groupId),
                "Bob's retained-epoch secrets must be wiped too",
            )

            // --- Admin commit that actually evicts Bob's leaf. ----------
            // The standalone-proposal ingestion pipeline is a separate
            // concern (MarmotInboundProcessor.processPublicMessage still
            // returns "Standalone proposals not yet supported" for the
            // PROPOSAL content type). Here we verify the equivalent
            // admin-kick flow: Alice removes Bob's leaf directly, which
            // is what an admin auto-commit would do after picking up the
            // proposal.
            val bobLeafIndex = bob.getGroup(groupId)?.leafIndex ?: 1
            alice.removeMember(groupId, bobLeafIndex)
            assertEquals(
                1,
                alice.getGroup(groupId)!!.memberCount,
                "Alice must be solo after evicting Bob",
            )

            // --- Rejoin: fresh KeyPackage + fresh Welcome, SAME groupId. -
            val bobBundle2 =
                MlsGroup
                    .create("bob".encodeToByteArray())
                    .createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
            val secondAdd = alice.addMember(groupId, bobBundle2.keyPackage.toTlsBytes())
            val secondWelcome =
                secondAdd.welcomeBytes ?: fail("Alice's re-add must produce a Welcome")

            bob.processWelcome(secondWelcome, bobBundle2, hintNostrGroupId = groupId)
            assertTrue(
                bob.isMember(groupId),
                "Bob must be a member again under the same nostrGroupId",
            )
            assertEquals(2, bob.getGroup(groupId)!!.memberCount)
            assertNotNull(
                bobStore.load(groupId),
                "Bob's store must hold fresh state for the same nostrGroupId",
            )

            // Epoch must be aligned.
            assertEquals(
                alice.getGroup(groupId)!!.epoch,
                bob.getGroup(groupId)!!.epoch,
                "Rejoiner must sit at Alice's current epoch",
            )

            // Bidirectional round-trip after rejoin — proves the shared
            // tree, keying material and exporter secrets are all back in
            // sync, not just that the Welcome deserialized cleanly.
            val afterHello = "hello after rejoin".encodeToByteArray()
            assertContentEquals(
                afterHello,
                bob.getGroup(groupId)!!.decrypt(alice.getGroup(groupId)!!.encrypt(afterHello)).content,
            )
            val afterPong = "pong after rejoin".encodeToByteArray()
            assertContentEquals(
                afterPong,
                alice.getGroup(groupId)!!.decrypt(bob.getGroup(groupId)!!.encrypt(afterPong)).content,
            )
        }
    }
}
