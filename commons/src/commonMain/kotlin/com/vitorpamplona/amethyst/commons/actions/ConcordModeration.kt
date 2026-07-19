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

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEditionBuilder
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.EditionFold
import com.vitorpamplona.quartz.concord.cord04Roles.GrantEntity
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
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
     * The current head of ([kind], [entityId]) within [current], or null if the entity
     * has no editions yet.
     *
     * [current] arrives in **wrap-arrival order**, which is not chain order — so the
     * first matching edition is whichever one a relay happened to deliver first, not
     * the newest. Chaining off that stale edition would fork the chain at an
     * already-used version, and [EditionFold] would then break the tie by
     * `minByOrNull { rumorId }` — a coin flip that can silently drop the new edition
     * (an unban or a role revocation quietly failing to apply). Fold the entity's
     * chain instead, exactly as every reader does.
     */
    private fun headOf(
        current: List<ControlEdition>,
        kind: ControlEntityKind,
        entityId: ByteArray,
    ): ControlEdition? = EditionFold.foldEntity(current.filter { it.entityKind == kind && it.entityId.contentEquals(entityId) })

    /** version/prevHash to chain onto the current head of ([kind], [entityId]), or genesis. */
    private fun versioning(
        current: List<ControlEdition>,
        kind: ControlEntityKind,
        entityId: ByteArray,
    ): Pair<Long, ByteArray?> {
        val head = headOf(current, kind, entityId)
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
    ): Event {
        val (version, prev) = versioning(current, ControlEntityKind.ROLE, roleId)
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
    ): Event {
        val (version, prev) = versioning(current, ControlEntityKind.CHANNEL, channelId)
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
    ): Event {
        val (version, prev) = versioning(current, ControlEntityKind.METADATA, communityId)
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
    ): Event {
        val entityId = ConcordKeyDerivation.grantCoordinate(communityId, member.hexToByteArray())
        val (version, prev) = versioning(current, ControlEntityKind.GRANT, entityId)
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
    ): Event = setBanlist(actor, controlPlane, communityId, currentBanned(current, communityId) + member.lowercase(), current, createdAt, citation)

    /** Removes [member] from the banlist. */
    suspend fun unban(
        actor: NostrSigner,
        controlPlane: GroupKey,
        communityId: ByteArray,
        member: HexKey,
        current: List<ControlEdition>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
    ): Event = setBanlist(actor, controlPlane, communityId, currentBanned(current, communityId) - member.lowercase(), current, createdAt, citation)

    /** The current banlist union across the head editions (lowercase hex). */
    fun currentBanned(
        current: List<ControlEdition>,
        communityId: ByteArray,
    ): Set<HexKey> {
        val entityId = ConcordKeyDerivation.banlistCoordinate(communityId)
        val head = headOf(current, ControlEntityKind.BANLIST, entityId)
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
    ): Event {
        val entityId = ConcordKeyDerivation.banlistCoordinate(communityId)
        val (version, prev) = versioning(current, ControlEntityKind.BANLIST, entityId)
        val content = ConcordJson.instance.encodeToString(ListSerializer(String.serializer()), banned.sorted())
        return wrap(actor, controlPlane, ControlEntityKind.BANLIST, entityId, version, prev, content, createdAt, citation)
    }
}
