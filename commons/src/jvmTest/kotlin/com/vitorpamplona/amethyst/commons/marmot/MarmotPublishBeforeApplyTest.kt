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
import com.vitorpamplona.quartz.marmot.protocolCore.GroupLifecycleState
import com.vitorpamplona.quartz.marmot.protocolCore.LocalOutboundGate
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `protocol-core/publish-lifecycle.md`: a locally generated group-state change
 * MUST NOT become local canonical state until its publish obligation succeeded.
 *
 * The rule is not "roll back if the publish fails". Applying first and undoing
 * afterwards leaves a window in which this client's canonical state is an epoch
 * no peer has, and a crash inside that window makes the fork permanent. So
 * these tests assert the group never moves at all until an acknowledgement
 * arrives.
 */
class MarmotPublishBeforeApplyTest {
    private val relay: NormalizedRelayUrl = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    /** Records what was handed to it, and answers with a fixed verdict. */
    private class RecordingPublisher(
        private val accepts: Boolean,
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

    private class Fixture(
        val manager: MarmotManager,
        val publisher: RecordingPublisher,
        val groupId: String,
        val bobKeyPackage: ByteArray,
        val bobPubKey: String,
    )

    private suspend fun fixture(accepts: Boolean): Fixture {
        val publisher = RecordingPublisher(accepts)
        val signer = NostrSignerInternal(KeyPair())
        val manager =
            MarmotManager(
                signer,
                InMemoryStateStore(),
                publisher = publisher,
            )
        val groupId = "a".repeat(64)
        manager.createGroup(
            groupId,
            MarmotGroupData(
                nostrGroupId = groupId,
                adminPubkeys = listOf(signer.pubKey),
                relays = listOf(relay.url),
            ),
        )

        val bob = KeyPair()
        val bundle =
            manager.groupManager
                .getGroup(groupId)!!
                .createKeyPackage(bob.pubKey, ByteArray(0))
        return Fixture(manager, publisher, groupId, bundle.keyPackage.toTlsBytes(), bob.pubKey.toHexKey())
    }

    /**
     * Creation is the one exception: a one-member epoch-0 group has no peer
     * that failure to publish could fork, so its obligation is empty and
     * immediately satisfied.
     */
    @Test
    fun groupCreationSatisfiesAnEmptyObligation() =
        runBlocking<Unit> {
            val fx = fixture(accepts = true)
            assertEquals(GroupLifecycleState.STABLE, fx.manager.lifecycle(fx.groupId))
            assertEquals(
                0L,
                fx.manager.groupManager
                    .getGroup(fx.groupId)!!
                    .epoch,
            )
            assertTrue(fx.publisher.published.isEmpty(), "creating a group publishes no group message")
        }

    /** An acknowledged commit becomes canonical and the group returns to Stable. */
    @Test
    fun anAcknowledgedCommitBecomesCanonical() =
        runBlocking<Unit> {
            val fx = fixture(accepts = true)
            val before =
                fx.manager.groupManager
                    .getGroup(fx.groupId)!!
                    .epoch

            fx.manager.addMember(
                nostrGroupId = fx.groupId,
                memberPubKey = fx.bobPubKey,
                keyPackageBytes = fx.bobKeyPackage,
                keyPackageEventId = "c".repeat(64),
                relays = listOf(relay),
            )

            assertEquals(1, fx.publisher.published.size)
            assertEquals(
                before + 1,
                fx.manager.groupManager
                    .getGroup(fx.groupId)!!
                    .epoch,
            )
            assertEquals(GroupLifecycleState.STABLE, fx.manager.lifecycle(fx.groupId))
            assertTrue(
                fx.manager.publishGate
                    .pendingFor(fx.groupId)
                    .isEmpty(),
            )
        }

    /**
     * The headline case. No relay accepted, so the group stays exactly where it
     * was — same epoch, same GroupContext, byte for byte.
     */
    @Test
    fun anUnacknowledgedCommitNeverBecomesCanonical() =
        runBlocking<Unit> {
            val fx = fixture(accepts = false)
            val beforeEpoch =
                fx.manager.groupManager
                    .getGroup(fx.groupId)!!
                    .epoch
            val beforeContext =
                fx.manager.groupManager
                    .snapshot(fx.groupId)!!
                    .groupContext
                    .toTlsBytes()

            val (_, welcome) =
                fx.manager.addMember(
                    nostrGroupId = fx.groupId,
                    memberPubKey = fx.bobPubKey,
                    keyPackageBytes = fx.bobKeyPackage,
                    keyPackageEventId = "c".repeat(64),
                    relays = listOf(relay),
                )

            assertEquals(1, fx.publisher.published.size, "it was still offered to the relay")
            assertEquals(
                beforeEpoch,
                fx.manager.groupManager
                    .getGroup(fx.groupId)!!
                    .epoch,
            )
            assertContentEquals(
                beforeContext,
                fx.manager.groupManager
                    .snapshot(fx.groupId)!!
                    .groupContext
                    .toTlsBytes(),
                "an unpublished commit must leave the canonical state untouched",
            )
            // And no Welcome: inviting someone into an epoch no relay accepted
            // would point them at a group that exists nowhere else.
            assertEquals(null, welcome)
        }

    /**
     * A raised outbound gate blocks all new local group-state work.
     *
     * `Leaving`, `Disbanding` and a realized `Removed` each mean this client
     * has no standing to publish a new commit: it is on its way out, or already
     * out. Preparing one anyway would produce an epoch nobody will accept, so
     * the attempt fails loudly rather than silently forking.
     */
    @Test
    fun anOutboundGateBlocksNewCommits() =
        runBlocking<Unit> {
            val fx = fixture(accepts = true)
            assertTrue(fx.manager.publishGate.canPrepareLocalCommit(fx.groupId))

            fx.manager.publishGate.raiseGate(fx.groupId, LocalOutboundGate.LEAVING)
            assertEquals(LocalOutboundGate.LEAVING, fx.manager.publishGate.outboundGate(fx.groupId))

            assertFailsWith<IllegalStateException> {
                fx.manager.updateGroupMetadata(
                    fx.groupId,
                    MarmotGroupData(nostrGroupId = fx.groupId, adminPubkeys = listOf(fx.manager.signer.pubKey)),
                    listOf(relay),
                )
            }
            assertTrue(fx.publisher.published.isEmpty(), "a gated commit is never even offered to a relay")

            // Cleared, the same commit goes through.
            fx.manager.publishGate.clearGate(fx.groupId)
            fx.manager.updateGroupMetadata(
                fx.groupId,
                MarmotGroupData(nostrGroupId = fx.groupId, adminPubkeys = listOf(fx.manager.signer.pubKey)),
                listOf(relay),
            )
            assertEquals(1, fx.publisher.published.size)
        }

    /**
     * A group whose publisher never acknowledges anything can still be read.
     * It simply cannot advance — which is the safe direction to fail.
     *
     * It also cannot start over. "No OK arrived" is not "no peer took it": a
     * timeout or a dropped connection leaves us unable to say. Discarding the
     * obligation and letting a SECOND commit be prepared for the same epoch is
     * how that uncertainty becomes a permanent fork — the peer that did
     * receive the first commit is at epoch 1, rejects our second, and the two
     * copies never reconcile. So the obligation stays, the group stays held,
     * and the retry republishes the SAME bytes.
     */
    @Test
    fun anUnconfirmedPublishHoldsTheGroupInsteadOfStartingOver() =
        runBlocking<Unit> {
            val fx = fixture(accepts = false)
            fx.manager.addMember(
                nostrGroupId = fx.groupId,
                memberPubKey = fx.bobPubKey,
                keyPackageBytes = fx.bobKeyPackage,
                keyPackageEventId = "c".repeat(64),
                relays = listOf(relay),
            )

            assertEquals(GroupLifecycleState.PENDING_PUBLISH, fx.manager.lifecycle(fx.groupId))
            assertEquals(
                1,
                fx.manager.publishGate
                    .pendingFor(fx.groupId)
                    .size,
                "an unconfirmed obligation stays retryable",
            )
            // Reading is unaffected; only advancing the group is blocked.
            assertEquals(
                0L,
                fx.manager.groupManager
                    .getGroup(fx.groupId)!!
                    .epoch,
            )

            // A second, different commit for the same epoch is refused.
            assertFailsWith<IllegalStateException> {
                fx.manager.addMember(
                    nostrGroupId = fx.groupId,
                    memberPubKey = fx.bobPubKey,
                    keyPackageBytes = fx.bobKeyPackage,
                    keyPackageEventId = "c".repeat(64),
                    relays = listOf(relay),
                )
            }
            assertEquals(1, fx.publisher.published.size, "no replacement commit was offered")
        }

    /** The group's own relay list is the default recipient scope. */
    @Test
    fun theRecipientScopeComesFromTheGroupsOwnRelayList() =
        runBlocking<Unit> {
            val fx = fixture(accepts = true)
            assertEquals(listOf(relay), fx.manager.groupRelays(fx.groupId))
        }

    private class InMemoryStateStore : com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore {
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

        override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> = retained[nostrGroupId] ?: emptyList()
    }
}
