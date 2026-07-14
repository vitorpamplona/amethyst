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
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropyStoreSync
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirmDetailed
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.RankTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

/**
 * Persists a set of GrapeRank scores as NIP-85 kind:30382 [ContactCardEvent]
 * trusted assertions in the local [IEventStore] — the durable, reusable form of a
 * score run — and pushes that local card set out to the operator's relays on
 * demand. The store is the source of truth; the two halves are separable:
 *
 *  - [reconcileLocal] runs after every scoring pass. It diffs the desired
 *    `(target, rank)` set against the cards this provider key already holds in the
 *    store, signs + inserts only what moved (an unchanged rank is skipped so no new
 *    event id churns), and retracts every card whose target dropped out with a
 *    NIP-09 kind:5. The store applies kind:5 on insert, so a retracted card
 *    disappears locally while the deletion event remains as the durable tombstone
 *    to propagate.
 *  - [syncToRelays] is pure transport: a NIP-77 up-only reconcile of everything the
 *    provider key authored (kind:30382 cards + kind:5 retractions) against each
 *    relay, so the relay converges to the local set — nothing is re-signed, and a
 *    card the relay lost (or never had) is restored. A relay that can't reconcile
 *    gets the full local set blast-published instead (relays dedup by id).
 */
class GrapeRankPublisher(
    private val store: IEventStore,
    private val log: (String) -> Unit = {},
) {
    /** Outcome of one [reconcileLocal]: what was signed, retracted, and left alone. */
    class LocalResult(
        /** New or rank-changed cards signed and inserted into the store. */
        val signed: Int,
        /** Stale cards retracted (kind:5) because their target left the desired set. */
        val retracted: Int,
        /** Desired cards whose stored rank already matched — no new signature. */
        val unchanged: Int,
    )

    /**
     * Reconcile the desired [scored] `(target, rank)` set (the caller has already
     * applied any rank cutoff) into the local store, signed by [providerSigner]:
     * upsert the changed cards, retract the stale ones, skip the unchanged rest.
     * Every card and retraction is stamped [createdAt], so a replacement is
     * strictly newer than what it displaces.
     */
    suspend fun reconcileLocal(
        providerSigner: NostrSigner,
        providerPubkey: HexKey,
        scored: List<Pair<HexKey, Int>>,
        createdAt: Long = TimeUtils.now(),
    ): LocalResult {
        val existing = existingCards(providerPubkey)
        val desiredTargets = scored.mapTo(HashSet()) { it.first }

        // Upsert targets whose rank tag STRING would change (or that have no card
        // yet). RankTag.assemble writes rank.toString(), so we diff that exact
        // string — an unchanged score never produces a new signature.
        val changed = scored.filter { (target, rank) -> existing[target]?.let(::rankTagValue) != rank.toString() }

        // Retract cards whose target is no longer desired — it dropped out of the
        // graph, or fell below the caller's cutoff. No stale assertion is left standing.
        val toRetract = existing.filterKeys { it !in desiredTargets }.values.toList()

        val signed = signAndInsertCards(providerSigner, changed, createdAt)
        val retracted = insertRetractions(providerSigner, toRetract, createdAt)

        return LocalResult(
            signed = signed,
            retracted = retracted,
            unchanged = scored.size - changed.size,
        )
    }

    /** Per-relay outcome of a [syncToRelays]. */
    class RelaySyncResult(
        val relay: NormalizedRelayUrl,
        /** Events the NIP-77 reconcile found missing on the relay and uploaded. */
        val uploaded: Int,
        /** Events blast-published because the relay couldn't reconcile. */
        val fallbackPublished: Int,
        val fallbackRejected: Int,
        /** The negentropy failure that triggered the fallback, or null when it reconciled. */
        val error: String?,
    ) {
        /** True when the relay now converged to the local set by either path. */
        val ok: Boolean get() = error == null || (fallbackPublished > 0 && fallbackRejected == 0)
    }

    /** Aggregate outcome of a [syncToRelays]. */
    class SyncResult(
        /** kind:30382 cards this provider key holds locally (the set being mirrored). */
        val cards: Int,
        /** kind:5 retraction events this provider key holds locally. */
        val deletions: Int,
        val perRelay: List<RelaySyncResult>,
    )

    /**
     * Make every relay in [relays] converge to the local card set of
     * [providerPubkey]: one NIP-77 up-only reconcile per relay over the provider's
     * kind:30382 + kind:5 (nothing is downloaded — the store is the source of
     * truth), falling back to blast-publishing the full local set when a relay
     * can't reconcile. Best-effort per relay; a failure never aborts the others.
     */
    suspend fun syncToRelays(
        client: INostrClient,
        providerPubkey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        relayConcurrency: Int = 4,
        idleTimeoutMs: Long = 30_000L,
        publishTimeoutSecs: Long = 15,
    ): SyncResult {
        val cards = store.query<Event>(Filter(kinds = listOf(ContactCardEvent.KIND), authors = listOf(providerPubkey)))
        val deletions = store.query<Event>(Filter(kinds = listOf(DeletionEvent.KIND), authors = listOf(providerPubkey)))

        val filter = Filter(kinds = listOf(ContactCardEvent.KIND, DeletionEvent.KIND), authors = listOf(providerPubkey))
        val groups =
            NegentropyStoreSync(
                client = client,
                store = store,
                config =
                    NegentropyStoreSync.Config(
                        // Up-only: the relay must converge to the store, never the
                        // reverse — pulling a stale card back down would resurrect a
                        // locally-retracted assertion. The kind:5s ride the same
                        // filter, so deletions propagate as ordinary uploads.
                        down = false,
                        up = true,
                        syncDeletions = false,
                        // The paged fallback DOWNLOADS the filter — wrong direction
                        // for a push. Failed groups get [blastPublish] instead.
                        pageFallback = false,
                        concurrency = relayConcurrency,
                        idleTimeoutMs = idleTimeoutMs,
                        publishTimeoutSecs = publishTimeoutSecs,
                    ),
                log = log,
            ).sync(relays.associateWith { listOf(filter) })

        val perRelay =
            groups.map { g ->
                if (g.error == null) {
                    RelaySyncResult(g.relay, uploaded = g.uploaded, fallbackPublished = 0, fallbackRejected = 0, error = null)
                } else {
                    log("[graperank] ${g.relay.url}: negentropy failed (${g.error}) — publishing the full local set instead")
                    val (ok, rejected) = blastPublish(client, cards + deletions, g.relay, publishTimeoutSecs)
                    RelaySyncResult(g.relay, uploaded = g.uploaded, fallbackPublished = ok, fallbackRejected = rejected, error = g.error)
                }
            }

        return SyncResult(cards = cards.size, deletions = deletions.size, perRelay = perRelay)
    }

    /**
     * The newest kind:30382 card [providerPubkey] holds per target in the local
     * store. Locally-retracted cards never show up — inserting their kind:5
     * removed them — so a target can be re-carded later without fighting a ghost.
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
     * tag (plus the d-tag target), so this one value decides whether a re-sign
     * would differ.
     */
    private fun rankTagValue(card: ContactCardEvent): String? =
        card.tags.firstNotNullOfOrNull { tag ->
            if (tag.size > 1 && tag[0] == RankTag.TAG_NAME) tag[1] else null
        }

    /**
     * Build + sign one kind:30382 card per (target, rank) — fanned out on
     * [Dispatchers.Default], since id-hash + Schnorr sign is CPU-bound — and insert
     * each into the store. Batched so a whole-network card set never materializes
     * in memory at once. Returns how many the store accepted.
     */
    private suspend fun signAndInsertCards(
        signer: NostrSigner,
        cards: List<Pair<HexKey, Int>>,
        createdAt: Long,
    ): Int {
        if (cards.isEmpty()) return 0
        var inserted = 0
        for (batch in cards.chunked(SIGN_BATCH)) {
            val signedBatch =
                coroutineScope {
                    batch
                        .map { (target, rank) ->
                            async(Dispatchers.Default) {
                                ContactCardEvent.create(
                                    targetUser = target,
                                    signer = signer,
                                    createdAt = createdAt,
                                    publicInitializer = { add(RankTag.assemble(rank)) },
                                )
                            }
                        }.awaitAll()
                }
            for (card in signedBatch) {
                if (insertQuietly(card)) inserted++
            }
        }
        return inserted
    }

    /**
     * Retract stale cards with NIP-09 kind:5 deletions signed by [signer] (the same
     * key that signed the cards), inserted into the store — which applies them, so
     * the retracted cards vanish locally and only the tombstone remains to sync.
     * Batches [DELETE_PER_EVENT] addressable coordinates per deletion so the kind:5
     * frame stays under the ~64KB event cap; each carries the card's `a` tag
     * (30382:provider:target), so re-publishing a newer version later isn't blocked.
     * Returns how many cards were retracted.
     */
    private suspend fun insertRetractions(
        signer: NostrSigner,
        cards: List<ContactCardEvent>,
        createdAt: Long,
    ): Int {
        if (cards.isEmpty()) return 0
        var retracted = 0
        for (chunk in cards.chunked(DELETE_PER_EVENT)) {
            val deletion = signer.sign(DeletionEvent.build(chunk, createdAt))
            if (insertQuietly(deletion)) retracted += chunk.size
        }
        return retracted
    }

    /**
     * Insert without letting a single-row failure abort the whole reconcile. The
     * one expected rejection is the store's own deletion guard (a re-carded target
     * whose kind:5 landed in the same second); anything else is logged. Returns
     * whether the store accepted the event.
     */
    private suspend fun insertQuietly(event: Event): Boolean =
        try {
            store.insert(event)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("[graperank] store rejected ${event.kind}/${event.id.take(8)}: ${e.message}")
            false
        }

    /**
     * Fallback for a relay without working NIP-77: publish every event of the local
     * set individually (the relay dedups by id), bounded-concurrently. Returns
     * (accepted, rejected) event counts.
     */
    private suspend fun blastPublish(
        client: INostrClient,
        events: List<Event>,
        relay: NormalizedRelayUrl,
        publishTimeoutSecs: Long,
    ): Pair<Int, Int> {
        var ok = 0
        var rejected = 0
        val relaySet = setOf(relay)
        for (batch in events.chunked(PUBLISH_CONCURRENCY)) {
            val acks =
                coroutineScope {
                    batch.map { event -> async { client.publishAndConfirmDetailed(event, relaySet, publishTimeoutSecs) } }.awaitAll()
                }
            for (ack in acks) {
                if (ack.values.any { it }) ok++ else rejected++
            }
        }
        return ok to rejected
    }

    companion object {
        /** Cards signed per batch — bounds live event objects, not parallelism. */
        const val SIGN_BATCH = 1024

        /** Concurrent per-event publishes in the [blastPublish] fallback. */
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
