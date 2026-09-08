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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRotationManager
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.protocolCore.ConvergencePolicy
import com.vitorpamplona.quartz.marmot.protocolCore.ConvergenceStatus
import com.vitorpamplona.quartz.marmot.protocolCore.GroupLifecycleState
import com.vitorpamplona.quartz.marmot.protocolCore.MarmotConvergenceEngine
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * End-to-end convergence through [MarmotInboundProcessor].
 *
 * The property that matters is order independence: two members who receive the
 * same two competing commits in opposite orders must end on the same group
 * state. That is what the superseded `created_at` / event-id tiebreak could not
 * guarantee — both are transport metadata a sender picks freely.
 */
class MarmotConvergenceWiringTest {
    private val groupId = "c".repeat(64)

    /** A 3-member group and two commits authored from the SAME epoch. */
    private class Fork(
        val observerStateBytes: ByteArray,
        val commitA: GroupEvent,
        val commitB: GroupEvent,
        /** An app payload Bob sent on HIS branch, authored honestly. */
        val bobPayload: GroupEvent,
        /** The same, but claiming an author Bob's MLS leaf does not authenticate. */
        val bobForgedPayload: GroupEvent,
        val forkEpoch: Long,
    )

    /**
     * A minimal inner Nostr event.
     *
     * Only the `pubkey` field matters here: the receiver check compares it to
     * the account the MLS sender leaf authenticates, and that comparison is
     * what separates a witness from a forgery.
     */
    private fun innerEventJson(authorHex: String) =
        """{"id":"${"0".repeat(64)}","pubkey":"$authorHex","created_at":1,"kind":9,""" +
            """"tags":[],"content":"hi","sig":"${"0".repeat(128)}"}"""

    private fun account(seed: Byte) = ByteArray(32) { seed }

    private fun groupData(vararg admins: String) = MarmotGroupData(nostrGroupId = groupId, adminPubkeys = admins.toList()).toExtension()

    /**
     * Build a genuine fork.
     *
     * Alice and Bob are both members at the same epoch and each authors a
     * commit against it, neither having seen the other's. Carol is the neutral
     * observer; her state at the fork epoch is captured as bytes so two
     * identical observers can be restored from it and fed in opposite orders.
     */
    private suspend fun buildFork(): Fork {
        val aliceMgr = MlsGroupManager(TestGroupStateStore())
        val bobMgr = MlsGroupManager(TestGroupStateStore())
        val carolMgr = MlsGroupManager(TestGroupStateStore())
        val outbound = MarmotOutboundProcessor(aliceMgr)

        aliceMgr.createGroup(groupId, account(0x0a))
        // The Welcome only carries a derivable nostrGroupId once the group data
        // extension is installed, and the admin policy has to name a member —
        // so Bob is promoted only after he actually holds a leaf.
        aliceMgr.updateGroupExtensions(groupId, listOf(groupData(account(0x0a).toHexKey())))

        val bobBundle = aliceMgr.getGroup(groupId)!!.createKeyPackage(account(0x0b), ByteArray(0))
        val addBob = aliceMgr.addMember(groupId, bobBundle.keyPackage.toTlsBytes())
        bobMgr.processWelcome(addBob.welcomeBytes!!, bobBundle)

        // Both Alice and Bob must be admins: the fork is two ADDs, and an add
        // by a non-admin is an authorization failure, not a candidate branch.
        val promoteBob =
            aliceMgr.updateGroupExtensions(
                groupId,
                listOf(groupData(account(0x0a).toHexKey(), account(0x0b).toHexKey())),
            )
        bobMgr.processFramedCommit(groupId, promoteBob.framedCommitBytes)

        val carolBundle = aliceMgr.getGroup(groupId)!!.createKeyPackage(account(0x0c), ByteArray(0))
        val addCarol = aliceMgr.addMember(groupId, carolBundle.keyPackage.toTlsBytes())
        // Bob is already a member, so he applies the add-Carol commit rather
        // than joining through it.
        bobMgr.processFramedCommit(groupId, addCarol.framedCommitBytes)
        carolMgr.processWelcome(addCarol.welcomeBytes!!, carolBundle)

        val forkEpoch = aliceMgr.getGroup(groupId)!!.epoch
        assertEquals(forkEpoch, bobMgr.getGroup(groupId)!!.epoch)
        assertEquals(forkEpoch, carolMgr.getGroup(groupId)!!.epoch)

        // Two adds authored from the SAME epoch by two different members.
        val daveBundle =
            aliceMgr.getGroup(groupId)!!.createKeyPackage(account(0x0d), ByteArray(32) { 3 })
        val eveBundle =
            bobMgr.getGroup(groupId)!!.createKeyPackage(account(0x0e), ByteArray(32) { 4 })

        val aliceCommit = aliceMgr.addMember(groupId, daveBundle.keyPackage.toTlsBytes())
        val bobCommit = bobMgr.addMember(groupId, eveBundle.keyPackage.toTlsBytes())

        val eventA =
            outbound.buildCommitEvent(groupId, aliceCommit.framedCommitBytes, aliceCommit.preCommitExporterSecret)
        val eventB =
            outbound.buildCommitEvent(groupId, bobCommit.framedCommitBytes, bobCommit.preCommitExporterSecret)

        // Bob speaks on his own branch. His epoch exporter is on neither the
        // canonical nor the retained-canonical key list, so these only peel at
        // all once the observer retains Bob's candidate state.
        val bobOutbound = MarmotOutboundProcessor(bobMgr)
        val honest =
            bobOutbound.buildGroupEventFromBytes(
                groupId,
                innerEventJson(account(0x0b).toHexKey()).encodeToByteArray(),
            )
        val forged =
            bobOutbound.buildGroupEventFromBytes(
                groupId,
                innerEventJson(account(0x0a).toHexKey()).encodeToByteArray(),
            )

        return Fork(
            observerStateBytes = carolMgr.snapshot(groupId)!!.encodeTls(),
            commitA = eventA.signedEvent,
            commitB = eventB.signedEvent,
            bobPayload = honest.signedEvent,
            bobForgedPayload = forged.signedEvent,
            forkEpoch = forkEpoch,
        )
    }

