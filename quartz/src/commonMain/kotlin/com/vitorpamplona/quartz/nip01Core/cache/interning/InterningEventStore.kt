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
package com.vitorpamplona.quartz.nip01Core.cache.interning

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore

/**
 * Decorator that canonicalises every [Event] returned by the inner
 * store through an [EventInterner], so all consumers of read paths
 * share a single object reference per event id.
 *
 * Wrap any [IEventStore] (SQLite, FS, in-memory test fake) when you
 * want event-identity sharing across the read paths:
 *
 * ```
 * val sqlite = EventStore(...)
 * val cached = InterningEventStore(sqlite)
 * val observable = ObservableEventStore(cached)
 * ```
 *
 * Writes (`insert`, `transaction`, `delete*`) pass through unchanged
 * — the decorator never substitutes the caller's event for a cached
 * one on the way in (sigs may differ across "same id, different
 * decode" cases). Canonicalisation only happens on results coming
 * back out of the store.
 *
 * The default [interner] is [EventInterner.Default]; pass a fresh
 * instance for tests or any context that needs isolation.
 */
class InterningEventStore(
    private val inner: IEventStore,
    private val interner: EventInterner = EventInterner.Default,
) : IEventStore {
    override val relay: NormalizedRelayUrl? get() = inner.relay

    override suspend fun insert(event: Event) = inner.insert(event)

    override suspend fun transaction(body: IEventStore.ITransaction.() -> Unit) = inner.transaction(body)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Event> query(filter: Filter): List<T> = inner.query<T>(filter).map { interner.intern(it) as T }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Event> query(filters: List<Filter>): List<T> = inner.query<T>(filters).map { interner.intern(it) as T }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Event> query(
        filter: Filter,
        onEach: (T) -> Unit,
    ) = inner.query<T>(filter) { onEach(interner.intern(it) as T) }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Event> query(
        filters: List<Filter>,
        onEach: (T) -> Unit,
    ) = inner.query<T>(filters) { onEach(interner.intern(it) as T) }

    override suspend fun count(filter: Filter): Int = inner.count(filter)

    override suspend fun count(filters: List<Filter>): Int = inner.count(filters)

    override suspend fun delete(filter: Filter) = inner.delete(filter)

    override suspend fun delete(filters: List<Filter>) = inner.delete(filters)

    override suspend fun deleteExpiredEvents() = inner.deleteExpiredEvents()

    override fun close() = inner.close()
}
