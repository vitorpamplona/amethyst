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
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.protocolCore.GroupLifecycleState
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Disbanding a group — `marmot.group.lifecycle.v1`, component `0x800c`.
 *
 * We already REFUSED work in a disbanded group; nothing could put a group into
 * that state from this side, so the enforcement was only ever reachable from a
 * peer's commit. These cover the initiator half.
 *
 * Disband is absorbing: there is no un-disband, no later branch supersedes it,
 * and a replacement conversation is a new MLS group. So the guards matter more
 * than the happy path — a mistaken disband cannot be undone, and one published
 * off unpublished or untrusted state would terminalize the group for everyone
 * on a commit its author never confirmed.
 */
class MarmotDisbandTest {
    private val nostrGroupId = "d".repeat(64)

    private class Fixture(
        publisher: MarmotPublisher = ACCEPTING_RELAY,
    ) {
        val signer = NostrSignerInternal(KeyPair())
        val manager =
            MarmotManager(
                signer,
                SnapshotStateStore(),
                SnapshotMessageStore(),
                SnapshotBundleStore(),
                publisher = publisher,
            )
    }

    private suspend fun Fixture.createCurrentProfile() =
        manager.createCurrentProfileGroup(
            nostrGroupId = nostrGroupId,
            relays = listOf("wss://relay.invalid"),
            profile = GroupProfileV1("doomed", ""),
        )

    @Test
    fun `an admin disbands the group and everything after is refused`() =
        runBlocking {
            val f = Fixture()
            f.createCurrentProfile()

            f.manager.disbandGroup(nostrGroupId)

            assertTrue(f.manager.groupState(nostrGroupId)?.isDisbanded == true)
            assertEquals(GroupLifecycleState.DISBANDED, f.manager.lifecycle(nostrGroupId))

            // The whole point of the state: outbound work stops.
            assertFailsWith<IllegalStateException> {
                f.manager.buildTextMessage(nostrGroupId, "anyone still here?")
            }
            Unit
        }

    @Test
    fun `disband is absorbing and cannot be issued twice`() =
        runBlocking {
            val f = Fixture()
            f.createCurrentProfile()
            f.manager.disbandGroup(nostrGroupId)

            val thrown = assertFailsWith<IllegalStateException> { f.manager.disbandGroup(nostrGroupId) }
            assertTrue(thrown.message.orEmpty().contains("already disbanded"))
        }

    @Test
    fun `a non-admin member cannot disband`() =
        runBlocking {
            // Two clients: the creator is the admin, the invitee is not. Peers
            // would reject a lifecycle change from a non-admin anyway; refusing
            // locally is what stops the invitee burning an epoch on a commit
            // nobody will apply.
            val alice = Fixture()
            val bob = Fixture()
            alice.createCurrentProfile()

            val kp = bob.manager.generateKeyPackageEvent(relays = emptyList())
            val (_, welcome) = alice.manager.addMember(nostrGroupId, kp, emptyList())
            bob.manager.ingest(welcome!!.giftWrapEvent)

            val thrown = assertFailsWith<IllegalStateException> { bob.manager.disbandGroup(nostrGroupId) }
            assertTrue(thrown.message.orEmpty().contains("Only an admin"))
        }

    @Test
    fun `a legacy group has no carrier for a lifecycle state`() =
        runBlocking {
            val f = Fixture()
            f.manager.createGroup(
                nostrGroupId,
                MarmotGroupData(
                    nostrGroupId = nostrGroupId,
                    name = "legacy",
                    relays = listOf("wss://relay.invalid"),
                ),
            )

            val thrown = assertFailsWith<IllegalStateException> { f.manager.disbandGroup(nostrGroupId) }
            assertTrue(thrown.message.orEmpty().contains("legacy"))
        }

    @Test
    fun `a disband no relay accepted does not terminalize the group locally`() =
        runBlocking {
            // Publish-before-apply, on the one commit that cannot be walked
            // back: a group disbanded here but nowhere else would be dead for
            // us and alive for everyone. The obligation stays queued, the local
            // group stays live, and the caller is told it did NOT happen —
            // otherwise the UI would announce an ending that never occurred.
            val f = Fixture(publisher = MarmotPublisher { _, _ -> false })
            f.createCurrentProfile()

            val thrown = assertFailsWith<IllegalStateException> { f.manager.disbandGroup(nostrGroupId) }
            assertTrue(thrown.message.orEmpty().contains("reached no relay"))

            assertTrue(f.manager.groupState(nostrGroupId)?.isDisbanded != true)
            assertTrue(f.manager.lifecycle(nostrGroupId) != GroupLifecycleState.DISBANDED)

            // Still a working group: nothing about a failed disband may leak
            // into the states that stop outbound work.
            f.manager.buildTextMessage(nostrGroupId, "still here")
            Unit
        }
}
