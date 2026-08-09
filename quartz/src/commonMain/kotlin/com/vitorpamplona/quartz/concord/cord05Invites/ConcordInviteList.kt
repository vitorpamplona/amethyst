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

import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

private val NoExtras: JsonObject = JsonObject(emptyMap())

/**
 * One minted invite link, as the creator's own bookkeeping (CORD-05, kind 13303).
 *
 * [token] is both the link's unlock secret and the **merge key** across devices, and [signerSk] is
 * the link signer's private key — which is what makes a link *refreshable*. The kind-33301 bundle is
 * addressable and authored by that signer, so re-posting under it moves the link to the current
 * epoch without changing the URL anyone already holds. Lose the secret and the link is orphaned at a
 * dead epoch forever, which is what made stranded recovery unreachable in practice.
 *
 * [residue] carries wire keys this build does not model. Armada types both the entry and the
 * tombstone as `[k: string]: unknown`, so unknown keys are part of the contract: dropping them on a
 * re-encode deletes another client's data.
 */
class ConcordInviteListEntry(
    val token: String,
    val signerSk: String,
    val communityId: String,
    val url: String,
    val label: String? = null,
    val createdAt: Long = 0,
    val expiresAt: Long? = null,
    val residue: JsonObject = NoExtras,
) {
    /** True when this link can no longer be joined, so it must not be refreshed (CORD-05). */
    fun isExpired(nowSecs: Long): Boolean = expiresAt != null && expiresAt <= nowSecs
}

/** A retired link: the creator's record that [token] is gone, kept so a merge cannot resurrect it. */
class ConcordInviteListTombstone(
    val token: String,
    val communityId: String,
    val residue: JsonObject = NoExtras,
)

/** The decoded kind-13303 document: live [entries], [tombstones], and document-level [residue]. */
class ConcordInviteListDocument(
    val entries: List<ConcordInviteListEntry> = emptyList(),
    val tombstones: List<ConcordInviteListTombstone> = emptyList(),
    val residue: JsonObject = NoExtras,
) {
    companion object {
        val EMPTY = ConcordInviteListDocument()
    }
}

/**
 * Codec + merge for the CORD-05 Invite List (kind 13303), the creator's private, NIP-44 self-
 * encrypted bookkeeping of the links they minted. Wire-compatible with Armada's `invite.ts`:
 *
 * ```jsonc
 * { "entries":    [ { "token", "signer_sk", "community_id", "url", "label?", "created_at", "expires_at?" } ],
 *   "tombstones": [ { "token", "community_id" } ] }
 * ```
 */
object ConcordInviteList {
    private const val EXTRAS = "__extras"

    /** Wraps a generated serializer so unknown keys survive a decode → modify → encode. */
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
            return JsonObject(extras + (obj - EXTRAS))
        }
    }

    @Serializable
    private class WireEntry(
        val token: String = "",
        @SerialName("signer_sk") val signerSk: String = "",
        @SerialName("community_id") val communityId: String = "",
        val url: String = "",
        val label: String? = null,
        @SerialName("created_at") val createdAt: Long = 0,
        @SerialName("expires_at") val expiresAt: Long? = null,
        @SerialName(EXTRAS) val extras: JsonObject = NoExtras,
    )

    @Serializable
    private class WireTombstone(
        val token: String = "",
        @SerialName("community_id") val communityId: String = "",
        @SerialName(EXTRAS) val extras: JsonObject = NoExtras,
    )

    private object WireEntrySerializer : ExtrasPreserving<WireEntry>(WireEntry.serializer())

    private object WireTombstoneSerializer : ExtrasPreserving<WireTombstone>(WireTombstone.serializer())

    @Serializable
    private class WireDocument(
        val entries: List<
            @Serializable(WireEntrySerializer::class)
            WireEntry,
        > = emptyList(),
        val tombstones: List<
            @Serializable(WireTombstoneSerializer::class)
            WireTombstone,
        > = emptyList(),
        @SerialName(EXTRAS) val extras: JsonObject = NoExtras,
    )

    private object WireDocumentSerializer : ExtrasPreserving<WireDocument>(WireDocument.serializer())

    /**
     * Decodes the plaintext document. A malformed document yields [ConcordInviteListDocument.EMPTY]
     * rather than throwing — but note the sharp edge this shape shares with the community list: one
     * unparseable entry aborts the whole array, so every field defaults instead of being required.
     */
    fun decode(json: String): ConcordInviteListDocument =
        try {
            val doc = ConcordJson.instance.decodeFromString(WireDocumentSerializer, json)
            ConcordInviteListDocument(
                entries =
                    doc.entries.map {
                        ConcordInviteListEntry(it.token, it.signerSk, it.communityId, it.url, it.label, it.createdAt, it.expiresAt, it.extras)
                    },
                tombstones = doc.tombstones.map { ConcordInviteListTombstone(it.token, it.communityId, it.extras) },
                residue = doc.extras,
            )
        } catch (_: Exception) {
            ConcordInviteListDocument.EMPTY
        }

    fun encode(doc: ConcordInviteListDocument): String =
        ConcordJson.instance.encodeToString(
            WireDocumentSerializer,
            WireDocument(
                entries =
                    doc.entries.map {
                        WireEntry(it.token, it.signerSk, it.communityId, it.url, it.label, it.createdAt, it.expiresAt, it.residue)
                    },
                tombstones = doc.tombstones.map { WireTombstone(it.token, it.communityId, it.residue) },
                extras = doc.residue,
            ),
        )

    /**
     * Merges [patch] onto [base], keyed by `token` — the spec's own merge key. A token present in
     * either side's tombstones is dropped from the result and kept tombstoned, so a retired link
     * cannot be resurrected by a device that still has it cached. [patch] wins field-by-field on a
     * token both sides carry, which is what makes "read remote, apply my change, publish" converge.
     */
    fun merge(
        base: ConcordInviteListDocument,
        patch: ConcordInviteListDocument,
    ): ConcordInviteListDocument {
        val tombstones = LinkedHashMap<String, ConcordInviteListTombstone>()
        for (t in base.tombstones + patch.tombstones) tombstones[t.token] = t

        val entries = LinkedHashMap<String, ConcordInviteListEntry>()
        for (e in base.entries + patch.entries) {
            if (e.token in tombstones) continue
            entries[e.token] = e
        }
        return ConcordInviteListDocument(
            entries = entries.values.toList(),
            tombstones = tombstones.values.toList(),
            residue = JsonObject(base.residue + patch.residue),
        )
    }
}