    /** An observer restored from [stateBytes], with convergence already seeded. */
    private class Observer(
        val manager: MlsGroupManager,
        val inbound: MarmotInboundProcessor,
    )

    private suspend fun observer(
        stateBytes: ByteArray,
        nowMs: () -> Long = { 0L },
    ): Observer {
        val store = TestGroupStateStore()
        store.save(groupId, stateBytes)
        val manager = MlsGroupManager(store)
        manager.restoreAll()
        val inbound =
            MarmotInboundProcessor(
                manager,
                KeyPackageRotationManager(),
                MarmotConvergenceEngine(manager, ConvergencePolicy.V1, nowMs),
            )
        inbound.trackGroup(groupId)
        return Observer(manager, inbound)
    }

    private fun groupContextOf(manager: MlsGroupManager) = manager.snapshot(groupId)!!.groupContext.toTlsBytes()

    /**
     * The headline property. Two observers, the same two commits, opposite
     * arrival orders, one final state.
     */
    @Test
    fun twoObserversConvergeRegardlessOfArrivalOrder() =
        runBlocking<Unit> {
            val fork = buildFork()

            val first = observer(fork.observerStateBytes)
            val second = observer(fork.observerStateBytes)

            // Whoever arrives first is applied eagerly; the other is the fork.
            assertIs<GroupEventResult.CommitProcessed>(first.inbound.processGroupEvent(fork.commitA))
            val firstSecond = first.inbound.processGroupEvent(fork.commitB)
            assertIs<GroupEventResult.CommitPending>(firstSecond)
            assertTrue(firstSecond.forkDetected, "the losing commit must open a pass, not be discarded")

            assertIs<GroupEventResult.CommitProcessed>(second.inbound.processGroupEvent(fork.commitB))
            assertIs<GroupEventResult.CommitPending>(second.inbound.processGroupEvent(fork.commitA))

            val r1 = first.inbound.resolveConvergence(groupId)
            val r2 = second.inbound.resolveConvergence(groupId)
            assertEquals(ConvergenceStatus.SETTLED, r1!!.status)
            assertEquals(ConvergenceStatus.SETTLED, r2!!.status)
            assertEquals(r1.canonicalEpoch, r2.canonicalEpoch)

            assertContentEquals(
                groupContextOf(first.manager),
                groupContextOf(second.manager),
                "both observers must land on the same GroupContext",
            )
            // Exactly one of the two had to rewind — they applied different
            // commits eagerly and only one branch can win.
            assertNotEquals(r1.rewound, r2.rewound)
        }

