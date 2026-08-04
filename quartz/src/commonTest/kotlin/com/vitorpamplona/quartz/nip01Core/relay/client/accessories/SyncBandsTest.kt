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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A paged relay has no memory of what it already sent, so without a band every
 * restart re-downloads its whole corpus. These pin the band arithmetic and, more
 * importantly, the cases where a band must NOT be used — a stale band silently
 * skips events, which is a worse failure than re-reading them.
 */
class SyncBandsTest {
    private val relay = RelayUrlNormalizer.normalize("wss://relay.example")
    private val other = RelayUrlNormalizer.normalize("wss://other.example")
    private val profiles = Filter(kinds = listOf(0))

    private fun now(): Long = TimeUtils.now()

    // ---- the band arithmetic ----------------------------------------------

    @Test
    fun `with nothing recorded the whole filter is fetched`() {
        val c = SyncBands()
        assertEquals(listOf(profiles), c.legs(relay, profiles))
    }

    @Test
    fun `a recorded band is fetched around rather than through`() {
        val c = SyncBands()
        c.record(relay, profiles, observedMin = 1_700_001_000L, observedMax = 1_700_002_000L, paged = true)

        val legs = c.legs(relay, profiles)
        assertEquals(2, legs.size, "one leg older than the band and one newer")
        assertEquals(1_700_001_000L, legs[0].until, "older leg stops AT the band floor")
        assertNull(legs[0].since, "and reaches as far back as the filter allows")
        assertEquals(1_700_002_000L, legs[1].since, "newer leg starts AT its ceiling")
        assertNull(legs[1].until)
    }

    @Test
    fun `an event sharing the band boundary second is still reachable`() {
        // A paged relay cuts pages by count, so a boundary can fall inside a run
        // of events sharing one created_at. Excluding the edge would strand the
        // rest of that second in no leg at all, while the band called it covered.
        val c = SyncBands()
        c.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)

        val legs = c.legs(relay, profiles)

        fun reachable(t: Long) = legs.any { (it.since ?: Long.MIN_VALUE) <= t && t <= (it.until ?: Long.MAX_VALUE) }

