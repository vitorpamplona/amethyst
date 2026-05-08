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
package com.vitorpamplona.amethyst.desktop.search

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.search.RelayUserSearchDelegate
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.generateSubId
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DesktopRelayUserSearchDelegate(
    private val relayManager: RelayConnectionManager,
    private val localCache: DesktopLocalCache,
    private val searchRelays: () -> Set<NormalizedRelayUrl>,
    private val scope: CoroutineScope,
) : RelayUserSearchDelegate {
    private var currentSubId: String? = null

    override fun searchPeople(
        query: String,
        limit: Int,
        onResult: (User) -> Unit,
        onComplete: () -> Unit,
    ): Job =
        scope.launch {
            // Cancel previous subscription
            currentSubId?.let { relayManager.unsubscribe(it) }

            val relays = searchRelays()
            if (relays.isEmpty()) {
                onComplete()
                return@launch
            }

            val subId = generateSubId("author-search")
            currentSubId = subId

            relayManager.subscribe(
                subId = subId,
                filters = listOf(FilterBuilders.searchPeople(query, limit)),
                relays = relays,
                listener =
                    object : SubscriptionListener {
                        override fun onEvent(
                            event: Event,
                            isLive: Boolean,
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            if (event is MetadataEvent) {
                                localCache.consumeMetadata(event)
                                val user = localCache.getUserIfExists(event.pubKey)
                                if (user != null) {
                                    onResult(user)
                                }
                            }
                        }

                        override fun onEose(
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            onComplete()
                        }
                    },
            )

            // Timeout after 8 seconds
            delay(8000)
            relayManager.unsubscribe(subId)
            onComplete()
        }
}
