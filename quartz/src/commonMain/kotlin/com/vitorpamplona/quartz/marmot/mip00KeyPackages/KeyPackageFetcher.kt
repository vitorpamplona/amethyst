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
     * Union of the three relay sets we'd ever want to query for a given user's
     * KeyPackage, in priority order of specificity:
     *
     * 1. target's kind:10051 KeyPackage Relay List (most authoritative)
     * 2. target's kind:10002 NIP-65 outbox (where the user publishes in general)
     * 3. our own outbox (shared-relay fallback — someone publishing and us
     *    reading often overlap here)
     */
    fun fetchRelaysFor(
        targetKeyPackageRelays: Collection<NormalizedRelayUrl>,
        targetOutbox: Collection<NormalizedRelayUrl>,
        myOutbox: Collection<NormalizedRelayUrl>,
    ): Set<NormalizedRelayUrl> =
        buildSet {
            addAll(targetKeyPackageRelays)
            addAll(targetOutbox)
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
        timeoutMs: Long = 30_000,
    ): KeyPackageEvent? {
        if (relays.isEmpty()) return null
        val filter = MarmotFilters.keyPackagesByAuthor(targetPubKey)
        val events = client.fetchAll(filters = relays.associateWith { listOf(filter) }, timeoutMs = timeoutMs)
        // fetchAll returns events sorted by created_at DESC, so the first
        // KeyPackageEvent is the most recent one any relay had.
        return events.firstNotNullOfOrNull { it as? KeyPackageEvent }
    }

    /**
     * Resolve which relays this account should publish its OWN KeyPackage to.
     *
     * Per MIP-00, a user's KeyPackages SHOULD live on the relays listed in their
     * kind:10051 KeyPackageRelayListEvent. If they haven't published one yet,
     * fall back to their NIP-65 outbox — that's where their other write-oriented
     * events land and it keeps discovery working in the common "just starting out"
     * case.
     */
    fun publishRelaysFor(
        keyPackageRelayList: Collection<NormalizedRelayUrl>,
        myOutbox: Collection<NormalizedRelayUrl>,
    ): Set<NormalizedRelayUrl> = if (keyPackageRelayList.isNotEmpty()) keyPackageRelayList.toSet() else myOutbox.toSet()
}
