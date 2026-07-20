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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEditionBuilder
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.GrantEntity
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Builds the Control Plane editions (CORD-04) that drive roles and moderation:
 * defining a role, granting roles to a member, and banning/unbanning members.
 *
 * Each is a kind-3308 edition, plaintext-sealed (so the author signature survives
 * re-encryption across epochs) and wrapped on the community's Control Plane. The
 * caller passes the community's **current** editions so this can chain the next
 * version onto the entity's head (`version = head.version + 1`, `prevHash =
 * head.hash`) and union the banlist. Authority is enforced at *fold* time by the
 * `AuthorityResolver`, not here — an edition whose author doesn't outrank its
 * target (or trace to the owner via [citation]) is simply dropped by every client.
 *
 * The owner needs no [citation]; a delegated moderator must cite the grant they
 * act under so the fold can verify the chain terminates at the owner.
 */
object ConcordModeration {
    /**
     * The current head of [entityId] within [current], or null if the entity has no
     * editions yet — **the authority-gated head**, i.e. the same edition a reader would
     * fold to, resolved against the community's [owner].
     *
     * Two traps live here, and both need the fold:
     *
     * 1. [current] arrives in **wrap-arrival order**, which is not chain order — so the
     *    first matching edition is whichever one a relay happened to deliver first, not
     *    the newest. Chaining off that stale edition forks the chain at an already-used
     *    version, silently dropping the change.
     * 2. The *ungated* structural tip may be an edition every reader **rejects**. Building
     *    on it does two kinds of damage: the rogue's version number is inflated into every
     *    honest edition that follows, and — for a replaced document like the banlist —
     *    the rogue's *content* is read as the current state and re-published under an
     *    authorized signature. That launders the attack: an unauthorized empty banlist
     *    becomes an owner-signed one the moment the owner bans anybody else.
     *
     * Armada's writers chain off `folded.heads` (its `pickHead` gated pick) for exactly
     * this reason; [ConcordCommunityState.authorizedHeads] is the same notion here.
     */
    private fun headOf(
        current: List<ControlEdition>,
        entityId: ByteArray,
        owner: HexKey,
    ): ControlEdition? = ConcordCommunityState.authorizedHeads(current, owner)[entityId.toHexKey()]?.known

    /** version/prevHash to chain onto the current head of [entityId], or genesis. */
    private fun versioning(
        current: List<ControlEdition>,
        entityId: ByteArray,
        owner: HexKey,
    ): Pair<Long, ByteArray?> {
        val head = headOf(current, entityId, owner)
        return if (head != null) (head.version + 1) to head.hash else 0L to null
    }

    private suspend fun wrap(
        actor: NostrSigner,
        controlPlane: GroupKey,
        kind: ControlEntityKind,
        entityId: ByteArray,
        version: Long,
        prevHash: ByteArray?,
        content: String,
        createdAt: Long,
        citation: AuthorityCitation?,
    ): Event {
        val rumor = ControlEditionBuilder.rumor(actor.pubKey, kind, entityId, version, prevHash, content, createdAt, citation)
        return ConcordStreamEnvelope.wrap(rumor, controlPlane, actor, encrypted = false, createdAt = createdAt)
    }

    /**
     * Defines (or updates) a role. [roleId] is the role's stable 32-byte entity id
     * — generate one for a new role and reuse it to edit or [RoleEntity.deleted] it.
     */
    suspend fun defineRole(
        actor: NostrSigner,
        controlPlane: GroupKey,
        roleId: ByteArray,
        role: RoleEntity,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event {
        val (version, prev) = versioning(current, roleId, owner)
        val content = ConcordJson.instance.encodeToString(RoleEntity.serializer(), role)
        return wrap(actor, controlPlane, ControlEntityKind.ROLE, roleId, version, prev, content, createdAt, citation)
    }

    /**
     * Defines (or updates) a channel (CORD-03/04, `vsk=2`). [channelId] is the channel's stable
     * 32-byte entity id — generate one for a new channel and reuse it to rename, flip its
     * private/voice flags, or [ChannelEntity.deleted] it (terminal; the id is never reused).
     * Honored at fold only when [actor] holds MANAGE_CHANNELS (or is the owner) tracing to the owner
     * via [citation].
     */
    suspend fun defineChannel(
        actor: NostrSigner,
        controlPlane: GroupKey,
        channelId: ByteArray,
        channel: ChannelEntity,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event {
        val (version, prev) = versioning(current, channelId, owner)
        val content = ConcordJson.instance.encodeToString(ChannelEntity.serializer(), channel)
        return wrap(actor, controlPlane, ControlEntityKind.CHANNEL, channelId, version, prev, content, createdAt, citation)
    }

    /**
     * Replaces the community metadata (name / icon / description / relays). The
     * metadata entity id is the community id itself (as in genesis), so this chains
     * the next version onto the metadata head. Honored at fold only when [actor]
     * holds MANAGE_METADATA (or is the owner) tracing to the owner via [citation].
     */
    suspend fun editMetadata(
        actor: NostrSigner,
        controlPlane: GroupKey,
        communityId: ByteArray,
        metadata: MetadataEntity,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event {
        val (version, prev) = versioning(current, communityId, owner)
        val content = ConcordJson.instance.encodeToString(MetadataEntity.serializer(), metadata)
        return wrap(actor, controlPlane, ControlEntityKind.METADATA, communityId, version, prev, content, createdAt, citation)
    }

    /** Grants [member] exactly [roleIds] (replaces their prior grant). Empty list revokes all roles. */
    suspend fun grant(
        actor: NostrSigner,
        controlPlane: GroupKey,
        communityId: ByteArray,
        member: HexKey,
        roleIds: List<String>,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event {
        val entityId = ConcordKeyDerivation.grantCoordinate(communityId, member.hexToByteArray())
        val (version, prev) = versioning(current, entityId, owner)
        val content = ConcordJson.instance.encodeToString(GrantEntity.serializer(), GrantEntity(member = member, roleIds = roleIds))
        return wrap(actor, controlPlane, ControlEntityKind.GRANT, entityId, version, prev, content, createdAt, citation)
    }

    /** Adds [member] to the banlist (union with the current head). */
    suspend fun ban(
        actor: NostrSigner,
        controlPlane: GroupKey,
        communityId: ByteArray,
        member: HexKey,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event = setBanlist(actor, controlPlane, communityId, currentBanned(current, communityId, owner) + member.lowercase(), current, createdAt, citation, owner)

    /** Removes [member] from the banlist. */
    suspend fun unban(
        actor: NostrSigner,
        controlPlane: GroupKey,
        communityId: ByteArray,
        member: HexKey,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
        owner: HexKey,
    ): Event = setBanlist(actor, controlPlane, communityId, currentBanned(current, communityId, owner) - member.lowercase(), current, createdAt, citation, owner)

    /** The current banlist union across the head editions (lowercase hex). */
    fun currentBanned(
        current: List<ControlEdition>,
        communityId: ByteArray,
        owner: HexKey,
    ): Set<HexKey> {
        val entityId = ConcordKeyDerivation.banlistCoordinate(communityId)
        val head = headOf(current, entityId, owner)
        return head?.let { ConcordJson.decodeBanlist(it.content) }?.mapTo(HashSet()) { it.lowercase() } ?: emptySet()
    }

    private suspend fun setBanlist(
        actor: NostrSigner,
        controlPlane: GroupKey,
        communityId: ByteArray,
        banned: Set<HexKey>,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation?,
        owner: HexKey,
    ): Event {
        val entityId = ConcordKeyDerivation.banlistCoordinate(communityId)
        val (version, prev) = versioning(current, entityId, owner)
        val content = ConcordJson.instance.encodeToString(ListSerializer(String.serializer()), banned.sorted())
        return wrap(actor, controlPlane, ControlEntityKind.BANLIST, entityId, version, prev, content, createdAt, citation)
    }
}
