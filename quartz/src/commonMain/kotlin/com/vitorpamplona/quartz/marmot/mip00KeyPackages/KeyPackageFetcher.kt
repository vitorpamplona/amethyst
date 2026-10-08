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
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
     * Drain every kind:30443 KeyPackage event for [targetPubKey] across [relays]
     * and return the most recently published one (highest `created_at`), or
     * `null` if nothing arrived before the timeout.
     *
     * No validation and no legacy kind:443: this answers "which of the
     * account's own 30443 publications is newest", which is what
     * `latestKeyPackageOwner` asks. To pick a KeyPackage to INVITE someone
     * with, use [fetchKeyPackageForInvite].
     *
     * Returning the newest is important after a KeyPackage rotation: a relay
     * that missed the replacement may still hold the prior KP of a slot, and
     * other slots hold other KPs. `fetchFirst` would race the relays and pick
     * whichever replied first, which would frequently be the older event.
     * Draining to EOSE and selecting by `created_at` matches MDK/whitenoise
     * semantics and keeps freshly-rotated bundles reachable.
     *
     * Draining to EOSE is bounded by [settleAfterFirstMs], though. The relay
     * set unions the invitee's relays with ours, and one of them that never
     * sends EOSE held every invite for the whole [idleTimeoutMs] — adding a
     * member took most of a minute. Once a KeyPackage has arrived, the other
     * relays get [settleAfterFirstMs] to report a newer one and the fetch
     * stops. A relay slower than that can only cost us a rotation that
     * happened in the last moments, and the older package still opens.
     */
    suspend fun fetchKeyPackage(
        client: INostrClient,
        targetPubKey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        idleTimeoutMs: Long = 30_000,
        settleAfterFirstMs: Long = 3_000,
    ): KeyPackageEvent? {
        if (relays.isEmpty()) return null
        return drain<KeyPackageEvent>(client, MarmotFilters.keyPackagesByAuthor(targetPubKey), targetPubKey, relays, idleTimeoutMs, settleAfterFirstMs) { true }
            .maxByOrNull { it.createdAt }
    }

    /**
     * Find the KeyPackage to invite [targetPubKey] with.
     *
     * Queries both KeyPackage kinds ([MarmotFilters.keyPackagesMigration],
     * `{kinds: [30443, 443]}`) and lets [KeyPackageUtils.selectForInvite]
     * choose: the newest VALID kind 30443, else the newest valid legacy
     * kind 443. White Noise's MDK still publishes 443, and some of its users
     * publish nothing else; querying 30443 alone left them uninvitable.
     *
     * Unlike [fetchKeyPackage], an invalid candidate is never returned: the
     * newest event that fails validation is skipped for an older one that
     * passes, and null means "nothing usable", not "nothing found".
     *
     * Only a kind 30443 starts the [settleAfterFirstMs] cut-off: MIP-00 says a client MUST prefer a
     * valid 30443, so a fast relay's legacy 443 must not end the drain before a slower relay's
     * 30443 arrives. When only 443s show up, the drain runs until the relays go idle.
     */
    suspend fun fetchKeyPackageForInvite(
        client: INostrClient,
        targetPubKey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        idleTimeoutMs: Long = 30_000,
        settleAfterFirstMs: Long = 3_000,
        nowSeconds: Long = TimeUtils.now(),
    ): PublishedKeyPackage? {
        if (relays.isEmpty()) return null
        val found = drain<PublishedKeyPackage>(client, MarmotFilters.keyPackagesMigration(targetPubKey), targetPubKey, relays, idleTimeoutMs, settleAfterFirstMs) { it is KeyPackageEvent }
        return KeyPackageUtils.selectForInvite(found, targetPubKey, nowSeconds)
    }

    /**
     * Collect every [T] signed by [author] that the [filter] returns from [relays], stopping
     * [settleAfterFirstMs] after the first one that [startsSettle] arrives or when every relay
     * went idle for [idleTimeoutMs].
     *
     * The event signature is checked here because nothing upstream does: the fetch hook hands over
     * raw relay events, and [KeyPackageUtils.selectForInvite] trusts `pubKey`. For a legacy 443,
     * which carries no identity proof, the Nostr signature is the only thing binding the key
     * package to [author]; without it a relay could serve its own package under the target's
     * pubkey and receive the Welcome.
     */
    private suspend inline fun <reified T : PublishedKeyPackage> drain(
        client: INostrClient,
        filter: Filter,
        author: HexKey,
        relays: Set<NormalizedRelayUrl>,
        idleTimeoutMs: Long,
        settleAfterFirstMs: Long,
        crossinline startsSettle: (T) -> Boolean,
    ): List<T> {
        // Collected from inside onEvent (single-threaded) rather than read from the
        // return value, which a cancelled fetch discards.
        val found = mutableListOf<T>()
        val firstArrived = CompletableDeferred<Unit>()
        coroutineScope {
            val fetch =
                launch {
                    client.fetchAllWithHooks(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = idleTimeoutMs) { _, event ->
                        if (event is T && event.pubKey == author && event.verify()) {
                            found.add(event)
                            if (startsSettle(event)) firstArrived.complete(Unit)
                        }
                        true
                    }
                }
            val settle =
                launch {
                    firstArrived.await()
                    delay(settleAfterFirstMs)
                    fetch.cancel()
                }
            fetch.join()
            settle.cancel()
        }
        return found
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
