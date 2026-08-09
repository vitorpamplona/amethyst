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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **B2 in `docs/concord-soft-ban-audit.md` — regression guard.** What a soft-banned staffer used to
 * be able to do to a community, and can no longer. Every test here failed before the two-pass rule
 * in [AuthorityResolver.resolve] and passes after it.
 *
 * The asymmetry they were written to pin: [ConcordCommunityState.fold] gates METADATA / CHANNEL /
 * INVITE through `authority.hasPermission`, which is `!isBanned && …`, but ROLE, GRANT and BANLIST
 * were gated *inside* [AuthorityResolver.resolve] by `holdsManageRoles` / `bitsOf` /
 * `effectivePermissionsOf` — none of which consulted the banlist, nor could they as written, since
 * the roles/grants fixpoint ran before `banned` was computed at all. Half the Control Plane honored
 * a ban and half was structurally blind to it, so a banned member still holding `control_root` kept
 * full authority over the roster: they banned everyone beneath them, revoked the surviving
 * moderators, retired the roles under them, and minted a fresh un-banned npub that passed every
 * ban-aware gate and finished the job.
 *
 * The fix resolves the ordering by making authority only ever shrink across two passes — see
 * [AuthorityResolver.resolve]. Note that a chain-local rule ("the author must not be banned by the
 * state their edition chains from") would NOT have been enough:
 * [aBannedAdminForksTheBanlistAtGenesisRatherThanChainingOntoTheirOwnBan] forks at genesis so no
 * parent ever mentions the ban, and CORD-04 §4's re-heal union would carry it in anyway. The rule
 * had to bind the union too, which is why it is expressed as a whole-pass mask.
 *
 * Three tests pin behaviour the fix had to *preserve* rather than change:
 * [selfUnbanIsStillRefused], [aJuniorPuppetCannotLiftASeniorsBan], and
 * [aBanByOneAdminDoesNotDropTheGrantsOfAnother]. One pins the cost it deliberately accepts:
 * [banningAnAdminAlsoDemotesEveryoneThatAdminPromoted].
 */
class BannedStaffEscalationTest {
    private val owner = "0f".repeat(32)
    private val alice = "a1".repeat(32) // Admin, position 1 — the member who gets banned
    private val bob = "b2".repeat(32) // Mod, position 5
    private val carol = "c3".repeat(32) // plain member, no role
    private val puppet = "e5".repeat(32) // a fresh npub alice controls

    private val adminRole = "11".repeat(32)
    private val modRole = "22".repeat(32)
    private val puppetRole = "33".repeat(32)

    private val banlistEntity = "44".repeat(32)
    private val channelEntity = "55".repeat(32)
    private val metadataEntity = "66".repeat(32)
    private val bobGrantEntity = "32".repeat(32)
    private val puppetGrantEntity = "35".repeat(32)

    // MANAGE_ROLES|MANAGE_CHANNELS|MANAGE_METADATA|KICK|BAN|CREATE_INVITE = 1+2+4+8+16+64
    private val adminJson = """{"name":"Admin","position":1,"permissions":"95"}"""
    private val modJson = """{"name":"Mod","position":5,"permissions":"24"}""" // KICK|BAN

    private fun edition(
        kind: ControlEntityKind,
        entity: String,
        version: Long,
        prev: ByteArray?,
        content: String,
        author: String,
        rumorId: String,
    ) = ControlEdition(kind, entity.hexToByteArray(), version, prev, null, content, author, rumorId, 0)

    private fun role(
        id: String,
        json: String,
        author: String = owner,
        version: Long = 0,
        prev: ByteArray? = null,
    ) = edition(ControlEntityKind.ROLE, id, version, prev, json, author, "role-$id-$version-$author")

    private fun grant(
        coordinate: String,
        member: String,
        roleIds: List<String>,
        author: String,
        version: Long = 0,
        prev: ByteArray? = null,
    ) = edition(
        ControlEntityKind.GRANT,
        coordinate,
        version,
        prev,
        """{"member":"$member","role_ids":[${roleIds.joinToString(",") { "\"$it\"" }}]}""",
        author,
        "grant-$coordinate-$version-$author",
    )

    private fun banlist(
        author: String,
        version: Long,
        prev: ByteArray?,
        vararg banned: String,
    ) = edition(
        ControlEntityKind.BANLIST,
        banlistEntity,
        version,
        prev,
        "[${banned.joinToString(",") { "\"$it\"" }}]",
        author,
        "ban-$version-$author",
    )

    private fun channel(
        json: String,
        author: String,
        version: Long,
        prev: ByteArray?,
    ) = edition(ControlEntityKind.CHANNEL, channelEntity, version, prev, json, author, "chan-$version-$author")

    private fun metadata(
        json: String,
        author: String,
        version: Long,
        prev: ByteArray?,
    ) = edition(ControlEntityKind.METADATA, metadataEntity, version, prev, json, author, "meta-$version-$author")

    private val channelV0 = channel("""{"name":"general"}""", owner, 0, null)
    private val metadataV0 = metadata("""{"name":"My Community"}""", owner, 0, null)
    private val bobGrantV0 = grant(bobGrantEntity, bob, listOf(modRole), owner)
    private val modRoleV0 = role(modRole, modJson)

    /** The owner-authored community every test starts from: two roles, two grants, a channel, metadata. */
    private fun community() =
        mutableListOf(
            role(adminRole, adminJson),
            modRoleV0,
            grant("31".repeat(32), alice, listOf(adminRole), owner),
            bobGrantV0,
            channelV0,
            metadataV0,
        )

    /** The owner bans alice. Genesis of the banlist, so every test can fork or chain off it. */
    private val ownerBansAlice = banlist(owner, 0, null, alice)

    /** Alice, already banned, mints a role just below herself and hands it to a fresh npub. */
    private fun aliceMintsAPuppet() =
        listOf(
            role(puppetRole, """{"name":"Puppet","position":2,"permissions":"95"}""", author = alice),
            grant(puppetGrantEntity, puppet, listOf(puppetRole), author = alice),
        )

    @Test
    fun aBanStripsTheAuthorityCheckedByFoldButNotTheOneCheckedByTheResolver() {
        val r = AuthorityResolver.resolve(community() + ownerBansAlice, owner)

        assertTrue(r.isBanned(alice), "the owner's ban lands")
        assertFalse(r.hasPermission(alice, ConcordPermissions.MANAGE_ROLES), "the ban-aware check refuses her")
        // effectivePermissions still reports what her ROLES say — that is its job, and the members
        // screen reads it to label her. What changed is that the resolver's own ROLE/GRANT/BANLIST
        // gates no longer consult it for a banned author; they drop the edition outright.
        assertTrue(
            r.effectivePermissions(alice).has(ConcordPermissions.MANAGE_ROLES),
            "the role-derived view is unchanged — only what it authorizes is",
        )
    }

    @Test
    fun aBannedAdminPromotesAFreshSockpuppetToAdmin() {
        val r = AuthorityResolver.resolve(community() + ownerBansAlice + aliceMintsAPuppet(), owner)

        assertEquals(null, r.rank(puppet), "the banned admin's role and grant editions are both dropped")
        assertFalse(r.isBanned(puppet), "the puppet itself is a clean npub — it is never banned, just powerless")
        assertFalse(
            r.hasPermission(puppet, ConcordPermissions.MANAGE_CHANNELS),
            "a banned member cannot mint authority it no longer has to give",
        )
    }

    @Test
    fun theSockpuppetDeletesEveryChannelAndRewritesTheMetadata() {
        val editions =
            community() + ownerBansAlice + aliceMintsAPuppet() +
                // A channel tombstone is terminal — CORD-03: the id is never reused.
                channel("""{"name":"general","deleted":true}""", puppet, 1, channelV0.hash) +
                metadata("""{"name":"Owned by the guy you banned"}""", puppet, 1, metadataV0.hash)

        val state = ConcordCommunityState.fold(editions, owner)

        assertEquals(1, state.channels.size, "the puppet holds nothing, so its tombstone is inert")
        assertEquals("My Community", state.metadata?.name, "and the community keeps its identity")
    }

    @Test
    fun theSockpuppetBansEveryMemberBeneathIt() {
        val editions = community() + ownerBansAlice + aliceMintsAPuppet() + banlist(puppet, 1, ownerBansAlice.hash, alice, bob, carol)

        val r = AuthorityResolver.resolve(editions, owner)

        assertFalse(r.isBanned(bob), "the puppet's banlist edition is unauthorized, so the moderator stands")
        assertFalse(r.isBanned(carol), "and so do the plain members")
    }

    @Test
    fun aBannedAdminBansEveryoneBeneathThemWithoutNeedingAPuppetAtAll() {
        val editions = community() + ownerBansAlice + banlist(alice, 1, ownerBansAlice.hash, alice, bob, carol)

        val r = AuthorityResolver.resolve(editions, owner)

        assertTrue(r.isBanned(alice), "her own ban stands — it was the owner's")
        assertFalse(r.isBanned(bob), "banGate now drops a banned author's edition outright")
        assertFalse(r.isBanned(carol), "same")
    }

    @Test
    fun aBannedAdminForksTheBanlistAtGenesisRatherThanChainingOntoTheirOwnBan() {
        // The same attack as above, except her edition does NOT chain onto the edition that banned
        // her — it forks at genesis. So a rule that only asks "was the author banned by this
        // edition's parent?" never sees her ban, and CORD-04 §4's re-heal union carries her bans in
        // regardless. Any fix has to bind the union, not just the chain.
        val editions = community() + ownerBansAlice + banlist(alice, 0, null, bob, carol)

        val r = AuthorityResolver.resolve(editions, owner)

        assertTrue(r.isBanned(alice), "the owner's ban survives the fork — the union is down-only")
        assertFalse(r.isBanned(bob), "the fix binds the UNION too: her fork is dropped before it can be healed in")
        assertFalse(r.isBanned(carol), "same")
    }

    @Test
    fun aBannedAdminRevokesTheSurvivingModerators() {
        val editions = community() + ownerBansAlice + grant(bobGrantEntity, bob, emptyList(), author = alice, version = 1, prev = bobGrantV0.hash)

        val r = AuthorityResolver.resolve(editions, owner)

        assertEquals(5, r.rank(bob), "a banned admin's revoke is dropped, so the moderator keeps their role")
        assertTrue(r.hasPermission(bob, ConcordPermissions.BAN), "and keeps the authority that comes with it")
    }

    @Test
    fun aBannedAdminDeletesEveryRoleBeneathThem() {
        val tombstone = role(modRole, """{"name":"Mod","position":5,"permissions":"24","deleted":true}""", author = alice, version = 1, prev = modRoleV0.hash)

        val r = AuthorityResolver.resolve(community() + ownerBansAlice + tombstone, owner)

        assertEquals(5, r.roles()[modRole]?.position, "a banned admin's tombstone is dropped, so the role survives")
        assertEquals(5, r.rank(bob), "and its holders keep their standing")
    }

    @Test
    fun selfUnbanIsStillRefused() {
        // docs/concord-banlist-rank-conformance.md §4 row 3, the half that IS closed: the delta rule
        // gates removals too, and strict outranking means nobody outranks themselves.
        val editions = community() + ownerBansAlice + banlist(alice, 1, ownerBansAlice.hash)

        assertTrue(AuthorityResolver.resolve(editions, owner).isBanned(alice), "a banned member may not lift their own ban")
    }

    @Test
    fun aJuniorPuppetCannotLiftASeniorsBan() {
        // The puppet sits at position 2 and alice at 1, and no edition may claim a position at or
        // above its own signer — so her delegation chain can only ever descend. Nothing she mints
        // can outrank her, and so nothing she mints can unban her.
        val editions = community() + ownerBansAlice + aliceMintsAPuppet() + banlist(puppet, 1, ownerBansAlice.hash)

        assertTrue(AuthorityResolver.resolve(editions, owner).isBanned(alice), "the puppet does not outrank its creator")
    }

    @Test
    fun aForkedBanlistThatOmitsHimCannotLaunderTheBanAway() {
        // A malicious client is not limited to what the ban/unban verb will author. The sharpest
        // hand-crafted route does not try to REMOVE his ban — removal is what the strict-outrank-self
        // rule guards — it forks at genesis and simply never mentions him, at a version high enough
        // to win the head fold. The head's own effective list then never carried his ban, so there is
        // nothing to remove and the rank rule never fires.
        //
        // §4's re-heal is what closes it: the owner's edition is authorized and is NOT on the forked
        // head's back-chain, so it is unioned back in as a concurrent ban.
        val editions = community() + ownerBansAlice + banlist(alice, 99, null, carol)

        assertTrue(AuthorityResolver.resolve(editions, owner).isBanned(alice), "the re-heal union must put the owner's ban back")
    }

    @Test
    fun aPrivateBanlistChainOfHisOwnCannotLaunderTheBanAway() {
        // The same idea two editions deep, so the winning head has a clean ancestry entirely of his
        // own making. Ancestry is walked over the full pool, so the owner's ban is still recognised
        // as a concurrent fork rather than a superseded ancestor.
        val mine = banlist(alice, 50, null)
        val editions = community() + ownerBansAlice + mine + banlist(alice, 51, mine.hash)

        assertTrue(AuthorityResolver.resolve(editions, owner).isBanned(alice), "a self-authored chain must not launder the ban away")
    }

    @Test
    fun aRogueRotatorCompactsTheBanAwayForEveryClientWithoutAFloor() {
        // The route that does work, and the one no signature check can catch. A CORD-06 §3 compaction
        // re-wraps ONE edition per entity and the ROTATOR picks it, so a rotator can simply not carry
        // the banlist forward. Every edition it serves is genuine; the ban is erased by omission.
        //
        // A banned member cannot rotate (drainConcordRekeys gates the rotator on hasPermission, which
        // is ban-aware) — but the puppet minted above is not banned, and it can. EntityFloor is the
        // whole defense, so this splits the community in two: clients that already folded the ban
        // refuse the rollback, while fresh joiners have no floor to refuse with and see no ban at all.
        val editions = community() + ownerBansAlice
        val floors = ConcordCommunityState.authorizedHeads(editions, owner)
        val compacted = editions.filter { it.entityKind != ControlEntityKind.BANLIST }

        assertFalse(
            ConcordCommunityState.fold(compacted, owner).authority.isBanned(alice),
            "ESCALATION: a fresh joiner holds no floor, so the omitted ban simply never existed",
        )
        assertTrue(
            ConcordCommunityState.fold(compacted, owner, floors).authority.isBanned(alice),
            "a client that already folded the ban must refuse the rollback",
        )
    }

    @Test
    fun banningAnAdminAlsoDemotesEveryoneThatAdminPromoted() {
        // The deliberate cascade, pinned because it is surprising and because it is the whole point.
        // CORD-04 §4 drops every event from a banned npub, authority actions included, so a grant
        // they made while in good standing goes too. That is what kills a sockpuppet minted moments
        // before the ban — and the same rule costs the owner a legitimate promotion, which they have
        // to re-issue. See B2 in docs/concord-soft-ban-audit.md.
        val promoted = grant("36".repeat(32), carol, listOf(modRole), author = alice)

        val before = AuthorityResolver.resolve(community() + promoted, owner)
        assertEquals(5, before.rank(carol), "while alice is in good standing, her grant stands")

        val after = AuthorityResolver.resolve(community() + promoted + ownerBansAlice, owner)
        assertEquals(null, after.rank(carol), "banning alice retroactively drops the grant she authored")
    }

    @Test
    fun aBanByOneAdminDoesNotDropTheGrantsOfAnother() {
        // The cascade must follow the banned author, not spread. Bob is untouched by alice's ban, so
        // everything he authored keeps standing.
        val carolByBob = grant("37".repeat(32), carol, listOf(modRole), author = bob)
        // bob is a Mod at position 5 and the role he hands out is that same position, so the grant is
        // only honored when authored by someone who outranks it — the owner does, bob does not.
        val carolByOwner = grant("38".repeat(32), carol, listOf(modRole), author = owner)

        val r = AuthorityResolver.resolve(community() + ownerBansAlice + carolByBob + carolByOwner, owner)

        assertTrue(r.isBanned(alice), "alice is the only one banned")
        assertEquals(5, r.rank(bob), "bob is untouched")
        assertEquals(5, r.rank(carol), "and the owner's grant of carol stands")
    }
}
