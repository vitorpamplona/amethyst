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
package com.vitorpamplona.quartz.nipXXSql

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.store.IEventStore

/** Scans through the store's filters and answers id walks by them, counting the walks. */
open class IdWalkBackend(
    store: IEventStore,
) : FilterStoreBackend(store) {
    var walks = 0

    override suspend fun idsAndTimes(
        spec: ScanSpec,
        onEach: (id: String, createdAt: Long) -> Unit,
    ): Boolean {
        walks++
        events(spec) { onEach(it.id, it.createdAt) }
        return true
    }
}

/**
 * Refuses unselective scans, as an index-only store does. Counts scans a join
 * key made selective, and id walks: a query that reads more than ids and times
 * through a walk gets the wrong rows, which the comparisons catch.
 */
class SelectiveBackend(
    store: IEventStore,
) : FilterStoreBackend(store) {
    var narrowed = 0
    var idWalks = 0

    override fun acceptsScan(spec: ScanSpec) = spec.isSelective

    override suspend fun events(
        spec: ScanSpec,
        onEvent: (Event) -> Unit,
    ) {
        if (!spec.exact && spec.ids != null) narrowed++
        super.events(spec, onEvent)
    }

    override suspend fun idsAndTimes(
        spec: ScanSpec,
        onEach: (id: String, createdAt: Long) -> Unit,
    ): Boolean {
        idWalks++
        super.events(spec) { onEach(it.id, it.createdAt) }
        return true
    }
}
