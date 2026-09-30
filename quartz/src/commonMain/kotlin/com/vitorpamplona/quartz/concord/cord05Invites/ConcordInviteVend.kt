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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Which Private Channel keys an invite bundle may carry (CORD-05 §1, CORD-03 §1, CORD-04 §2), and
 * what a bundle for an already-joined community may contribute. Pinned to Armada's
 * `channelAccess.ts` (`isEntitled`, `vendableChannels`) and `directInvite.ts` (`catchUpChannelIds`).
 *
 * The Roles scoped to a channel (`scope: {kind:"channel", channel_id}`) ARE its access list. Read
 * access is enforced by key possession alone; this decides who a key is delivered TO.
 */
object ConcordInviteVend {
    private const val SCOPE_CHANNEL = "channel"

    /** The live Role ids conferring read access to [channelIdHex] (Roles scoped to that channel). */
    fun channelRoleIds(
        authority: AuthorityResolver,
        channelIdHex: HexKey,
    ): Set<String> =
        authority
            .roles()
            .filter { (_, role) -> !role.deleted && role.scope?.kind == SCOPE_CHANNEL && role.scope.channelId.equals(channelIdHex, ignoreCase = true) }
            .keys

    /**
     * Is [memberHex] entitled to Private Channel [channelIdHex]'s key? The owner always is
     * (CORD-04 §2); anyone else must hold a Role scoped to that channel.
     */
    fun isEntitled(
        authority: AuthorityResolver,
        memberHex: HexKey,
        channelIdHex: HexKey,
    ): Boolean {
        if (authority.isOwner(memberHex)) return true
        val held = authority.rolesOf(memberHex)
        if (held.isEmpty()) return false
        return channelRoleIds(authority, channelIdHex).any { it in held }
    }

    /**
     * The held Private Channel keys a bundle may carry for its audience (CORD-05 §1):
     *  - a **link** ([memberHex] null) has no recipient and holds no Role, so it gets none;
     *  - a **member** (a Direct Invite's recipient) gets exactly the channels their Roles entitle
     *    them to ([isEntitled]) — that CORD-05 §6 can't *prevent* an unentitled whisper doesn't make
     *    one right.
     *
     * Keyless listings are never vended.
     */
    fun vendableChannels(
        held: List<PrivateChannelKey>,
        authority: AuthorityResolver,
        memberHex: HexKey?,
    ): List<PrivateChannelKey> {
        if (memberHex == null) return emptyList()
        return held.filter { it.key.isNotBlank() && isEntitled(authority, memberHex, it.channelId) }
    }

    /**
     * Everyone entitled to [channelIdHex]'s key under [authority]: the owner plus every non-banned
     * holder of a Role scoped to the channel. Entitlement needs a Grant, so the roster enumerates it
     * exactly — no member census required. This is the keep-set of a channel rotation (CORD-06).
     */
    fun entitledMembers(
        authority: AuthorityResolver,
        channelIdHex: HexKey,
    ): Set<HexKey> {
        val scoped = channelRoleIds(authority, channelIdHex)
        val out = HashSet<HexKey>()
        out.add(authority.owner())
        for (member in authority.roleHolders()) {
            if (authority.isBanned(member)) continue
            if (authority.rolesOf(member).any { it in scoped }) out.add(member.lowercase())
        }
        return out
    }

    /** Who gained and who lost a Private Channel's entitlement between two folds. */
    class AccessChange(
        val channelIdHex: HexKey,
        val gained: Set<HexKey>,
        val lost: Set<HexKey>,
    )

    /**
     * How a roster change — a Grant, a revoke, a Role's scope edit or deletion, a ban — moved
     * entitlement to each of [channelIds] (Armada `channelsHingingOn`, generalised to any edit):
     * the members to vend each channel's key to, and the ones a rotation must now cut. Channels
     * nobody gained or lost are omitted.
     */
    fun accessChanges(
        before: AuthorityResolver,
        after: AuthorityResolver,
        channelIds: Collection<HexKey>,
    ): List<AccessChange> =
        channelIds.mapNotNull { id ->
            val was = entitledMembers(before, id)
            val now = entitledMembers(after, id)
            val gained = now - was
            val lost = was - now
            if (gained.isEmpty() && lost.isEmpty()) null else AccessChange(id.lowercase(), gained, lost)
        }

    /** The [held] keys as bundle channel grants (lowercase hex, as Armada writes them). */
    fun toInviteChannels(held: List<PrivateChannelKey>): List<InviteChannel> = held.map { InviteChannel(it.channelId.lowercase(), it.key.lowercase(), it.epoch, it.name) }

    /**
     * The Private Channel ids (lowercase hex) a [bundle] for an already-joined community would NEWLY
     * contribute to [held] — empty when it is not a catch-up. Armada `catchUpChannelIds`.
     *
     * A catch-up may never move the base: nothing binds `community_root` to `community_id`
     * (CORD-02 §1/§2), so a hostile bundle carrying a real id/owner/salt could otherwise relocate
     * the member onto attacker-read streams. So it counts only on the SAME `community_root`,
     * `root_epoch` and `control_pk` (swapping `control_pk` alone would eclipse the member onto an
     * attacker's Control Plane); the base advances only by a CORD-06 rekey.
     *
     * And it only ever ADDS a channel this member holds no key for. A held key moves forward only
     * through a channel rekey (CORD-06 §2), whose `prevcommit` proves it extends the very key held;
     * a bare bundle proves nothing, so letting one replace a held key would let any keyholder
     * park a member on a dead key at an absurd epoch that every later honest delivery then loses
     * to. A key below a recorded cut (the rotation that removed us) never comes back either.
     */
    fun catchUpChannelIds(
        held: ConcordCommunityListEntry?,
        bundle: CommunityInvite,
    ): List<HexKey> {
        if (held == null) return emptyList()
        if (!bundle.communityId.equals(held.id, ignoreCase = true)) return emptyList()
        if (!bundle.communityRoot.equals(held.root, ignoreCase = true)) return emptyList()
        if (bundle.rootEpoch != held.rootEpoch) return emptyList()
        if (!sameOptionalHex(bundle.controlPk, held.controlPk)) return emptyList()
        val heldIds = held.privateChannels.filter { HEX64.matches(it.key) }.mapTo(HashSet()) { it.channelId.lowercase() }
        return bundle.channels
            .filter { HEX64.matches(it.id) && HEX64.matches(it.key) }
            .filter { it.id.lowercase() !in heldIds }
            .filterNot { ConcordChannelKeyring.isCutOff(held, it.id, it.epoch) }
            .map { it.id.lowercase() }
            .distinct()
    }

