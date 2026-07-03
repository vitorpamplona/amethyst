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
package com.vitorpamplona.relaybench

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.relaybench.bench.SyncBenchmark
import com.vitorpamplona.relaybench.corpus.CorpusGenerator
import com.vitorpamplona.relaybench.corpus.CorpusSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorpusDeterminismTest {
    private val spec =
        CorpusSpec(
            seed = 42,
            events = 400,
            baseTime = CorpusSpec.DEFAULT_BASE_TIME,
            spanSeconds = 3600,
        )

    @Test
    fun sameSpecSameEventIds() {
        val a = CorpusGenerator.generate(spec)
        val b = CorpusGenerator.generate(spec)
        assertEquals(a.events.size, b.events.size)
        assertEquals(a.events.map { it.id }, b.events.map { it.id }, "same spec must reproduce identical event ids")
        assertEquals(a.fingerprint, b.fingerprint)
    }

    @Test
    fun corpusIsFullySignedAndShaped() {
        val corpus = CorpusGenerator.generate(spec)
        assertEquals(spec.events, corpus.events.size)
        assertTrue(corpus.events.all { it.verify() }, "every generated event must carry a valid signature")

        val kinds = corpus.kindHistogram()
        // Social shape: profiles, contacts, notes, reactions must all exist.
        for (kind in listOf(0, 1, 3, 7)) {
            assertTrue((kinds[kind] ?: 0) > 0, "expected kind $kind in corpus, got $kinds")
        }
        // Timestamps stay inside the spec window (strfry accepts them).
        assertTrue(corpus.events.all { it.createdAt in (spec.baseTime - spec.spanSeconds)..spec.baseTime })
        // References point backwards: replies/reactions tag already-published events.
        val idsSoFar = HashSet<String>()
        for (e in corpus.events) {
            for (tag in e.tags) {
                if (tag.size >= 2 && tag[0] == "e") {
                    assertTrue(tag[1] in idsSoFar, "event ${e.id} (kind ${e.kind}) references a later event")
                }
            }
            idsSoFar.add(e.id)
        }
    }

    @Test
    fun scenariosDeriveFromCorpus() {
        val corpus = CorpusGenerator.generate(spec)
        val scenarios = Scenarios.derive(corpus)
        val keys = scenarios.map { it.key }
        assertTrue("global-feed" in keys && "follow-feed" in keys && "thread" in keys, "expected core scenarios, got $keys")
        // Filters must be inside strfry's default request limits.
        for (s in scenarios) {
            assertTrue((s.filter.limit ?: 0) <= 500, "${s.key} limit over strfry maxFilterLimit")
            assertTrue((s.filter.authors?.size ?: 0) + (s.filter.ids?.size ?: 0) <= 200, "${s.key} filter too large")
        }
    }

    @Test
    fun effectiveEventsCollapsesReplaceables() {
        val corpus = CorpusGenerator.generate(spec)
        val effective = SyncBenchmark.effectiveEvents(corpus.events)
        // The generator emits exactly one kind-0 and one kind-3 per author,
        // so nothing should collapse — but the result must never grow.
        assertTrue(effective.size <= corpus.events.size)
        val replaceableKeys = effective.filter { it.kind == 0 }.map { it.pubKey }
        assertEquals(replaceableKeys.size, replaceableKeys.distinct().size)
    }
}
