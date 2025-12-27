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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.feed.FeedHeader
import com.vitorpamplona.amethyst.commons.ui.note.NoteCard
import com.vitorpamplona.amethyst.commons.util.toNoteDisplayData
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent

@Composable
fun FeedScreen(relayManager: DesktopRelayConnectionManager) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val events = remember { mutableStateListOf<Event>() }
    val seenIds = remember { mutableSetOf<String>() }

    // Use relayStatuses.keys (configured relays) to initiate subscription,
    // not connectedRelays (which is empty until subscriptions trigger connections)
    val configuredRelays = relayStatuses.keys

    DisposableEffect(configuredRelays) {
        if (configuredRelays.isNotEmpty()) {
            val subId = "global-feed-${System.currentTimeMillis()}"
            val filters = listOf(
                Filter(
                    kinds = listOf(TextNoteEvent.KIND),
                    limit = 50,
                )
            )

            relayManager.subscribe(
                subId = subId,
                filters = filters,
                relays = configuredRelays,
                listener = object : IRequestListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?
                    ) {
                        if (event.id !in seenIds) {
                            seenIds.add(event.id)
                            events.add(0, event)
                            if (events.size > 200) {
                                val removed = events.removeAt(events.size - 1)
                                seenIds.remove(removed.id)
                            }
                        }
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?
                    ) {
                        // End of stored events
                    }
                }
            )

            onDispose {
                relayManager.unsubscribe(subId)
            }
        } else {
            onDispose { }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FeedHeader(
            title = "Global Feed",
            connectedRelayCount = connectedRelays.size,
            onRefresh = { relayManager.connect() }
        )

        Spacer(Modifier.height(16.dp))

        if (connectedRelays.isEmpty()) {
            LoadingState("Connecting to relays...")
        } else if (events.isEmpty()) {
            LoadingState("Loading notes...")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    // Use NoteCard from commons
                    NoteCard(
                        note = event.toNoteDisplayData()
                    )
                }
            }
        }
    }
}
