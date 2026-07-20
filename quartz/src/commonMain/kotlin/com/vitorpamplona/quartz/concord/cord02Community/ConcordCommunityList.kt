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

import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A past root key for a specific epoch, kept so historical channel keys stay derivable. */
@Serializable
class HeldRoot(
    val epoch: Long,
    val key: String,
)

/** A private channel's delivered key at a given epoch (for private channels the member can read). */
@Serializable
class PrivateChannelKey(
    val channelId: String,
    val key: String,
    val epoch: Long,
    val name: String = "",
)

/**
 * One joined Concord community in the member's private list. Carries everything
 * needed to re-derive the community's planes on any device: identity ([id],
 * [owner], [ownerSalt]), the current access [root] at [rootEpoch] plus past
 * [heldRoots], any [privateChannels] keys, bootstrap [relays], and a cached
 * display [name]. [addedAt] is the wire join timestamp (ms) that tiebreaks
 * liveness against tombstones.
 *
 * [inviteRef] is the invite link this membership was joined through, kept in the
 * domain-agnostic bare `<naddr>#<fragment>` form (Armada's `invite_ref`, CORD-05
 * §2/§3). It is the anchor for stranded recovery: a Refounding carries no
 * recipient list, so a member left out of the rekey set never hears about the new
 * epoch — re-resolving this link is the only way back. Entries joined without a
 * link (direct invites, legacy entries) simply have none and are inert for
 * recovery. [excludedAtEpoch] records the epoch at which we observed ourselves
 * excluded, if ever.
 */
@Serializable
class ConcordCommunityListEntry(
    val id: String,
    val owner: String,
    val ownerSalt: String,
    val root: String,
    val rootEpoch: Long = 0,
    val heldRoots: List<HeldRoot> = emptyList(),
    val privateChannels: List<PrivateChannelKey> = emptyList(),
    val relays: List<String> = emptyList(),
    val name: String = "",
    val addedAt: Long = 0,
    val inviteRef: String? = null,
    val excludedAtEpoch: Long? = null,
)

/**
 * The member's private, self-encrypted list of joined Concord communities
 * (kind [ConcordCommunityListEvent.KIND] = 13302, CORD-05) — the NIP-51 analog that
 * lets a client return to the groups the user signed up for. Replaceable and
 * NIP-44-encrypted to the member's own key, so relays store only ciphertext.
 *
 * The plaintext document is wire-compatible with Soapbox Armada's `communityList.ts`:
 * `{ "entries": [ { "community_id", "seed": JoinMaterial, "current": JoinMaterial,
 * "added_at" } ], "tombstones": [ { "community_id", "removed_at" } ] }`, where
 * [JoinMaterialWire] is the snake_case per-snapshot key bundle. Liveness is derived —
 * an entry is dropped only when a later tombstone removes it — and each entry keeps a
 * [CommunityListEntryWire.seed] (backfill anchor) plus [CommunityListEntryWire.current]
 * (latest) snapshot; we hydrate from `current`, falling back to `seed`.
 *
 * (Channels are not listed here beyond their private keys: once the [root] is held,
 * folding the Control Plane yields the community's channels.)
 */
object ConcordCommunityList {
    // ---- wire DTOs (snake_case, Armada communityList.ts) ----------------------

    @Serializable
    private class WireChannel(
        val id: String,
        val key: String,
        val epoch: Long,
        val name: String = "",
    )

    @Serializable
    private class WireHeldRoot(
        val epoch: Long,
        val key: String,
    )

    @Serializable
    private class JoinMaterialWire(
        @SerialName("community_id") val communityId: String,
        val owner: String,
        @SerialName("owner_salt") val ownerSalt: String,
        @SerialName("community_root") val communityRoot: String,
        @SerialName("root_epoch") val rootEpoch: Long,
        val channels: List<WireChannel> = emptyList(),
        val relays: List<String> = emptyList(),
        val name: String = "",
        @SerialName("held_roots") val heldRoots: List<WireHeldRoot> = emptyList(),
        val refounder: String? = null,
    )

    @Serializable
    private class CommunityListEntryWire(
        @SerialName("community_id") val communityId: String,
        val seed: JoinMaterialWire? = null,
        val current: JoinMaterialWire? = null,
        @SerialName("added_at") val addedAt: Long = 0,
        @SerialName("invite_ref") val inviteRef: String? = null,
        @SerialName("excluded_at_epoch") val excludedAtEpoch: Long? = null,
    )

    @Serializable
    private class CommunityTombstoneWire(
        @SerialName("community_id") val communityId: String,
        @SerialName("removed_at") val removedAt: Long = 0,
    )

    @Serializable
    private class CommunityListDoc(
        val entries: List<CommunityListEntryWire> = emptyList(),
        val tombstones: List<CommunityTombstoneWire> = emptyList(),
    )

    private fun ConcordCommunityListEntry.toJoinMaterial() =
        JoinMaterialWire(
            communityId = id,
            owner = owner,
            ownerSalt = ownerSalt,
            communityRoot = root,
            rootEpoch = rootEpoch,
            channels = privateChannels.map { WireChannel(it.channelId, it.key, it.epoch, it.name) },
            relays = relays,
            name = name,
            heldRoots = heldRoots.map { WireHeldRoot(it.epoch, it.key) },
        )

