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
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.AuthOutcome
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.authSuccessMark
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.awaitAuthOutcome
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Downloads [batch] ids and returns the matching events on `EOSE`/close/timeout.
 * All events for a single relay arrive on its one reader thread, so collecting
 * here needs no synchronisation.
 *
 * Usually one `REQ`, but two kinds of relay make it more:
 *
 *  - **A relay that caps every `REQ`, ids included.** Some answer a 500-id `REQ`
 *    with their page size (100, 200), `limit` or not, then send `EOSE` as if that
 *    were all. So after an `EOSE` that left
 *    ids unserved, the ones still missing are asked for again, for as long as
 *    each round brings something new. A round that brings nothing ends it: those
 *    ids are ones the relay will not serve (it names them in a reconcile but
 *    cannot load them, or deleted them since).
 *  - **A relay that refuses an ids-only filter** as a "wildcard subscription", and
 *    serves the same ids once the `REQ` names the sync filter's `kinds`. So the `REQ` carries [reconciledFrom]'s `kinds`, which
 *    every id a reconcile of [reconciledFrom] named matches. A filter with no kinds may hold
 *    a long `authors` list, too heavy to repeat on every batch, so its ids go out
 *    bare and its `authors` and tags are added only to re-ask a `CLOSED` batch.
 *
 * An `auth-required:` refusal is re-asked once, after this client's NIP-42 responder
 * (if it has one) authenticates.
 *
 * A dropped connection is re-asked too, a few times, after a short pause: some relays close
 * the socket once several large downloads stream at once (one `REQ` at a time never trips
 * them), which used to lose every batch in flight. Batches that share a [pace] also stop
 * overlapping from the first drop on; a relay that never drops keeps all of them.
 *
 * Events are deduped across the rounds (a [HashSet] bounded by the batch size, so
 * still O(pipeline) memory). A REQ-by-ids should return each id once, but the client
 * may re-send the REQ on a reconnect/filter-sync mid-flight, which makes the relay
 * replay the batch; without this the same event would be delivered twice. We rely on
 * NIP-77 yielding a distinct id set across batches, so no global dedup is needed.
 */
suspend fun INostrClient.fetchByIds(
    relay: NormalizedRelayUrl,
    batch: List<HexKey>,
    idleTimeoutMs: Long,
    reconciledFrom: Filter? = null,
    pace: DownloadPace? = null,
): List<Event> {
    val collected = ArrayList<Event>(batch.size)
    // Ids asked for and not yet received. An event the relay sends that we did not ask
    // for is not collected: it would stop the rounds early and skew the caller's walk.
    val pending = batch.toHashSet()
    val seen = HashSet<HexKey>(batch.size)
    var missing = batch

    var widened = false
    var authed = false
    var drops = 0
    // A batch on its own still pauses and gives up the same way; it just learns alone.
    val pacing = pace ?: DownloadPace()
    val batchIdleMs = if (idleTimeoutMs > 0) idleTimeoutMs else DEFAULT_DOWNLOAD_IDLE_MS

    while (true) {
        val before = collected.size
        val authMark = authSuccessMark(relay)
        val filter =
            when {
                widened -> Filter(ids = missing, authors = reconciledFrom?.authors, kinds = reconciledFrom?.kinds, tags = reconciledFrom?.tags, tagsAll = reconciledFrom?.tagsAll)
                else -> Filter(ids = missing, kinds = reconciledFrom?.kinds)
            }
        if (pacing.gaveUp) break
        val end = pacing.turn { fetchByIdsRound(relay, filter, batchIdleMs, pending, seen, collected) }
        pacing.record(end)
        Log.d("negentropySync") { "${relay.url} asked for ${missing.size} ids, got ${collected.size - before}: $end" }
        when (end) {
            RoundEnd.EOSE -> {
                if (collected.size == before) break
            }

            RoundEnd.AUTH_REQUIRED -> {
                if (authed || awaitAuthOutcome(relay, authMark, settleMs = batchIdleMs) != AuthOutcome.AUTHENTICATED) break
                authed = true
            }

            RoundEnd.DROPPED -> {
                pacing.dropped()
                if (++drops > MAX_DOWNLOAD_DROPS) break
                pacing.pauseAfterDrop(drops)
            }

            RoundEnd.CLOSED -> {
                if (widened || !reconciledFrom.narrowsBeyondKinds()) break
                widened = true
            }

            RoundEnd.IDLE -> {
                break
            }
        }
        if (collected.size >= batch.size) break
        missing = missing.filter { it !in seen }
        if (missing.isEmpty()) break
    }
    return collected
}

