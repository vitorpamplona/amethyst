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
package com.vitorpamplona.amethyst.desktop.subscriptions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Represents an active relay subscription that can be unsubscribed.
 */
data class SubscriptionHandle(
    val subId: String,
    val unsubscribe: () -> Unit,
)

/**
 * Configuration for a relay subscription.
 */
data class SubscriptionConfig(
    val subId: String,
    val filters: List<Filter>,
    val relays: Set<NormalizedRelayUrl>,
    val onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    val onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
)

/**
 * Composable that remembers a subscription and automatically unsubscribes on dispose.
 *
 * Usage:
 * ```kotlin
 * rememberSubscription(relayStatuses, pubKeyHex) {
 *     SubscriptionConfig(
 *         subId = "my-sub-${System.currentTimeMillis()}",
 *         filters = listOf(Filter(kinds = listOf(1), limit = 50)),
 *         relays = relayStatuses.keys,
 *         onEvent = { event, _, _, _ -> events.add(event) }
 *     )
 * }
 * ```
 */
@Composable
fun rememberSubscription(
    vararg keys: Any?,
    relayManager: RelayConnectionManager,
    config: () -> SubscriptionConfig?,
): SubscriptionHandle? {
    val subscription = remember(*keys) { config() }

    DisposableEffect(*keys, subscription?.subId) {
        subscription?.let { cfg ->
            if (cfg.relays.isNotEmpty()) {
                relayManager.subscribe(
                    subId = cfg.subId,
                    filters = cfg.filters,
                    relays = cfg.relays,
                    listener =
                        object : IRequestListener {
                            override fun onEvent(
                                event: Event,
                                isLive: Boolean,
                                relay: NormalizedRelayUrl,
                                forFilters: List<Filter>?,
                            ) {
                                cfg.onEvent(event, isLive, relay, forFilters)
                            }

                            override fun onEose(
                                relay: NormalizedRelayUrl,
                                forFilters: List<Filter>?,
                            ) {
                                cfg.onEose(relay, forFilters)
                            }
                        },
                )
            }
        }

        onDispose {
            subscription?.let { relayManager.unsubscribe(it.subId) }
        }
    }

    return subscription?.let { SubscriptionHandle(it.subId, { relayManager.unsubscribe(it.subId) }) }
}

/**
 * Generates a unique subscription ID with timestamp.
 */
fun generateSubId(prefix: String): String = "$prefix-${System.currentTimeMillis()}"
