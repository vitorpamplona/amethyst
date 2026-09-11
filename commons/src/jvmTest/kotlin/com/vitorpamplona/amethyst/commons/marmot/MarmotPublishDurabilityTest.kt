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

import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.marmot.protocolCore.GroupLifecycleState
import com.vitorpamplona.quartz.marmot.protocolCore.MarmotPublishObligationStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Publish-before-apply is only a real rule if it survives the process.
 *
 * A commit is recorded, published, and only then applied. A crash inside that
 * window has to leave a durable trace, because the alternative is that the next
 * launch has no memory of the commit, mints a REPLACEMENT for the same epoch,
 * and forks this client against every peer that accepted the first one. So
 * these tests restart the manager against the same stores and assert the
 * obligation is still there, is retried with the SAME bytes, and only then
 * lets the group move.
 */
class MarmotPublishDurabilityTest {
    private val relay: NormalizedRelayUrl = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    /** Answers with a verdict that can be flipped between "runs". */
    private class SwitchablePublisher(
        var accepts: Boolean,
    ) : MarmotPublisher {
        val published = mutableListOf<Event>()

        override suspend fun publish(
            event: Event,
            relays: Set<NormalizedRelayUrl>,
        ): Boolean {
            published.add(event)
            return accepts
        }
    }

    private class MemoryObligationStore : MarmotPublishObligationStore {
        val entries = LinkedHashMap<HexKey, ByteArray>()

        override suspend fun save(
            obligationId: HexKey,
            bytes: ByteArray,
        ) {
            entries[obligationId] = bytes
        }

        override suspend fun delete(obligationId: HexKey) {
            entries.remove(obligationId)
        }

        override suspend fun loadAll(): List<ByteArray> = entries.values.toList()
    }

    /** Same MLS state across "restarts", like a real store on disk. */
    private class MemoryStateStore : MlsGroupStateStore {
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
            epochs: List<ByteArray>,
        ) {
            retained[nostrGroupId] = epochs
        }

