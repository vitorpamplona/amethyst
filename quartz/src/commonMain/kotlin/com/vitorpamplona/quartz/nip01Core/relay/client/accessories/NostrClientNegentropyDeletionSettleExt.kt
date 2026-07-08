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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.deletionsCovering
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent

/**
 * Outcome of a [negentropySettleDeletions] run.
 *
 * @property sentUp      distinct local deletions published to the relay (up direction).
 * @property appliedDown distinct relay deletions ingested into [store] (down direction).
 * @property rounds      reconcile rounds run before convergence (or the cap).
 */
class DeletionSettleResult(
    val sentUp: Int,
    val appliedDown: Int,
    val rounds: Int,
)

/**
 * Converge deletions between [store] and [relay] AFTER a content sync has settled the
 * two sides — the second half of a two-pass sync. NIP-77 reconciles by id, so a plain
 * content sync converges everything except events a deletion physically stops from
 * moving; those survive as the reconcile's residual, which this resolves:
 *
 *  - **[sendUp]** — a residual **need** (relay has it, [store] still lacks it after the
 *    content pass tried to download it) means we deleted it. Publish OUR covering
 *    deletion up ([IEventStore.deletionsCovering]) so the relay drops it.
 *  - **[applyDown]** — a residual **have** ([store] has it, relay still lacks it after
 *    the content pass tried to upload it) means the relay deleted it. Pull the RELAY'S
 *    covering **kind-5** down and ingest it, so [store] drops it too. A NIP-62 vanish is
 *    deliberately NOT applied on pull — its blast radius is the author's whole account.
 *
 * Because it works off the residual — not every id — the cost is one cheap reconcile
 * per round plus the (small) residual, independent of database size. It loops until a
 * round resolves nothing (converged, and thereby self-verified) or [maxRounds] is hit.
 *
 * **Direction requires the matching content pass.** A residual need is a clean signal
 * only after the content sync attempted the download ([sendUp] pairs with a `--down`
 * content pass); a residual have only after it attempted the upload ([applyDown] pairs
 * with `--up`). Passing a direction whose content pass didn't run makes its residual the
 * full unsettled set, not a deletion signal — so drive this with the same directions the
 * content pass used.
 *
 * Best-effort: a reconcile failure ([NegentropySyncException]) stops the loop and returns
 * what already settled rather than throwing — the content sync is the primary work.
 *
 * @param batchSize   ids per reconcile chunk and per by-id fetch.
 * @param idleTimeoutMs idle watchdog for the reconciles and fetches.
 * @param maxRounds   hard cap on rounds; the "resolved nothing" check usually stops first.
 * @param reconcileConcurrency overlapped `created_at`-window reconciles after an over-cap split.
 */
suspend fun INostrClient.negentropySettleDeletions(
    relay: NormalizedRelayUrl,
    filter: Filter,
    store: IEventStore,
    sendUp: Boolean,
    applyDown: Boolean,
    batchSize: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    maxRounds: Int = 4,
    reconcileConcurrency: Int = 1,
): DeletionSettleResult {
    if ((!sendUp && !applyDown) || maxRounds <= 0) return DeletionSettleResult(0, 0, 0)

    val publishTimeoutSecs = (idleTimeoutMs / 1000).coerceAtLeast(1)
    val sentUp = HashSet<HexKey>()
    val appliedDown = HashSet<HexKey>()
    var rounds = 0

    while (rounds < maxRounds) {
        rounds++
        val diff =
            try {
                negentropyReconcileIds(
                    relay = relay,
                    filter = filter,
                    localEntries = store.snapshotIdsForNegentropy(listOf(filter)),
                    batchSize = batchSize,
                    idleTimeoutMs = idleTimeoutMs,
                    reconcileConcurrency = reconcileConcurrency,
                )
            } catch (e: NegentropySyncException) {
                break
            }

        var resolved = 0

        // residual needs → publish our covering deletions up.
        if (sendUp) {
            for (chunk in diff.needIds.chunked(batchSize)) {
                val events = fetchAll(relay, Filter(ids = chunk), idleTimeoutMs)
                for (del in store.deletionsCovering(events, relay)) {
                    if (sentUp.add(del.id)) {
                        if (publishAndConfirm(del, setOf(relay), publishTimeoutSecs)) resolved++
                    }
                }
            }
        }

        // residual haves → ingest the relay's covering kind-5 (never a vanish).
        if (applyDown) {
            for (chunk in diff.haveIds.chunked(batchSize)) {
                val ours = store.query<Event>(Filter(ids = chunk))
                val relayDeletions = deletionsCovering(ours, relay) { f -> fetchAll(relay, f, idleTimeoutMs) }
                for (del in relayDeletions.filterIsInstance<DeletionEvent>()) {
                    if (del.verify() && appliedDown.add(del.id)) {
                        store.insert(del)
                        resolved++
                    }
                }
            }
        }

        if (resolved == 0) break
    }

    return DeletionSettleResult(sentUp.size, appliedDown.size, rounds)
}