    /**
     * A commit that lost a race is never silently dropped: it opens a recovery
     * pass, and the group reports `Recovering` for as long as that pass runs.
     */
    @Test
    fun aLosingCommitOpensARecoveryPass() =
        runBlocking<Unit> {
            val fork = buildFork()
            val obs = observer(fork.observerStateBytes)

            assertEquals(GroupLifecycleState.STABLE, obs.inbound.groupLifecycle(groupId))
            obs.inbound.processGroupEvent(fork.commitA)
            assertTrue(obs.inbound.openConvergencePasses().isEmpty(), "a linear commit opens no pass")

            obs.inbound.processGroupEvent(fork.commitB)
            assertEquals(mapOf(groupId to fork.forkEpoch + 1), obs.inbound.openConvergencePasses())
            assertEquals(ConvergenceStatus.SYNCING, obs.inbound.convergenceStatus(groupId))
            assertEquals(GroupLifecycleState.RECOVERING, obs.inbound.groupLifecycle(groupId))

            obs.inbound.resolveConvergence(groupId)
            assertTrue(obs.inbound.openConvergencePasses().isEmpty())
            assertEquals(ConvergenceStatus.SETTLED, obs.inbound.convergenceStatus(groupId))
        }

    /**
     * A pass settles on its own once quiescence elapses, without anyone forcing
     * it — driven by the monotonic clock, so the test is deterministic rather
     * than a sleep.
     */
    @Test
    fun aPassSettlesAtItsQuiescenceCutoff() =
        runBlocking<Unit> {
            val fork = buildFork()
            var now = 0L
            val obs = observer(fork.observerStateBytes) { now }

            obs.inbound.processGroupEvent(fork.commitA)
            obs.inbound.processGroupEvent(fork.commitB)
            assertEquals(1, obs.inbound.openConvergencePasses().size)

            // Still inside the quiescence window: nothing is due.
            now = ConvergencePolicy.V1.settlementQuiescenceMs - 1
            assertTrue(obs.inbound.settleDueConvergence().isEmpty())
            assertEquals(1, obs.inbound.openConvergencePasses().size)

            now = ConvergencePolicy.V1.settlementQuiescenceMs
            val settled = obs.inbound.settleDueConvergence()
            assertEquals(1, settled.size)
            assertEquals(ConvergenceStatus.SETTLED, settled.single().status)
            assertTrue(obs.inbound.openConvergencePasses().isEmpty())
        }

    /**
     * A plain relay redelivery is still just a duplicate. Convergence only
     * claims a commit when a RETAINED state authenticates it, and an echo's
     * parent was consumed by the very commit it echoes.
     */
    @Test
    fun anEchoOfAnAppliedCommitIsNotTreatedAsAFork() =
        runBlocking<Unit> {
            val fork = buildFork()
            val obs = observer(fork.observerStateBytes)

            assertIs<GroupEventResult.CommitProcessed>(obs.inbound.processGroupEvent(fork.commitA))

            // Same MLS bytes, a different Nostr event: exactly what a second
            // relay delivers, and what the event-id dedup could never catch.
            val republished = MarmotOutboundProcessor(obs.manager)
            val echo =
                republished
                    .buildCommitEvent(
                        groupId,
                        GroupEventEncryption.decrypt(
                            fork.commitA.content,
                            obs.manager.retainedExporterSecrets(groupId).first(),
                        ),
                        obs.manager.retainedExporterSecrets(groupId).first(),
                    ).signedEvent
            assertNotEquals(fork.commitA.id, echo.id)

            assertIs<GroupEventResult.Duplicate>(obs.inbound.processGroupEvent(echo))
            assertTrue(obs.inbound.openConvergencePasses().isEmpty(), "an echo must not open a pass")
        }

    /**
     * Selection compares the incumbent by the same rule as its challenger. The
     * branch it picks is a function of the commits, so the observer that had
     * already applied the winner does not rewind, and the one that had not,
     * does.
     */
    @Test
    fun theIncumbentBranchIsScoredNotAssumed() =
        runBlocking<Unit> {
            val fork = buildFork()
            val obs = observer(fork.observerStateBytes)

            obs.inbound.processGroupEvent(fork.commitA)
            val afterEager = groupContextOf(obs.manager)
            obs.inbound.processGroupEvent(fork.commitB)

            val resolution = obs.inbound.resolveConvergence(groupId)!!
            val afterSettle = groupContextOf(obs.manager)

            if (resolution.rewound) {
                assertFalse(afterEager.contentEquals(afterSettle), "a rewind must change the state")
            } else {
                assertContentEquals(afterEager, afterSettle, "keeping the incumbent must change nothing")
            }
            // Either way both commits were accounted for, not dropped.
            assertEquals(2, resolution.outcomes.size)
            assertEquals(
                setOf(sha256Hex(fork.commitA, obs), sha256Hex(fork.commitB, obs)),
                resolution.outcomes.map { it.commitId }.toSet(),
            )
        }

