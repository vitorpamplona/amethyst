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

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The `vac` authority citation (CORD-04 §1/§5) for writers and for verifiers outside the
 * Control Plane fold — Kicks, rekey rotations (kind 3303), and anything else that acts under
 * a Grant.
 *
 * Every non-owner authority action cites the exact Grant edition its actor holds their rank
 * under — their own `grant_locator(community_id, actor)` coordinate, at the version and edition
 * hash of the head the roster folded. A reader drops (parks) the action until it holds that
 * Grant at or past the cited version, then judges the actor's rank against its **current**
 * roster, so a stale citation grandfathers nothing. The owner is proven by the `community_id`
 * and cites nothing. Mirrors Armada `citationSatisfied` (control.ts).
 */
object AuthorityCitations {
    /**
     * The citation [actor] must attach to an authority action, resolved from the community's
     * folded [authority] roster; null for the owner, and for an actor holding no honored Grant
     * (no reader would honor their action anyway).
     */
    fun forActor(
        authority: AuthorityResolver,
        actor: HexKey,
    ): AuthorityCitation? = authority.citationFor(actor)

    /**
     * [forActor] straight from the Control Plane [editions]: folds the roster of the community
     * [communityId], owned by [ownerPubKey], and cites [actor]'s Grant head in it. Short-circuits
     * for the owner without folding.
     */
    fun forActor(
        editions: Collection<ControlEdition>,
        communityId: ByteArray,
        ownerPubKey: HexKey,
        actor: HexKey,
    ): AuthorityCitation? {
        if (actor.equals(ownerPubKey, ignoreCase = true)) return null
        return AuthorityResolver.resolve(editions, communityId, ownerPubKey).citationFor(actor)
    }

    /**
     * Whether [citation] satisfies CORD-04 §5's sync floor for an action by [actor] against the
     * folded [authority] roster: the owner needs none; anyone else must cite their own Grant
     * coordinate, held at or past the cited version (hash matching at equality). Completeness
     * only — the caller still checks the actor's bit and rank against [authority].
     */
    fun isSatisfied(
        authority: AuthorityResolver,
        actor: HexKey,
        citation: AuthorityCitation?,
    ): Boolean = authority.citationSatisfied(actor, citation)
}
