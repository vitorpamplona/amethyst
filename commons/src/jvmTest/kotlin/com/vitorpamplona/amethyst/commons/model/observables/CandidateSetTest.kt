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
package com.vitorpamplona.amethyst.commons.model.observables

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A family whose feeds are always on starts its set in the background ([CandidateSet.start]). A
 * feed that rebuilds while that first walk is still running must get the walk's result, not
 * `null`: a `null` sends the rebuild to walk the whole cache a second time (seen on every account
 * switch, as `notifications/… from cache walk` right after the account went active).
 */
class CandidateSetTest {
    private val signer = NostrSignerSync(KeyPair())
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")

    // The cache holds notes weakly (LargeSoftCache) and nothing else references these, so a GC
    // during the walk would drop some and the snapshot would come back short.
    private val pinned = mutableListOf<Note>()

    private fun EventCache.consumePinned(event: Event) {
        pinned.add(getOrCreateNote(event.id))
        justConsume(event, relay, true)
    }

    @Test
    fun aRebuildDuringTheBackgroundWalkWaitsForItInsteadOfWalkingAgain() {
        val cache = EventCache()
        repeat(50) { cache.consumePinned(signer.sign(1_000L + it, TextNoteEvent.KIND, emptyArray(), "n$it")) }

        val walkStarted = CountDownLatch(1)
        val slowFilter =
            IFilter {
                walkStarted.countDown()
                Thread.sleep(5)
                true
            }

        val scope = CoroutineScope(SupervisorJob())
        try {
            val set = CandidateSet(cache, scope, slowFilter).start()
            assertTrue(walkStarted.await(5, TimeUnit.SECONDS), "the background walk never started")

            val snapshot = set.snapshot()
            assertNotNull(snapshot, "a rebuild during the first walk got null and would walk the cache again")
            assertEquals(50, snapshot.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun theFirstSnapshotStartsTheSetAndReturnsItsWalk() {
        val cache = EventCache()
        repeat(10) { cache.consumePinned(signer.sign(2_000L + it, TextNoteEvent.KIND, emptyArray(), "m$it")) }

        val scope = CoroutineScope(SupervisorJob())
        try {
            val set = CandidateSet(cache, scope, IFilter { true })
            assertEquals(10, set.snapshot()?.size)
        } finally {
            scope.cancel()
        }
    }
}
