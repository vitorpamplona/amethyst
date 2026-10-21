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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [NostrJsonReader] over every event of the recorded startup capture, as an event and as an
 * EVENT frame, every prefix of a sample, and random edits of real events, each set pinned by
 * a [Pin].
 *
 * The pins were recorded while every outcome was also compared with the Jackson path the
 * reader replaced: whenever both accepted an input they built the same event, and the reader
 * never accepted what Jackson rejected. It rejects some inputs Jackson took (its stricter
 * cases, and a jackson-core quirk that took any character whose low byte is a hex digit
 * inside a \u escape).
 */
class NostrJsonReaderPinnedTest {
    private fun reader(json: String): Event? =
        try {
            NostrJsonReader.readEvent(json)
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun sameEvent(
        a: Event,
        b: Event,
    ): Boolean =
        a::class == b::class &&
            a.id == b.id &&
            a.pubKey == b.pubKey &&
            a.createdAt == b.createdAt &&
            a.kind == b.kind &&
            a.tags.contentDeepEquals(b.tags) &&
            a.content == b.content &&
            a.sig == b.sig

    private fun describe(e: Event): String = "${e::class.simpleName}|${e.id}|${e.pubKey}|${e.createdAt}|${e.kind}|${e.tags.joinToString("|") { it.joinToString(",") }}|${e.content}|${e.sig}"

    private class Tally {
        val pin = Pin("")
        var accepted = 0
        var rejected = 0
    }

    private fun compare(
        json: String,
        t: Tally,
    ) {
        val r = reader(json)
        t.pin.add(r?.let { describe(it) })
        if (r != null) t.accepted++ else t.rejected++
    }

    private fun assertPinned(
        name: String,
        t: Tally,
    ) {
        t.pin.name = name
        println("$name: accepted ${t.accepted}, rejected ${t.rejected}")
        t.pin.check(PINS.getValue(name))
    }

    /** Every top-level object of the capture's single-line JSON array, as the raw text. */
    private fun forEachCapturedEvent(action: (String) -> Unit) {
        val stream =
            javaClass.classLoader?.getResourceAsStream(CORPUS)
                ?: throw IllegalStateException("corpus $CORPUS missing from the test classpath")
        GZIPInputStream(stream).bufferedReader().use { reader ->
            val buf = CharArray(1 shl 16)
            val sb = StringBuilder()
            var depth = 0
            var inStr = false
            var esc = false
            var n = reader.read(buf)
            while (n > 0) {
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
                                action(sb.toString())
                                sb.setLength(0)
                            }
                        }
                    }
                }
                n = reader.read(buf)
            }
        }
    }

    @Test
    fun everyCapturedEventParsesAsPinned() {
        val events = Tally()
        var frames = 0
        forEachCapturedEvent { json ->
            compare(json, events)

            val frame = "[\"EVENT\",\"sub\",$json]"
            val viaFrame = NostrJsonReader.readMessage(frame) as EventMessage
            assertEquals("sub", viaFrame.subId)
            assertTrue(sameEvent(NostrJsonReader.readEvent(json), viaFrame.event), "EVENT frame differs: ${json.take(300)}")
            frames++
        }
        assertPinned("captured events", events)
        assertTrue(events.accepted > 200_000, "expected the whole capture, got ${events.accepted}")
        assertEquals(events.accepted, frames)
        assertEquals(0, events.rejected)
    }

    /** The first 2,000 captured events under 4,000 chars, read once for every test that needs it. */
    private fun sample(count: Int): List<String> {
        val all =
            cachedSample ?: ArrayList<String>(SAMPLE_SIZE).also { out ->
                forEachCapturedEvent { if (out.size < SAMPLE_SIZE && it.length < 4000) out += it }
                cachedSample = out
            }
        return all.subList(0, count)
    }

    @Test
    fun threadsParsingAtOnceDoNotShareState() {
        // Each thread keeps its own reader; four threads over the same events must each get
        // exactly what one thread alone builds.
        val events = sample(SAMPLE_SIZE)
        val expected = events.map { NostrJsonReader.readEvent(it) }
        val failures = AtomicInteger()
        val threads =
            List(4) { t ->
                Thread {
                    repeat(5) { round ->
                        for (k in events.indices) {
                            val idx = (k * 7 + t * 13 + round) % events.size
                            val got = NostrJsonReader.readEvent(events[idx])
                            if (!sameEvent(expected[idx], got)) failures.incrementAndGet()
                        }
                    }
                }
            }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(0, failures.get())
    }

    @Test
    fun everyTruncationIsRejected() {
        val t = Tally()
        for (json in sample(60)) for (k in 0 until json.length) compare(json.substring(0, k), t)
        assertPinned("truncations", t)
        assertEquals(0, t.accepted)
    }

    @Test
    fun randomEditsParseAsPinned() {
        val rnd = Random(20261021)
        val events = sample(SAMPLE_SIZE)
        val t = Tally()
        for (json in events) {
            repeat(25) {
                val sb = StringBuilder(json)
                repeat(1 + rnd.nextInt(3)) {
                    val p = rnd.nextInt(sb.length)
                    when (rnd.nextInt(4)) {
                        0 -> sb.deleteCharAt(p)
                        1 -> sb.insert(p, MUTATION_CHARS[rnd.nextInt(MUTATION_CHARS.length)])
                        2 -> sb.setCharAt(p, MUTATION_CHARS[rnd.nextInt(MUTATION_CHARS.length)])
                        else -> {
                            val q = rnd.nextInt(sb.length)
                            val c = sb[p]
                            sb.setCharAt(p, sb[q])
                            sb.setCharAt(q, c)
                        }
                    }
                }
                compare(sb.toString(), t)
            }
        }
        assertPinned("random edits", t)
        assertTrue(t.accepted > 10_000)
    }

    companion object {
        /** See [Pin]: recorded while every outcome was also compared with Jackson. */
        private val PINS =
            mapOf(
                "captured events" to "8d14fbe28335faba96c5be2ee89c3e54fc1fe4416521cc1c085cef5201cb26c4",
                "truncations" to "d7c2f5c951128648f9af1c91c7d6820694cb3c9c045fbe8b08a9018fe98899eb",
                "random edits" to "45aa82e140702ef8bcdd0ed3a26dad73a4dd9cf5ca6d0b1328e83384430eedcc",
            )

        const val CORPUS = "nostr_vitor_startup_data.json.gz"
        const val SAMPLE_SIZE = 2_000
        private var cachedSample: List<String>? = null
        const val MUTATION_CHARS = "\"\\{}[],: \n\tau0-9.eE+nlftré\uD83D❤"
    }
}
