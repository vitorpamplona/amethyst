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
 * The deletion kinds in [deletionKinds] that this content [Filter] does NOT already
 * carry — i.e. the ones a side-channel still needs to reconcile. Empty when the filter
 * has no `kinds` constraint (it already matches every deletion kind) or already lists
 * all of them. NIP-77 reconciles strictly by the filter's `kinds`, so listing kind 5
 * does NOT cover kind 62: each is checked independently.
 */
fun Filter.missingDeletionKinds(deletionKinds: List<Int> = DELETION_PROPAGATION_KINDS): List<Int> {
    val k = kinds ?: return emptyList()
    return deletionKinds.filter { it !in k }
}

/**
 * Whether the content [Filter] would *exclude* at least one [deletionKinds], so a
 * side-channel is needed. A filter with no `kinds` constraint matches every deletion
 * kind (returns false); a scoped filter that omits kind 5 and/or 62 returns true.
 */
fun Filter.excludesDeletionKinds(deletionKinds: List<Int> = DELETION_PROPAGATION_KINDS): Boolean = missingDeletionKinds(deletionKinds).isNotEmpty()

/**
 * The companion "deletion side-channel" filter for a content [Filter]: the deletion
 * kinds the filter doesn't already carry, scoped to [authors]. A kind-5/62 only affects
 * its own author's events, so the deletions worth reconciling are exactly those
 * authors' — and [authors] MUST be bounded (the content filter's authors, or, for a
 * personal store, the authors actually held locally). An empty/`null` [authors] here
 * means "every author on the relay", which for a personal store would pull the relay's
 * ENTIRE deletion history and apply it locally — callers must not do that.
 *
 * Carries **no** `since`/`until`: a deletion's `created_at` is when it was issued, not
 * when its target was created, so inheriting the content window would drop a recent
 * deletion of an old event (or an old deletion synced late).
 */
fun Filter.deletionSideChannelFilter(
    authors: List<HexKey>? = this.authors,
    deletionKinds: List<Int> = DELETION_PROPAGATION_KINDS,
): Filter = Filter(kinds = missingDeletionKinds(deletionKinds), authors = authors)

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
 * A no-op returning `null` when [contentFilter] already covers every [deletionKinds],
 * or when [scopeAuthors] is empty (nothing to scope — an unscopeable deletion set must
 * not be reconciled, or it would pull the relay's entire deletion history).
 *
 * **Scope is the caller's responsibility.** [scopeAuthors] bounds the reconcile — it
 * MUST be a bounded author set (the content filter's authors, or the authors actually
 * held locally). It defaults to `contentFilter.authors`, so an author-less content
 * filter yields an EMPTY scope → this returns null rather than reconcile everything.
 *
 * The caller owns I/O: [download] fetches + ingests the given ids however it fetches
 * content (REQ-by-id, verify, store), and [upload] publishes one local event. Both
 * suspend the reconcile round that produced them, so the relay is back-pressured.
 *
 * @param localDeletions the local deletion events (in [deletionKinds]) — both the
 *   reconcile set and the source the `have` ids resolve against for [upload].
 * @param scopeAuthors   the authors to bound the deletion reconcile to. Empty/`null`
 *   disables the side-channel (see above).
 * @param deletionKinds  which deletion kinds to propagate (default 5 & 62). Pass `[5]`
 *   to propagate only precise NIP-09 deletions and skip account-wide NIP-62 vanishes.
 */
suspend fun INostrClient.negentropyPropagateDeletions(
    relay: NormalizedRelayUrl,
    contentFilter: Filter,
    localDeletions: List<Event>,
    scopeAuthors: List<HexKey>? = contentFilter.authors,
    deletionKinds: List<Int> = DELETION_PROPAGATION_KINDS,
    batchSize: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    download: suspend (List<HexKey>) -> Unit,
    upload: suspend (Event) -> Unit,
): NegentropyReconcileResult? {
    if (scopeAuthors.isNullOrEmpty()) return null
    if (!contentFilter.excludesDeletionKinds(deletionKinds)) return null

    val byId = localDeletions.associateBy { it.id }
    val localEntries = localDeletions.map { IdAndTime(it.createdAt, it.id) }

    return negentropyReconcile(
        relay = relay,
        filter = contentFilter.deletionSideChannelFilter(authors = scopeAuthors, deletionKinds = deletionKinds),
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
