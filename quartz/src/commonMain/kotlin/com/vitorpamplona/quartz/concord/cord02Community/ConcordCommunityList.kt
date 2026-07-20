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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** The "nothing unknown was seen here" extras bag. */
val NoExtras: JsonObject = JsonObject(emptyMap())

/**
 * A past root key for a specific epoch, kept so historical channel keys stay derivable.
 *
 * [extras] carries any key another client wrote inside this held root that we do not
 * model, so a read-modify-write does not delete it (see [ConcordCommunityList]).
 */
@Serializable
class HeldRoot(
    val epoch: Long,
    val key: String,
    val extras: JsonObject = NoExtras,
)

/**
 * A private channel's delivered key at a given epoch (for private channels the member can read).
 *
 * [extras] carries any unmodelled key another client wrote inside this channel entry.
 */
@Serializable
class PrivateChannelKey(
    val channelId: String,
    val key: String,
    val epoch: Long,
    val name: String = "",
    val extras: JsonObject = NoExtras,
)

/**
 * Everything about one list entry that we parsed but do not model, kept verbatim so a
 * read-modify-write cycle re-emits it byte-for-byte instead of deleting another client's data.
 *
 * - [entryExtras] — unknown keys at the entry level (siblings of `community_id`/`current`).
 * - [seed] — the entry's `seed` object **exactly as it arrived**. `seed` is the immutable
 *   join anchor (Armada's backfill snapshot); we never hydrate a live entry from it when a
 *   `current` exists, so we have no business rewriting it either. Re-emitting it verbatim
 *   preserves every key at every depth inside it for free. Null when the document had none,
 *   in which case the encoder seeds it from the current join material.
 * - [currentExtras] — unknown keys inside the `current` join material. Unknown keys nested
 *   deeper (inside a channel or a held root) ride on [PrivateChannelKey.extras] and
 *   [HeldRoot.extras].
 */
class ConcordEntryResidue(
    val entryExtras: JsonObject = NoExtras,
    val seed: JsonObject? = null,
    val currentExtras: JsonObject = NoExtras,
) {
    companion object {
        val EMPTY = ConcordEntryResidue()
    }
}

/**
 * Everything about the list *document* that we parsed but do not model.
 *
 * - [extras] — unknown keys at the document root (siblings of `entries`/`tombstones`).
 * - [tombstones] — the document's tombstones, verbatim. We derive liveness from them but
 *   never author one, so dropping them on write would both lose another client's unknown
 *   keys and resurrect communities that client deliberately removed.
 */
class ConcordListResidue(
    val extras: JsonObject = NoExtras,
    val tombstones: List<JsonObject> = emptyList(),
) {
    companion object {
        val EMPTY = ConcordListResidue()
    }
}

