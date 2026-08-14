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
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MessageDecoder
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Is the production [CachingEventDecoder] capacity big enough for a cold start?
 *
 * The app connects to every relay it knows and pulls everything at once, by design —
 * that redundancy is the point, and throttling it would defeat the product. What is
 * NOT wanted is paying a full JSON parse for the same event once per relay that
 * serves it. [CachingEventDecoder] exists precisely to avoid that, and it is wired in
 * (`AppModules.kt`, `NostrClient(websocketBuilder, applicationIOScope,
 * CachingEventDecoder())`) — at its **default capacity of 2048**.
 *
 * `DedupDecodeBenchmark` proves the mechanism, but with `capacity = UNIQUE * 2`
 * (40,000) and a frame order that spaces each duplicate 20,000 frames apart. Both
 * choices flatter the cache. This measures the setting that actually ships, against
 * the real multi-relay capture (248,241 frames / 31,161 unique -- ~8x duplication) in
 * the order it was recorded.
 *
 * The decisive statistic is the **reuse distance**: how many frames pass between two
 * deliveries of the same event. A capacity only catches duplicates whose reuse
 * distance falls inside it, so the distance histogram gives the hit rate for every
 * candidate capacity at once, without re-running the decoder for each.
 */
class DedupCapacityBenchmark {
    companion object {
        const val CORPUS = "nostr_vitor_startup_data.json.gz"
        const val FRAMES = 150_000
        val CAPACITIES = listOf(2_048, 8_192, 32_768, 131_072)
    }

    /** Raw event JSON in recorded order, wrapped as EVENT frames. */
    private fun frames(): List<String> {
        val stream =
            javaClass.classLoader?.getResourceAsStream(CORPUS)
                ?: throw IllegalStateException("corpus $CORPUS missing from the test classpath")

        val out = ArrayList<String>(FRAMES)
        GZIPInputStream(stream).bufferedReader().use { reader ->
            // one giant single-line JSON array; walk it and slice out each top-level object
            val buf = CharArray(1 shl 16)
            val sb = StringBuilder()
            var depth = 0
            var inStr = false
            var esc = false
            var n = reader.read(buf)
            while (n > 0 && out.size < FRAMES) {
                for (i in 0 until n) {
                    val c = buf[i]
                    if (depth > 0) sb.append(c)
                    when {
                        esc -> esc = false
                        c == '\\' && inStr -> esc = true
                        c == '"' -> inStr = !inStr
                        inStr -> {}
                        c == '{' -> {
                            if (depth == 0) sb.append(c)
                            depth++
                        }
                        c == '}' -> {
                            depth--
                            if (depth == 0) {
                                out.add("[\"EVENT\",\"s\",$sb]")
                                sb.setLength(0)
                                if (out.size >= FRAMES) break
                            }
                        }
                    }
                }
                if (out.size >= FRAMES) break
                n = reader.read(buf)
            }
        }
        return out
    }

    private fun idOf(frame: String): String? {
        val k = frame.indexOf("\"id\":\"")
        if (k < 0 || k + 6 + 64 > frame.length) return null
        return frame.substring(k + 6, k + 6 + 64)
    }

    @Test
    fun productionCapacityAgainstRealArrivalOrder() {
        val frames = frames()
        assertTrue(frames.size > FRAMES / 2, "corpus yielded only ${frames.size} frames")

        // ---- reuse-distance histogram: hit rate for ANY capacity, in one pass ----
        val lastSeen = HashMap<String, Int>(frames.size)
        val distances = ArrayList<Int>(frames.size)
        var unique = 0
        frames.forEachIndexed { i, f ->
            val id = idOf(f) ?: return@forEachIndexed
            val prev = lastSeen.put(id, i)
            if (prev == null) unique++ else distances.add(i - prev)
        }
        val dupes = distances.size
        println("\n=== real capture: ${frames.size} frames, $unique unique, $dupes duplicates (${dupes * 100 / frames.size}%) ===")

        println("\nREUSE DISTANCE -> share of duplicates a cache of that size can catch")
        CAPACITIES.forEach { cap ->
            val caught = distances.count { it <= cap }
            val label = if (cap == 2_048) " <- PRODUCTION" else ""
            println("  capacity %7d  catches %5.1f%% of duplicates%s".format(cap, caught * 100.0 / dupes, label))
        }
        val med = distances.sorted()[dupes / 2]
        println("  median reuse distance: $med frames")

        // ---- confirm with the real decoder at the shipping capacity ----
        println("\nACTUAL DECODER (parse time over ${frames.size} frames)")
        (listOf(0) + CAPACITIES).forEach { cap ->
            val decoder = if (cap == 0) MessageDecoder.Default else CachingEventDecoder(capacity = cap)
            repeat(2_000) { decoder.decode(frames[it]) } // warm
            val t0 = System.nanoTime()
            frames.forEach { decoder.decode(it) }
            val ms = (System.nanoTime() - t0) / 1_000_000
            val reuse =
                if (decoder is CachingEventDecoder) {
                    " reused=${decoder.reusedCount} parsed=${decoder.parsedCount}"
                } else {
                    ""
                }
            println("  capacity %7s  %6d ms%s".format(if (cap == 0) "none" else "$cap", ms, reuse))
        }
    }
}
