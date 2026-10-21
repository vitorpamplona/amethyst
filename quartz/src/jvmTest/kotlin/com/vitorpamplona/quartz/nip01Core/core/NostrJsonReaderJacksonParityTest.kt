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

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [NostrJsonReader] against Quartz's Jackson path, which it replaces: every event of the
 * recorded startup capture, as an event and as an EVENT frame, every prefix of a sample, and
 * random edits of real events.
 *
 * Whenever both accept an input they must build the same event. The reader may reject what
 * Jackson accepts (its stricter cases, and a jackson-core quirk that takes any character whose
 * low byte is a hex digit inside a \u escape) but must never accept what Jackson rejects.
 */
class NostrJsonReaderJacksonParityTest {
    private fun jackson(json: String): Event? =
        try {
            JacksonMapper.fromJson(json)
        } catch (e: Exception) {
            null
        }

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
        var same = 0
        var bothReject = 0
        var onlyJackson = 0
        val readerOnly = ArrayList<String>()
        val different = ArrayList<String>()
    }

    private fun compare(
        json: String,
        t: Tally,
    ) {
        val j = jackson(json)
        val r = reader(json)
        t.pin.add(r?.let { describe(it) })
        when {
            j != null && r != null -> if (sameEvent(j, r)) t.same++ else t.different += json
            j == null && r == null -> t.bothReject++
            j != null -> t.onlyJackson++
            else -> t.readerOnly += json
        }
    }

    private fun assertParity(
        name: String,
        t: Tally,
    ) {
        t.pin.name = name
        t.pin.check(PINS.getValue(name))
        println("$name: same ${t.same}, both reject ${t.bothReject}, only Jackson ${t.onlyJackson}, only reader ${t.readerOnly.size}, different ${t.different.size}")
        if (t.different.isNotEmpty()) fail("$name: ${t.different.size} inputs build different events, e.g. ${t.different.first().take(300)}")
        if (t.readerOnly.isNotEmpty()) fail("$name: ${t.readerOnly.size} inputs only the reader accepts, e.g. ${t.readerOnly.first().take(300)}")
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
    fun everyCapturedEventParsesTheSame() {
        val events = Tally()
        var frames = 0
        forEachCapturedEvent { json ->
            compare(json, events)

            val frame = "[\"EVENT\",\"sub\",$json]"
            val viaJackson = JacksonMapper.fromJsonToMessage(frame) as EventMessage
            val viaReader = NostrJsonReader.readMessage(frame) as EventMessage
            assertEquals(viaJackson.subId, viaReader.subId)
            assertTrue(sameEvent(viaJackson.event, viaReader.event), "EVENT frame differs: ${json.take(300)}")
            frames++
        }
        assertParity("captured events", events)
        assertTrue(events.same > 200_000, "expected the whole capture, got ${events.same}")
        assertEquals(events.same, frames)
        assertEquals(0, events.onlyJackson)
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
        // exactly what Jackson builds.
        val events = sample(SAMPLE_SIZE)
        val expected = events.map { JacksonMapper.fromJson(it) }
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
        assertParity("truncations", t)
        assertEquals(0, t.same)
        assertEquals(0, t.onlyJackson)
    }

    @Test
    fun randomEditsNeverParseDifferently() {
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
        assertParity("random edits", t)
        assertTrue(t.same > 10_000)
    }

    companion object {
        /** See [Pin]: recorded while every outcome was also checked against Jackson. */
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