/** A decoded kind-13302 document: the live [entries] plus everything else it carried. */
class ConcordCommunityListDocument(
    val entries: List<ConcordCommunityListEntry>,
    val residue: ConcordListResidue = ConcordListResidue.EMPTY,
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
 *
 * [residue] is the unmodelled part of the wire entry. Every copy of an entry MUST
 * carry it forward, or the next write deletes another client's data.
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
    @Transient val residue: ConcordEntryResidue = ConcordEntryResidue.EMPTY,
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
 * `seed` (backfill anchor) plus `current` (latest) snapshot; we hydrate from `current`,
 * falling back to `seed`.
 *
 * (Channels are not listed here beyond their private keys: once the [root] is held,
 * folding the Control Plane yields the community's channels.)
 *
 * ## Unknown keys are data, not noise
 *
 * Armada's entry type ends in `[k: string]: unknown`: unknown keys are part of the
 * contract, and a client is expected to hand them back untouched. [ConcordJson] sets
 * `ignoreUnknownKeys = true` precisely *because* "entity shapes are deliberately
 * client-extensible" — but ignoring on decode plus a closed DTO on encode means we
 * silently strip every extension we don't model, for every community, on every write.
 * The format is designed for extension and we were deleting the extensions.
 *
 * So every wire DTO here is decoded through [ExtrasPreserving], which lifts the known
 * fields and parks the remainder in an `__extras` bag that encode merges back
 * (known fields win on conflict). The bags exist at **every** level — document, entry,
 * `current` join material, each channel, each held root — because a top-level-only
 * catch-all would still destroy everything nested inside, which is where the data is.
 * `seed` and `tombstones` are kept verbatim instead (see [ConcordEntryResidue] /
 * [ConcordListResidue]), which preserves them at every depth by construction.
 *
 * This is also why [JoinMaterialWire] has no `refounder` field: it used to be typed,
 * parsed, never mapped into the domain, and therefore destroyed on the first write.
 * It now rides the generic extras path like any other client extension.
 */
object ConcordCommunityList {
    // ---- unknown-key preservation --------------------------------------------

    /** Where an unknown key hides between decode and encode. Never appears on the wire. */
    private const val EXTRAS = "__extras"

    /**
     * Wraps a generated serializer so unknown keys survive a decode → modify → encode.
     *
     * On decode, keys the DTO does not declare are moved into its `__extras` property; on
     * encode they are merged back as siblings, with the declared fields winning any name
     * collision. The known-key set is read off the delegate's descriptor rather than
     * hand-listed, so it can never drift from the DTO.
     */
    private open class ExtrasPreserving<T>(
        delegate: KSerializer<T>,
    ) : JsonTransformingSerializer<T>(delegate) {
        @OptIn(ExperimentalSerializationApi::class)
        private val known = delegate.descriptor.elementNames.toSet() - EXTRAS

        override fun transformDeserialize(element: JsonElement): JsonElement {
            val obj = element as? JsonObject ?: return element
            val extras = obj.filterKeys { it !in known }
            if (extras.isEmpty()) return obj
            return JsonObject(obj.filterKeys { it in known } + (EXTRAS to JsonObject(extras)))
        }

        override fun transformSerialize(element: JsonElement): JsonElement {
            val obj = element as? JsonObject ?: return element
            val extras = obj[EXTRAS]?.jsonObject ?: return obj
            // Known fields win: they are applied on top of the preserved unknowns.
            return JsonObject(extras + (obj - EXTRAS))
        }
    }

    // ---- wire DTOs (snake_case, Armada communityList.ts) ----------------------

    @Serializable
    private class WireChannel(
        val id: String,
        val key: String,
        val epoch: Long,
        val name: String = "",
        @SerialName(EXTRAS) val extras: JsonObject = NoExtras,
    )

    @Serializable
    private class WireHeldRoot(
        val epoch: Long,
        val key: String,
        @SerialName(EXTRAS) val extras: JsonObject = NoExtras,
    )

    @Serializable
    private class JoinMaterialWire(
        @SerialName("community_id") val communityId: String,
        val owner: String,
        @SerialName("owner_salt") val ownerSalt: String,
        @SerialName("community_root") val communityRoot: String,
        @SerialName("root_epoch") val rootEpoch: Long,
        val channels: List<
            @Serializable(WireChannelSerializer::class)
            WireChannel,
        > = emptyList(),
        val relays: List<String> = emptyList(),
        val name: String = "",
        @SerialName("held_roots") val heldRoots: List<
            @Serializable(WireHeldRootSerializer::class)
            WireHeldRoot,
        > = emptyList(),
        @SerialName(EXTRAS) val extras: JsonObject = NoExtras,
    )

    @Serializable
    private class CommunityListEntryWire(
        @SerialName("community_id") val communityId: String,
        /** Kept as raw JSON: the join anchor is never rewritten, only handed back. */
        val seed: JsonObject? = null,
        @Serializable(JoinMaterialWireSerializer::class) val current: JoinMaterialWire? = null,
        @SerialName("added_at") val addedAt: Long = 0,
        @SerialName("invite_ref") val inviteRef: String? = null,
        @SerialName("excluded_at_epoch") val excludedAtEpoch: Long? = null,
        @SerialName(EXTRAS) val extras: JsonObject = NoExtras,
    )

    @Serializable
    private class CommunityListDoc(
        val entries: List<
            @Serializable(CommunityListEntryWireSerializer::class)
            CommunityListEntryWire,
        > = emptyList(),
        /** Kept as raw JSON: we read tombstones but never author one. */
        val tombstones: List<JsonObject> = emptyList(),
        @SerialName(EXTRAS) val extras: JsonObject = NoExtras,
    )

    private object WireChannelSerializer : ExtrasPreserving<WireChannel>(WireChannel.serializer())

    private object WireHeldRootSerializer : ExtrasPreserving<WireHeldRoot>(WireHeldRoot.serializer())

    private object JoinMaterialWireSerializer : ExtrasPreserving<JoinMaterialWire>(JoinMaterialWire.serializer())

    private object CommunityListEntryWireSerializer : ExtrasPreserving<CommunityListEntryWire>(CommunityListEntryWire.serializer())

    private object CommunityListDocSerializer : ExtrasPreserving<CommunityListDoc>(CommunityListDoc.serializer())

    // ---- wire <-> domain ------------------------------------------------------

    private fun ConcordCommunityListEntry.toJoinMaterial() =
        JoinMaterialWire(
            communityId = id,
            owner = owner,
            ownerSalt = ownerSalt,
            communityRoot = root,
            rootEpoch = rootEpoch,
            channels = privateChannels.map { WireChannel(it.channelId, it.key, it.epoch, it.name, it.extras) },
            relays = relays,
            name = name,
            heldRoots = heldRoots.map { WireHeldRoot(it.epoch, it.key, it.extras) },
            extras = residue.currentExtras,
        )

    private fun JoinMaterialWire.toEntry(
        addedAt: Long,
        inviteRef: String? = null,
        excludedAtEpoch: Long? = null,
        residue: ConcordEntryResidue = ConcordEntryResidue.EMPTY,
    ) = ConcordCommunityListEntry(
        id = communityId,
        owner = owner,
        ownerSalt = ownerSalt,
        root = communityRoot,
        rootEpoch = rootEpoch,
        heldRoots = heldRoots.map { HeldRoot(it.epoch, it.key, it.extras) },
        privateChannels = channels.map { PrivateChannelKey(it.id, it.key, it.epoch, it.name, it.extras) },
        relays = relays,
        name = name,
        addedAt = addedAt,
        inviteRef = inviteRef,
        excludedAtEpoch = excludedAtEpoch,
        residue = residue,
    )

    // ---- build / codec --------------------------------------------------------

    /** Builds the encrypted kind-13302 list event from [entries], signed by [signer]. */
    suspend fun build(
        signer: NostrSigner,
        entries: List<ConcordCommunityListEntry>,
        createdAt: Long,
        residue: ConcordListResidue = ConcordListResidue.EMPTY,
    ): Event {
        val content = signer.nip44Encrypt(encode(entries, residue), signer.pubKey)
        return signer.sign(createdAt, ConcordCommunityListEvent.KIND, emptyArray(), content)
    }

    /**
     * Serializes [entries] to the plaintext JSON document that gets NIP-44 self-encrypted,
     * handing back everything [residue] (and each entry's own residue) preserved on decode.
     */
    fun encode(
        entries: List<ConcordCommunityListEntry>,
        residue: ConcordListResidue = ConcordListResidue.EMPTY,
    ): String {
        val doc =
            CommunityListDoc(
                entries =
                    entries.map { e ->
                        val jm = e.toJoinMaterial()
                        CommunityListEntryWire(
                            communityId = e.id,
                            // The join anchor is immutable: re-emit the original when we have it,
                            // and only mint one from the current material for a brand new entry.
                            seed = e.residue.seed ?: ConcordJson.instance.encodeToJsonElement(JoinMaterialWireSerializer, jm).jsonObject,
                            current = jm,
                            addedAt = e.addedAt,
                            inviteRef = e.inviteRef,
                            excludedAtEpoch = e.excludedAtEpoch,
                            extras = e.residue.entryExtras,
                        )
                    },
                tombstones = residue.tombstones,
                extras = residue.extras,
            )
        return ConcordJson.instance.encodeToString(CommunityListDocSerializer, doc)
    }

    /**
     * Parses the decrypted plaintext JSON document back into live entries, or empty on
     * failure. An entry is live unless a tombstone for the same community removed it
     * strictly after it was added; hydration prefers `current`, falling back to `seed`.
     */
    fun decode(json: String): List<ConcordCommunityListEntry> = decodeDocument(json).entries

    /**
     * Parses the decrypted plaintext JSON document into its live entries plus the
     * document-level residue (unknown keys and tombstones) that [encode] must hand back.
     * Returns an empty document on failure.
     */
    fun decodeDocument(json: String): ConcordCommunityListDocument =
        try {
            val doc = ConcordJson.instance.decodeFromString(CommunityListDocSerializer, json)
            val latestRemoval = HashMap<String, Long>()
            for (t in doc.tombstones) {
                val id = (t["community_id"] as? JsonPrimitive)?.contentOrNull ?: continue
                val removedAt = (t["removed_at"] as? JsonPrimitive)?.longOrNull ?: 0L
                val prev = latestRemoval[id]
                if (prev == null || removedAt > prev) latestRemoval[id] = removedAt
            }
            val entries =
                doc.entries.mapNotNull { e ->
                    val removedAt = latestRemoval[e.communityId]
                    if (removedAt != null && e.addedAt <= removedAt) return@mapNotNull null
                    val current = e.current
                    val seed = e.seed?.let { ConcordJson.instance.decodeFromJsonElement(JoinMaterialWireSerializer, it) }
                    // Hydrating from `seed` mints a fresh `current`; its unknown keys stay safe in
                    // the verbatim seed, so they are not copied into the new snapshot.
                    val residue =
                        ConcordEntryResidue(
                            entryExtras = e.extras,
                            seed = e.seed,
                            currentExtras = if (current != null) current.extras else NoExtras,
                        )
                    (current ?: seed)?.toEntry(e.addedAt, e.inviteRef, e.excludedAtEpoch, residue)
                }
            ConcordCommunityListDocument(
                entries = entries,
                residue = ConcordListResidue(extras = doc.extras, tombstones = doc.tombstones),
            )
        } catch (_: Exception) {
            ConcordCommunityListDocument(emptyList())
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
            residue = residue,
        )
}
