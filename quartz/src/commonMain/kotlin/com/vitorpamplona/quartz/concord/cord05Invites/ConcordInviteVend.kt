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

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityList.withPrivateChannels
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
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
        val heldEpochs = held.privateChannels.filter { it.key.isNotBlank() }.associate { it.channelId.lowercase() to it.epoch }
        return bundle.channels
            .filter { HEX64.matches(it.id) && HEX64.matches(it.key) }
            .filter { c ->
                val heldEpoch = heldEpochs[c.id.lowercase()]
                heldEpoch == null || c.epoch > heldEpoch
            }.map { it.id.lowercase() }
            .distinct()
    }

    /**
     * [held] with the Private Channel keys [bundle] newly contributes ([catchUpChannelIds]) merged
     * in — a newer epoch replaces the held one — or null when the bundle contributes nothing. The
     * base, epoch, control keys and every other field stay exactly as held.
     */
    fun adoptCatchUp(
        held: ConcordCommunityListEntry,
        bundle: CommunityInvite,
    ): ConcordCommunityListEntry? {
        val newIds = catchUpChannelIds(held, bundle).toSet()
        if (newIds.isEmpty()) return null
        val delivered =
            bundle.channels
                .filter { it.id.lowercase() in newIds && HEX64.matches(it.key) }
                .groupBy { it.id.lowercase() }
                .map { (id, grants) -> grants.maxBy { it.epoch }.let { PrivateChannelKey(id, it.key.lowercase(), it.epoch, it.name) } }
        val kept = held.privateChannels.filterNot { it.channelId.lowercase() in newIds }
        return held.withPrivateChannels(kept + delivered)
    }

    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

    private fun sameOptionalHex(
        a: String?,
        b: String?,
    ): Boolean = a?.lowercase() == b?.lowercase()
}
