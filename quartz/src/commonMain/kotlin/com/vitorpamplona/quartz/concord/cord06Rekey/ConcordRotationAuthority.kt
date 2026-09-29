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
package com.vitorpamplona.quartz.concord.cord06Rekey

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.EntityFloor
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * The `vac` authority citation on a rotation (CORD-06 §3 "Authority", CORD-04 §5): a rotation
 * cites the Grant its rotator acts under like any authority action, so a just-demoted admin's
 * rotation is never honored by a lagging client. The owner cites nothing — the `community_id`
 * proves them.
 *
 * The citation is a *sync floor*, not the verdict: a verifier refuses the rotation until it
 * holds at least the cited Grant edition, then resolves the rotator's rank against its current
 * roster (the caller's `hasPermission(BAN)` check). Mirrors the reference client's
 * `citationSatisfied`: a newer Grant head than the cited one satisfies, the same version must
 * match the edition hash (a fork is refused), an older head parks the rotation (fail closed).
 *
 * [heads] is the authority-gated head of every Control entity keyed by `eid` hex — exactly
 * [ConcordCommunityState.authorizedHeads] over the current epoch's editions ([headsOf]).
 */
object ConcordRotationAuthority {
    /** The authority-gated entity heads a citation is minted from and checked against. */
    fun headsOf(
        editions: Collection<ControlEdition>,
        ownerPubKey: HexKey,
    ): Map<String, EntityFloor> = ConcordCommunityState.authorizedHeads(editions, ownerPubKey)

    /**
     * The citation [actor] puts on a rotation: their own Grant's current head, or null for the
     * owner (who cites nothing) and for an actor with no Grant (whose rotation nobody honors).
     */
    fun citationFor(
        communityIdHex: HexKey,
        actor: HexKey,
        ownerPubKey: HexKey,
        heads: Map<String, EntityFloor>,
    ): AuthorityCitation? {
        if (actor.equals(ownerPubKey, ignoreCase = true)) return null
        val eid = ConcordKeyDerivation.grantCoordinate(communityIdHex.hexToByteArray(), actor.lowercase().hexToByteArray())
        val head = heads[eid.toHexKey()] ?: return null
        val hash = head.hashHex.hexToByteArrayOrNull() ?: return null
        return AuthorityCitation(eid, head.version, hash)
    }

    /** Whether [citation] authorizes [actor]'s rotation under the verifier's [heads]. */
    fun citationSatisfied(
        communityIdHex: HexKey,
        actor: HexKey,
        ownerPubKey: HexKey,
        citation: AuthorityCitation?,
        heads: Map<String, EntityFloor>,
    ): Boolean {
        if (actor.equals(ownerPubKey, ignoreCase = true)) return true
        if (citation == null) return false
        // Must name the actor's OWN Grant coordinate.
        val eid = ConcordKeyDerivation.grantCoordinate(communityIdHex.hexToByteArray(), actor.lowercase().hexToByteArray()).toHexKey()
        if (citation.grantId.toHexKey() != eid) return false
        val head = heads[eid] ?: return false
        return when {
            head.version > citation.grantVersion -> true
            // At exactly it: the hash must match our fold's winner, not a fork.
            head.version == citation.grantVersion -> head.hashHex.equals(citation.grantHash.toHexKey(), ignoreCase = true)
            // Behind it: park until the Grant arrives.
            else -> false
        }
    }
}