private fun Filter?.narrowsBeyondKinds() = this != null && (authors != null || tags != null || tagsAll != null)

/**
 * Shared by the [fetchByIds] batches of one sync against one connection: they run side by
 * side until the relay drops the connection under them, and one at a time from then on.
 * After [MAX_FAILED_ROUNDS] rounds in a row, across batches, dropped or went silent with no
 * answered round between them, it gives up: a relay that refuses the connection (an HTTP 503
 * at the upgrade puts it in backoff for minutes) would otherwise cost every queued batch a
 * full idle window, one after another.
 *
 * @param pause how a dropped round waits before it is asked again, given the milliseconds;
 *   a test passes one that returns at once.
 */
@OptIn(ExperimentalAtomicApi::class)
class DownloadPace(
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    @Volatile
    private var oneAtATime = false

    @Volatile
    private var exhausted = false

    private val failedInARow = AtomicInt(0)
    private val mutex = Mutex()

    /** False once the relay dropped the connection under overlapping downloads. */
    val overlapping: Boolean get() = !oneAtATime

    /** True once too many rounds in a row failed; later rounds are not asked. */
    val gaveUp: Boolean get() = exhausted

    internal suspend fun <T> turn(round: suspend () -> T): T = if (oneAtATime) mutex.withLock { round() } else round()

    internal fun dropped() {
        oneAtATime = true
    }

    internal suspend fun pauseAfterDrop(drops: Int) = pause(DROP_PAUSE_MS * drops)

    internal fun record(end: RoundEnd) {
        when (end) {
            RoundEnd.DROPPED, RoundEnd.IDLE -> if (failedInARow.incrementAndFetch() >= MAX_FAILED_ROUNDS) exhausted = true
            else -> failedInARow.store(0)
        }
    }
}

internal enum class RoundEnd { EOSE, AUTH_REQUIRED, CLOSED, DROPPED, IDLE }

/**
 * One `REQ` of [fetchByIds]: adds what arrives to [collected] and says how the relay
 * ended it. [batchIdleMs] is always finite, even when the caller disabled the
 * whole-sync watchdog: each event resets the clock, so a batch that keeps streaming
 * is never cut off, but one that stalls (relay stops mid-flight) unblocks instead of
 * hanging a worker.
 */
private suspend fun INostrClient.fetchByIdsRound(
    relay: NormalizedRelayUrl,
    filter: Filter,
    batchIdleMs: Long,
    pending: Set<HexKey>,
    seen: HashSet<HexKey>,
    collected: ArrayList<Event>,
): RoundEnd {
    val subId = newSubId()
    val done = Channel<RoundEnd>(Channel.CONFLATED)
    val clock = IdleClock()

    val listener =
        object : SubscriptionListener {
            override suspend fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                clock.bump()
                if (event.id in pending && seen.add(event.id)) collected.add(event)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                done.trySend(RoundEnd.EOSE)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                Log.d("negentropySync") { "${relay.url} closed a ${filter.ids?.size}-id download: $message" }
                val authRequired = MachineReadablePrefix.parse(message) == MachineReadablePrefix.AUTH_REQUIRED
                done.trySend(if (authRequired) RoundEnd.AUTH_REQUIRED else RoundEnd.CLOSED)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                Log.d("negentropySync") { "${relay.url} dropped a ${filter.ids?.size}-id download: $message" }
                done.trySend(RoundEnd.DROPPED)
            }
        }

    try {
        subscribe(subId, mapOf(relay to listOf(filter)), listener)
        return done.receiveWithinIdle(clock, batchIdleMs) ?: RoundEnd.IDLE
    } finally {
        unsubscribe(subId)
        done.close()
    }
}

/** Times one [fetchByIds] batch is re-asked after the relay dropped the connection under it. */
private const val MAX_DOWNLOAD_DROPS = 3

/** Pause before re-asking a dropped batch, times the drops so far. */
private const val DROP_PAUSE_MS = 1_000L

/** Download rounds in a row, across a sync's batches, that may drop or go silent before it gives up. */
private const val MAX_FAILED_ROUNDS = 6
