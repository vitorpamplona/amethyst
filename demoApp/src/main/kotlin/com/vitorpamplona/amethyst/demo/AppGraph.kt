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

import com.vitorpamplona.amethyst.demo.net.KtorWebSocket
import com.vitorpamplona.quartz.nip01Core.cache.interning.InterningEventStore
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.EventCollector
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * Process-singleton wiring of the layered Quartz stack:
 *
 *   NostrClient → ObservableEventStore → InterningEventStore → EventStore (SQLite)
 *
 * Lazily initialised on first reference (typically `AppGraph.db` from
 * `main()`), so the SQLite handle, the connection-level [collector],
 * and the periodic NIP-40 sweep are all process-scoped — not tied to
 * any UI lifecycle.
 *
 * [signer] is `var` so the app can swap in a real signer when the
 * user logs in. The default is a guest identity that lasts only for
 * this process.
 */
object AppGraph {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    /** Throwaway guest identity. Reassign on login to swap in a real signer. */
    var signer: NostrSigner = NostrSignerInternal(KeyPair())

    init {
        // Periodic NIP-40 sweep — drops expired events from SQLite and
        // emits StoreChange.DeleteExpired so live projections drop them
        // too. Without this the on-disk store grows monotonically.
        scope.launch {
            while (isActive) {
                delay(15.minutes)
                runCatching { db.deleteExpiredEvents() }
            }
        }
    }
}
