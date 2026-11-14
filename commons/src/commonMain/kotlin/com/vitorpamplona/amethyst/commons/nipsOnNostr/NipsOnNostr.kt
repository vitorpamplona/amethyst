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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * NIPs published on Nostr (kind 30817): the ones in the cache, the ones that define a kind (by
 * their `k` tags), and one-shot fetches from relays to fill the cache. Each fetch runs at most
 * once per session per question, since NIPs are few and change rarely; [changes] ticks after
 * each one so screens can re-read the cache.
 */
object NipsOnNostr {
    private val mutex = Mutex()
    private val asked = HashSet<String>()
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    private const val ALL = "all"
    private const val FETCH_LIMIT = 500
    private const val IDLE_TIMEOUT_MS = 6_000L

    /** Every cached NIP, newest first. */
    fun cached(): List<AddressableNote> =
        LocalCache.addressables
            .filterIntoSet(NipTextEvent.KIND) { _, note -> note.event is NipTextEvent }
            .sortedByDescending { it.createdAt() ?: 0L }

    /** The cached NIPs whose `k` tags define [kind], newest first. */
    fun definitionsOf(kind: Int): List<AddressableNote> =
        LocalCache.addressables
            .filterIntoSet(NipTextEvent.KIND) { _, note ->
                val event = note.event as? NipTextEvent
                event != null && definesKind(event.tags, kind)
            }.sortedByDescending { it.createdAt() ?: 0L }

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

    /** Asks the account's relays for every NIP once per session. */
    suspend fun fetchAll(
        account: Account,
        force: Boolean = false,
    ) = fetchOnce(account, ALL, force, Filter(kinds = listOf(NipTextEvent.KIND), limit = FETCH_LIMIT))

    /** Asks the account's relays, once per session, for the NIPs that define [kind]. */
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
        // Before marking the question asked: with no relay list loaded yet, a later call must still ask.
        val relays = account.followPlusAllMineWithSearch.flow.value
        if (relays.isEmpty()) return

        val first = mutex.withLock { asked.add(key) }
        if (!first && !force) return

        val found =
            account.client
                .fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = IDLE_TIMEOUT_MS)
                .filterIsInstance<NipTextEvent>()
        found.forEach { account.cache.justConsume(it, null, false) }
        _changes.value++
    }

    private fun definesKind(
        tags: Array<Array<String>>,
        kind: Int,
    ): Boolean {
        val target = kind.toString()
        for (tag in tags) {
            if (tag.size > 1 && tag[0] == "k" && tag[1].trim() == target) return true
        }
        return false
    }
}
