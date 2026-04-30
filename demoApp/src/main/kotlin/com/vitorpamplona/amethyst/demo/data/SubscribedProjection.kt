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
package com.vitorpamplona.amethyst.demo.data

import com.vitorpamplona.quartz.nip01Core.cache.projection.ProjectionState
import com.vitorpamplona.quartz.nip01Core.cache.projection.project
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * A [project] flow that drives a relay subscription for the same
 * [filter] for as long as the projection is being collected.
 *
 *  - On `collect`: call [client] `subscribe`, then start emitting the
 *    cold projection.
 *  - On cancel / completion / error: `unsubscribe`.
 *
 * The relay-side cache-write path lives separately in
 * [com.vitorpamplona.quartz.nip01Core.relay.client.accessories.EventCollector],
 * so events keep landing in the store regardless of which projection
 * happens to be alive.
 *
 * Pairs with `stateIn(scope, SharingStarted.WhileSubscribed(...))` —
 * once the last UI subscriber goes away, the relay subscription is
 * released; a new subscriber re-opens it.
 */
fun <T : Event> ObservableEventStore.projectFromRelays(
    client: NostrClient,
    relays: Set<NormalizedRelayUrl>,
    filter: Filter,
): Flow<ProjectionState<T>> {
    val subId = newSubId()
    return project<T>(filter)
        .onStart { client.subscribe(subId, relays.associateWith { listOf(filter) }) }
        .onCompletion { client.unsubscribe(subId) }
}
