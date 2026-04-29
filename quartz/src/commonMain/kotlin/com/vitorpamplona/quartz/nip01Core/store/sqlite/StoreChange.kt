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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * One atomic batch of mutations that occurred inside the event store.
 *
 * The store emits exactly one [StoreChange] per successfully committed
 * unit of work — `insertEvent`, a `transaction { ... }` block,
 * `delete(...)`, `deleteExpiredEvents()`, or `clearDB()`. Empty changes
 * (e.g. an insert that was rejected by a trigger) are not emitted.
 *
 * `removedIds` covers every row that left `event_headers` during the
 * unit of work, regardless of cause: the supersession triggers for
 * replaceable / addressable events, NIP-09 deletion fan-out, NIP-62
 * vanish cascades, NIP-40 expiration sweeps, and direct `delete(...)`
 * calls. They are captured by an `AFTER DELETE` trigger that writes
 * `OLD.id` into a per-connection TEMP table.
 *
 * `inserted` carries the events that survived the writer and are now
 * in the database. A replaceable / addressable supersession therefore
 * appears as a single change with `inserted = [new]` and
 * `removedIds = [oldId]`.
 */
data class StoreChange(
    val inserted: List<Event>,
    val removedIds: List<HexKey>,
) {
    fun isEmpty() = inserted.isEmpty() && removedIds.isEmpty()

    fun isNotEmpty() = !isEmpty()

    companion object {
        val EMPTY = StoreChange(emptyList(), emptyList())
    }
}
