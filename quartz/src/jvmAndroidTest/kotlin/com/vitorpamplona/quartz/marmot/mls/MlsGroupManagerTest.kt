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

import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
