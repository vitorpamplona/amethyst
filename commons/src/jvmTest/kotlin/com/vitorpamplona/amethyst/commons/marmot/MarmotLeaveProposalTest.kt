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

import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What happens to a member who leaves.
 *
 * MIP-03 makes a departure a standalone `SelfRemove` PROPOSAL: the leaver
 * cannot evict themselves, because a proposal advances nothing. Until an
 * authorized member commits it, the leaver is STILL IN THE TREE — which means
 * still holding the group's keys and still able to decrypt everything sent
 * after they left. That is the opposite of what leaving is for, so the commit
 * is not a nicety; it is the point.
 */
class MarmotLeaveProposalTest {
    private val nostrGroupId = "1".repeat(64)

    private class Fixture {
        val signer = NostrSignerInternal(KeyPair())
        val manager =
            MarmotManager(
                signer,
                SnapshotStateStore(),
                SnapshotMessageStore(),
                SnapshotBundleStore(),
                publisher = ACCEPTING_RELAY,
            )
    }

    @Test
    fun `an admin commits a departing member's SelfRemove and the tree shrinks`() =
        runBlocking {
            val alice = Fixture()
            val bob = Fixture()
            alice.manager.createCurrentProfileGroup(
                nostrGroupId = nostrGroupId,
                relays = listOf("wss://relay.invalid"),
                profile = GroupProfileV1("departures", ""),
            )

            val kp = bob.manager.generateKeyPackageEvent(relays = emptyList())
            val (commit, welcome) = alice.manager.addMember(nostrGroupId, kp, emptyList())
            bob.manager.ingest(welcome!!.giftWrapEvent)
            alice.manager.ingest(commit.signedEvent)
            assertEquals(2, alice.manager.memberCount(nostrGroupId))

            // Bob departs. The proposal is all he can produce.
            val proposal = bob.manager.leaveGroup(nostrGroupId)

            val staged = alice.manager.ingest(proposal.signedEvent)
            assertIs<MarmotIngestResult.ProposalStaged>(
                staged,
                "a peer's SelfRemove must reach the pending pool, not be dropped",
            )
            assertTrue(
                alice.manager.groupManager.hasPendingProposals(nostrGroupId),
                "the staged proposal must be visible as pending work",
            )

            assertTrue(
                alice.manager.commitPendingProposals(nostrGroupId, emptyList()) != null,
                "there was pending work, so a commit must have been produced",
            )

            assertEquals(
                1,
                alice.manager.memberCount(nostrGroupId),
                "committing a SelfRemove must actually evict the leaver — otherwise they keep " +
                    "the group's keys and keep reading everything sent after they left",
            )
        }
}
