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
package com.vitorpamplona.quartz.nip01Core.relay.client.acessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayState
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId

fun NostrClient.downloadFirstEvent(
    subscriptionId: String = newSubId(),
    filters: List<RelayBasedFilter> = listOf(),
    onResponse: (Event) -> Unit,
) {
    subscribe(
        object : IRelayClientListener {
            override fun onEvent(
                relay: IRelayClient,
                subId: String,
                event: Event,
                arrivalTime: Long,
                afterEOSE: Boolean,
            ) {
                if (subId == subscriptionId) {
                    unsubscribe(this)
                    close(subscriptionId)

                    onResponse(event)
                }
            }

            override fun onClosed(
                relay: IRelayClient,
                subId: String,
                message: String,
            ) {
                unsubscribe(this)
                close(subscriptionId)
                super.onClosed(relay, subId, message)
            }

            override fun onEOSE(
                relay: IRelayClient,
                subId: String,
                arrivalTime: Long,
            ) {
                unsubscribe(this)
                close(subscriptionId)
                super.onEOSE(relay, subId, arrivalTime)
            }

            override fun onError(
                relay: IRelayClient,
                subId: String,
                error: Error,
            ) {
                unsubscribe(this)
                close(subscriptionId)
                super.onError(relay, subId, error)
            }

            override fun onRelayStateChange(
                relay: IRelayClient,
                type: RelayState,
            ) {
                if (type == RelayState.DISCONNECTED) {
                    unsubscribe(this)
                    close(subscriptionId)
                }
                super.onRelayStateChange(relay, type)
            }
        },
    )

    sendFilter(subscriptionId, filters)
}
