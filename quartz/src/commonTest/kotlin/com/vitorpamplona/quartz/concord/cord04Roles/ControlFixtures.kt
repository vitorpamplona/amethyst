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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * Shared scaffolding for tests that hand-build Control Plane editions.
 *
 * The fold pins every derived coordinate to the `community_id` (a Grant at its member's
 * `grant_locator`, the Banlist at `banlist_locator`, Metadata at the id itself) and honors a
 * non-owner edition only when it cites its author's Grant (`vac`, CORD-04 §5). Tests that are
 * about something else build editions at [grantEid] / [banlistEid] / [COMMUNITY_ID_HEX] and run
 * them through [cited], which stamps every uncited non-owner edition with the citation an honest
 * client would have written — its author's Grant head as the fold sees it.
 */
object ControlFixtures {
    const val COMMUNITY_ID_HEX = "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf"
    val communityId: ByteArray = COMMUNITY_ID_HEX.hexToByteArray()

    /** `grant_locator(community_id, member)` as hex. */
    fun grantEid(
        member: String,
        cid: ByteArray = communityId,
    ): String = ConcordKeyDerivation.grantCoordinate(cid, member.hexToByteArray()).toHexKey()

    /** `banlist_locator(community_id)` as hex. */
    fun banlistEid(cid: ByteArray = communityId): String = ConcordKeyDerivation.banlistCoordinate(cid).toHexKey()

    /**
     * [editions] with every non-owner edition that carries no `vac` given the citation its author
     * would have written: their Grant head in the resolved roster. Iterated because a delegated
     * granter's own Grant only resolves once the editions above it are cited. Editions that already
     * carry a citation (a test's deliberate forgery, say) are left exactly as they are.
     */
    fun cited(
        editions: List<ControlEdition>,
        owner: String,
        cid: ByteArray = communityId,
    ): List<ControlEdition> {
        var current = editions
        repeat(editions.size + 1) {
            val authority = AuthorityResolver.resolve(current, cid, owner)
            var changed = false
            val next =
                current.map { e ->
                    if (e.authorityCitation != null || e.author.equals(owner, ignoreCase = true)) {
                        e
                    } else {
                        authority.citationFor(e.author)?.let {
                            changed = true
                            e.withCitation(it)
                        } ?: e
                    }
                }
            if (!changed) return current
            current = next
        }
        return current
    }

    fun resolve(
        editions: List<ControlEdition>,
        owner: String,
        cid: ByteArray = communityId,
    ): AuthorityResolver = AuthorityResolver.resolve(cited(editions, owner, cid), cid, owner)

    fun fold(
        editions: List<ControlEdition>,
        owner: String,
        floors: Map<String, EntityFloor> = emptyMap(),
        cid: ByteArray = communityId,
    ): ConcordCommunityState = ConcordCommunityState.fold(cited(editions, owner, cid), cid, owner, floors)

    fun authorizedHeads(
        editions: List<ControlEdition>,
        owner: String,
        floors: Map<String, EntityFloor> = emptyMap(),
        cid: ByteArray = communityId,
    ): Map<String, EntityFloor> = ConcordCommunityState.authorizedHeads(cited(editions, owner, cid), cid, owner, floors)
}
