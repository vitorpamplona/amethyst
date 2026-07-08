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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.geode.testing.publish
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcileIds
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `amy sync` deletion rule, end-to-end: for the ids the relay HAS that we LACK
 * (the negentropy need set), if we hold a kind-5 deletion targeting one of them,
 * publish that deletion up so the relay deletes it too — instead of re-downloading a
 * note we deleted. Only these ids, only kind-5, up only; nothing is pulled down or
 * applied locally, so the personal store can never be over-deleted.
 *
 * This exercises the exact wiring `SyncCommand` uses (reconcile → look up the local
 * kind-5 by its `e` tag for the need ids → publish), which the CLI itself has no test
 * harness for.
 */
class DeletionSyncTest : RelayClientTest() {
    private val signer = NostrSignerSync(KeyPair())

    private fun note(text: String): Event = signer.sign(TextNoteEvent.build(text))

    private fun deletionOf(target: Event): Event = signer.sign(DeletionEvent.build(listOf(target), createdAt = target.createdAt + 1))

    /** The SyncCommand step under test: publish local kind-5 deletions targeting [needIds]. */
    private suspend fun sendDeletionsFor(
        local: com.vitorpamplona.geode.RelayEngine,
        needIds: List<String>,
    ): Int {
        var sent = 0
        val mine = local.store.query<Event>(Filter(kinds = listOf(DeletionEvent.KIND), tags = mapOf("e" to needIds)))
        for (del in mine) {
            defaultRelay.publish(del)
            sent++
        }
        return sent
    }

    @Test
    fun sendsDeletionForANeedIdWeDeleted() =
        runBlocking {
            val note = note("delete me")
            val deletion = deletionOf(note)

            // Relay has the note (no deletion).
            defaultRelay.preload(listOf(note))
            assertEquals(1, defaultRelay.store.query<Event>(Filter(ids = listOf(note.id))).size)

            // Local already applied the deletion → it holds only the kind-5, so the
            // note is a "need" (relay has it, we lack it).
            val local = hub.getOrCreate(RelayUrlNormalizer.normalize("ws://local/"))
            local.preload(listOf(note, deletion))
            assertTrue(local.store.query<Event>(Filter(ids = listOf(note.id))).isEmpty(), "local deleted the note")

            val localKind1 = local.store.query<Event>(Filter(kinds = listOf(1))).map { IdAndTime(it.createdAt, it.id) }
            val diff =
                withTimeout(20_000) {
                    client.negentropyReconcileIds(relay = defaultRelayUrl, filter = Filter(kinds = listOf(1)), localEntries = localKind1)
                }
            assertEquals(setOf(note.id), diff.needIds.toSet(), "the deleted note is the only need id")

            val sent = sendDeletionsFor(local, diff.needIds)

            assertEquals(1, sent, "the deletion targeting the need id was sent")
            assertTrue(
                defaultRelay.store.query<Event>(Filter(ids = listOf(note.id))).isEmpty(),
                "relay applied the pushed deletion and removed the note",
            )
        }

    @Test
    fun sendsNothingForANeedIdWeNeverHad() =
        runBlocking {
            // Relay has a note we simply never had and never deleted — a plain download,
            // no deletion to send.
            val other = note("just never had this")
            defaultRelay.preload(listOf(other))

            val local = hub.getOrCreate(RelayUrlNormalizer.normalize("ws://local2/"))
            // Local holds an unrelated deletion (targets a different note) — must NOT be
            // sent for `other`.
            local.preload(listOf(deletionOf(note("unrelated"))))

            val diff =
                withTimeout(20_000) {
                    client.negentropyReconcileIds(relay = defaultRelayUrl, filter = Filter(kinds = listOf(1)), localEntries = emptyList())
                }
            assertTrue(other.id in diff.needIds, "the note is a need id")

            val sent = sendDeletionsFor(local, diff.needIds)

            assertEquals(0, sent, "no deletion targets the need id, so nothing is sent")
            assertEquals(1, defaultRelay.store.query<Event>(Filter(ids = listOf(other.id))).size, "the note is untouched on the relay")
        }
}
