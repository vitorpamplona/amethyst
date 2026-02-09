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
package com.vitorpamplona.quartz.nip01Core.relay.client.reqs

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.RandomInstance

class NostrClientStaticReq(
    val client: INostrClient,
    val filter: Map<NormalizedRelayUrl, List<Filter>>,
    val onEvent: (event: Event) -> Unit = {},
) : IRequestListener,
    IOpenNostrRequest {
    val subId = RandomInstance.randomChars(10)

    override fun onEvent(
        event: Event,
        isLive: Boolean,
        relay: NormalizedRelayUrl,
        forFilters: List<Filter>?,
    ) {
        onEvent(event)
    }

    /**
     * Creates or Updates the filter with relays. This method should be called
     * everytime the filter changes.
     */
    override fun updateFilter() = client.openReqSubscription(subId, filter, this)

    override fun close() = client.close(subId)

    init {
        updateFilter()
    }
}

fun INostrClient.req(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    onEvent: (event: Event) -> Unit = {},
): IOpenNostrRequest = NostrClientStaticReq(this, mapOf(relay to filters), onEvent)

fun INostrClient.req(
    relay: NormalizedRelayUrl,
    filter: Filter,
    onEvent: (event: Event) -> Unit = {},
): IOpenNostrRequest = NostrClientStaticReq(this, mapOf(relay to listOf(filter)), onEvent)

fun INostrClient.req(
    relays: List<NormalizedRelayUrl>,
    filters: List<Filter>,
    onEvent: (event: Event) -> Unit = {},
): IOpenNostrRequest = NostrClientStaticReq(this, relays.associateWith { filters }, onEvent)

fun INostrClient.req(
    relays: List<NormalizedRelayUrl>,
    filter: Filter,
    onEvent: (event: Event) -> Unit = {},
): IOpenNostrRequest = NostrClientStaticReq(this, relays.associateWith { listOf(filter) }, onEvent)

// -----------------------------------
// Helper methods with relay as string
// -----------------------------------
fun INostrClient.req(
    relay: String,
    filters: List<Filter>,
    onEvent: (event: Event) -> Unit = {},
): IOpenNostrRequest = NostrClientStaticReq(this, mapOf(RelayUrlNormalizer.normalize(relay) to filters), onEvent)

fun INostrClient.req(
    relay: String,
    filter: Filter,
    onEvent: (event: Event) -> Unit = {},
): IOpenNostrRequest = NostrClientStaticReq(this, mapOf(RelayUrlNormalizer.normalize(relay) to listOf(filter)), onEvent)