    /**
     * A payload sent on a branch that is not ours decrypts on no canonical
     * epoch, so it must not be delivered — but it must not vanish either. It
     * is reported as living on a candidate branch, and (because it passes the
     * payload checks) counted as an app-payload witness for that branch.
     */
    @Test
    fun aPayloadOnTheLosingBranchWitnessesInsteadOfBeingDelivered() =
        runBlocking<Unit> {
            val fork = buildFork()
            val obs = observer(fork.observerStateBytes)

            // Our observer follows Alice's branch; Bob's is the candidate.
            assertIs<GroupEventResult.CommitProcessed>(obs.inbound.processGroupEvent(fork.commitA))
            assertIs<GroupEventResult.CommitPending>(obs.inbound.processGroupEvent(fork.commitB))

            val onBob = fork.bobPayload
            val result = obs.inbound.processGroupEvent(onBob)
            val branchMsg = assertIs<GroupEventResult.AppMessageOnCandidateBranch>(result)
            assertTrue(branchMsg.countedAsWitness, "a payload passing the author check is a witness")
            assertEquals(fork.forkEpoch + 1, branchMsg.epoch)
        }

    /**
     * Decryption alone is not a witness. A payload whose inner author does not
     * match the MLS-authenticated sender is reported on its branch but MUST NOT
     * be counted — otherwise one member could mint many sender identities and
     * buy the witness quorum outright.
     */
    @Test
    fun aPayloadFailingTheAuthorCheckIsNotCountedAsAWitness() =
        runBlocking<Unit> {
            val fork = buildFork()
            val obs = observer(fork.observerStateBytes)

            obs.inbound.processGroupEvent(fork.commitA)
            obs.inbound.processGroupEvent(fork.commitB)

            val result = obs.inbound.processGroupEvent(fork.bobForgedPayload)
            val branchMsg = assertIs<GroupEventResult.AppMessageOnCandidateBranch>(result)
            assertFalse(branchMsg.countedAsWitness, "a mismatched author must not witness")
        }

    /**
     * A payload for a branch we do not hold is `transport_deferred`, not a
     * terminal failure.
     *
     * The trial set really is bounded — canonical epochs plus retained
     * candidates, nothing else — but "I could not peel this" is a statement
     * about the keys we hold right now. Bob's commit may still arrive, and
     * `protocol-core/inbound-processing.md` requires a retry whenever the
     * transport decryption context changes. Reporting it terminal would strand
     * the payload permanently on a race the group is about to resolve.
     */
    @Test
    fun aPayloadForNoRetainedBranchIsDeferredNotRejected() =
        runBlocking<Unit> {
            val fork = buildFork()
            val obs = observer(fork.observerStateBytes)
            obs.inbound.processGroupEvent(fork.commitA)

            // Bob's branch was never offered, so nothing retains its state and
            // no key we hold derives his epoch exporter.
            val before = obs.inbound.processGroupEvent(fork.bobPayload)
            assertIs<GroupEventResult.UndecryptableOuterLayer>(before)

            // Retaining Bob's branch IS a change of transport decryption
            // context, and the retry now succeeds.
            obs.inbound.processGroupEvent(fork.commitB)
            val after = obs.inbound.processGroupEvent(fork.bobPayload)
            assertIs<GroupEventResult.AppMessageOnCandidateBranch>(after)
        }

    /**
     * A pass whose group falls silent still settles.
     *
     * Inbound traffic ticks convergence opportunistically, so a busy group
     * resolves itself. A group where the fork was the LAST thing to arrive has
     * nothing to carry it — which is why `settleDueConvergence()` exists and
     * why the app layer drives it while a pass is open.
     */
    @Test
    fun aQuietGroupStillSettlesThroughTheDueSweep() =
        runBlocking<Unit> {
            val fork = buildFork()
            var now = 0L
            val obs = observer(fork.observerStateBytes) { now }

            obs.inbound.processGroupEvent(fork.commitA)
            obs.inbound.processGroupEvent(fork.commitB)
            // Nothing else ever arrives for this group.
            assertEquals(1, obs.inbound.openConvergencePasses().size)

            now = ConvergencePolicy.V1.maxConvergencePassMs
            assertEquals(1, obs.inbound.settleDueConvergence().size)
            assertTrue(obs.inbound.openConvergencePasses().isEmpty())
            assertEquals(ConvergenceStatus.SETTLED, obs.inbound.convergenceStatus(groupId))
        }

    /** The Marmot message id of a commit event, as the engine computes it. */
    private suspend fun sha256Hex(
        event: GroupEvent,
        obs: Observer,
    ): String {
        val keys = listOf(obs.manager.exporterSecret(groupId)) + obs.manager.retainedExporterSecrets(groupId)
        for (key in keys) {
            try {
                return sha256(GroupEventEncryption.decrypt(event.content, key)).toHexKey()
            } catch (_: Exception) {
                // try the next retained key
            }
        }
        error("no key decrypted the commit event")
    }
}
