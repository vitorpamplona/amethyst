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

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Restart behavior of [MarmotManager.restoreAll].
 *
 * The kind:445 subscription state and the application ratchet position are
 * both in-memory only (group state persists only at commits), so without a
 * seeded `since` every restart re-downloads the full kind:445 backlog and
 * re-decrypts old application messages as if they were new. These tests pin
 * down the two defenses:
 *
 *   1. `restoreAll` seeds each restored group's subscription `since` from
 *      the newest persisted decrypted message, minus the refetch overlap.
 *   2. [MarmotMessageStore.appendMessage] is idempotent, so replays that do
 *      slip through the overlap window cannot grow the on-disk log.
 */
class MarmotManagerRestoreTest {
    private val nostrGroupId = "b".repeat(64)
    private val relay =
        RelayUrlNormalizer.normalizeOrNull("wss://example.invalid/")
            ?: error("test relay must normalize")

    @Test
    fun testRestoreAllSeedsSubscriptionSinceFromStoredMessages() {
        runBlocking {
            val signer = NostrSignerInternal(KeyPair())
            val mlsStore = InMemoryStateStore()
            val messageStore = InMemoryMessageStore()
            val kpStore = InMemoryBundleStore()

            val manager = MarmotManager(signer, mlsStore, messageStore, kpStore)
            manager.createGroup(
                nostrGroupId,
                MarmotGroupData(
                    nostrGroupId = nostrGroupId,
                    name = "restore-since",
                    relays = listOf(relay.url),
                ),
            )
            val bundle = manager.buildTextMessage(nostrGroupId, "survives restart")

            // Simulate an app restart: fresh manager over the same stores.
            val restarted = MarmotManager(signer, mlsStore, messageStore, kpStore)
            restarted.restoreAll()

            val filter = restarted.subscriptionManager.activeGroupFilters().single()
            assertEquals(
                bundle.innerEvent.createdAt - MarmotManager.GROUP_EVENT_REFETCH_OVERLAP_SEC,
                filter.since,
                "restored kind:445 filter must resume just behind the newest persisted message",
            )
        }
    }

    @Test
    fun testRestoreAllLeavesSinceUnsetWithoutStoredMessages() {
        runBlocking {
            val signer = NostrSignerInternal(KeyPair())
            val mlsStore = InMemoryStateStore()
            val messageStore = InMemoryMessageStore()
            val kpStore = InMemoryBundleStore()

            val manager = MarmotManager(signer, mlsStore, messageStore, kpStore)
            manager.createGroup(
                nostrGroupId,
                MarmotGroupData(
                    nostrGroupId = nostrGroupId,
                    name = "restore-no-messages",
                    relays = listOf(relay.url),
                ),
            )

            val restarted = MarmotManager(signer, mlsStore, messageStore, kpStore)
            restarted.restoreAll()

            val filter = restarted.subscriptionManager.activeGroupFilters().single()
            assertNull(filter.since, "a group with no persisted messages must fetch its full history")
        }
    }

    @Test
    fun testPersistDecryptedMessageIsIdempotent() {
        runBlocking {
            val signer = NostrSignerInternal(KeyPair())
            val manager =
                MarmotManager(signer, InMemoryStateStore(), InMemoryMessageStore(), InMemoryBundleStore())

            val json = """{"id":"00","kind":9,"content":"hi","created_at":1700000000}"""
            manager.persistDecryptedMessage(nostrGroupId, json)
            manager.persistDecryptedMessage(nostrGroupId, json)

            assertEquals(
                1,
                manager.loadStoredMessages(nostrGroupId).size,
                "replaying an already-persisted message must not grow the log",
            )
        }
    }
}

// Minimal in-memory stores; the file-backed implementations live in the
// platform modules (amethyst, cli) and follow the same contracts.

private class InMemoryStateStore : MlsGroupStateStore {
    private val states = mutableMapOf<String, ByteArray>()
    private val retained = mutableMapOf<String, List<ByteArray>>()

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        states[nostrGroupId] = state
    }

    override suspend fun load(nostrGroupId: String): ByteArray? = states[nostrGroupId]

    override suspend fun delete(nostrGroupId: String) {
        states.remove(nostrGroupId)
        retained.remove(nostrGroupId)
    }

    override suspend fun listGroups(): List<String> = states.keys.toList()

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) {
        retained[nostrGroupId] = retainedSecrets
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> = retained[nostrGroupId] ?: emptyList()
}

private class InMemoryMessageStore : MarmotMessageStore {
    private val messages = mutableMapOf<String, MutableList<String>>()

    override suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    ) {
        val log = messages.getOrPut(nostrGroupId) { mutableListOf() }
        if (innerEventJson !in log) log.add(innerEventJson)
    }

    override suspend fun loadMessages(nostrGroupId: String): List<String> = messages[nostrGroupId]?.toList() ?: emptyList()

    override suspend fun delete(nostrGroupId: String) {
        messages.remove(nostrGroupId)
    }
}

private class InMemoryBundleStore : KeyPackageBundleStore {
    private var snapshot: ByteArray? = null

    override suspend fun save(snapshot: ByteArray) {
        this.snapshot = snapshot
    }

    override suspend fun load(): ByteArray? = snapshot

    override suspend fun delete() {
        snapshot = null
    }
}
