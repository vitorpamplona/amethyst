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

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEditionBuilder
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * The Invite Registry (CORD-05 §5, `vsk 8`): a creator's member-facing list of their live link
 * coordinates, published as a Control Plane edition at `invite_links_locator(community_id, creator)`
 * (CORD-02 A.6), so each creator owns exactly their own list and nobody can forge entries into
 * anyone else's.
 *
 * Its content is a bare JSON array of **link-signer pubkeys** (the authors of the kind-33301
 * bundles, whose `d` is empty, §2) — locators only, never tokens, URLs or signing secrets:
 * ```jsonc
 * ["<link_signer pubkey hex>", "<link_signer pubkey hex>"]
 * ```
 *
 * Members fold every creator's registry (honored only while its author holds `CREATE_INVITE`)
 * into one aggregate active-set, and that set is the community's **Public/Private source of
 * truth**: non-empty means Public, empty means Private (see
 * [com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState.isPublic]).
 */
object ConcordInviteRegistry {
    private val LINK_SIGNER = Regex("^[0-9a-fA-F]{64}$")

    /** Strict JSON: the reference client reads the content with `JSON.parse`, which refuses what a lenient parser would accept. */
    private val strict = Json

    /** The registry coordinate (entity id) of [creator] in [communityId]. */
    fun coordinate(
        communityId: ByteArray,
        creator: HexKey,
    ): ByteArray = ConcordKeyDerivation.inviteLinksCoordinate(communityId, creator.hexToByteArray())

    /** [coordinate] as hex. */
    fun coordinateHex(
        communityId: ByteArray,
        creator: HexKey,
    ): HexKey = coordinate(communityId, creator).toHexKey()

    /** Whether [value] is a link-signer entry a reader keeps: a 64-hex x-only pubkey. */
    fun isLinkSigner(value: String): Boolean = LINK_SIGNER.matches(value)

    /**
     * The registry content for [linkSigners]: lowercase, de-duplicated and sorted, so two devices of
     * one creator holding the same set write the same bytes. Anything that is not a link-signer
     * pubkey is dropped rather than published, since every reader would drop it anyway.
     */
    fun encode(linkSigners: Collection<HexKey>): String {
        val clean =
            linkSigners
                .filter(::isLinkSigner)
                .map { it.lowercase() }
                .distinct()
                .sorted()
        return strict.encodeToString(JsonArray.serializer(), JsonArray(clean.map { JsonPrimitive(it) }))
    }

    /**
     * Whether [content] is a well-formed registry: a JSON array, whatever it holds (Armada's
     * `Array.isArray(JSON.parse(content))`). A malformed edition is not honored, so the entity falls
     * back to the creator's previous authorized edition.
     */
    fun isWellFormed(content: String): Boolean = parseArrayOrNull(content) != null

    /**
     * The link signers [content] lists, lowercase and de-duplicated, keeping only string entries
     * that are 64-hex pubkeys; null when [content] is not a JSON array at all.
     */
    fun decodeOrNull(content: String): List<HexKey>? {
        val array = parseArrayOrNull(content) ?: return null
        return array
            .mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content }
            .filter(::isLinkSigner)
            .map { it.lowercase() }
            .distinct()
    }

    private fun parseArrayOrNull(content: String): JsonArray? =
        try {
            strict.parseToJsonElement(content) as? JsonArray
        } catch (_: Exception) {
            null
        }

    /**
     * The link signers [creator]'s next registry edition lists (CORD-05 §5, "a Registry edit
     * accompanies every mint and every retire"): the registry they currently publish ([published],
     * their honored head), plus every link their Invite List [list] still holds for [communityIdHex],
     * plus [minted]; minus [retired], and minus every link the list records as tombstoned or past its
     * `expires_at` at [nowSecs] — an elapsed link can no longer be joined, so it must stop keeping the
     * community Public. A null [list] (unreadable) contributes nothing and prunes nothing.
     *
     * The Invite List half heals a registry that fell behind: a link minted before any registry was
     * published (or by a device whose registry edit never landed) is re-listed on the next edit.
     */
    fun nextLinks(
        published: Collection<HexKey>,
        list: ConcordInviteListDocument?,
        communityIdHex: HexKey,
        nowSecs: Long,
        minted: Collection<HexKey> = emptyList(),
        retired: Collection<HexKey> = emptyList(),
    ): List<HexKey> {
        val dead = retired.mapTo(HashSet()) { it.lowercase() }
        val live = LinkedHashSet<HexKey>()
        published.forEach { live += it.lowercase() }
        if (list != null) {
            val tombstoned = list.tombstones.mapTo(HashSet()) { it.token }
            for (entry in list.entries) {
                if (!entry.communityId.equals(communityIdHex, ignoreCase = true)) continue
                val signer = runCatching { entry.signerPubKeyHex().lowercase() }.getOrNull() ?: continue
                if (entry.token in tombstoned || entry.isExpired(nowSecs)) dead += signer else live += signer
            }
        }
        minted.forEach { live += it.lowercase() }
        return live.filter { it !in dead && isLinkSigner(it) }.sorted()
    }

    /**
     * An unsigned registry edition rumor for [creator] listing [linkSigners] (CORD-05 §5). Chain it
     * onto the creator's current authorized head ([version] = head + 1, [prevHash] = its hash; a
     * first registry is version 1 with no prev) and cite the Grant the creator acts under
     * ([authorityCitation], null for the owner) like any authority edition (CORD-04 §5, `vac`).
     */
    fun rumor(
        creator: HexKey,
        communityId: ByteArray,
        linkSigners: Collection<HexKey>,
        version: Long,
        prevHash: ByteArray?,
        createdAt: Long,
        authorityCitation: AuthorityCitation? = null,
    ): Event =
        ControlEditionBuilder.rumor(
            authorPubKey = creator,
            entityKind = ControlEntityKind.INVITE_REGISTRY,
            entityId = coordinate(communityId, creator),
            version = version,
            prevHash = prevHash,
            content = encode(linkSigners),
            createdAt = createdAt,
            authorityCitation = authorityCitation,
        )
}
