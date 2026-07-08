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
package com.vitorpamplona.geode.mirror

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A kind-scoped mirror (`filter = {kinds:[1]}`) must still propagate the kind-5/62
 * that delete those notes — otherwise the deletion is stuck upstream and the note
 * lives forever on the mirror. [MirrorWorker]'s deletion side-channel reconciles
 * kinds 5/62 on their own, in the mirror's configured direction, independent of
 * the operator filter.
 */
class MirrorDeletionSyncTest {
    private val upstreamStore = EventStore(null)
    private val downstreamStore = EventStore(null)

    private val upstream = RelayEngine(url = "ws://127.0.0.1:7896/".normalizeRelayUrl(), store = upstreamStore)
    private val downstream =
        RelayEngine(url = "ws://127.0.0.1:7897/".normalizeRelayUrl(), store = downstreamStore, parallelVerify = true)

    private var server: KtorRelay? = null
    private var worker: MirrorWorker? = null

    @AfterTest
    fun tearDown() {
        worker?.close()
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        upstream.close()
        downstream.close()
    }

    @Test
    fun scopedDownMirrorPropagatesDeletion() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            val note = signer.sign(TextNoteEvent.build("delete me"))
            val deletion = signer.sign(DeletionEvent.build(listOf(note), createdAt = note.createdAt + 1))

            // Upstream already applied the deletion → it holds only the kind-5.
            upstreamStore.insert(note)
            upstreamStore.insert(deletion)
            assertEquals(0, upstreamStore.count(Filter(ids = listOf(note.id))), "upstream removed the note")

            // Downstream (the mirror) still holds the note, no deletion.
            downstreamStore.insert(note)
            assertEquals(1, downstreamStore.count(Filter(ids = listOf(note.id))), "mirror starts with the note")

            server = KtorRelay(upstream, host = "127.0.0.1", port = 7896).start()

            worker =
                MirrorWorker(
                    upstreams =
                        listOf(
                            MirrorUpstream(
                                url = "ws://127.0.0.1:7896/".normalizeRelayUrl(),
                                trusted = true,
                                backfillSeconds = 86_400,
                                // Scoped to kind 1 — would drop the kind-5 without the side-channel.
                                filter = Filter(kinds = listOf(1)),
                            ),
                        ),
                    server = downstream.server,
                    store = downstreamStore,
                    negentropyBackfill = true,
                ).also { it.start() }

            val gone =
                withTimeoutOrNull(30_000) {
                    while (downstreamStore.count(Filter(ids = listOf(note.id))) > 0) delay(200)
                    true
                }

            assertTrue(gone == true, "scoped down mirror did not propagate the deletion")
            assertEquals(
                1,
                downstreamStore.count(Filter(kinds = listOf(DeletionEvent.KIND))),
                "mirror ingested the deletion event itself",
            )
        }
}
