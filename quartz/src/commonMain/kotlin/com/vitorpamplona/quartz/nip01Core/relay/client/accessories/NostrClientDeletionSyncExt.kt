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
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent

/**
 * Event kinds that carry *deletion intent* — NIP-09 deletion requests (kind 5)
 * and NIP-62 request-to-vanish (kind 62). They are propagation instructions, not
 * content, so a sync should carry them **regardless of its content filter**: a
 * `--kind 1` sync that dropped the kind-5 deleting one of those notes would leave
 * the note un-deleted on the other side forever.
 */
val DELETION_PROPAGATION_KINDS = listOf(DeletionEvent.KIND, RequestToVanishEvent.KIND)

/**
 * Whether this content [Filter] would *exclude* the deletion kinds — i.e. it pins
 * `kinds` and none of them is 5/62. A filter with no `kinds` constraint already
 * matches deletions, so it needs no side-channel. Anything else (a scoped `kinds`
 * list without 5/62) would silently drop deletions and wants
 * [deletionSideChannelFilter].
 */
fun Filter.excludesDeletionKinds(): Boolean {
    val k = kinds ?: return false
    return DELETION_PROPAGATION_KINDS.none { it in k }
}

/**
 * The companion "deletion side-channel" filter for a content [Filter]: kinds 5/62,
 * scoped to the same `authors`. A kind-5/62 only affects its own author's events, so
 * when the content sync is author-scoped the deletions worth reconciling are exactly
 * those authors'. Carries **no** `since`/`until`: a deletion's `created_at` is when it
 * was issued, not when its target was created, so inheriting the content window would
 * drop a recent deletion of an old event (or an old deletion synced late).
 */
fun Filter.deletionSideChannelFilter(): Filter = Filter(kinds = DELETION_PROPAGATION_KINDS, authors = authors)

/**
 * Whether a local deletion-family [event] may be pushed UP to [relay].
 *
 *  - **kind 5** — always. A deletion request is owner-scoped (the relay only removes
 *    the deleting author's own events), so propagating it can never delete a third
 *    party's data; the worst case is a no-op the relay ignores.
 *  - **kind 62** — only when the request actually targets [relay] (its `relay` tags
 *    name that URL, or `ALL_RELAYS`). A vanish triggers a pubkey-wide mass delete on
 *    every relay that ingests it, so we must not fan one out to a relay the author
 *    never named — we honor the author's declared targets, no broader.
 */
fun shouldPropagateDeletionUp(
    event: Event,
    relay: NormalizedRelayUrl,
): Boolean =
    when (event) {
        is RequestToVanishEvent -> event.shouldVanishFrom(relay)
        else -> true
    }

/**
 * Propagates deletion-family events (kinds 5 & 62) between the local set and [relay],
 * **independent of a content sync's [contentFilter]** and **always bidirectional**:
 *
 *  - **down** — deletions the relay has and we lack are handed to [download]; feeding
 *    them into the local store lets NIP-09/62 remove the targets locally too (and its
 *    reject-trigger keeps them from being re-added by a later content sync).
 *  - **up** — deletions we have and the relay lacks are handed to [upload]; publishing
 *    them makes the relay apply the deletion. Kind-62 vanishes are gated by
 *    [shouldPropagateDeletionUp] so one is only sent to a relay it targets.
 *
 * A no-op returning `null` when [contentFilter] already covers kinds 5/62 (an
 * unscoped or already-deletion-including sync carries them itself). Otherwise runs one
 * extra [negentropyReconcile] over [deletionSideChannelFilter]; the set is tiny on any
 * real store, so the cost is a single short reconcile, not a second full sync.
 *
 * The caller owns I/O: [download] fetches + ingests the given ids however it fetches
 * content (REQ-by-id, verify, store), and [upload] publishes one local event. Both
 * suspend the reconcile round that produced them, so the relay is back-pressured.
 *
 * @param localDeletions the local kind-5/62 events — both the reconcile set and the
 *   source the `have` ids resolve against for [upload].
 */
suspend fun INostrClient.negentropyPropagateDeletions(
    relay: NormalizedRelayUrl,
    contentFilter: Filter,
    localDeletions: List<Event>,
    batchSize: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    download: suspend (List<HexKey>) -> Unit,
    upload: suspend (Event) -> Unit,
): NegentropyReconcileResult? {
    if (!contentFilter.excludesDeletionKinds()) return null

    val byId = localDeletions.associateBy { it.id }
    val localEntries = localDeletions.map { IdAndTime(it.createdAt, it.id) }

    return negentropyReconcile(
        relay = relay,
        filter = contentFilter.deletionSideChannelFilter(),
        localEntries = localEntries,
        batchSize = batchSize,
        idleTimeoutMs = idleTimeoutMs,
        onNeedIds = { batch -> download(batch) },
        onHaveIds = { batch ->
            for (id in batch) {
                val event = byId[id] ?: continue
                if (shouldPropagateDeletionUp(event, relay)) upload(event)
            }
        },
    )
}
