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
package com.vitorpamplona.quartz.experimental.graperank

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.RankTag
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Publishes a set of GrapeRank scores as NIP-85 kind:30382 [ContactCardEvent]
 * trusted assertions (one `rank` card per scored user), reconciled against what
 * this provider key has already published so a repeat run only writes what moved.
 *
 * Reconciliation, given the desired `(target, rank)` set the scorer produced:
 *  - **skip** a target whose stored card already carries the same rank string —
 *    re-signing an unchanged card would churn a new event id for no client benefit;
 *  - **upsert** a target whose rank changed (or that has no card yet), up to a
 *    publish limit;
 *  - **retract** every stored card whose target is no longer in the desired set
 *    (it fell below the caller's cutoff, or dropped out of the graph) with a NIP-09
 *    kind:5 deletion, batched so the frame stays under the ~64KB event cap.
 *
 * Transport-agnostic like [GrapeRankCrawler]: it reads prior cards from an
 * [IEventStore] and emits through an injected [publish] function (event + relays →
 * per-relay ack), so the store/relay wiring stays in the application while the
 * reconcile + card-construction logic is reusable (e.g. by the Android app).
 */
class GrapeRankPublisher(
    private val store: IEventStore,
    private val publish: suspend (Event, Set<NormalizedRelayUrl>) -> Map<NormalizedRelayUrl, Boolean>,
) {
    /** Outcome counts for one reconcile: what was written, retracted, and skipped. */
    class Result(
        val published: Int,
        val publishRejected: Int,
        val deleted: Int,
        val deleteRejected: Int,
        val skippedUnchanged: Int,
        /** Changed cards beyond [publishLimit] that were not upserted this run. */
        val truncated: Int,
    )

    /**
     * Reconcile the desired [scored] `(target, rank)` set (the caller has already
     * applied any rank cutoff) against the cards [providerPubkey] previously
     * published, then upsert the changes and retract the stale cards, all signed by
     * [providerSigner]. At most [publishLimit] changed cards are upserted per run.
     */
    suspend fun reconcileAndPublish(
        providerSigner: NostrSigner,
        providerPubkey: HexKey,
        scored: List<Pair<HexKey, Int>>,
        relays: Set<NormalizedRelayUrl>,
        publishLimit: Int,
        publishConcurrency: Int = PUBLISH_CONCURRENCY,
    ): Result {
        // Newest card per target this provider already published (read back from
        // the store, which every published card was persisted to).
        val existing = existingCards(providerPubkey)
        val publishableTargets = scored.mapTo(HashSet()) { it.first }

        // Upsert publishable targets whose rank tag STRING would change (or that
        // have no card yet). RankTag.assemble writes rank.toString(), so we diff
        // that exact string — an unchanged score is skipped so clients only sync
        // ranks that moved.
        val changed = scored.filter { (target, rank) -> existing[target]?.let(::rankTagValue) != rank.toString() }
        val toUpsert = changed.take(publishLimit)

        // Retract existing cards whose target is no longer publishable — it dropped
        // out of the graph, or fell below the caller's cutoff. We won't leave a
        // stale assertion standing.
        val toDelete = existing.filterKeys { it !in publishableTargets }.values.toList()

        val (ok, rejected) = publishCards(providerSigner, toUpsert, relays, publishConcurrency)
        val (deleted, deleteRejected) = publishDeletions(providerSigner, toDelete, relays)

        return Result(
            published = ok,
            publishRejected = rejected,
            deleted = deleted,
            deleteRejected = deleteRejected,
            skippedUnchanged = scored.size - changed.size,
            truncated = (changed.size - toUpsert.size).coerceAtLeast(0),
        )
    }

    /**
     * The newest kind:30382 card [providerPubkey] published per target, read from
     * the store (every card [publish] sends is persisted first, so on repeat runs
     * this reflects what is already out there).
     */
    private suspend fun existingCards(providerPubkey: HexKey): Map<HexKey, ContactCardEvent> =
        store
            .query<Event>(Filter(kinds = listOf(ContactCardEvent.KIND), authors = listOf(providerPubkey)))
            .filterIsInstance<ContactCardEvent>()
            .groupBy { it.aboutUser() }
            .mapNotNull { (target, cards) ->
                val t = target.ifBlank { return@mapNotNull null }
                t to (cards.maxByOrNull { it.createdAt } ?: return@mapNotNull null)
            }.toMap()

    /**
     * The raw `rank` tag value string on a card — exactly what a client diffs, so an
     * unchanged score never produces a new signature. Our cards carry only a `rank`
     * tag (plus the d-tag target), so this one value decides whether a re-publish
     * would differ.
     */
    private fun rankTagValue(card: ContactCardEvent): String? =
        card.tags.firstNotNullOfOrNull { tag ->
            if (tag.size > 1 && tag[0] == RankTag.TAG_NAME) tag[1] else null
        }

    /** Build + publish one kind:30382 card per (target, rank), bounded-concurrently. */
    private suspend fun publishCards(
        signer: NostrSigner,
        cards: List<Pair<HexKey, Int>>,
        relays: Set<NormalizedRelayUrl>,
        concurrency: Int,
    ): Pair<Int, Int> {
        var published = 0
        var rejected = 0
        for (batch in cards.chunked(concurrency)) {
            val acks =
                coroutineScope {
                    batch
                        .map { (pubkey, rank) ->
                            async {
                                val card =
                                    ContactCardEvent.create(
                                        targetUser = pubkey,
                                        signer = signer,
                                        publicInitializer = { add(RankTag.assemble(rank)) },
                                    )
                                publish(card, relays)
                            }
                        }.awaitAll()
                }
            for (ack in acks) {
                if (ack.values.any { it }) published++ else rejected++
            }
        }
        return published to rejected
    }

    /**
     * Retract stale cards with NIP-09 kind:5 deletions signed by [signer] (the same
     * key that signed the cards). Batches [DELETE_PER_EVENT] addressable coordinates
     * per deletion so the kind:5 frame stays under the ~64KB event cap; each carries
     * the card's `a` tag (30382:provider:target), so re-publishing a newer version
     * later isn't blocked. Returns (deleted, rejected) card counts.
     */
    private suspend fun publishDeletions(
        signer: NostrSigner,
        cards: List<ContactCardEvent>,
        relays: Set<NormalizedRelayUrl>,
    ): Pair<Int, Int> {
        if (cards.isEmpty()) return 0 to 0
        var deleted = 0
        var rejected = 0
        for (chunk in cards.chunked(DELETE_PER_EVENT)) {
            val event = signer.sign(DeletionEvent.build(chunk))
            val ack = publish(event, relays)
            if (ack.values.any { it }) deleted += chunk.size else rejected += chunk.size
        }
        return deleted to rejected
    }

    companion object {
        /** Concurrent card publishes when upserting. */
        const val PUBLISH_CONCURRENCY = 16

        /**
         * Addressable coordinates cited per kind:5 retraction. Each `a` tag is
         * ~130 bytes (30382:<64hex>:<64hex>), so 400 keeps the whole event ~52KB —
         * under the 64KB event-size cap many relays enforce (stricter than the
         * 256KB message cap).
         */
        const val DELETE_PER_EVENT = 400
    }
}
