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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vitorpamplona.amethyst.demo.data.EventRepository
import com.vitorpamplona.amethyst.demo.net.KtorWebSocket
import com.vitorpamplona.amethyst.demo.ui.FeedScreen
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.sqlite.SQLiteEventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Public relays the demo subscribes to and publishes against. */
private val DEMO_RELAYS =
    setOf(
        "wss://relay.damus.io".normalizeRelayUrl(),
        "wss://nos.lol".normalizeRelayUrl(),
        "wss://relay.nostr.band".normalizeRelayUrl(),
    )

/** Show the latest 100 kind:1 text notes from the firehose. */
private val FEED_FILTER =
    Filter(
        kinds = listOf(TextNoteEvent.KIND),
        limit = 100,
    )

private class AppGraph(
    val notesRepo: EventRepository<TextNoteEvent>,
    val signer: NostrSignerInternal,
    val client: NostrClient,
)

private fun buildAppGraph(): AppGraph {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Throwaway identity for the demo. Restart the app -> new keys.
    val signer = NostrSignerInternal(KeyPair())

    // Quartz's bundled SQLite driver, file-backed in the working directory.
    val store = SQLiteEventStore(dbName = "demo-events.db")

    // Ktor-driven WebSocket transport (see KtorWebSocket.kt).
    val client = NostrClient(websocketBuilder = KtorWebSocket.Builder())

    // Generic repository, narrowed to TextNoteEvent for this demo.
    val notesRepo =
        EventRepository<TextNoteEvent>(
            store = store,
            client = client,
            relays = DEMO_RELAYS,
            scope = scope,
            accept = { it as? TextNoteEvent },
        )

    return AppGraph(notesRepo, signer, client)
}

fun main() =
    application {
        val state = rememberWindowState(size = DpSize(560.dp, 720.dp))
        Window(onCloseRequest = ::exitApplication, state = state, title = "Nostr Kind 1 Demo") {
            MaterialTheme {
                val graph = remember { buildAppGraph() }
                val uiScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    graph.client.connect()
                    graph.notesRepo.observe(FEED_FILTER)
                }

                FeedScreen(
                    notesFlow = graph.notesRepo.events,
                    onSend = { text ->
                        uiScope.launch {
                            val signed = graph.signer.sign<TextNoteEvent>(TextNoteEvent.build(text))
                            graph.notesRepo.publish(signed)
                        }
                    },
                )
            }
        }
    }
