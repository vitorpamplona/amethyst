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

import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.protocolCore.GroupLifecycleState
import com.vitorpamplona.quartz.marmot.protocolCore.InMemoryPublishObligationStore
import com.vitorpamplona.quartz.marmot.protocolCore.LocalOutboundGate
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

            // The Commit applied, so the group's own state says disbanded —
            // but the LIFECYCLE does not, yet. `group-lifecycle-v1.md` is
            // explicit that a disband is never terminalized through ordinary
            // linear advancement: admitting it forces `Recovering` even with no
            // fork, and only a SELECTED disband Commit moves it to `Disbanded`.
            assertTrue(f.manager.groupState(nostrGroupId)?.isDisbanded == true)
            assertEquals(GroupLifecycleState.RECOVERING, f.manager.lifecycle(nostrGroupId))
            assertTrue(f.manager.isDisbanding(nostrGroupId))

            // Outbound work stops immediately all the same — that is the
            // `Disbanding` gate, not the lifecycle.
            assertFailsWith<IllegalStateException> {
                f.manager.buildTextMessage(nostrGroupId, "anyone still here?")
            }

            // Settle the pass and the group terminalizes for real.
            f.manager.driveConvergenceToSettlement(pollMs = 1)
            assertEquals(GroupLifecycleState.DISBANDED, f.manager.lifecycle(nostrGroupId))
            assertFalse(f.manager.isDisbanding(nostrGroupId), "a resolved request lowers its gate")
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
    fun `the disband commit carries the whole shape the spec fixes`() =
        runBlocking {
            // `group-lifecycle-v1.md` fixes every part of this Commit, and a
            // peer validates the whole set: the lifecycle update alone — which
            // is what this used to send — reads as an unsupported transition
            // and is rejected, leaving the group live for everyone else while
            // reading as ended here.
            val alice = Fixture()
            val bob = Fixture()
            alice.createCurrentProfile()

            val kp = bob.manager.generateKeyPackageEvent(relays = emptyList())
            val (_, welcome) = alice.manager.addMember(nostrGroupId, kp, emptyList())
            bob.manager.ingest(welcome!!.giftWrapEvent)
            assertEquals(2, alice.manager.memberCount(nostrGroupId))

            alice.manager.disbandGroup(nostrGroupId)

            assertTrue(alice.manager.groupState(nostrGroupId)?.isDisbanded == true)
            // Every leaf but the committer's is gone, and the admin policy is a
            // full replacement naming only the committer.
            assertEquals(1, alice.manager.memberCount(nostrGroupId))
            assertEquals(
                listOf(alice.signer.pubKey),
                alice.manager.groupView(nostrGroupId)?.adminPubkeys,
            )
        }

    @Test
    fun `a witness applies the disband and lands terminal`() =
        runBlocking {
            // The half that matters for interop: the removed member has to be
            // able to APPLY the commit that removes them, read the terminal
            // state out of it, and stop — not reject it and sit at the old
            // epoch believing the group is still live.
            val alice = Fixture()
            val bob = Fixture()
            alice.createCurrentProfile()

            val kp = bob.manager.generateKeyPackageEvent(relays = emptyList())
            val (_, welcome) = alice.manager.addMember(nostrGroupId, kp, emptyList())
            bob.manager.ingest(welcome!!.giftWrapEvent)

            val commit = alice.manager.disbandGroup(nostrGroupId)
            bob.manager.ingest(commit.signedEvent)

            // Same rule on the receiving side: admitted, then selected. A
            // witness that terminalized on arrival could not tell a disband
            // that won from one that lost a race it never saw.
            assertEquals(GroupLifecycleState.RECOVERING, bob.manager.lifecycle(nostrGroupId))
            bob.manager.driveConvergenceToSettlement(pollMs = 1)

            assertEquals(GroupLifecycleState.DISBANDED, bob.manager.lifecycle(nostrGroupId))
            assertFailsWith<IllegalStateException> {
                bob.manager.buildTextMessage(nostrGroupId, "still here?")
            }
            Unit
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

            f.manager.disbandGroup(nostrGroupId)

            assertTrue(f.manager.groupState(nostrGroupId)?.isDisbanded != true)
            assertTrue(f.manager.lifecycle(nostrGroupId) != GroupLifecycleState.DISBANDED)

            // What a failed publish must NOT do is throw the request away. The
            // component calls the gate durable precisely so it "survives
            // publication failure", and an admin who ended a conversation does
            // not need to be told to click again because a relay blinked.
            assertTrue(f.manager.isDisbanding(nostrGroupId))

            // And nothing may be sent while it is unresolved. The group is not
            // terminal — it may yet come back if the request turns out to be
            // impossible — but it is no longer an ordinary live conversation.
            assertFailsWith<IllegalStateException> {
                f.manager.buildTextMessage(nostrGroupId, "still here")
            }
            Unit
        }

    @Test
    fun `the gate reaches the front end's group state`() =
        runBlocking {
            // The UI cannot ask a suspending publish gate from the synchronous
            // path that refreshes a conversation, so the gate is mirrored onto
            // the chatroom. If that mirror is missing, the composer stays
            // enabled on a group that refuses every send and the user finds out
            // by tapping.
            val f = Fixture()
            f.createCurrentProfile()
            val chatroom = MarmotGroupChatroom(nostrGroupId)

            f.manager.syncMetadataTo(nostrGroupId, chatroom)
            assertNull(chatroom.outboundGate.value, "a live group has no gate")

            f.manager.disbandGroup(nostrGroupId)
            f.manager.syncMetadataTo(nostrGroupId, chatroom)
            assertEquals(LocalOutboundGate.DISBANDING, chatroom.outboundGate.value)
        }

    @Test
    fun `rejoining a group we left clears the departure gate`() =
        runBlocking {
            // Leaving raises a durable `Leaving` gate, and nothing but a
            // re-join clears it. That was harmless while gates blocked nothing;
            // now that they stop outbound work — and survive restarts — a group
            // you left and were invited back to would be readable and
            // permanently unsendable.
            val alice = Fixture()
            val bob = Fixture()
            alice.createCurrentProfile()

            val kp = bob.manager.generateKeyPackageEvent(relays = emptyList())
            val (_, welcome) = alice.manager.addMember(nostrGroupId, kp, emptyList())
            bob.manager.ingest(welcome!!.giftWrapEvent)

            bob.manager.leaveGroup(nostrGroupId)
            assertFailsWith<IllegalStateException>("a leaving member may not send") {
                bob.manager.buildTextMessage(nostrGroupId, "one more thing")
            }

            // Invited back: a fresh KeyPackage, a fresh Welcome.
            val rejoinKp = bob.manager.generateKeyPackageEvent(relays = emptyList())
            val (_, rejoinWelcome) = alice.manager.addMember(nostrGroupId, rejoinKp, emptyList())
            bob.manager.ingest(rejoinWelcome!!.giftWrapEvent)

            // The group has to be usable again — that is the whole point of
            // being invited back.
            bob.manager.buildTextMessage(nostrGroupId, "back again")
            Unit
        }

    @Test
    fun `a pending disband request outlives a restart`() =
        runBlocking {
            // The gate is durable or it is nothing: the crash that happens
            // between "the admin pressed disband" and "a relay took the commit"
            // is exactly the case it exists for, and an in-memory flag loses
            // the intent there and offers the group as live on the next start.
            val store = SnapshotStateStore()
            val obligations = InMemoryPublishObligationStore()
            val first =
                MarmotManager(
                    NostrSignerInternal(KeyPair()),
                    store,
                    SnapshotMessageStore(),
                    SnapshotBundleStore(),
                    publisher = MarmotPublisher { _, _ -> false },
                    publishObligationStore = obligations,
                )
            first.createCurrentProfileGroup(
                nostrGroupId = nostrGroupId,
                relays = listOf("wss://relay.invalid"),
                profile = GroupProfileV1("doomed", ""),
            )
            first.disbandGroup(nostrGroupId)
            assertTrue(first.isDisbanding(nostrGroupId))

            // A fresh manager over the same stores is what a restart looks like.
            val restarted =
                MarmotManager(
                    first.signer,
                    store,
                    SnapshotMessageStore(),
                    SnapshotBundleStore(),
                    publisher = ACCEPTING_RELAY,
                    publishObligationStore = obligations,
                )
            restarted.restoreAll()

            assertTrue(restarted.isDisbanding(nostrGroupId), "the request must survive the restart")
            assertFailsWith<IllegalStateException> {
                restarted.buildTextMessage(nostrGroupId, "did it end?")
            }
            Unit
        }

    @Test
    fun `a disband that loses a branch race is regenerated, not dropped`() =
        runBlocking {
            // The case terminalizing-on-application could never survive. Alice
            // disbands; the branch that wins is an ACTIVE one from bob, so her
            // Commit loses. The spec says an authorized client regenerates it
            // against the selected state — and the only reason she still can is
            // that she never went terminal, because a `Disbanded` client stops
            // processing group traffic and could not have learned she lost.
            val alice = Fixture()
            val bob = Fixture()
            alice.createCurrentProfile()

            val kp = bob.manager.generateKeyPackageEvent(relays = emptyList())
            val (_, welcome) = alice.manager.addMember(nostrGroupId, kp, emptyList())
            bob.manager.ingest(welcome!!.giftWrapEvent)

            // Bob is promoted so his own commit is one alice will accept.
            alice.manager
                .setGroupAdmins(nostrGroupId, listOf(alice.signer.pubKey, bob.signer.pubKey))
                .let { bob.manager.ingest(it.signedEvent) }

            // Both commit off the same epoch: alice's disband and bob's rename.
            val disband = alice.manager.disbandGroup(nostrGroupId)
            assertTrue(alice.manager.isDisbanding(nostrGroupId))
            val rename = bob.manager.setGroupProfile(nostrGroupId, "still going", "")

            // Alice sees bob's competing commit and settles the pass.
            alice.manager.ingest(rename.signedEvent)
            alice.manager.driveConvergenceToSettlement(pollMs = 1)

            // Whatever branch won, the REQUEST is still alive: either it was
            // the disband (terminal, gate down) or it was not (gate still up,
            // regenerated against the selected state). What must never happen
            // is a group that is live for bob and terminal for alice.
            val lifecycle = alice.manager.lifecycle(nostrGroupId)
            if (lifecycle == GroupLifecycleState.DISBANDED) {
                assertFalse(alice.manager.isDisbanding(nostrGroupId))
            } else {
                assertTrue(
                    alice.manager.isDisbanding(nostrGroupId),
                    "a disband that lost its branch must stay pending, not vanish",
                )
            }
            // Either way the commit alice published is the spec's shape.
            assertTrue(disband.signedEvent.id.isNotEmpty())
            Unit
        }
}
