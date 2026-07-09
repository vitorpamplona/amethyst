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
import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

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
)

/**
 * One joined Concord community in the member's private list. Carries everything
 * needed to re-derive the community's planes on any device: identity ([id],
 * [owner], [ownerSalt]), the current access [root] at [rootEpoch] plus past
 * [heldRoots], any [privateChannels] keys, bootstrap [relays], and a cached
 * display [name].
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
)

/**
 * The member's private, self-encrypted list of joined Concord communities
 * (kind [ConcordKinds.COMMUNITY_LIST] = 13302, CORD-05) — the NIP-51 analog that
 * lets a client return to the groups the user signed up for. Replaceable and
 * NIP-44-encrypted to the member's own key, so relays store only ciphertext.
 *
 * (Channels are not listed here: once the [root] is held, folding the Control
 * Plane yields the community's channels.)
 */
object ConcordCommunityList {
    /** Builds the encrypted kind-13302 list event from [entries], signed by [signer]. */
    suspend fun build(
        signer: NostrSigner,
        entries: List<ConcordCommunityListEntry>,
        createdAt: Long,
    ): Event {
        val json = ConcordJson.instance.encodeToString(ListSerializer(ConcordCommunityListEntry.serializer()), entries)
        val content = signer.nip44Encrypt(json, signer.pubKey)
        return signer.sign(createdAt, ConcordKinds.COMMUNITY_LIST, emptyArray(), content)
    }

    /** Decrypts and parses a kind-13302 list event with [signer], or empty on failure. */
    suspend fun parse(
        event: Event,
        signer: NostrSigner,
    ): List<ConcordCommunityListEntry> {
        if (event.kind != ConcordKinds.COMMUNITY_LIST) return emptyList()
        return try {
            val json = signer.nip44Decrypt(event.content, signer.pubKey)
            ConcordJson.instance.decodeFromString(ListSerializer(ConcordCommunityListEntry.serializer()), json)
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
            if (existing == null || e.rootEpoch > existing.rootEpoch) byId[e.id] = e
        }
        return byId.values.toList()
    }
}
