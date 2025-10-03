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
package com.vitorpamplona.quartz.nip01Core.relay.client

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayPool
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface INostrClient {
    fun relayStatusFlow(): StateFlow<RelayPool.RelayPoolStatus>

    fun connect()

    fun disconnect()

    fun reconnect(
        onlyIfChanged: Boolean = false,
        ignoreRetryDelays: Boolean = false,
    )

    fun isActive(): Boolean

    fun openReqSubscription(
        subId: String = newSubId(),
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    )

    fun openCountSubscription(
        subId: String = newSubId(),
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    )

    fun close(subscriptionId: String)

    fun sendIfExists(
        event: Event,
        connectedRelay: NormalizedRelayUrl,
    )

    fun send(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
    )

    fun subscribe(listener: IRelayClientListener)

    fun unsubscribe(listener: IRelayClientListener)

    fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>?

    fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>?
}

object EmptyNostrClient : INostrClient {
    override fun relayStatusFlow() = MutableStateFlow(RelayPool.RelayPoolStatus())

    override fun connect() { }

    override fun disconnect() { }

    override fun reconnect(
        onlyIfChanged: Boolean,
        ignoreRetryDelays: Boolean,
    ) { }

    override fun isActive() = false

    override fun openReqSubscription(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) { }

    override fun openCountSubscription(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) { }

    override fun close(subscriptionId: String) { }

    override fun sendIfExists(
        event: Event,
        connectedRelay: NormalizedRelayUrl,
    ) { }

    override fun send(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
    ) { }

    override fun subscribe(listener: IRelayClientListener) {}

    override fun unsubscribe(listener: IRelayClientListener) {}

    override fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null
}
