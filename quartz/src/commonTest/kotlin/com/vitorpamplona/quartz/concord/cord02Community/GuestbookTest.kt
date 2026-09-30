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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.ControlFixtures
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuestbookTest {
    private val member = KeyPair().pubKey.toHexKey()
    private val creator = KeyPair().pubKey.toHexKey()
    private val target = KeyPair().pubKey.toHexKey()

    @Test
    fun joinWithInviteAttributionRoundTrips() {
        val rumor = Guestbook.join(member, createdAt = 1_700_000_000L, subMs = 128, inviteCreator = creator, inviteLabel = "Reddit")
        assertEquals(Guestbook.KIND_JOIN_LEAVE, rumor.kind)
        assertEquals("join", rumor.content)

        val entry = Guestbook.parse(rumor)
        assertEquals(GuestbookAction.JOIN, entry?.action)
        assertEquals(member, entry?.member)
        assertEquals(creator, entry?.inviteCreator)
        assertEquals("Reddit", entry?.inviteLabel)
        assertEquals(1_700_000_000_128L, entry?.ms)
        assertEquals(rumor.id, entry?.rumorId)
    }

    @Test
    fun leaveParses() {
        val entry = Guestbook.parse(Guestbook.leave(member, createdAt = 1L))
        assertEquals(GuestbookAction.LEAVE, entry?.action)
        assertNull(entry?.inviteCreator)
    }

    @Test
    fun kickTargetsAMember() {
        val rumor = Guestbook.kick(actorPubKey = creator, target = target, createdAt = 1L)
        assertEquals(Guestbook.KIND_KICK, rumor.kind)
        assertEquals(target, Guestbook.kickTarget(rumor))
    }

    @Test
    fun kickMatchesTheExampleWireShape() {
        // examples.md §3.2: content "", tags ms, p, vac.
        val citation = AuthorityCitation(ByteArray(32) { 1 }, 3, ByteArray(32) { 2 })
        val rumor = Guestbook.kick(creator, target, createdAt = 1_722_410_000L, subMs = 301, citation = citation)
        assertEquals("", rumor.content)
        assertEquals(listOf("ms", "p", "vac"), rumor.tags.map { it[0] })
        assertEquals(listOf("ms", "301"), rumor.tags[0].toList())
        assertEquals(listOf("vac", "01".repeat(32), "3", "02".repeat(32)), rumor.tags[2].toList())

        val entry = assertNotNull(Guestbook.parse(rumor))
        assertEquals(GuestbookAction.KICK, entry.action)
        assertEquals(target, entry.member) // the state it sets is the target's
        assertEquals(creator, entry.author)
        assertEquals(1_722_410_000_301L, entry.ms)
        assertEquals(3L, entry.citation?.grantVersion)
    }

    @Test
    fun parseIgnoresNonGuestbookAndMalformedRumors() {
        assertNull(Guestbook.parse(Event("00".repeat(32), member, 1L, 9, emptyArray(), "join", "")))
        // A 3306 whose verb is "kick" is not a Kick: only kind 3309 can kick.
        assertNull(Guestbook.parse(Event("00".repeat(32), member, 1L, Guestbook.KIND_JOIN_LEAVE, emptyArray(), "kick", "")))
        // A malformed ms is dropped, never interpreted (CORD-02 §5).
        assertNull(Guestbook.parse(Event("00".repeat(32), member, 1L, Guestbook.KIND_JOIN_LEAVE, arrayOf(arrayOf("ms", "1000")), "join", "")))
        // A Kick naming no valid target.
        assertNull(Guestbook.parse(Event("00".repeat(32), member, 1L, Guestbook.KIND_KICK, arrayOf(arrayOf("p", "zz")), "", "")))
    }

    // ---- Kick authority (CORD-04 §5/§6) --------------------------------------

    private val owner = "0f".repeat(32)
    private val admin = "a1".repeat(32)
    private val mod = "b2".repeat(32)
    private val plain = "c3".repeat(32)
    private val other = "d4".repeat(32)
    private val adminRole = "11".repeat(32)
    private val modRole = "22".repeat(32)

    private fun role(
        roleId: String,
        json: String,
    ) = ControlEdition(ControlEntityKind.ROLE, roleId.hexToByteArray(), 0, null, null, json, owner, "role-$roleId", 0)

    private fun grant(
        member: String,
        roleIds: List<String>,
    ) = ControlEdition(
        ControlEntityKind.GRANT,
        ControlFixtures.grantEid(member).hexToByteArray(),
        0,
        null,
        null,
        """{"member":"$member","role_ids":[${roleIds.joinToString(",") { "\"$it\"" }}]}""",
        owner,
        "grant-$member",
        0,
    )

    private fun banlist(vararg banned: String) = ControlEdition(ControlEntityKind.BANLIST, ControlFixtures.banlistEid().hexToByteArray(), 0, null, null, "[${banned.joinToString(",") { "\"$it\"" }}]", owner, "ban", 0)

    /** Admin at position 1 (KICK|BAN|MANAGE_ROLES), Mod at position 5 (KICK only). */
    private fun roster(vararg extra: ControlEdition): AuthorityResolver =
        ControlFixtures.resolve(
            listOf(
                role(adminRole, """{"name":"Admin","position":1,"permissions":"25"}"""),
                role(modRole, """{"name":"Mod","position":5,"permissions":"8"}"""),
                grant(admin, listOf(adminRole)),
                grant(mod, listOf(modRole)),
            ) + extra,
            owner,
        )

    private fun kickBy(
        actor: String,
        target: String,
        authority: AuthorityResolver,
        at: Long = 100L,
        citation: AuthorityCitation? = authority.citationFor(actor),
    ) = Guestbook.parse(Guestbook.kick(actor, target, at, subMs = 0, citation = citation))!!

    private fun join(
        who: String,
        at: Long,
    ) = Guestbook.parse(Guestbook.join(who, at, subMs = 0))!!

    @Test
    fun kickHonoredOnlyFromAKickHolderWhoOutranksTheTarget() {
        val r = roster()
        assertTrue(Guestbook.canKick(r, kickBy(owner, plain, r))) // owner, no citation needed
        assertTrue(Guestbook.canKick(r, kickBy(mod, plain, r))) // KICK at position 5 over a roleless member
        assertTrue(Guestbook.canKick(r, kickBy(admin, mod, r))) // 1 outranks 5
        assertFalse(Guestbook.canKick(r, kickBy(mod, admin, r))) // 5 does not outrank 1
        assertFalse(Guestbook.canKick(r, kickBy(plain, other, r))) // no KICK bit
        assertFalse(Guestbook.canKick(r, kickBy(admin, owner, r))) // the owner is never a target
        assertFalse(Guestbook.canKick(r, kickBy(mod, mod, r))) // equal cannot act on equal
    }

    @Test
    fun kickWithoutASyncedCitationParks() {
        val r = roster()
        // No vac from a non-owner: the sync floor cannot be met.
        assertFalse(Guestbook.canKick(r, kickBy(mod, plain, r, citation = null)))
        // A vac ahead of the Grant we hold (not yet synced) parks too.
        val held = r.citationFor(mod)!!
        val ahead = AuthorityCitation(held.grantId, held.grantVersion + 1, held.grantHash)
        assertFalse(Guestbook.canKick(r, kickBy(mod, plain, r, citation = ahead)))
    }

    @Test
    fun coalesceTakesTheLatestHonoredMotionPerMember() {
        val r = roster()
        val joined = join(plain, 50L)
        val kicked = kickBy(mod, plain, r, at = 100L)
        val folded = Guestbook.coalesce(listOf(kicked, joined), nowMs = 200_000L, authority = r)
        assertEquals(GuestbookAction.KICK, folded[plain]?.action)

        // A kicked member may re-join: a newer Join supersedes the Kick.
        val rejoined = join(plain, 150L)
        assertEquals(GuestbookAction.JOIN, Guestbook.coalesce(listOf(kicked, joined, rejoined), 200_000L, r)[plain]?.action)

        // A Kick from someone who may not kick leaves the member joined.
        val forged = kickBy(plain, other, r, at = 100L)
        val otherJoined = join(other, 50L)
        assertEquals(GuestbookAction.JOIN, Guestbook.coalesce(listOf(otherJoined, forged), 200_000L, r)[other]?.action)

        // Without a roster no Kick is honored.
        assertEquals(GuestbookAction.JOIN, Guestbook.coalesce(listOf(joined, kicked), 200_000L, authority = null)[plain]?.action)
    }

    @Test
    fun coalesceDropsFutureAndBannedEntriesAndTiesToTheLowerRumorId() {
        val now = 1_000_000L
        val future = Guestbook.parse(Guestbook.leave(plain, now / 1000 + 3601, subMs = 0))!!
        val joined = join(plain, 10L)
        assertEquals(GuestbookAction.JOIN, Guestbook.coalesce(listOf(joined, future), now, authority = null)[plain]?.action)

        // A banned member's own motions are dropped.
        val banned = roster(banlist(other))
        assertNull(Guestbook.coalesce(listOf(join(other, 10L)), now, banned)[other])

        val a = Guestbook.parse(Guestbook.join(plain, 10L, subMs = 5))!!
        val b = Guestbook.parse(Guestbook.leave(plain, 10L, subMs = 5))!!
        val lower = if (a.rumorId < b.rumorId) a else b
        assertEquals(lower.rumorId, Guestbook.coalesce(listOf(a, b), now, authority = null)[plain]?.rumorId)
        assertEquals(lower.rumorId, Guestbook.coalesce(listOf(b, a), now, authority = null)[plain]?.rumorId)
    }
}
