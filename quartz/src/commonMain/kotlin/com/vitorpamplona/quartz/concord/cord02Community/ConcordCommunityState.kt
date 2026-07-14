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

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.EditionFold
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity

/** A channel id paired with its current folded definition. */
class ConcordChannel(
    val channelIdHex: String,
    val definition: ChannelEntity,
)

/**
 * The current, folded state of a Concord community's Control Plane (CORD-02).
 *
 * Produced by [fold] from the set of decrypted, verified control editions plus
 * the community's known [ownerPubKey]. It exposes the community [metadata], the
 * live (non-deleted) [channels], the live [roles], the owner-rooted [authority]
 * resolver, and whether the community has been [dissolved].
 *
 * "Every member keeps the entire Control Plane in sync — it is small and must
 * stay complete." Recompute this whenever the known editions change.
 */
class ConcordCommunityState(
    val ownerPubKey: String,
    val metadata: MetadataEntity?,
    val channels: Map<String, ConcordChannel>,
    val roles: Map<String, RoleEntity>,
    val authority: AuthorityResolver,
    val dissolved: Boolean,
) {
    companion object {
        fun fold(
            editions: Collection<ControlEdition>,
            ownerPubKey: String,
        ): ConcordCommunityState {
            val heads = EditionFold.fold(editions).values
            // Resolve authority from the FULL edition set (not the structural heads): the resolver
            // folds each role/grant chain through authorized editions only, so a rogue higher-version
            // edition can't supersede a legit one before authority is even judged.
            val authority = AuthorityResolver.resolve(editions, ownerPubKey)

            // CORD-04 §1: "an edition whose signer isn't authorized is dropped." Authority is
            // owner-rooted (the AuthorityResolver resolves it from the owner outward via the grant
            // fixpoint), so gating each managed entity by its required permission BEFORE the
            // structural fold filters out spoofed editions — e.g. a decoy metadata genesis minted by
            // an unprivileged key — instead of letting a higher-version forgery win the chain. The
            // permission check also excludes banned authors (hasPermission is false for a banned npub).
            fun editorsWith(
                kind: ControlEntityKind,
                bit: Int,
            ): List<ControlEdition> =
                editions.filter {
                    it.entityKind == kind && (authority.isOwner(it.author) || authority.hasPermission(it.author, bit))
                }

            // Metadata is one entity (== community id), gated by MANAGE_METADATA. Fold only the
            // authorized editions, then take the highest-version head (guarding against strays).
            val metadata =
                EditionFold
                    .fold(editorsWith(ControlEntityKind.METADATA, ConcordPermissions.MANAGE_METADATA))
                    .values
                    .maxByOrNull { it.version }
                    ?.let { ConcordJson.decodeOrNull<MetadataEntity>(it.content) }

            // Channels are gated by MANAGE_CHANNELS. Fold each channel entity from its authorized
            // editions only, dropping the tombstoned ones.
            val channels = LinkedHashMap<String, ConcordChannel>()
            for (head in EditionFold.fold(editorsWith(ControlEntityKind.CHANNEL, ConcordPermissions.MANAGE_CHANNELS)).values) {
                val def = ConcordJson.decodeOrNull<ChannelEntity>(head.content) ?: continue
                if (def.deleted) continue
                channels[head.entityIdHex] = ConcordChannel(head.entityIdHex, def)
            }

            // Role definitions ride along for display; the AuthorityResolver already owner-roots the
            // privileged roster via the grant fixpoint, so a role a rogue defines is inert until an
            // authorized granter (who must outrank it and hold MANAGE_ROLES) actually hands it out.
            val roles = HashMap<String, RoleEntity>()
            for (e in heads) {
                if (e.entityKind != ControlEntityKind.ROLE) continue
                val r = ConcordJson.decodeOrNull<RoleEntity>(e.content) ?: continue
                if (r.deleted) continue
                roles[e.entityIdHex] = r
            }

            // Dissolution is owner-only — a rogue tombstone must not appear to kill the community.
            val dissolved = heads.any { it.entityKind == ControlEntityKind.DISSOLVED && authority.isOwner(it.author) }

            return ConcordCommunityState(
                ownerPubKey = ownerPubKey.lowercase(),
                metadata = metadata,
                channels = channels,
                roles = roles,
                authority = authority,
                dissolved = dissolved,
            )
        }
    }
}
