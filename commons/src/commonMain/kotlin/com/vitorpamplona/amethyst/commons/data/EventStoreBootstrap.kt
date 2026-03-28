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
package com.vitorpamplona.amethyst.commons.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.utils.Log

/**
 * Loads events from the SQLite [IEventStore] into memory on cold start.
 *
 * This runs once at app launch, before relay connections are established,
 * so the UI can display cached data immediately. Each event is fed through
 * a consumer callback (typically [LocalCache.justConsumeMyOwnEvent]) so
 * the in-memory caches, indexes, and observable flows are populated exactly
 * as if the events had arrived from a relay.
 */
class EventStoreBootstrap(
    private val store: IEventStore,
) {
    /**
     * Loads events matching the given [filters] from SQLite and feeds each
     * one through [consumer]. Returns the total number of events loaded.
     *
     * @param filters   Filters selecting which events to load (e.g., user's
     *                  own replaceable events, recent feed, deletion events).
     * @param consumer  Callback that ingests each event into memory caches.
     *                  Should NOT re-enqueue events to the [AsyncEventWriter]
     *                  since they are already persisted.
     */
    fun loadInto(
        filters: List<Filter>,
        consumer: (Event) -> Unit,
    ): Int {
        var count = 0
        for (filter in filters) {
            try {
                store.query<Event>(filter) { event ->
                    consumer(event)
                    count++
                }
            } catch (e: Exception) {
                Log.e("EventStoreBootstrap", "Failed to load filter $filter: ${e.message}", e)
            }
        }
        Log.d("EventStoreBootstrap", "Loaded $count events from local store")
        return count
    }

    /**
     * Queries the store for a single event by ID. Useful for on-demand
     * cache-miss recovery when a Note has been garbage-collected from
     * the in-memory [LargeSoftCache].
     */
    fun queryById(id: HexKey): Event? =
        try {
            val results: List<Event> = store.query(Filter(ids = listOf(id), limit = 1))
            results.firstOrNull()
        } catch (e: Exception) {
            Log.e("EventStoreBootstrap", "Failed to query event $id: ${e.message}", e)
            null
        }

    /**
     * Queries the store with an arbitrary filter. Useful for on-demand
     * lookups when the memory cache doesn't have the data.
     */
    fun <T : Event> query(filter: Filter): List<T> =
        try {
            store.query(filter)
        } catch (e: Exception) {
            Log.e("EventStoreBootstrap", "Failed to query: ${e.message}", e)
            emptyList()
        }
}
