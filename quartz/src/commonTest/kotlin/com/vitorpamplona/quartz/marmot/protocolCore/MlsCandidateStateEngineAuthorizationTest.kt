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
package com.vitorpamplona.quartz.marmot.protocolCore

import com.vitorpamplona.quartz.marmot.groups.MarmotGroupPolicy
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The convergence engine's authorization gate, over real MLS groups.
 *
 * `MlsCandidateGraphTest` proves the graph algebra; this proves the one thing
 * that decides whether a fork is *allowed* rather than merely well formed.
 *
 * It exists because the gate was silently off. MIP-03's rules used to live
 * inside `MlsGroup` and ran on every path by construction; moving them behind
 * `MlsGroupPolicy` made them something a caller has to ask for, and
 * `MlsGroup.restore` defaults to `Permissive`, whose `authorizeCommit` does
 * nothing. The engine restored without a policy, so `isAuthorized` returned
 * true for every commit it could parse — a non-admin could remove an admin and
 * convergence would accept the branch.
 *
 * Nothing failed while it was broken: the gate only says *no* to commits no
 * honest client sends, so every existing test passed either way. That is the
 * shape of bug this file is here to catch.
 */
class MlsCandidateStateEngineAuthorizationTest {
    private val engine = MlsCandidateStateEngine()

    private fun account(seed: Byte) = ByteArray(32) { seed }

    /**
     * A group whose only admin is Alice, with Bob as an ordinary member.
     *
     * Built through `MarmotGroupPolicy` rather than the default so the group
     * is the one Marmot actually runs; a group created permissively would not
     * carry the rules the gate reads.
     */
    private fun adminGroup(): Pair<MlsGroup, MlsGroup> {
        val aliceId = account(0x0a)
        val groupData =
            MarmotGroupData(
                nostrGroupId = ByteArray(32) { 0x11 }.toHexKey(),
                name = "Admins",
                adminPubkeys = listOf(aliceId.toHexKey()),
            )

        val alice =
            MlsGroup.create(
                identity = aliceId,
                initialExtensions = listOf(groupData.toExtension()),
                policy = MarmotGroupPolicy,
            )
        val bobBundle = alice.createKeyPackage(identity = account(0x0b), signingKey = ByteArray(32) { 1 })
        val addBob = alice.addMember(bobBundle.keyPackage.toTlsBytes())

        // Bob is restored WITHOUT Marmot's policy on purpose. The local gate
        // refuses to author an unauthorized commit, which is correct and is
        // why this fixture cannot use it: convergence exists to judge commits
        // that arrived from somebody else's client, and a hostile or merely
        // non-compliant one does not censor itself. A permissive Bob is the
        // honest stand-in for that peer.
        val bob = MlsGroup.processWelcome(addBob.welcomeBytes!!, bobBundle)

        return alice to bob
    }

    @Test
    fun `the group really does name one admin`() {
        // Guards the fixture, not the engine. If the admin set came back empty
        // the test below would pass through isAuthorized's bootstrap
        // short-circuit and prove nothing at all.
        val (alice, _) = adminGroup()
        assertTrue(MarmotGroupPolicy.adminIdentitiesIn(alice.extensions).isNotEmpty(), "fixture names no admin")
    }

    @Test
    fun `a non-admin may not commit the removal of an admin`() {
        val (alice, bob) = adminGroup()
        val parent = alice.saveState()

        // Bob is an ordinary member. Removing Alice is exactly the change
        // MIP-03 reserves to admins, and the admin-depletion guard refuses it
        // besides — she is the only one.
        val bobRemovesAlice = bob.removeMember(targetLeafIndex = 0)

        assertFalse(
            engine.isAuthorized(parent, bobRemovesAlice.framedCommitBytes),
            "a non-admin removing the only admin must not be authorized",
        )
    }

    @Test
    fun `an admin may commit the same kind of change`() {
        // The other half: a gate that says no to everything would also satisfy
        // the test above, so prove it still says yes where it should. Alice
        // authors through her own (Marmot) policy, which is the real path.
        val (alice, _) = adminGroup()
        val parent = alice.saveState()
        val aliceRemovesBob = MlsGroup.restore(parent, MarmotGroupPolicy).removeMember(targetLeafIndex = 1)

        assertTrue(
            engine.isAuthorized(parent, aliceRemovesBob.framedCommitBytes),
            "the admin's own removal of an ordinary member must stay authorized",
        )
    }
}
