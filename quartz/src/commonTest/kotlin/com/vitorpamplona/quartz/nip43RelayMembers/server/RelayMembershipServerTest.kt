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
package com.vitorpamplona.quartz.nip43RelayMembers.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.joinRequest.RelayJoinRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.leaveRequest.RelayLeaveRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.membersWithRoles
import com.vitorpamplona.quartz.nip43RelayMembers.list.tags.RelayMember
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRoleEvent
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayMembershipServerTest {
    private val relay = NostrSignerSync(KeyPair())
    private val alice = NostrSignerSync(KeyPair())

    private val now = 1_700_000_000L

    /** Everything the engine published, in order. */
    private val published = mutableListOf<Event>()

    private fun server(
        banStore: BanStore = BanStore(),
        stored: List<Event> = emptyList(),
    ) = RelayMembershipServer(
        signer = relay,
        banStore = banStore,
        publish = {
            published += it
            true
        },
        load = { filter -> stored.filter { filter.match(it) } },
        relayName = "wss://test.relay",
        clock = { now },
    )

    private fun ofKind(kind: Int) = published.filter { it.kind == kind }

    @Test
    fun joinAdmitsAValidClaimAndPublishesSignedEvents() =
        runTest {
            val store = BanStore().apply { createClaim("code") }
            val server = server(store)

            val ok = server.join(alice.sign(RelayJoinRequestEvent.build("code", now)))
            assertTrue(ok.success, ok.message)
            assertEquals("info: welcome to wss://test.relay!", ok.message)
            assertTrue(store.isAllowedPubkey(alice.pubKey))

            assertTrue(published.all { it.pubKey == relay.pubKey }, "every NIP-43 event is signed by the relay's key")
            assertEquals(listOf(alice.pubKey), (ofKind(RelayAddMemberEvent.KIND).single() as RelayAddMemberEvent).memberPubKeys())
            assertEquals(listOf(RelayMember(alice.pubKey)), ofKind(RelayMembershipListEvent.KIND).single().tags.membersWithRoles())
        }

    @Test
    fun joinFailuresUseNip01Prefixes() =
        runTest {
            val store = BanStore().apply { createClaim("code") }
            val server = server(store)

            assertEquals("restricted: that is an invalid invite code.", server.join(alice.sign(RelayJoinRequestEvent.build("other", now))).message)
            assertTrue(server.join(alice.sign(RelayJoinRequestEvent.build("code", now - 3600))).message.startsWith("invalid:"))
            val noClaim = alice.sign<Event>(now, RelayJoinRequestEvent.KIND, arrayOf(arrayOf("-")), "")
            assertTrue(server.join(noClaim).message.startsWith("restricted:"))

            assertFalse(store.isAllowedPubkey(alice.pubKey))
            assertTrue(published.isEmpty())
        }

    @Test
    fun leaveNeedsTheProtectedTag() =
        runTest {
            val store = BanStore().apply { allowPubkey(alice.pubKey) }
            val server = server(store)
            // The boot-time sync: publishes alice as a member (8000 + 13534).
            server.sync()
            assertEquals(1, ofKind(RelayAddMemberEvent.KIND).size)

            val unprotected = alice.sign<Event>(now, RelayLeaveRequestEvent.KIND, emptyArray(), "")
            assertFalse(server.leave(unprotected).success)
            assertTrue(store.isAllowedPubkey(alice.pubKey))

            val ok = server.leave(alice.sign(RelayLeaveRequestEvent.build(now)))
            assertTrue(ok.success)
            assertFalse(store.isAllowedPubkey(alice.pubKey))
            assertEquals(listOf(alice.pubKey), (ofKind(RelayRemoveMemberEvent.KIND).single() as RelayRemoveMemberEvent).memberPubKeys())
        }

    @Test
    fun syncIsIdempotentAndStampsMonotonically() =
        runTest {
            val store = BanStore()
            val server = server(store)

            store.createRole(RelayRole("mod", label = "Moderator", color = 120, order = 1))
            server.sync()
            store.editRole(RelayRole("mod", label = "Mod"))
            server.sync()
            server.sync()

            val roles = ofKind(RelayRoleEvent.KIND).map { it as RelayRoleEvent }
            assertEquals(listOf("Moderator", "Mod"), roles.map { it.label() })
            // Same clock second, yet the edit is strictly newer, so it supersedes.
            assertEquals(now + 1, roles[1].createdAt)
            assertTrue(roles[1].createdAt > roles[0].createdAt)
            // Three syncs, but the (empty) member list changed only once: at first publish.
            assertEquals(1, ofKind(RelayMembershipListEvent.KIND).size)
        }

    @Test
    fun startsFromWhatTheStoreAlreadyHolds() =
        runTest {
            val store = BanStore().apply { allowPubkey(alice.pubKey) }
            val previous = relay.sign(RelayMembershipListEvent.buildWithRoles(listOf(RelayMember(alice.pubKey)), now - 10))
            val server = server(store, stored = listOf(previous))

            server.sync()
            assertTrue(published.isEmpty(), "nothing changed since the stored 13534")

            store.unallowPubkey(alice.pubKey)
            server.sync()
            assertEquals(1, ofKind(RelayRemoveMemberEvent.KIND).size)
            assertTrue(ofKind(RelayMembershipListEvent.KIND).single().createdAt > previous.createdAt)
        }

    @Test
    fun otherKindsAreNotHandled() =
        runTest {
            val server = server()
            assertNull(server.handle(alice.sign<Event>(now, 1, emptyArray(), "hi"), ctx = NoContext))
        }

    private object NoContext : RequestContext {
        override val connectionId = 0L
        override val policy: IRelayPolicy = EmptyPolicy
        override val authenticatedUsers = emptySet<HexKey>()
    }
}
