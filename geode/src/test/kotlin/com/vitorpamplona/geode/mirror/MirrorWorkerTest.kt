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

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.geode.testing.publish
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventAssembler
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end `[[mirror]]` behavior over the in-process transport: a
 * downstream relay (verification ON via the parallel-verify queue) dials
 * an upstream and streams its events.
 *
 *  - `trusted = true` — the relay-to-relay trust switch — must land
 *    events the downstream could never verify itself.
 *  - `trusted = false` must keep verify-everything semantics: forged
 *    events from the upstream are dropped, valid ones land.
 */
class MirrorWorkerTest {
    private val upstreamUrl = RelayUrlNormalizer.normalize("ws://upstream.relay/")
    private val downstreamUrl = RelayUrlNormalizer.normalize("ws://downstream.relay/")

    /** Upstream side: no verification (EmptyPolicy), so forged events store fine. */
    private val hub = InProcessRelays()

    /** Downstream side: signature verification on, in the IngestQueue. */
    private val downstreamStore = EventStore(null)
    private val downstream =
        RelayEngine(
            url = downstreamUrl,
            store = downstreamStore,
            parallelVerify = true,
        )

    private var worker: MirrorWorker? = null

    private val signer = KeyPair()

    @After
    fun tearDown() {
        worker?.close()
        downstream.close()
        hub.close()
    }

    private fun startMirror(trusted: Boolean): MirrorWorker =
        MirrorWorker(
            upstreams = listOf(MirrorUpstream(upstreamUrl, trusted = trusted, backfillSeconds = 3600)),
            server = downstream.server,
            websocketBuilder = hub,
        ).also {
            worker = it
            it.start()
        }

    private fun forgedEvent(idSeed: Int): Event =
        Event(
            id = idSeed.toString().padStart(64, '0'),
            pubKey = "1".repeat(64),
            createdAt = TimeUtils.now() - idSeed,
            kind = 1,
            tags = emptyArray(),
            content = "forged $idSeed",
            sig = "f".repeat(128),
        )

    private fun signedEvent(content: String): Event =
        EventAssembler.hashAndSign(
            pubKey = signer.pubKey.toHexKey(),
            createdAt = TimeUtils.now() - 5,
            kind = 1,
            tags = emptyArray(),
            content = content,
            privKey = signer.privKey!!,
        )

    private suspend fun awaitDownstreamCount(expected: Int) =
        withTimeout(15_000) {
            while (downstreamStore.count(Filter()) < expected) delay(25)
        }

    private suspend fun await(condition: suspend () -> Boolean) =
        withTimeout(15_000) {
            while (!condition()) delay(25)
        }

    // NOTE: assertions are on the downstream STORE, not on exact counter
    // values — the client may legitimately re-send its REQ while settling
    // (connect + filter sync), so an upstream can replay an event twice and
    // the duplicate shows up as one extra rejection. That's the documented
    // mirror behavior, not a failure.

    @Test
    fun trustedUpstreamLandsEventsTheDownstreamCannotVerify() =
        runBlocking {
            val stored = forgedEvent(1)
            val live = forgedEvent(2)

            // Stored replay: exists on the upstream before the mirror dials.
            hub.getOrCreate(upstreamUrl).preload(stored)

            startMirror(trusted = true)
            awaitDownstreamCount(1)

            // Live tail: published upstream after the mirror subscribed.
            hub.getOrCreate(upstreamUrl).publish(live)
            awaitDownstreamCount(2)

            val ids = downstreamStore.query<Event>(Filter()).map { it.id }.toSet()
            assertEquals(setOf(stored.id, live.id), ids)
        }

    @Test
    fun untrustedUpstreamStillVerifiesEverything() =
        runBlocking {
            val forged = forgedEvent(1)
            val valid = signedEvent("the real one")
            hub.getOrCreate(upstreamUrl).preload(forged, valid)

            val mirror = startMirror(trusted = false)

            // The valid event lands; the forged one is verified and dropped
            // (a forged event can never land untrusted, so once both have
            // been processed the store can only hold the valid one).
            await { mirror.rejected.get() >= 1 && downstreamStore.count(Filter()) == 1 }

            val stored = downstreamStore.query<Event>(Filter()).single()
            assertEquals(valid.id, stored.id)
            assertTrue(downstreamStore.query<Event>(Filter(ids = listOf(forged.id))).isEmpty())
        }
}
