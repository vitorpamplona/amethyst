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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/** A channel id paired with its current folded definition. */
data class ConcordChannel(
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
data class ConcordCommunityState(
    val ownerPubKey: String,
    val metadata: MetadataEntity?,
    val channels: Map<String, ConcordChannel>,
    val roles: Map<String, RoleEntity>,
    val authority: AuthorityResolver,
    val dissolved: Boolean,
) {
    /**
     * This state with [dissolved] set from the community's dissolution plane
     * ([ConcordDissolution.isDissolved]). One-way by the caller's contract: there is no un-dissolve.
     */
    fun withDissolved(dissolved: Boolean): ConcordCommunityState = if (dissolved == this.dissolved) this else copy(dissolved = dissolved)

    companion object {
        /** The Community Signals sub-kind (CORD-04 §8, upstream PR #17), carried but not modeled here. */
        private const val VSK_SIGNALS = "12"

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
                ControlEntityKind.PIN_LIST -> ConcordPermissions.PIN_MESSAGES
            }

        /**
         * Whether a reader honors [edition] as its entity's head: well-formed at its coordinate,
         * authored by the owner or a holder of the kind's bit, citing the Grant it acts under.
         *
         * A sub-kind we do not model is still gated — a floor or a compaction must only remember
         * editions some reader honors: a Signal by `MANAGE_CHANNELS` (the one gate Armada implements, `pause`), and anything newer by any
         * staff bit, the set whose actions are Control editions at all (CORD-04 §3).
         */
        private fun honors(
            authority: AuthorityResolver,
            edition: ControlEdition,
        ): Boolean {
            val kind = edition.entityKind ?: return honorsUnmodeled(authority, edition)
            return authority.admits(edition, requiredPermission(kind))
        }

        private fun honorsUnmodeled(
            authority: AuthorityResolver,
            edition: ControlEdition,
        ): Boolean =
            when (edition.vsk) {
                VSK_SIGNALS -> authority.admits(edition, ConcordPermissions.MANAGE_CHANNELS)
                else -> authority.isOwner(edition.author) || (authority.isStaff(edition.author) && authority.citationSatisfied(edition))
            }

        /**
         * The authority-gated structural head of **every** control entity, keyed by
         * [ControlEdition.entityIdHex] — the source of the anti-rollback [EntityFloor]s a
         * client carries across a CORD-06 Refounding, and the set of heads a Refounding's
         * compaction re-wraps (sub-kinds we do not model included, so another client's Pins
         * or Signals survive our Refounding).
         *
         * It is deliberately gated the same way [fold] gates each entity kind (and, for
         * [ControlEntityKind.DISSOLVED], owner-only): an *ungated* head map would let any
         * ex-member who still holds a rotated-out root mint a high-version edition on the
         * old Control Plane and thereby raise our floor, freezing the entity for us. The
         * floor must only ever remember editions we would actually have honored.
         */
        fun authorizedHeads(
            editions: Collection<ControlEdition>,
            communityId: ByteArray,
            ownerPubKey: String,
            floors: Map<String, EntityFloor> = emptyMap(),
        ): Map<String, EntityFloor> {
            // This call folds exactly one epoch's editions, so they ARE the snapshot: an entity
            // appearing here has been re-wrapped into this epoch and must anchor on version, not
            // on a `prev` that necessarily dangles back into the epoch the floor came from. The
            // known heads `admissible` re-seats are not in the set, so an entity this epoch never
            // mentioned correctly keeps chain-walk semantics.
            val snapshot = editions.mapTo(HashSet(editions.size)) { it.rumorId }
            val pool = EditionFold.admissible(editions, floors, snapshot = snapshot)
            val authority = AuthorityResolver.resolve(pool, communityId, ownerPubKey)
            val out = HashMap<String, EntityFloor>(floors)
            for ((_, list) in pool.groupBy { it.entityKind }) {
                // Gate the CANDIDATES, don't pre-filter the chain: a rejected edition mid-chain must
                // stay inert instead of orphaning the authorized editions above it (EditionFold.candidates).
                val heads =
                    EditionFold.foldGated(list, floors, snapshot = snapshot, rank = authority::tieBreakRank) {
                        honors(authority, it)
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

        /**
         * Folds one epoch's Control Plane [editions] of the community [communityId] (which pins
         * every derived entity coordinate, CORD-04 §1) owned by [ownerPubKey] into its current
         * state, honoring the anti-rollback [floors] carried from earlier epochs.
         */
        fun fold(
            editions: Collection<ControlEdition>,
            communityId: ByteArray,
            ownerPubKey: String,
            floors: Map<String, EntityFloor> = emptyMap(),
        ): ConcordCommunityState {
            // Everything below folds a *derived* view of the same editions (the resolver's
            // authority chains, the per-kind gated folds), so the anti-rollback floor is applied
            // once, up front, on the shared pool: a rolled-back edition is never seen by any of
            // them, and the head we already folded is re-seated so the entity keeps its state.
            // The editions handed in are one epoch's — the current one — so they are the
            // snapshot that selects the compaction arm. Captured BEFORE `admissible` re-seats
            // known heads from older epochs, which must not be mistaken for this epoch's.
            val snapshot = editions.mapTo(HashSet(editions.size)) { it.rumorId }

            @Suppress("NAME_SHADOWING")
            val editions = EditionFold.admissible(editions, floors, snapshot = snapshot)

            // Resolve authority from the FULL edition set (not the structural heads): the resolver
            // folds each role/grant chain through authorized editions only, so a rogue higher-version
            // edition can't supersede a legit one before authority is even judged.
            val authority = AuthorityResolver.resolve(editions, communityId, ownerPubKey)

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
            //
            // Every gate also demands the edition sit at its derived coordinate and cite the Grant
            // its author acts under (CORD-04 §5, `vac`) — AuthorityResolver.admits — and an
            // equal-version tie goes to the higher-ranked author before the rumor id (§1).
            fun foldGatedBy(
                kind: ControlEntityKind,
                bit: Int,
            ): Map<String, ControlEdition> =
                EditionFold.foldGated(editions.filter { it.entityKind == kind }, floors, snapshot = snapshot, rank = authority::tieBreakRank) {
                    authority.admits(it, bit)
                }

            // Metadata is ONE entity, at the community_id itself (CORD-04 §1): an edition at any
            // other coordinate is not this community's metadata however high its version, so it can
            // neither shadow the chain nor bypass it (S8). Name and description caps are fold gates.
            val metadata =
                foldGatedBy(ControlEntityKind.METADATA, ConcordPermissions.MANAGE_METADATA)[communityId.toHexKey()]
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

            // Dissolution is NOT read from the Control Plane. The tombstone is chainless and lives at its
            // own address (CORD-02 §9, [ConcordDissolution]) where it must also name this community in its
            // `eid`; a vsk-10 edition folded here would skip that binding check, so an owner's tombstone
            // for another community re-wrapped onto this plane would kill this one. The caller that reads
            // the dissolved plane sets [dissolved] via [withDissolved].
            val dissolved = false

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