    private fun JoinMaterialWire.toEntry(
        addedAt: Long,
        inviteRef: String? = null,
        excludedAtEpoch: Long? = null,
    ) = ConcordCommunityListEntry(
        id = communityId,
        owner = owner,
        ownerSalt = ownerSalt,
        root = communityRoot,
        rootEpoch = rootEpoch,
        heldRoots = heldRoots.map { HeldRoot(it.epoch, it.key) },
        privateChannels = channels.map { PrivateChannelKey(it.id, it.key, it.epoch, it.name) },
        relays = relays,
        name = name,
        addedAt = addedAt,
        inviteRef = inviteRef,
        excludedAtEpoch = excludedAtEpoch,
    )

    // ---- build / codec --------------------------------------------------------

    /** Builds the encrypted kind-13302 list event from [entries], signed by [signer]. */
    suspend fun build(
        signer: NostrSigner,
        entries: List<ConcordCommunityListEntry>,
        createdAt: Long,
    ): Event {
        val content = signer.nip44Encrypt(encode(entries), signer.pubKey)
        return signer.sign(createdAt, ConcordCommunityListEvent.KIND, emptyArray(), content)
    }

    /** Serializes [entries] to the plaintext JSON document that gets NIP-44 self-encrypted. */
    fun encode(entries: List<ConcordCommunityListEntry>): String {
        val doc =
            CommunityListDoc(
                entries =
                    entries.map { e ->
                        val jm = e.toJoinMaterial()
                        CommunityListEntryWire(
                            communityId = e.id,
                            seed = jm,
                            current = jm,
                            addedAt = e.addedAt,
                            inviteRef = e.inviteRef,
                            excludedAtEpoch = e.excludedAtEpoch,
                        )
                    },
                tombstones = emptyList(),
            )
        return ConcordJson.instance.encodeToString(CommunityListDoc.serializer(), doc)
    }

    /**
     * Parses the decrypted plaintext JSON document back into live entries, or empty on
     * failure. An entry is live unless a tombstone for the same community removed it
     * strictly after it was added; hydration prefers `current`, falling back to `seed`.
     */
    fun decode(json: String): List<ConcordCommunityListEntry> =
        try {
            val doc = ConcordJson.instance.decodeFromString(CommunityListDoc.serializer(), json)
            val latestRemoval = HashMap<String, Long>()
            for (t in doc.tombstones) {
                val prev = latestRemoval[t.communityId]
                if (prev == null || t.removedAt > prev) latestRemoval[t.communityId] = t.removedAt
            }
            doc.entries.mapNotNull { e ->
                val removedAt = latestRemoval[e.communityId]
                if (removedAt != null && e.addedAt <= removedAt) return@mapNotNull null
                (e.current ?: e.seed)?.toEntry(e.addedAt, e.inviteRef, e.excludedAtEpoch)
            }
        } catch (_: Exception) {
            emptyList()
        }

    /** Decrypts and parses a kind-13302 list event with [signer], or empty on failure. */
    suspend fun parse(
        event: Event,
        signer: NostrSigner,
    ): List<ConcordCommunityListEntry> {
        if (event.kind != ConcordCommunityListEvent.KIND) return emptyList()
        return try {
            decode(signer.nip44Decrypt(event.content, signer.pubKey))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Merges two decrypted lists (e.g. from two devices), keeping one entry per
     * community id. When both hold the same community, the one with the higher
     * [ConcordCommunityListEntry.rootEpoch] wins so the freshest access key
     * survives.
     */
    fun merge(
        a: List<ConcordCommunityListEntry>,
        b: List<ConcordCommunityListEntry>,
    ): List<ConcordCommunityListEntry> {
        val byId = LinkedHashMap<String, ConcordCommunityListEntry>()
        for (e in a + b) {
            val existing = byId[e.id]
            if (existing == null) {
                byId[e.id] = e
            } else if (e.rootEpoch > existing.rootEpoch) {
                // A winner without an invite_ref inherits the loser's: that link is the only anchor
                // stranded recovery has, and dropping it on a merge would disarm recovery forever.
                byId[e.id] = if (e.inviteRef == null) e.withInviteRef(existing.inviteRef) else e
            } else if (existing.inviteRef == null && e.inviteRef != null) {
                byId[e.id] = existing.withInviteRef(e.inviteRef)
            }
        }
        return byId.values.toList()
    }

    /** Copy of this entry carrying [inviteRef]; every other field untouched. */
    fun ConcordCommunityListEntry.withInviteRef(inviteRef: String?) =
        ConcordCommunityListEntry(
            id = id,
            owner = owner,
            ownerSalt = ownerSalt,
            root = root,
            rootEpoch = rootEpoch,
            heldRoots = heldRoots,
            privateChannels = privateChannels,
            relays = relays,
            name = name,
            addedAt = addedAt,
            inviteRef = inviteRef,
            excludedAtEpoch = excludedAtEpoch,
        )
}
