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
package com.vitorpamplona.quartz.nipCCGeocaching.firstToFind

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationValidator

/**
 * Who claimed a `first-to-find` cache.
 *
 * NIP-CC decides this in two stages, and the second one exists because the first cannot be
 * trusted:
 *
 * 1. **Provisionally**, the winner is the verified found log with the earliest `created_at`, ties
 *    broken by ascending lexicographic event id.
 * 2. **Finally**, once the owner publishes an `F` tag on the listing, that pubkey is the winner —
 *    "regardless of which verified found log currently appears earliest".
 *
 * Stage 2 is not a convenience. `created_at` is author-supplied, so anyone who finds the cache a
 * month late can sign a log dated before the real winner's and take the claim by stage 1 alone.
 * Locking the winner in is what makes the claim stick, which is why [winnerPubKey] reads `F`
 * first and only falls back to timestamps while none has been published.
 *
 * Every function here filters to *verified* logs first. An unverified found log on a
 * `first-to-find` cache is somebody's word; the claim is reserved for logs that carry a kind 7517
 * that holds up against the listing.
 */
object FirstToFindResolver {
    /** The logs that are about [listing] and carry a verification that checks out against it. */
    fun verifiedLogs(
        listing: GeocacheListingEvent,
        logs: List<GeocacheFoundLogEvent>,
    ): List<GeocacheFoundLogEvent> =
        logs.filter {
            it.geocache() == listing.address() && GeocacheVerificationValidator.isValid(it, listing)
        }

    /**
     * The earliest verified log, ties broken by ascending event id.
     *
     * "Provisional" is literal: this is only the winner while the owner has not locked one in,
     * and a log with a forged `created_at` wins it.
     */
    fun provisionalWinningLog(
        listing: GeocacheListingEvent,
        logs: List<GeocacheFoundLogEvent>,
    ): GeocacheFoundLogEvent? = verifiedLogs(listing, logs).minWithOrNull(compareBy({ it.createdAt }, { it.id }))

    /**
     * The winning log: the owner's locked-in winner's earliest verified log where `F` is
     * published, the provisional winner otherwise.
     *
     * Null on a cache that is not `first-to-find` — there is no exclusive claim to award.
     */
    fun winningLog(
        listing: GeocacheListingEvent,
        logs: List<GeocacheFoundLogEvent>,
    ): GeocacheFoundLogEvent? {
        if (!listing.isFirstToFind()) return null

        val lockedIn = listing.firstToFindWinner() ?: return provisionalWinningLog(listing, logs)

        return verifiedLogs(listing, logs)
            .filter { it.pubKey == lockedIn }
            .minWithOrNull(compareBy({ it.createdAt }, { it.id }))
    }

    /**
     * The pubkey holding the exclusive claim, or null if nobody does yet.
     *
     * An `F` tag wins even when no log for it is in [logs] — the owner has confirmed the claim
     * and the log may simply not have been fetched.
     */
    fun winnerPubKey(
        listing: GeocacheListingEvent,
        logs: List<GeocacheFoundLogEvent>,
    ): HexKey? {
        if (!listing.isFirstToFind()) return null
        return listing.firstToFindWinner() ?: provisionalWinningLog(listing, logs)?.pubKey
    }

    /** Whether the owner has locked the winner in, as opposed to it resting on timestamps. */
    fun isLockedIn(listing: GeocacheListingEvent) = listing.firstToFindWinner() != null

    /**
     * Whether the claim is taken.
     *
     * NIP-CC: once it is, clients should render the listing as effectively archived and hide the
     * find-submission affordances. Later verified logs stay valid records of physical presence —
     * they are simply not additional claims.
     */
    fun isClaimed(
        listing: GeocacheListingEvent,
        logs: List<GeocacheFoundLogEvent>,
    ) = winnerPubKey(listing, logs) != null
}