    /**
     * The subset of [catchUpChannelIds] a catch-up may actually deliver against the held fold: only
     * from a [sender] who is staff there (the owner or a Control-writing permission holder,
     * CORD-04 §3 — a plain keyholder could otherwise plant a wrong key that blocks the right one),
     * and only for channels the fold knows as live Private Channels ([privateChannelIds]). Empty
     * when either fails.
     */
    fun admissibleCatchUpIds(
        held: ConcordCommunityListEntry?,
        bundle: CommunityInvite,
        authority: AuthorityResolver,
        privateChannelIds: Set<HexKey>,
        sender: HexKey,
    ): List<HexKey> {
        if (!authority.isStaff(sender)) return emptyList()
        val live = privateChannelIds.mapTo(HashSet()) { it.lowercase() }
        return catchUpChannelIds(held, bundle).filter { it in live }
    }

    /**
     * [held] with the Private Channel keys [bundle] newly contributes ([catchUpChannelIds], narrowed
     * to [only] when given) added, or null when the bundle contributes nothing. A held key is never
     * replaced; the base, epoch, control keys and every other field stay exactly as held.
     */
    fun adoptCatchUp(
        held: ConcordCommunityListEntry,
        bundle: CommunityInvite,
        only: Collection<HexKey>? = null,
    ): ConcordCommunityListEntry? {
        val newIds = catchUpChannelIds(held, bundle).filter { only == null || it in only }.toSet()
        if (newIds.isEmpty()) return null
        val delivered =
            bundle.channels
                .filter { it.id.lowercase() in newIds && HEX64.matches(it.key) }
                .groupBy { it.id.lowercase() }
                .map { (id, grants) -> grants.maxBy { it.epoch }.let { PrivateChannelKey(id, it.key.lowercase(), it.epoch, it.name) } }
        // Through the keyring: a replaced key keeps the unknown fields another client wrote in it.
        var next = held
        for (key in delivered) next = ConcordChannelKeyring.withChannelKey(next, key) ?: next
        return if (next === held) null else next
    }

    /** Why a catch-up Direct Invite may or may not be adopted without a click ([judgeCatchUp]). */
    enum class CatchUpVerdict {
        /** The Grant was the consent: adopt now. */
        ADOPT,

        /** No folded roster yet (the Grant's fold lags): wait. */
        NO_FOLD,

        /** Not a catch-up at all ([catchUpChannelIds] is empty). */
        NOTHING_NEW,

        /** The recipient is banned (CORD-04 §4). */
        BANNED,

        /** A plain keyholder sent it; only staff may plant a key automatically. */
        SENDER_NOT_STAFF,

        /** It carries a channel the recipient's Roles don't entitle them to. */
        NOT_ENTITLED,
    }

    /**
     * Whether a parked catch-up invite — a Direct Invite carrying a Private Channel key an existing
     * member lacks, which is how a role grant's key arrives (CORD-05 §6) — may be adopted WITHOUT a
     * click (Armada `judgeCatchUp`). Consent came from the Grant; this checks the bundle is the
     * delivery it prescribes, against the folded [authority]:
     *  - the [sender] is the owner or staff (CORD-04 §3), so a plain keyholder can't plant a wrong
     *    key that would block the right one;
     *  - the [recipient] isn't banned;
     *  - EVERY newly contributed channel is one the recipient's Roles entitle them to ([isEntitled]).
     *
     *  - every such channel is a live Private Channel in the fold ([privateChannelIds]).
     *
     * A manual Accept still needs a staff sender and a live Private Channel
     * ([admissibleCatchUpIds]); only the entitlement check is waived by the click.
     */
    fun judgeCatchUp(
        authority: AuthorityResolver?,
        privateChannelIds: Set<HexKey>,
        recipient: HexKey,
        sender: HexKey,
        bundle: CommunityInvite,
        held: ConcordCommunityListEntry?,
    ): CatchUpVerdict {
        val vended = catchUpChannelIds(held, bundle)
        if (vended.isEmpty()) return CatchUpVerdict.NOTHING_NEW
        if (authority == null) return CatchUpVerdict.NO_FOLD
        if (authority.isBanned(recipient)) return CatchUpVerdict.BANNED
        if (!authority.isStaff(sender)) return CatchUpVerdict.SENDER_NOT_STAFF
        val live = privateChannelIds.mapTo(HashSet()) { it.lowercase() }
        if (vended.any { it !in live || !isEntitled(authority, recipient, it) }) return CatchUpVerdict.NOT_ENTITLED
        return CatchUpVerdict.ADOPT
    }

    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

    private fun sameOptionalHex(
        a: String?,
        b: String?,
    ): Boolean = a?.lowercase() == b?.lowercase()
}
