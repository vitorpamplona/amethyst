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
import com.vitorpamplona.quartz.concord.cord04Roles.EntityFloor
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord04Roles.asFloor

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
        /**
         * The permission bit an edition of each entity kind must be authored under.
         * `null` means owner-only (no bit grants it). Mirrors the per-kind gating
         * [fold] applies before the structural fold.
         */
        private fun requiredPermission(kind: ControlEntityKind): Int? =
            when (kind) {
                ControlEntityKind.METADATA -> ConcordPermissions.MANAGE_METADATA
                ControlEntityKind.CHANNEL -> ConcordPermissions.MANAGE_CHANNELS
                ControlEntityKind.ROLE, ControlEntityKind.GRANT -> ConcordPermissions.MANAGE_ROLES
                ControlEntityKind.BANLIST -> ConcordPermissions.BAN
                ControlEntityKind.INVITE_LIVE, ControlEntityKind.INVITE_REGISTRY, ControlEntityKind.INVITE_REVOKED -> ConcordPermissions.CREATE_INVITE
                ControlEntityKind.DISSOLVED -> null
            }

        /**
         * The authority-gated structural head of **every** control entity, keyed by
         * [ControlEdition.entityIdHex] — the source of the anti-rollback [EntityFloor]s a
         * client carries across a CORD-06 Refounding.
         *
         * It is deliberately gated the same way [fold] gates each entity kind (and, for
         * [ControlEntityKind.DISSOLVED], owner-only): an *ungated* head map would let any
         * ex-member who still holds a rotated-out root mint a high-version edition on the
         * old Control Plane and thereby raise our floor, freezing the entity for us. The
         * floor must only ever remember editions we would actually have honored.
         */
        fun authorizedHeads(
            editions: Collection<ControlEdition>,
            ownerPubKey: String,
            floors: Map<String, EntityFloor> = emptyMap(),
        ): Map<String, EntityFloor> {
            val pool = EditionFold.admissible(editions, floors)
            val authority = AuthorityResolver.resolve(pool, ownerPubKey)
            val out = HashMap<String, EntityFloor>(floors)
            for ((kind, list) in pool.groupBy { it.entityKind }) {
                val bit = requiredPermission(kind)
                // Gate the CANDIDATES, don't pre-filter the chain: a rejected edition mid-chain must
                // stay inert instead of orphaning the authorized editions above it (EditionFold.candidates).
                val heads =
                    EditionFold.foldGated(list, floors) {
                        authority.isOwner(it.author) || (bit != null && authority.hasPermission(it.author, bit))
                    }
                for ((entity, head) in heads) {
                    // Monotonic: a floor only ever rises. Folding epoch by epoch, an entity the
                    // newer epoch never mentions keeps the version the older one reached.
                    val prior = out[entity]
                    if (prior == null || head.version >= prior.version) out[entity] = head.asFloor()
                }
            }
            return out
        }

        fun fold(
            editions: Collection<ControlEdition>,
            ownerPubKey: String,
            floors: Map<String, EntityFloor> = emptyMap(),
        ): ConcordCommunityState {
            // Everything below folds a *derived* view of the same editions (the resolver's
            // authority chains, the per-kind gated folds), so the anti-rollback floor is applied
            // once, up front, on the shared pool: a rolled-back edition is never seen by any of
            // them, and the head we already folded is re-seated so the entity keeps its state.
            @Suppress("NAME_SHADOWING")
            val editions = EditionFold.admissible(editions, floors)

            val heads = EditionFold.fold(editions, floors).values
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
            // The gate is applied to each entity's ORDERED CANDIDATES (chain head first, then the
            // remaining editions version-descending), never as a pre-filter on the chain: dropping a
            // rejected edition out of the middle of a chain permanently orphans every honest edition
            // above it, freezing the entity. See EditionFold.candidates.
            fun foldGatedBy(
                kind: ControlEntityKind,
                bit: Int,
            ): Map<String, ControlEdition> =
                EditionFold.foldGated(editions.filter { it.entityKind == kind }, floors) {
                    authority.isOwner(it.author) || authority.hasPermission(it.author, bit)
                }

            // Metadata is one entity (== community id), gated by MANAGE_METADATA. Take the
            // highest-version gated head (guarding against strays).
            val metadata =
                foldGatedBy(ControlEntityKind.METADATA, ConcordPermissions.MANAGE_METADATA)
                    .values
                    .maxByOrNull { it.version }
                    ?.let { ConcordJson.decodeOrNull<MetadataEntity>(it.content) }

            // Channels are gated by MANAGE_CHANNELS, per channel entity, dropping the tombstoned ones.
            val channels = LinkedHashMap<String, ConcordChannel>()
            for (head in foldGatedBy(ControlEntityKind.CHANNEL, ConcordPermissions.MANAGE_CHANNELS).values) {
                val def = ConcordJson.decodeOrNull<ChannelEntity>(head.content) ?: continue
                if (def.deleted) continue
                channels[head.entityIdHex] = ConcordChannel(head.entityIdHex, def)
            }

            // Role definitions come from the authority-gated fold (not the raw structural heads): a
            // rogue can mint a higher-version edition on a legit role's coordinate (e.g. marking the
            // Admin role deleted) that would win a structural fold and corrupt the displayed roster,
            // so we take the roles the AuthorityResolver actually accepted from the owner outward.
            val roles = authority.roles()

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
