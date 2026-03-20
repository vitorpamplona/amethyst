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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isEphemeral
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory [EventStore] implementation.
 *
 * - Regular events are stored by id.
 * - Replaceable events (kinds 0, 3, 10000-19999) keep only the newest per
 *   (pubKey, kind) pair.
 * - Addressable events (kinds 30000-39999) keep only the newest per
 *   (pubKey, kind, d-tag) triple.
 * - Ephemeral events (kinds 20000-29999) are broadcast but not persisted.
 */
class InMemoryEventStore : EventStore {
    private val mutex = Mutex()

    /** All non-ephemeral events indexed by id. */
    private val eventsById = LinkedHashMap<HexKey, Event>()

    /** Newest replaceable event per "pubKey:kind". */
    private val replaceableIndex = HashMap<String, HexKey>()

    /** Newest addressable event per "pubKey:kind:dTag". */
    private val addressableIndex = HashMap<String, HexKey>()

    private val _newEvents = MutableSharedFlow<Event>(extraBufferCapacity = 256)
    override val newEvents: SharedFlow<Event> = _newEvents.asSharedFlow()

    override suspend fun store(event: Event): Boolean {
        if (event.kind.isEphemeral()) {
            _newEvents.emit(event)
            return true
        }

        val accepted =
            mutex.withLock {
                if (eventsById.containsKey(event.id)) return@withLock false

                when {
                    event.kind.isReplaceable() -> {
                        storeReplaceable(event)
                    }

                    event.kind.isAddressable() -> {
                        storeAddressable(event)
                    }

                    else -> {
                        eventsById[event.id] = event
                        true
                    }
                }
            }

        if (accepted) {
            _newEvents.emit(event)
        }
        return accepted
    }

    /** Must be called while holding [mutex]. */
    private fun storeReplaceable(event: Event): Boolean {
        val key = "${event.pubKey}:${event.kind}"
        val existingId = replaceableIndex[key]
        if (existingId != null) {
            val existing = eventsById[existingId]
            if (existing != null && existing.createdAt >= event.createdAt) return false
            eventsById.remove(existingId)
        }
        eventsById[event.id] = event
        replaceableIndex[key] = event.id
        return true
    }

    /** Must be called while holding [mutex]. */
    private fun storeAddressable(event: Event): Boolean {
        val dTag = event.tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""
        val key = "${event.pubKey}:${event.kind}:$dTag"
        val existingId = addressableIndex[key]
        if (existingId != null) {
            val existing = eventsById[existingId]
            if (existing != null && existing.createdAt >= event.createdAt) return false
            eventsById.remove(existingId)
        }
        eventsById[event.id] = event
        addressableIndex[key] = event.id
        return true
    }

    override suspend fun query(filter: Filter): List<Event> =
        mutex.withLock {
            val matched =
                eventsById.values
                    .filter { filter.match(it) }
                    .sortedByDescending { it.createdAt }

            if (filter.limit != null) matched.take(filter.limit) else matched
        }

    override suspend fun count(filter: Filter): Int =
        mutex.withLock {
            eventsById.values.count { filter.match(it) }
        }
}