        override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> = retained[nostrGroupId].orEmpty()
    }

    private fun manager(
        signer: NostrSignerInternal,
        store: MlsGroupStateStore,
        obligations: MarmotPublishObligationStore,
        publisher: MarmotPublisher,
    ) = MarmotManager(
        signer,
        store,
        publisher = publisher,
        publishObligationStore = obligations,
    )

    @Test
    fun anUnresolvedObligationOutlivesTheProcessAndIsRetriedVerbatim() =
        runBlocking<Unit> {
            val signer = NostrSignerInternal(KeyPair())
            val store = MemoryStateStore()
            val obligations = MemoryObligationStore()
            val publisher = SwitchablePublisher(accepts = false)
            val groupId = "b".repeat(64)

            val first = manager(signer, store, obligations, publisher)
            first.createGroup(
                groupId,
                MarmotGroupData(
                    nostrGroupId = groupId,
                    adminPubkeys = listOf(signer.pubKey),
                    relays = listOf(relay.url),
                ),
            )

            // Get past the FOUNDING add first. It merges locally under the
            // empty publication obligation (`publish-lifecycle.md`) and
            // publishes nothing, so it is not a commit publish-before-apply
            // governs — a durability test that used it would be testing the
            // exception instead of the rule.
            val founder = KeyPair()
            first.addMember(
                nostrGroupId = groupId,
                memberPubKey = founder.pubKey.toHexKey(),
                keyPackageBytes =
                    first.groupManager
                        .getGroup(groupId)!!
                        .createKeyPackage(founder.pubKey, ByteArray(0))
                        .keyPackage
                        .toTlsBytes(),
                keyPackageEventId = "f".repeat(64),
                relays = listOf(relay),
            )
            publisher.published.clear()

            val bob = KeyPair()
            val bundle =
                first.groupManager
                    .getGroup(groupId)!!
                    .createKeyPackage(bob.pubKey, ByteArray(0))

            // The publish is refused, so the group must NOT move and the
            // obligation must remain on disk.
            runCatching {
                first.addMember(
                    nostrGroupId = groupId,
                    memberPubKey = bob.pubKey.toHexKey(),
                    keyPackageBytes = bundle.keyPackage.toTlsBytes(),
                    keyPackageEventId = "d".repeat(64),
                    relays = listOf(relay),
                )
            }
            assertEquals(GroupLifecycleState.PENDING_PUBLISH, first.lifecycle(groupId))
            assertEquals(1L, first.groupManager.getGroup(groupId)!!.epoch, "the founding add stands; the refused one did not apply")
            assertEquals(1, obligations.entries.size, "an unacknowledged commit leaves its obligation durable")
            val recordedBytes =
                obligations.entries.values
                    .first()
                    .copyOf()
            val firstAttempt = publisher.published.single()

            // Restart against the same stores. The relay accepts this time.
            publisher.accepts = true
            val second = manager(signer, store, obligations, publisher)
            second.restoreAll()

            val retry = publisher.published.last()
            assertEquals(
                firstAttempt.toJson(),
                retry.toJson(),
                "the retry republishes the same event, not a replacement commit for the same epoch",
            )
            assertEquals(GroupLifecycleState.STABLE, second.lifecycle(groupId))
            assertEquals(2L, second.groupManager.getGroup(groupId)!!.epoch, "the acknowledged commit applies")
            assertTrue(obligations.entries.isEmpty(), "a resolved obligation is deleted")
            assertTrue(recordedBytes.isNotEmpty())
        }

    @Test
    fun aWedgedGroupRecoversOnTheNextCommitWithoutARestart() =
        runBlocking<Unit> {
            // `PendingPublish` correctly refuses new commits, but the only
            // thing that ever resolved it was `restoreAll` — so a single
            // dropped socket left the group unable to commit anything until the
            // app was restarted. The next attempt has to BE the recovery.
            val signer = NostrSignerInternal(KeyPair())
            val store = MemoryStateStore()
            val obligations = MemoryObligationStore()
            val publisher = SwitchablePublisher(accepts = false)
            val groupId = "e".repeat(64)

            val manager = manager(signer, store, obligations, publisher)
            manager.createGroup(
                groupId,
                MarmotGroupData(
                    nostrGroupId = groupId,
                    adminPubkeys = listOf(signer.pubKey),
                    relays = listOf(relay.url),
                ),
            )
            val founder = KeyPair()
            manager.addMember(
                nostrGroupId = groupId,
                memberPubKey = founder.pubKey.toHexKey(),
                keyPackageBytes =
                    manager.groupManager
                        .getGroup(groupId)!!
                        .createKeyPackage(founder.pubKey, ByteArray(0))
                        .keyPackage
                        .toTlsBytes(),
                keyPackageEventId = "f".repeat(64),
                relays = listOf(relay),
            )

            // A commit the relay never acknowledged: the group is held.
            runCatching { manager.setGroupProfile(groupId, "first try", "", listOf(relay)) }
            assertEquals(GroupLifecycleState.PENDING_PUBLISH, manager.lifecycle(groupId))

            // The relay is back. Without a restart, the next commit must clear
            // the stuck obligation and then land.
            publisher.accepts = true
            manager.setGroupProfile(groupId, "second try", "", listOf(relay))

            assertEquals(GroupLifecycleState.STABLE, manager.lifecycle(groupId))
            assertTrue(obligations.entries.isEmpty(), "the stuck obligation resolved on the way through")
            assertEquals("second try", manager.groupView(groupId)?.name)
        }

    @Test
    fun aRetryThatFailsAgainKeepsTheGroupHeld() =
        runBlocking<Unit> {
            val signer = NostrSignerInternal(KeyPair())
            val store = MemoryStateStore()
            val obligations = MemoryObligationStore()
            val publisher = SwitchablePublisher(accepts = false)
            val groupId = "c".repeat(64)

            val first = manager(signer, store, obligations, publisher)
            first.createGroup(
                groupId,
                MarmotGroupData(
                    nostrGroupId = groupId,
                    adminPubkeys = listOf(signer.pubKey),
                    relays = listOf(relay.url),
                ),
            )

            // Get past the FOUNDING add first. It merges locally under the
            // empty publication obligation (`publish-lifecycle.md`) and
            // publishes nothing, so it is not a commit publish-before-apply
            // governs — a durability test that used it would be testing the
            // exception instead of the rule.
            val founder = KeyPair()
            first.addMember(
                nostrGroupId = groupId,
                memberPubKey = founder.pubKey.toHexKey(),
                keyPackageBytes =
                    first.groupManager
                        .getGroup(groupId)!!
                        .createKeyPackage(founder.pubKey, ByteArray(0))
                        .keyPackage
                        .toTlsBytes(),
                keyPackageEventId = "f".repeat(64),
                relays = listOf(relay),
            )
            publisher.published.clear()

            val carol = KeyPair()
            val bundle =
                first.groupManager
                    .getGroup(groupId)!!
                    .createKeyPackage(carol.pubKey, ByteArray(0))
            runCatching {
                first.addMember(
                    nostrGroupId = groupId,
                    memberPubKey = carol.pubKey.toHexKey(),
                    keyPackageBytes = bundle.keyPackage.toTlsBytes(),
                    keyPackageEventId = "e".repeat(64),
                    relays = listOf(relay),
                )
            }

            val second = manager(signer, store, obligations, publisher)
            second.restoreAll()

            // Still refused: the group stays held rather than quietly moving
            // on, which is what stops a new commit stacking on an epoch peers
            // never accepted.
            assertEquals(GroupLifecycleState.PENDING_PUBLISH, second.lifecycle(groupId))
            assertEquals(1L, second.groupManager.getGroup(groupId)!!.epoch, "still held at the founding epoch")
            assertEquals(1, obligations.entries.size)
            assertTrue(bundle.keyPackage.toTlsBytes().isNotEmpty())
        }
}
