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
     * The two vectors we cannot replay, and exactly why.
     *
     * Both create a group with several invitees. The reference adds them in one
     * commit, so the group is at epoch 1; our `addMember` stages one Add per
     * commit, so we would reach epoch 2. Neither is a protocol error — a commit
     * per Add is valid MLS and any peer processes it — but the traces cannot
     * match until we can commit several Adds together, and creating a group
     * costs us an extra round trip per invitee until then.
     *
     * Asserted rather than deleted so the divergence stays visible: the day
     * batched adds land, this test fails and these two move up to [replay].
     */
    @Test
    fun aMultiInviteeCreateStillDivergesOnBatching() {
        listOf(
            "three-client-message-exchange.v1.json",
            "convergence-committer-selected.v1.json",
            "conversation.v1.json",
        ).forEach { name ->
            val thrown =
                assertFailsWith<ScenarioBatchingDivergence>("$name should still diverge on batching") {
                    runBlocking { MarmotScenarioRunner(load(name)).run() }
                }
            assertTrue(
                thrown.message.orEmpty().contains("one commit"),
                "the divergence must say what it is: ${thrown.message}",
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
}
