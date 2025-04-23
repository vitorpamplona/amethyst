/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand

import android.util.Log
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.datasources.SubscriptionOrchestrator
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates a data source where the filters are derived from
 * data (keys) that is being watched in the UI.
 */
abstract class QueryBasedSubscriptionOrchestrator<T>(
    client: NostrClient,
) : SubscriptionOrchestrator(client) {
    private var queries: ConcurrentHashMap<T, T> = ConcurrentHashMap()

    // This is called by main. Keep it really fast.
    fun subscribe(query: T?) {
        if (query == null) return

        val wasEmpty = queries.isEmpty()

        queries.put(query, query)

        if (wasEmpty) {
            start()
        }

        invalidateFilters()

        if (isDebug) {
            Log.d(this::class.simpleName, "Watch $query (${queries.size} queries)")
        }
    }

    // This is called by main. Keep it really fast.
    fun unsubscribe(query: T?) {
        if (query == null) return

        queries.remove(query)

        invalidateFilters()

        if (queries.isEmpty()) {
            stop()
        }

        if (isDebug) {
            Log.d(this::class.simpleName, "Unwatch $query (${queries.size} queries)")
        }
    }

    fun forEachSubscriber(action: (T) -> Unit) {
        queries.keys.forEach(action)
    }

    final override fun updateSubscriptions() {
        updateSubscriptions(queries.keys)
    }

    abstract fun updateSubscriptions(keys: Set<T>)
}
