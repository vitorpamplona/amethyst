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
package com.vitorpamplona.quartz.nip86RelayManagement.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BanStoreTest {
    @Test
    fun pubkeyBanIsCaseInsensitive() {
        val s = BanStore()
        s.banPubkey("ABCDEF1234".padEnd(64, '0'), "spam")
        assertTrue(s.isBanned("abcdef1234".padEnd(64, '0')))
        s.unbanPubkey("abcdef1234".padEnd(64, '0'))
        assertFalse(s.isBanned("ABCDEF1234".padEnd(64, '0')))
    }

    @Test
    fun allowListEmptyMeansEveryoneAllowed() {
        val s = BanStore()
        assertFalse(s.hasAllowList())
        // No allow list → policy decision is purely deny-based; the
        // store doesn't say a pubkey IS allowed unless it's listed.
        assertFalse(s.isAllowedPubkey("aaaa".padEnd(64, '0')))
    }

    @Test
    fun allowListNonEmptyTracksMembers() {
        val s = BanStore()
        s.allowPubkey("aa".padEnd(64, '0'), "trusted")
        assertTrue(s.hasAllowList())
        assertTrue(s.isAllowedPubkey("aa".padEnd(64, '0')))
        assertFalse(s.isAllowedPubkey("bb".padEnd(64, '0')))
        s.unallowPubkey("aa".padEnd(64, '0'))
        assertFalse(s.hasAllowList())
    }

    @Test
    fun eventBanRoundTrip() {
        val s = BanStore()
        s.banEvent("ee".padEnd(64, '0'), "policy")
        assertTrue(s.isBannedEvent("EE".padEnd(64, '0')))
        s.unbanEvent("ee".padEnd(64, '0'))
        assertFalse(s.isBannedEvent("ee".padEnd(64, '0')))
        assertFalse(s.isAllowedEvent("ee".padEnd(64, '0')), "unban must not allow-list")
    }

    @Test
    fun banAndAllowAreMutuallyExclusiveForPubkeys() {
        val s = BanStore()
        val pk = "aa".padEnd(64, '0')
        s.allowPubkey(pk, "trusted")
        s.banPubkey(pk, "spam")
        assertTrue(s.isBanned(pk))
        assertFalse(s.isAllowedPubkey(pk), "banpubkey must remove from the allow list")

        s.allowPubkey(pk.uppercase(), "trusted again")
        assertTrue(s.isAllowedPubkey(pk))
        assertFalse(s.isBanned(pk), "allowpubkey must remove from the ban list")

        s.unallowPubkey(pk)
        assertFalse(s.isAllowedPubkey(pk))
        assertFalse(s.isBanned(pk), "unallowpubkey must not ban")

        s.banPubkey(pk)
        s.unbanPubkey(pk)
        assertFalse(s.isBanned(pk))
        assertFalse(s.isAllowedPubkey(pk), "unbanpubkey must not allow-list")
    }

    @Test
    fun banAndAllowAreMutuallyExclusiveForEvents() {
        val s = BanStore()
        val id = "ee".padEnd(64, '0')
        s.allowEvent(id, "approved")
        assertTrue(s.isAllowedEvent(id))
        assertEquals(listOf(id to "approved"), s.listAllowedEvents())

        s.banEvent(id, "spam")
        assertTrue(s.isBannedEvent(id))
        assertFalse(s.isAllowedEvent(id), "banevent must remove from the allow list")

        s.allowEvent(id)
        assertTrue(s.isAllowedEvent(id))
        assertFalse(s.isBannedEvent(id), "allowevent must remove from the ban list")

        s.unallowEvent(id)
        assertFalse(s.isAllowedEvent(id))
        assertFalse(s.isBannedEvent(id), "unallowevent must not ban")
    }

    @Test
    fun rolesAssignmentsAndDeletion() {
        val s = BanStore()
        val pk = "aa".padEnd(64, '0')
        assertTrue(s.createRole(RelayRole("b", label = "B", order = 2)))
        assertTrue(s.createRole(RelayRole("a", label = "A", order = 1)))
        assertTrue(s.createRole(RelayRole("z")))
        assertFalse(s.createRole(RelayRole("a", label = "dup")))
        assertEquals(listOf("a", "b", "z"), s.listRoles().map { it.id }, "sorted by order, unordered last")

        assertFalse(s.editRole(RelayRole("missing")))
        assertTrue(s.editRole(RelayRole("a", label = "Alpha", color = 30)))
        assertEquals(RelayRole("a", label = "Alpha", color = 30), s.getRole("a"))

        assertFalse(s.assignRole(pk, "missing"))
        assertTrue(s.assignRole(pk.uppercase(), "a"))
        assertTrue(s.assignRole(pk, "b"))
        assertTrue(s.assignRole(pk, "a"))
        assertEquals(listOf("a", "b"), s.rolesOf(pk))

        s.deleteRole("a")
        assertEquals(listOf("b"), s.rolesOf(pk))
        s.unassignRole(pk, "b")
        assertEquals(emptyList(), s.rolesOf(pk))
        assertEquals(emptyList(), s.listRoleAssignments())
    }

    @Test
    fun claims() {
        val s = BanStore()
        s.createClaim("code-1")
        s.createClaim("code-1")
        s.createClaim("code-2")
        assertEquals(listOf("code-1", "code-2"), s.listClaims())
        assertTrue(s.isValidClaim("code-2"))
        s.deleteClaim("code-2")
        assertFalse(s.isValidClaim("code-2"))
    }

    @Test
    fun mutationsFireHookButFailedRoleEditsDoNot() {
        var count = 0
        val s = BanStore(onMutation = { count++ })
        s.allowEvent("ee".padEnd(64, '0'))
        assertEquals(1, count)
        s.editRole(RelayRole("missing"))
        s.assignRole("aa".padEnd(64, '0'), "missing")
        assertEquals(1, count)
    }

    @Test
    fun seedFromSnapshotRestoresNewSectionsAndResolvesConflicts() {
        val s = BanStore()
        val pk = "aa".padEnd(64, '0')
        val id = "ee".padEnd(64, '0')
        s.seedFromSnapshot(
            bannedPubkeys = listOf(pk to "spam"),
            allowedPubkeys = listOf(pk to "hand-edited conflict"),
            bannedEvents = listOf(id to "x"),
            allowedEvents = listOf(id to "conflict", "ff".padEnd(64, '0') to "ok"),
            roles = listOf(RelayRole("mod")),
            roleAssignments = listOf(pk to listOf("mod", "ghost")),
            claims = listOf("c"),
        )
        assertTrue(s.isBanned(pk))
        assertFalse(s.isAllowedPubkey(pk))
        assertTrue(s.isBannedEvent(id))
        assertFalse(s.isAllowedEvent(id))
        assertTrue(s.isAllowedEvent("ff".padEnd(64, '0')))
        assertEquals(listOf("mod"), s.rolesOf(pk), "assignments to unknown roles are dropped")
        assertTrue(s.isValidClaim("c"))
    }

    @Test
    fun policyLetsAllowListedEventsBypassPubkeyAndKindRules() {
        val s = BanStore()
        val policy = BanListPolicy(s)
        val author = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
        val allowedId = "a".repeat(64)
        val otherId = "b".repeat(64)

        fun event(id: String) = Event(id, author, 1000L, 1, emptyArray(), "hi", "0".repeat(128))

        // Empty event allow list changes nothing.
        assertTrue(policy.accept(EventCmd(event(otherId))) is PolicyResult.Accepted)

        s.banPubkey(author)
        s.disallowKind(1)
        assertTrue(policy.accept(EventCmd(event(otherId))) is PolicyResult.Rejected)

        s.allowEvent(allowedId)
        assertTrue(policy.accept(EventCmd(event(allowedId))) is PolicyResult.Accepted)
        assertTrue(policy.accept(EventCmd(event(otherId))) is PolicyResult.Rejected)

        s.banEvent(allowedId)
        assertTrue(policy.accept(EventCmd(event(allowedId))) is PolicyResult.Rejected)
    }

    @Test
    fun kindAllowDenyRules() {
        val s = BanStore()
        // Empty allow + empty deny → every kind is allowed.
        assertTrue(s.isKindAllowed(1))

        s.allowKind(1)
        s.allowKind(7)
        // Allow non-empty → only listed kinds are allowed.
        assertTrue(s.isKindAllowed(1))
        assertFalse(s.isKindAllowed(4))

        s.disallowKind(7)
        // Disallowing a kind removes it from the allow list and blocks.
        assertFalse(s.isKindAllowed(7))
        assertTrue(s.isKindAllowed(1))
        assertEquals(listOf(1), s.listAllowedKinds())
        assertEquals(listOf(7), s.listDisallowedKinds())
    }

    @Test
    fun listsReflectStateForAuditTrail() {
        val s = BanStore()
        s.banPubkey("aa".padEnd(64, '0'), "spam")
        s.banPubkey("bb".padEnd(64, '0'), null)
        val banned = s.listBannedPubkeys().toMap()
        assertEquals("spam", banned["aa".padEnd(64, '0')])
        assertEquals(null, banned["bb".padEnd(64, '0')])
    }
}
