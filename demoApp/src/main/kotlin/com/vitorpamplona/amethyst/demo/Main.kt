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
package com.vitorpamplona.amethyst.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vitorpamplona.amethyst.demo.net.KtorWebSocket
import com.vitorpamplona.amethyst.demo.ui.FeedScreen
import com.vitorpamplona.quartz.nip01Core.cache.interning.InterningEventStore
import com.vitorpamplona.quartz.nip01Core.cache.projection.ProjectionState
import com.vitorpamplona.quartz.nip01Core.cache.projection.project
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.EventCollector
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Layered Quartz stack from the README:
 *
 *   NostrClient → ObservableEventStore → InterningEventStore → EventStore (SQLite)
 *
 * The [collector] is a single connection-level listener that drops
 * every event the [client] receives — across every subscription on
 * every relay — into [db]. UI projections are independent cold flows;
 * each one owns its own `client.subscribe`/`unsubscribe` pair tied to
 * its collection lifecycle.
 */
private class AppGraph(
    val client: NostrClient,
    val db: ObservableEventStore,
    val signer: NostrSignerInternal,
    val collector: EventCollector,
)

private fun buildAppGraph(): AppGraph {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val sqlite = EventStore(dbName = "demo-events.db")
    val interned = InterningEventStore(sqlite)
    val db = ObservableEventStore(interned)

    val client = NostrClient(websocketBuilder = KtorWebSocket.Builder())

    // Connection-wide drain: every EventMessage on every relay lands
    // in the store, regardless of which subscription pulled it.
    val collector =
        EventCollector(client) { event, _ ->
            scope.launch { runCatching { db.insert(event) } }
        }

    // Throwaway identity for the demo. Restart the app -> new keys.
    val signer = NostrSignerInternal(KeyPair())

    return AppGraph(client, db, signer, collector)
}

fun main() =
    application {
        val state = rememberWindowState(size = DpSize(560.dp, 720.dp))
        Window(onCloseRequest = ::exitApplication, state = state, title = "Nostr Kind 1 Demo") {
            MaterialTheme {
                val graph = remember { buildAppGraph() }
                val uiScope = rememberCoroutineScope()

                val relays =
                    remember {
                        setOf(
                            "wss://relay.damus.io".normalizeRelayUrl(),
                            "wss://nos.lol".normalizeRelayUrl(),
                            "wss://relay.nostr.band".normalizeRelayUrl(),
                        )
                    }

                // Cold projection over the local store. While being
                // collected, it holds open one relay subscription for
                // the same filter; cancellation tears it down.
                val projection =
                    remember(graph, relays) {
                        val subId = newSubId()
                        val filter = Filter(kinds = listOf(TextNoteEvent.KIND), limit = 100)
                        graph.db
                            .project<TextNoteEvent>(filter)
                            .onStart { graph.client.subscribe(subId, relays.associateWith { listOf(filter) }) }
                            .onCompletion { graph.client.unsubscribe(subId) }
                    }
                val projectionState by projection.collectAsState(initial = ProjectionState.Loading)

                LaunchedEffect(Unit) { graph.client.connect() }

                FeedScreen(
                    state = projectionState,
                    onSend = { text ->
                        uiScope.launch {
                            val signed = graph.signer.sign<TextNoteEvent>(TextNoteEvent.build(text))
                            // Hits the bus → projection picks it up alongside any inbound relay copy.
                            graph.db.insert(signed)
                            graph.client.publish(signed, relays)
                        }
                    },
                )
            }
        }
    }
