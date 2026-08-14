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
package com.vitorpamplona.quartz.nip01Core.prodbench

import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test

/**
 * `String.intern()` is applied to every event `id`, `pubKey` (EventDeserializer) and
 * every tag value (TagArrayDeserializer). An on-device profile of a release build
 * during ingest put `art::InternTable::InternWeak` at **3.5% of the ingest workers'
 * CPU** — the second largest symbol after memcpy.
 *
 * Interning is a MEMORY optimisation, not a speed one: Nostr repeats strings heavily,
 * so collapsing duplicates to one instance is why it is there. Removing it could
 * therefore cost more memory than the CPU it saves. This measures both axes, split by
 * how duplicated each field actually is, because the answer differs per field:
 *
 *  - tag names (`p`, `e`, `a`)  — a handful of values repeated on every event
 *  - relay URLs                 — tens of values repeated across everything
 *  - pubkeys                    — measured on device: 1,880 distinct for 11,338 notes
 *  - event ids                  — one per event, but every `e` tag that references
 *                                 that event repeats it, so they dedup too
 *
 * Cardinalities come from the device probe cited above, scaled to a corpus big enough
 * for the heap delta to clear GC noise.
 *
 * Results (JVM, 60k events / 840k strings):
 *
 * ```
 *                      retained   parse CPU
 *   no interning         69.1 MB     146 ms
 *   intern everything    17.8 MB     354 ms
 *   app-level pool       23.3 MB     192 ms
 *   intern all but ids   35.8 MB     257 ms
 * ```
 *
 * So interning is a ~4x memory win and worth its CPU. Skipping the hex fields is not a
 * shortcut — it gives up half the memory for half the CPU, because those hex strings
 * repeat too. An app-level pool is the only variant that beats interning on CPU, but it
 * holds *strong* references where ART's intern table is weak, so it would never release
 * a string whose event was pruned; the 5.5 MB it loses to interning is its own node
 * headers. Not worth trading a self-clearing table for an unbounded one to reclaim ~2.7%
 * of ingest CPU on a device that is already memory-bound.
 *
 * Measures the interning primitive on realistic string distributions — not the full
 * Jackson parse path — so it isolates the tradeoff without needing a second
 * deserializer wired in. Note this is HotSpot's `intern()`; ART's `InternWeak` is a
 * different implementation, so treat the ordering as transferable and the magnitudes as
 * indicative.
 */
class InternTradeoffBenchmark {
    companion object {
        const val EVENTS = 60_000
        const val AUTHORS = 10_000 // ~6 events per author, as measured on device
        const val RELAYS = 60
        val TAG_NAMES = listOf("p", "e", "a", "t", "r", "alt")

        fun hex(seed: Int): String = "%064x".format(seed)

        fun used(): Long {
            val rt = Runtime.getRuntime()
            return rt.totalMemory() - rt.freeMemory()
        }

        /** Retained bytes of whatever [build] returns, with GC settled either side. */
        fun retained(build: () -> Any): Pair<Long, Any> {
            repeat(3) {
                System.gc()
                Thread.sleep(60)
            }
            val before = used()
            val held = build()
            repeat(3) {
                System.gc()
                Thread.sleep(60)
            }
            val after = used()
            return (after - before) to held
        }
    }

    /** The strings one event contributes, as fresh instances (as if just parsed off the wire). */
    private fun eventStrings(i: Int): List<String> =
        buildList {
            add(String(hex(i).toCharArray())) // id — unique per event
            add(String(hex(i % AUTHORS).toCharArray())) // pubKey — repeats
            repeat(4) { t ->
                add(String(TAG_NAMES[(i + t) % TAG_NAMES.size].toCharArray())) // tag name — tiny set
                add(String("wss://relay${(i + t) % RELAYS}.example.com/".toCharArray())) // relay url
                add(String(hex((i + t) * 31).toCharArray())) // tag value: a referenced id
            }
        }

    @Test
    fun internCostAndBenefit() {
        var sink: Any? = null

        println("\n=== corpus: $EVENTS events, $AUTHORS authors, $RELAYS relays ===")

        // ---- memory ----
        val (memNone, holdA) = retained { (0 until EVENTS).flatMap { eventStrings(it) } }
        sink = holdA
        val (memAll, holdB) = retained { (0 until EVENTS).flatMap { eventStrings(it).map { s -> s.intern() } } }
        sink = holdB
        // intern everything EXCEPT the two unique-hex classes (event id, tag id refs)
        val (memSel, holdC) =
            retained {
                (0 until EVENTS).flatMap { i ->
                    eventStrings(i).mapIndexed { idx, s ->
                        // index 0 is the id; every 3rd tag slot is a referenced id
                        if (idx == 0 || (idx > 1 && (idx - 2) % 3 == 2)) s else s.intern()
                    }
                }
            }
        sink = holdC

        println("\nRETAINED HEAP")
        println("  no interning        %6.1f MB".format(memNone / 1048576.0))
        println("  intern everything   %6.1f MB   (%+.1f MB vs none)".format(memAll / 1048576.0, (memAll - memNone) / 1048576.0))
        println("  intern all but ids  %6.1f MB   (%+.1f MB vs none)".format(memSel / 1048576.0, (memSel - memNone) / 1048576.0))

        // ---- cpu ----
        fun timeIt(
            label: String,
            op: (String, Int) -> String,
        ) {
            repeat(2) { i -> (0 until 5_000).forEach { eventStrings(it).forEachIndexed { idx, s -> op(s, idx) } } }
            val t0 = System.nanoTime()
            var n = 0
            (0 until EVENTS).forEach { i -> eventStrings(i).forEachIndexed { idx, s -> if (op(s, idx) !== s) n++ } }
            val ms = (System.nanoTime() - t0) / 1_000_000
            println("  %-20s %5d ms  (%d strings)".format(label, ms, EVENTS * 14))
        }

        // An app-level dedup table gives the same collapsing without going through
        // ART's global InternTable, which is what showed up in the device profile.
        val pool = ConcurrentHashMap<String, String>(1 shl 16)
        val (memPool, holdD) = retained { (0 until EVENTS).flatMap { eventStrings(it).map { s -> pool.putIfAbsent(s, s) ?: s } } }
        sink = holdD
        println("  app-level pool      %6.1f MB   (%+.1f MB vs none, pool holds %d)".format(memPool / 1048576.0, (memPool - memNone) / 1048576.0, pool.size))

        println("\nPARSE-SIDE CPU (build + optionally intern)")
        timeIt("no interning") { s, _ -> s }
        timeIt("intern everything") { s, _ -> s.intern() }
        timeIt("intern all but ids") { s, idx -> if (idx == 0 || (idx > 1 && (idx - 2) % 3 == 2)) s else s.intern() }
        val pool2 = ConcurrentHashMap<String, String>(1 shl 16)
        timeIt("app-level pool") { s, _ -> pool2.putIfAbsent(s, s) ?: s }

        check(sink != null)
    }
}
