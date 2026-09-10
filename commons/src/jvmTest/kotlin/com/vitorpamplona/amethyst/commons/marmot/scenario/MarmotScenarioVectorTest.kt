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
package com.vitorpamplona.amethyst.commons.marmot.scenario

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Replays the reference implementation's own conformance scenarios.
 *
 * These vectors are marked `portable` in their manifest — written for one
 * engine and meant to be replayed by another. That is a different claim from
 * what the interop harness tests: the harness proves we can TALK to `wn` over a
 * relay, and these prove that the same sequence of events lands us in the same
 * group state.
 *
 * Only the vectors whose steps the runner implements are here. The rest need
 * fault injection (withhold/release, partition, duplicate, reorder, restart) or
 * group-data and admin-policy steps; `MarmotScenarioRunner` throws
 * [UnsupportedScenarioStep] rather than ignoring an unknown step, so widening
 * the set means implementing a step, never loosening a check.
 */
class MarmotScenarioVectorTest {
    private fun load(name: String): ScenarioVector {
        val stream =
            javaClass.classLoader?.getResourceAsStream("marmot/vectors/$name")
                ?: fail("vector $name is missing from test resources")
        return ScenarioVector.parse(stream.bufferedReader().use { it.readText() })
    }

    private fun replay(name: String) =
        runBlocking {
            val vector = load(name)
            MarmotScenarioRunner(vector).run()
        }

    @Test
    fun inviteMember() = replay("invite-member.v1.json")

    @Test
    fun currentProfileRequiredSet() = replay("current-profile-required-set.v1.json")

    @Test
    fun latecomerForwardSecrecy() = replay("latecomer-forward-secrecy.v1.json")

    @Test
    fun multigroupIsolation() = replay("multigroup-isolation.v1.json")

    @Test
    fun publishFail() = replay("publish-fail.v1.json")

    @Test
    fun invitePublishFail() = replay("invite-publish-fail.v1.json")

    /**
     * The vectors whose `create_group` names several invitees. The reference
     * adds them all in ONE commit — epoch 1, one Welcome carrying an
     * EncryptedGroupSecrets per invitee — and so do we now, so the traces line
     * up. They were refused as a batching divergence until batched Adds landed.
     */
    @Test
    fun threeClientMessageExchange() = replay("three-client-message-exchange.v1.json")

    @Test
    fun conversation() = replay("conversation.v1.json")

    /** A rename lands on every member as a commit, not as a hint. */
    @Test
    fun groupDataUpdate() = replay("group-data-update.v1.json")

    /** A client that missed several rounds catches up on one tick. */
    @Test
    fun deferredTickCatchup() = replay("deferred-tick-catchup.v1.json")

    /** Members added at each step see only what came after them. */
    @Test
    fun incrementalGrowth() = replay("incremental-growth.v1.json")

    /**
     * The delivery-fault family: a dropped message, and a queue that duplicates
     * and reorders before it delivers. What arrives twice must be acted on
     * once, and out-of-order arrival must not change the end state.
     */
    @Test
    fun dropQueued() = replay("drop-queued.v1.json")

    @Test
    fun queueFaults() = replay("queue-faults.v1.json")

    /** An application message from an epoch the group has already left. */
    @Test
    fun delayedPastEpochAppMessage() = replay("delayed-past-epoch-app-message.v1.json")

    /** Evicted and invited back: the second membership is not the first. */
    @Test
    fun readdAfterEviction() = replay("readd-after-eviction.v1.json")

    /**
     * A restart in the middle of a replayed, duplicated, reordered queue.
     * Nothing may depend on state that only lived in memory.
     */
    @Test
    fun restartDeliveryFaults() = replay("restart-delivery-faults.v1.json")

    /**
     * A leaver stops being able to read the group at the commit that evicts
     * them — and every OTHER member applies that commit too.
     *
     * This vector found two real defects. The staged-proposal pool did not
     * travel with the group state, so the commit meant to evict the leaver
     * carried an empty proposal list and left them in the tree with the keys.
     * And a peer's proposal was inlined into that commit, which attributes it
     * to the committer, while the committer derived a path-based commit secret
     * for a commit that carries no path — so every witness rejected it and
     * fell an epoch behind.
     */
    @Test
    fun leaverRemovalSecrecy() = replay("leaver-removal-secrecy.v1.json")

    /**
     * `convergence-committer-selected` concludes with a `convergence_decision`
     * — which tip the client picked, under which rule, and whether the witness
     * quorum was met. Our convergence engine makes that decision but does not
     * report it in those terms, so there is nothing to compare against and the
     * runner refuses the vector rather than passing it on the observations it
     * can check.
     *
     * Asserted rather than deleted so the gap stays visible: the day the engine
     * exposes its decision, this test fails and the vector moves up to [replay].
     */
    @Test
    fun aConvergenceDecisionIsStillUnmodelled() {
        val thrown =
            assertFailsWith<UnsupportedScenarioOutcome> {
                runBlocking { MarmotScenarioRunner(load("convergence-committer-selected.v1.json")).run() }
            }
        assertEquals("convergence_decision", thrown.outcomeType)
    }

    /**
     * Every vector must parse to at least one thing to check.
     *
     * Two expectation shapes ship in this set — `expected_trace.observations`
     * and `expected_outcomes` — and reading only the first left seven of the
     * nine vectors with nothing to compare against, replaying their steps and
     * reporting green. A vector that asserts nothing is worse than a missing
     * vector, so this guards the parser rather than any one scenario.
     */
    @Test
    fun everyVectorStatesSomethingToCheck() {
        VECTORS.forEach { name ->
            val vector = load(name)
            assertTrue(
                vector.observations.isNotEmpty() || vector.unmodelledOutcomes.isNotEmpty(),
                "$name parsed to zero expectations — it would pass without checking anything",
            )
        }
    }

    @Test
    fun theVectorsAreTheOnesTheShippingAppsEmbed() {
        // A vector refreshed from tip would test us against an engine nobody
        // runs. The copies here come from the commit both White Noise clients
        // pin, and their own `conformance_version` is what says so.
        val vector = load("three-client-message-exchange.v1.json")
        assertTrue(
            vector.conformanceVersion.startsWith("0.9."),
            "unexpected conformance version ${vector.conformanceVersion}",
        )
    }

    private companion object {
        /** Every vector this suite ships, so the parser guard covers them all. */
        val VECTORS =
            listOf(
                "invite-member.v1.json",
                "current-profile-required-set.v1.json",
                "latecomer-forward-secrecy.v1.json",
                "multigroup-isolation.v1.json",
                "publish-fail.v1.json",
                "invite-publish-fail.v1.json",
                "three-client-message-exchange.v1.json",
                "conversation.v1.json",
                "convergence-committer-selected.v1.json",
                "group-data-update.v1.json",
                "deferred-tick-catchup.v1.json",
                "incremental-growth.v1.json",
                "drop-queued.v1.json",
                "queue-faults.v1.json",
                "delayed-past-epoch-app-message.v1.json",
                "readd-after-eviction.v1.json",
                "restart-delivery-faults.v1.json",
                "leaver-removal-secrecy.v1.json",
            )
    }
}
