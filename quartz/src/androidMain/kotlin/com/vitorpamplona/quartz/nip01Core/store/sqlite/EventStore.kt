/**
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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import android.content.Context
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

class EventStore(
    context: Context,
    dbName: String? = "events.db",
    val relayUrl: String? = "wss://quartz.local",
) {
    val store = SQLiteEventStore(context, dbName, relayUrl)

    fun insert(event: Event) = store.insertEvent(event)

    fun query(filter: Filter) = store.query(filter)

    fun query(filters: List<Filter>) = store.query(filters)

    fun query(
        filter: Filter,
        onEach: (Event) -> Unit,
    ) = store.query(filter, onEach)

    fun query(
        filters: List<Filter>,
        onEach: (Event) -> Unit,
    ) = store.query(filters, onEach)

    fun count(filter: Filter) = store.count(filter)

    fun count(filters: List<Filter>) = store.count(filters)

    fun delete(filter: Filter) = store.delete(filter)

    fun delete(filters: List<Filter>) = store.delete(filters)

    fun deleteExpiredEvents() = store.deleteExpiredEvents()

    fun close() = store.close()
}