        assertTrue(reachable(1_700_001_000L), "the band floor second must be re-read")
        assertTrue(reachable(1_700_002_000L), "and its ceiling second")
        assertTrue(reachable(1_700_000_999L), "below the band")
        assertTrue(reachable(1_700_002_001L), "above it")
        // Only the interior is skipped, which is the entire point.
        assertTrue(!reachable(1_700_001_500L), "the covered interior is not re-read")
    }

    @Test
    fun `successive runs widen the band rather than replacing it`() {
        val c = SyncBands()
        c.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)
        // A later run reaches further back and picks up newer events.
        c.record(relay, profiles, 1_700_000_500L, 1_700_002_500L, paged = true)

        val band = c.band(relay, profiles)!!
        assertEquals(1_700_000_500L, band.minCreatedAt)
        assertEquals(1_700_002_500L, band.maxCreatedAt)
    }

    @Test
    fun `a capped relay walks further back on each run`() {
        // The case that makes this worth having: a relay that only ever answers
        // with its newest N events. Each run starts below the last one's floor.
        val c = SyncBands()
        c.record(relay, profiles, 1_700_009_000L, 1_700_010_000L, paged = true)
        assertEquals(1_700_009_000L, c.legs(relay, profiles)[0].until)

        c.record(relay, profiles, 1_700_008_000L, 1_700_008_999L, paged = true)
        assertEquals(1_700_008_000L, c.legs(relay, profiles)[0].until)
    }

    // ---- when a band must not be used --------------------------------------

    @Test
    fun `a negentropy sync that reported no outcome records nothing`() {
        // Only a sync that says how far it reconciled earns a band; a bare
        // paged=false call carries no claim to record.
        val c = SyncBands()
        c.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = false)
        assertNull(c.band(relay, profiles))
        assertEquals(listOf(profiles), c.legs(relay, profiles))
    }

    // ---- coverage: what a finished reconcile earns -------------------------

    @Test
    fun `a finished reconcile is in sync through the instant it started`() {
        // Not through the newest event it happened to see: "the relay had nothing
        // newer" and "we never asked" must not record the same thing.
        val c = SyncBands()
        val startedAt = now() - 60
        c.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = false, reconciledThrough = startedAt)

        val band = c.band(relay, profiles)!!
        assertTrue(band.complete)
        assertEquals(startedAt, band.maxCreatedAt)
    }

    @Test
    fun `a reconcile that downloaded nothing still records coverage`() {
        // The empty case is the WHOLE point: nothing came back because we already
        // have it, and that is exactly when the next run should ask for a sliver.
        val c = SyncBands()
        val startedAt = now() - 60
        c.record(relay, profiles, null, null, paged = false, reconciledThrough = startedAt)

        val leg = c.legs(relay, profiles).single()
        assertEquals(startedAt, leg.since)
        assertNull(leg.until)
    }

    @Test
    fun `a complete band drops its older leg while a paged one keeps it`() {
        val reconciled = SyncBands()
        reconciled.record(relay, profiles, null, null, paged = false, reconciledThrough = 1_700_002_000L)
        val only = reconciled.legs(relay, profiles).single()
        assertEquals(1_700_002_000L, only.since)

        val walked = SyncBands()
        walked.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)
        assertEquals(2, walked.legs(relay, profiles).size, "a paged walk says nothing about what it never asked for")
    }

    // ---- the periodic full re-walk -----------------------------------------

    @Test
    fun `a band stops narrowing once it is older than the resync period`() {
        val c = SyncBands(fullResyncSeconds = 60)
        c.record(relay, profiles, null, null, paged = false, reconciledThrough = now() - 3600)
        // Recorded 'now' whatever the created_at claim, so age it by rewriting.
        c.record(relay, profiles, null, null, paged = false, reconciledThrough = now())
        assertEquals(1, c.legs(relay, profiles).size, "fresh band still narrows")

        val stale = SyncBands(fullResyncSeconds = 0)
        stale.record(relay, profiles, null, null, paged = false, reconciledThrough = now())
        assertSame(profiles, stale.legs(relay, profiles).single(), "a band past its period re-walks everything")
    }

    @Test
    fun `the re-walk replaces the old claim instead of widening it`() {
        // Widening would carry the stale band's floor forward forever and the
        // periodic pass would never actually reset anything.
        val c = SyncBands(fullResyncSeconds = 0)
        c.record(relay, profiles, 1_700_000_000L, 1_700_001_000L, paged = true)
        c.record(relay, profiles, 1_700_005_000L, 1_700_006_000L, paged = true)

        val band = c.band(relay, profiles)!!
        assertEquals(1_700_005_000L, band.minCreatedAt, "the second pass walked everything; its span is the whole picture")
    }

    // ---- the shared snapshot window ----------------------------------------

    @Test
    fun `covering window collapses to the oldest ceiling once everyone is caught up`() {
        val c = SyncBands()
        c.record(relay, profiles, null, null, paged = false, reconciledThrough = 1_700_009_000L)
        c.record(other, profiles, null, null, paged = false, reconciledThrough = 1_700_003_000L)

        assertEquals(1_700_003_000L, c.coveringWindow(listOf(relay, other), profiles).since)
    }

    @Test
    fun `one relay that has never synced puts the window back to the whole filter`() {
        // It genuinely needs everything — narrowing the shared snapshot would
        // reconcile it against ids we never looked up.
        val c = SyncBands()
        c.record(relay, profiles, null, null, paged = false, reconciledThrough = 1_700_009_000L)

        // The filter itself, unnarrowed — identity, since Filter has no equals.
        assertSame(profiles, c.coveringWindow(listOf(relay, other), profiles))
        assertSame(profiles, c.coveringWindow(emptyList(), profiles))
    }

    @Test
    fun `one shared window serves a whole stream of relays`() {
        // Every url in a stream shares that stream's filter, so a backfill can
        // take ONE snapshot for all of them instead of walking the identical
        // range once per relay for byte-identical answers.
        val c = SyncBands()
        val third = RelayUrlNormalizer.normalize("wss://third.example")
        c.record(relay, profiles, null, null, paged = false, reconciledThrough = 1_700_009_000L)
        c.record(other, profiles, null, null, paged = false, reconciledThrough = 1_700_003_000L)
        c.record(third, profiles, null, null, paged = false, reconciledThrough = 1_700_007_000L)

        // The hungriest of them sets the floor; the other two re-read a little.
        assertEquals(1_700_003_000L, c.coveringWindow(listOf(relay, other, third), profiles).since)
    }

    @Test
    fun `a relay with an older gap also widens the shared window`() {
        val c = SyncBands()
        c.record(relay, profiles, null, null, paged = false, reconciledThrough = 1_700_009_000L)
        c.record(other, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)

        assertSame(profiles, c.coveringWindow(listOf(relay, other), profiles))
    }

    @Test
    fun `an empty fetch records nothing`() {
        // No events says nothing about what the relay holds, only that this
        // window was empty — recording it would fabricate coverage.
        val c = SyncBands()
        c.record(relay, profiles, null, null, paged = true)
        assertNull(c.band(relay, profiles))
    }

    @Test
    fun `one misdated event does not cost a relay its whole band`() {
        // A single future-dated stamp among hundreds of thousands must not fail
        // a check applied to the aggregate. Screening per event keeps the rest.
        val c = SyncBands()
        val far = now() + 400L * 86_400
        val observed = listOf(1_700_001_000L, far, 1_700_002_000L, 0L)

        val plausible = observed.filter { SyncBands.isPlausible(it) }
        c.record(relay, profiles, plausible.min(), plausible.max(), paged = true)

        val band = c.band(relay, profiles)!!
        assertEquals(1_700_001_000L, band.minCreatedAt)
        assertEquals(1_700_002_000L, band.maxCreatedAt)
    }

    @Test
    fun `changing the filter starts over`() {
        val c = SyncBands()
        c.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)

        // Widening the kinds means the old band skipped events it never fetched.
        val wider = Filter(kinds = listOf(0, 10002))
        assertEquals(listOf(wider), c.legs(relay, wider), "a new filter has no band")
        assertNull(c.band(relay, wider))
        // ...and the original is untouched, so reverting resumes where it was.
        assertEquals(1_700_001_000L, c.band(relay, profiles)!!.minCreatedAt)
    }

    @Test
    fun `each relay keeps its own band`() {
        val c = SyncBands()
        c.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)
        assertEquals(listOf(profiles), c.legs(other, profiles))
    }

    // ---- the filter's own bounds still win ---------------------------------

    @Test
    fun `a bounded filter never widens past its own since and until`() {
        val bounded = Filter(kinds = listOf(0), since = 1_700_001_000L, until = 1_700_005_000L)
        val c = SyncBands()
        c.record(relay, bounded, 1_700_002_000L, 1_700_003_000L, paged = true)

        val legs = c.legs(relay, bounded)
        assertEquals(2, legs.size)
        assertEquals(1_700_001_000L, legs[0].since, "the older leg keeps the configured floor")
        assertEquals(1_700_002_000L, legs[0].until)
        assertEquals(1_700_003_000L, legs[1].since)
        assertEquals(1_700_005_000L, legs[1].until, "the newer leg keeps the configured ceiling")
    }

    @Test
    fun `a fully covered bounded filter re-reads only its two edge seconds`() {
        // Inclusive edges mean "covered" can never quite mean "ask for nothing":
        // the two boundary seconds are always re-read, because that is the only
        // way to catch a run of same-second events a page boundary cut in half.
        val bounded = Filter(kinds = listOf(0), since = 1_700_001_000L, until = 1_700_005_000L)
        val c = SyncBands()
        c.record(relay, bounded, 1_700_001_000L, 1_700_005_000L, paged = true)

        val legs = c.legs(relay, bounded)
        assertEquals(2, legs.size)
        assertEquals(1_700_001_000L to 1_700_001_000L, legs[0].since to legs[0].until, "the floor second only")
        assertEquals(1_700_005_000L to 1_700_005_000L, legs[1].since to legs[1].until, "the ceiling second only")
    }

    // ---- persistence hooks --------------------------------------------------

    @Test
    fun `export and restore round-trip the bands`() {
        val c = SyncBands()
        c.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)

        val reopened = SyncBands()
        reopened.restore(c.export())

        val band = reopened.band(relay, profiles)!!
        assertEquals(1_700_001_000L, band.minCreatedAt)
        assertEquals(1_700_002_000L, band.maxCreatedAt)
    }

    @Test
    fun `onChange fires when a band changes so persistence can mark dirty`() {
        var changes = 0
        val c = SyncBands(onChange = { changes++ })

        c.record(relay, profiles, null, null, paged = true)
        assertEquals(0, changes, "an empty fetch records nothing and must not dirty the store")

        c.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)
        assertEquals(1, changes)
    }

    @Test
    fun `the same filter instance is fingerprinted once`() {
        // Filter.toJson() runs to tens of thousands of characters for an
        // author-scoped filter, and a fan-out keys once per relay per cycle.
        val big = Filter(kinds = listOf(30382), authors = (1..500).map { it.toString(16).padStart(64, '0') })
        val c = SyncBands()
        c.record(relay, big, 1_700_001_000L, 1_700_002_000L, paged = true)

        // Same instance, many lookups: still one band, and cheap.
        repeat(50) { c.legs(relay, big) }
        assertEquals(1_700_001_000L, c.band(relay, big)!!.minCreatedAt)

        // An equal-but-distinct instance keys the same way; it just misses the cache.
        val copy = Filter(kinds = listOf(30382), authors = (1..500).map { it.toString(16).padStart(64, '0') })
        assertEquals(1_700_001_000L, c.band(relay, copy)?.minCreatedAt, "identity caching must not change the key")
    }
}
