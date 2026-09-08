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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

import com.vitorpamplona.quartz.marmot.MarmotFilters
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Discovery helpers for MIP-00 KeyPackages.
 *
 * Pulled out of Amethyst's `Account.fetchKeyPackageAndAddMember` so the CLI
 * (and any future non-Android caller) does not re-implement the same union-
 * of-relays logic when inviting a user to a Marmot group.
 *
 * This is the lowest-useful layer — it knows *nothing* about how callers
 * discover the target user's kind:10051 (KeyPackage Relay List) or kind:10002
 * (NIP-65 outbox) — those live in platform-specific caches. Callers collect
 * those sets themselves and pass them in.
 */
object KeyPackageFetcher {
    /**
     * Relays to query for a given user's KeyPackages.
     *
     * The spec's rule is the target's NIP-65 (kind 10002) WRITE-capable set:
     * "There is no dedicated KeyPackage relay list." MIP-00's kind 10051 is
     * gone, so [targetKeyPackageRelays] is now only a legacy hint — still worth
     * querying, because a peer that has not migrated may only be publishing
     * there, and querying an extra relay costs nothing and changes no
     * validity. Our own outbox stays as a shared-relay fallback.
     *
     * Order here is presentation only; the caller queries the whole set.
     */
    fun fetchRelaysFor(
        targetOutbox: Collection<NormalizedRelayUrl>,
        myOutbox: Collection<NormalizedRelayUrl>,
        targetKeyPackageRelays: Collection<NormalizedRelayUrl> = emptySet(),
    ): Set<NormalizedRelayUrl> =
        buildSet {
            addAll(targetOutbox)
            addAll(targetKeyPackageRelays)
            addAll(myOutbox)
        }

    /**
     * Drain every kind:443 KeyPackage event for [targetPubKey] across [relays]
     * and return the most recently published one (highest `created_at`), or
     * `null` if nothing arrived before the timeout.
     *
     * Returning the newest is important after a KeyPackage rotation: MIP-00
     * does not use addressable replacement for kind:443, so a relay may still
     * hold the prior KP alongside the new one. `fetchFirst` would race the
     * relays and pick whichever replied first, which would frequently be the
     * older event. Draining to EOSE and selecting by `created_at` matches
     * MDK/whitenoise semantics and keeps freshly-rotated bundles reachable.
     */
    suspend fun fetchKeyPackage(
        client: INostrClient,
        targetPubKey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        idleTimeoutMs: Long = 30_000,
    ): KeyPackageEvent? {
        if (relays.isEmpty()) return null
        val filter = MarmotFilters.keyPackagesByAuthor(targetPubKey)
        val events = client.fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = idleTimeoutMs)
        // fetchAll returns events sorted by created_at DESC, so the first
        // KeyPackageEvent is the most recent one any relay had.
        return events.firstNotNullOfOrNull { it as? KeyPackageEvent }
    }

    /**
     * Resolve which relays this account should publish its OWN KeyPackages to.
     *
     * The NIP-65 write-capable set, per `transports/nostr.md`: "The account
     * publishes its kind 30443 KeyPackage events to its write-capable set."
     *
     * This deliberately no longer prefers a kind:10051 list. Publishing only
     * where a now-removed list points would make us undiscoverable to a
     * conformant peer, which looks in the NIP-65 set and nowhere else.
     * [legacyKeyPackageRelayList] is unioned in rather than replacing the
     * outbox, so a peer still on the MIP-era path keeps finding us.
     */
    fun publishRelaysFor(
        myOutbox: Collection<NormalizedRelayUrl>,
        legacyKeyPackageRelayList: Collection<NormalizedRelayUrl> = emptySet(),
    ): Set<NormalizedRelayUrl> = myOutbox.toSet() + legacyKeyPackageRelayList.toSet()
}
