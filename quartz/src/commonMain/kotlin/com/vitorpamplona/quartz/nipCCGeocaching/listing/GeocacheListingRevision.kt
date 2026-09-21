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
package com.vitorpamplona.quartz.nipCCGeocaching.listing

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.update
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Owner-side edits that republish an existing listing at the same address.
 *
 * 37516 is replaceable, so the owner's lifecycle — archiving a cache, locking in the first
 * finder — is not a separate kind and not a delete. It is a new revision carrying every tag the
 * old one had plus the change, which every client already rendering the cache picks up for free.
 *
 * Both build on [update], which seeds the builder from the event's own tag array rather than
 * rebuilding from parsed fields. That matters: a real listing carries tags this library models
 * nothing for — `client`, `expiration`, NIP-32 labels, Lightning Piggy's payout hints — and a
 * rebuild would silently drop them. That is how an owner loses data by pressing Archive in the
 * wrong app.
 */
object GeocacheListingRevision {
    /**
     * The same listing with `archived` added to its `t` tags.
     *
     * NIP-CC puts `archived` alongside the cache type rather than in a field of its own, so this
     * adds a value rather than replacing one: a traditional cache that gets archived is still a
     * traditional cache, and a client that reads `t` as a single value — as the reference ones
     * do — at least still sees something true.
     */
    fun archived(
        listing: GeocacheListingEvent,
        createdAt: Long = TimeUtils.now(),
    ) = listing.update(createdAt) { archived() }

    /**
     * The same listing with its first-to-find winner locked in.
     *
     * `F` is authoritative and permanent once published — NIP-CC says clients MUST attribute the
     * exclusive claim to the pubkey it names — so this replaces any earlier `F` rather than
     * appending to it. Two winners is not a state the spec has an answer for.
     */
    fun withFirstToFindWinner(
        listing: GeocacheListingEvent,
        winnerPubKey: HexKey,
        createdAt: Long = TimeUtils.now(),
    ) = listing.update(createdAt) { firstToFindWinner(winnerPubKey) }
}
