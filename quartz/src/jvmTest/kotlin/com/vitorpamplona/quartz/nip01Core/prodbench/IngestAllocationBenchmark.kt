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

import com.fasterxml.jackson.core.JsonToken
import com.sun.management.ThreadMXBean
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findIndexTagsWithPeople
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import java.lang.management.ManagementFactory
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Allocation-RATE profile of the per-event ingest path.
 *
 * Every previous memory measurement in this codebase looked at *retained* heap (heap
 * dumps, histograms, the intern tradeoff). That answers "what is alive", which is the
 * wrong question for the observed problem: on an SM-T220, ingest saturates all 8 cores
 * and the driver is GC read barriers charged to the mutator threads, not any single
 * consume stage. Read-barrier cost tracks the *allocation rate* — bytes created and
 * discarded per second — which retained-heap analysis is blind to.
 *
 * `com.sun.management.ThreadMXBean.getThreadAllocatedBytes` reports exactly that, per
 * thread, with no sampling error. It counts TLAB bumps, so it sees garbage that never
 * survives a single GC — precisely the allocations a heap dump cannot show.
 *
 * The headline number to look for is **bytes allocated per byte of wire JSON**. An event
 * arrives as N bytes of text; if turning it into an `Event` costs 10N, the ingest path
 * is a garbage pump and bounding concurrency will not help, because every added thread
 * multiplies the same waste.
 *
 * Runs on the JVM, so the absolute numbers are HotSpot's. Allocation *counts* come from
 * the same bytecode ART runs and the object layouts are close, so the per-stage ranking
 * and the ratios transfer; treat the absolute MB/s as indicative and confirm any fix
 * on device.
 */
class IngestAllocationBenchmark {
    companion object {
        /** The real multi-relay capture of an account cold start, checked into quartz. */
        const val CORPUS = "nostr_vitor_startup_data.json.gz"
        const val EVENTS = 20_000

        private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

        /** Anything the measured loops produce, parked here so JIT cannot elide them. */
        var keep: Any? = null

        /**
         * Bytes allocated by running [block] over [reps] iterations, minus the loop's own
         * overhead.
         *
         * Warmed hard on purpose: escape analysis only kicks in after C2 compiles the
         * method, and a cold measurement reports allocations the JIT would have scalarised
         * away — i.e. it over-reports exactly the transient garbage this is looking for.
         */
        fun allocBytes(
            reps: Int,
            warmup: Int = reps,
            block: (Int) -> Any?,
        ): Long {
            var sink: Any? = null
            for (i in 0 until warmup) sink = block(i % reps)
            keep = sink

            val id = Thread.currentThread().threadId()
            val before = threads.getThreadAllocatedBytes(id)
            for (i in 0 until reps) sink = block(i)
            val after = threads.getThreadAllocatedBytes(id)
            keep = sink

            // the empty loop itself allocates nothing measurable, but the accessor does
            val idle = threads.getThreadAllocatedBytes(id)
            val overhead = idle - after
            return (after - before) - overhead
        }

        fun used(): Long {
            val rt = Runtime.getRuntime()
            return rt.totalMemory() - rt.freeMemory()
        }

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
            return (used() - before) to held
        }
    }

    /**
     * The real capture of Vitor's account startup — the exact traffic this profile is
     * about — streamed rather than slurped, because the file is 247 MB decompressed and
     * only the first [EVENTS] are needed.
     *
     * Real signatures, so `verify()` does its real work, and a real kind/size/tag mix
     * rather than a guess at one.
     */
    private fun corpus(): List<Event> {
        val stream =
            javaClass.classLoader?.getResourceAsStream(CORPUS)
                ?: throw IllegalStateException("corpus $CORPUS missing from the test classpath")

        val out = ArrayList<Event>(EVENTS)
        val seen = HashSet<String>(EVENTS * 2)
        GZIPInputStream(stream).use { gz ->
            val parser = JacksonMapper.mapper.factory.createParser(gz)
            check(parser.nextToken() == JsonToken.START_ARRAY) { "$CORPUS is not a JSON array" }
            while (out.size < EVENTS && parser.nextToken() == JsonToken.START_OBJECT) {
                val event: Event = JacksonMapper.mapper.readValue(parser, JacksonMapper.eventTypeInstance)
                // the capture spans many relays, so the same event arrives several times
                if (seen.add(event.id)) out.add(event)
            }
        }
        return out
    }

    @Test
    fun allocationPerIngestStage() {
        val events = corpus()
        val eventJson = events.map { it.toJson() }
        val frames = eventJson.mapIndexed { i, j -> "[\"EVENT\",\"sub$i\",$j]" }
        val frameBytes = frames.map { it.toByteArray(Charsets.UTF_8) }

        val count = events.size
        val wireBytes = frames.sumOf { it.length.toLong() }
        val n = count.toLong()

        assertTrue(count > EVENTS / 2, "corpus yielded only $count events")
        assertTrue(eventJson.all { it.length > 50 }, "corpus produced empty events")

        println("\n=== corpus: $count events, ${wireBytes / 1024} KB of wire JSON, mean ${wireBytes / n} B/event ===")

        // ---- what surviving the parse actually costs to keep ----
        val (retainedEvents, held) = retained { frames.map { Message.fromJson(it) } }
        keep = held

        // ---- per stage ----
        val decode = allocBytes(count) { i -> String(frameBytes[i], Charsets.UTF_8) }
        val msgParse = allocBytes(count) { i -> Message.fromJson(frames[i]) }
        val evtParse = allocBytes(count) { i -> JacksonMapper.fromJson(eventJson[i]) }
        val verify = allocBytes(count) { i -> events[i].verify() }
        val scans =
            allocBytes(count) { i ->
                val e = events[i]
                findHashtags(e.content)
                findIndexTagsWithPeople(e.content, e.tags)
                Nip19Parser.parseAll(e.content)
            }

        fun row(
            label: String,
            bytes: Long,
        ) = println(
            "  %-22s %8.1f MB total %8d B/event  %5.2fx wire".format(
                label,
                bytes / 1048576.0,
                bytes / n,
                bytes.toDouble() / wireBytes,
            ),
        )

        println("\nALLOCATED (transient + surviving)")
        row("utf8 decode", decode)
        row("frame -> Message", msgParse)
        row("event json -> Event", evtParse)
        row("verify()", verify)
        row("content scans", scans)
        row("TOTAL decode+parse+verify", decode + msgParse + verify)

        println("\nRETAINED after parse")
        println("  parsed events         %8.1f MB total %8d B/event  %5.2fx wire".format(retainedEvents / 1048576.0, retainedEvents / n, retainedEvents.toDouble() / wireBytes))
        println("  garbage per kept byte %8.1fx".format((decode + msgParse + verify - retainedEvents).toDouble() / retainedEvents))
    }
}
