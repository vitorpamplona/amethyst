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
package com.vitorpamplona.amethyst.commons.nipsOnNostr

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * NIPs published on Nostr (kind 30817): the ones in the cache, the ones that define a kind (by
 * their `k` tags), and one-shot fetches from relays to fill the cache.
 *
 * Each question (every NIP, the NIPs of one kind) goes to the relays once per session once it has
 * succeeded: NIPs are few and change rarely. A caller asking while the same question is in flight
 * waits for that fetch instead of starting a second one or returning early, and a failed fetch can
 * be asked again. [changes] ticks after each fetch so screens re-read the cache.
 *
 * The fetches ask the account's own relays, its search and index relays, and the relays the client
 * is already connected to (most follows' outboxes, while a feed is open), the set the kind sampler
 * uses. That reaches NIPs whose authors publish off the big public relays without opening a socket
 * to every follow's outbox, hundreds of them, for a handful of events.
 */
object NipsOnNostr {
    private val mutex = Mutex()
    private val done = HashSet<String>()
    private val inFlight = HashMap<String, CompletableDeferred<Unit>>()
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    private const val TAG = "NipsOnNostr"
    private const val ALL = "all"
    private const val FETCH_LIMIT = 500
    private const val IDLE_TIMEOUT_MS = 6_000L

    /** The relays NIP lookups ask: the account's own, its search and index relays, and those already connected. */
    fun discoveryRelays(account: Account): Set<NormalizedRelayUrl> = account.homeRelays.flow.value + account.searchRelays() + account.indexRelays() + account.client.connectedRelaysFlow().value

    /** Every cached NIP, newest first. */
    fun cached(): List<AddressableNote> =
        LocalCache.addressables
            .filterIntoSet(NipTextEvent.KIND) { _, note -> note.event is NipTextEvent }
            .sortedByDescending { it.createdAt() ?: 0L }

    /**
     * The cached NIPs by the kinds their `k` tags define, built in one pass and reused until the
     * next fetch (or [INDEX_TTL_MS], for NIPs that reached the cache some other way). A feed of
     * unknown-kind cards reads it once per card instead of scanning every NIP each time.
     */
    private class KindIndex(
        val version: Int,
        val builtAt: Long,
        val byKind: Map<Int, List<AddressableNote>>,
    )

    private var kindIndex: KindIndex? = null
    private const val INDEX_TTL_MS = 30_000L

    private fun index(): Map<Int, List<AddressableNote>> {
        val now = TimeUtils.now() * 1000
        val current = kindIndex
        if (current != null && current.version == _changes.value && now - current.builtAt < INDEX_TTL_MS) return current.byKind

        val byKind = HashMap<Int, MutableList<AddressableNote>>()
        for (note in cached()) {
            val tags = (note.event as? NipTextEvent)?.tags ?: continue
            val seen = HashSet<Int>()
            for (tag in tags) {
                if (tag.size < 2 || tag[0] != "k") continue
                val kind = tag[1].trim().toIntOrNull() ?: continue
                if (seen.add(kind)) byKind.getOrPut(kind) { ArrayList() }.add(note)
            }
        }
        // A race only builds the same index twice.
        kindIndex = KindIndex(_changes.value, now, byKind)
        return byKind
    }

    /** The cached NIPs whose `k` tags define [kind], newest first. */
    fun definitionsOf(kind: Int): List<AddressableNote> = index()[kind] ?: emptyList()

    /** The name the newest cached NIP defining [kind] gives it in its `k` tag, if any. */
    fun labelOf(kind: Int): String? {
        val target = kind.toString()
        for (note in definitionsOf(kind)) {
            val tags = note.event?.tags ?: continue
            for (tag in tags) {
                if (tag.size > 2 && tag[0] == "k" && tag[1].trim() == target) tag[2].trim().ifBlank { null }?.let { return it }
            }
        }
        return null
    }

    /** Asks the relays for every NIP, once per session unless [force]d. */
    suspend fun fetchAll(
        account: Account,
        force: Boolean = false,
    ) = fetchOnce(account, ALL, force, Filter(kinds = listOf(NipTextEvent.KIND), limit = FETCH_LIMIT))

    /** Asks the relays, once per session, for the NIPs that define [kind]. */
    suspend fun fetchDefinitionsOf(
        account: Account,
        kind: Int,
    ) = fetchOnce(account, "k:$kind", false, Filter(kinds = listOf(NipTextEvent.KIND), tags = mapOf("k" to listOf(kind.toString()))))

    private suspend fun fetchOnce(
        account: Account,
        key: String,
        force: Boolean,
        filter: Filter,
    ) {
        // With no relay list loaded yet there is nothing to ask; a later call still asks.
        val relays = discoveryRelays(account)
        if (relays.isEmpty()) return

        val (job, owner) =
            mutex.withLock {
                if (!force && key in done) return
                val running = inFlight[key]
                if (running != null) {
                    running to false
                } else {
                    CompletableDeferred<Unit>().also { inFlight[key] = it } to true
                }
            }

        if (!owner) {
            job.await()
            return
        }

        try {
            val found =
                account.client
                    .fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = IDLE_TIMEOUT_MS)
                    .filterIsInstance<NipTextEvent>()
            found.forEach { account.cache.justConsume(it, null, false) }
            mutex.withLock { done.add(key) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not marked done: the next caller asks again.
            Log.w(TAG, "Could not fetch NIPs for $key", e)
        } finally {
            mutex.withLock { inFlight.remove(key) }
            _changes.value++
            job.complete(Unit)
        }
    }
}
