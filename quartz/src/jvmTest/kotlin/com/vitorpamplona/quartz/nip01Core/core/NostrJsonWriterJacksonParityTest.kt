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

import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.LimitsMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [NostrJsonWriter] against the Jackson serializers it replaces, byte for byte: every event of
 * the recorded startup capture (as JSON, as its id preimage in text and UTF-8, as tags, as a
 * rumor and inside frames), hand-picked and random strings that need escaping, and every
 * message and command type.
 */
class NostrJsonWriterJacksonParityTest {
    // What EventHasherSerializer did on the JVM before the writer: a tree for the text and a
    // UTF-8 generator combining surrogate pairs for the bytes.
    private fun jacksonIdJson(
        pubKey: String,
        createdAt: Long,
        kind: Int,
        tags: TagArray,
        content: String,
    ): String {
        val factory = JsonNodeFactory.instance
        val node =
            factory.arrayNode(6).apply {
                add(0)
                add(pubKey)
                add(createdAt)
                add(kind)
                add(factory.arrayNode(tags.size).apply { tags.forEach { tag -> add(factory.arrayNode(tag.size).apply { tag.forEach { add(it) } }) } })
                add(content)
            }
        return JacksonMapper.toJson(node)
    }

    private fun jacksonIdBytes(
        pubKey: String,
        createdAt: Long,
        kind: Int,
        tags: TagArray,
        content: String,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        JacksonMapper.mapper.createGenerator(out, JsonEncoding.UTF8).use {
            it.enable(JsonGenerator.Feature.COMBINE_UNICODE_SURROGATES_IN_UTF8)
            it.writeStartArray()
            it.writeNumber(0)
            it.writeString(pubKey)
            it.writeNumber(createdAt)
            it.writeNumber(kind)
            it.writeStartArray()
            tags.forEach { tag ->
                it.writeStartArray()
                tag.forEach { value -> it.writeString(value) }
                it.writeEndArray()
            }
            it.writeEndArray()
            it.writeString(content)
            it.writeEndArray()
        }
        return out.toByteArray()
    }

    private val sha = MessageDigest.getInstance("SHA-256")

    /** Every way the writer serializes [e], each checked against Jackson. */
    private fun assertSameAsJackson(e: Event) {
        val label = { "for ${JacksonMapper.toJson(e).take(300)}" }
        assertEquals(JacksonMapper.toJson(e), NostrJsonWriter.toJson(e), "event ${label()}")
        assertEquals(JacksonMapper.mapper.writeValueAsString(e), NostrJsonWriter.toJson(e), "EventSerializer ${label()}")
        assertEquals(JacksonMapper.toJson(e.tags), NostrJsonWriter.toJson(e.tags), "tags ${label()}")

        val idJson = jacksonIdJson(e.pubKey, e.createdAt, e.kind, e.tags, e.content)
        assertEquals(idJson, EventHasherSerializer.makeJsonForId(e.pubKey, e.createdAt, e.kind, e.tags, e.content), "id text ${label()}")

        val idBytes = jacksonIdBytes(e.pubKey, e.createdAt, e.kind, e.tags, e.content)
        assertContentEquals(idBytes, EventHasherSerializer.fastMakeJsonForId(e.pubKey, e.createdAt, e.kind, e.tags, e.content), "id bytes ${label()}")
        assertContentEquals(sha.digest(idBytes), NostrJsonWriter.idHash(e.pubKey, e.createdAt, e.kind, e.tags, e.content), "id hash ${label()}")

        val rumor = Rumor.create(e)
        assertEquals(JacksonMapper.toJson(rumor), NostrJsonWriter.toJson(rumor), "rumor ${label()}")
    }

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
                        esc -> {
                            esc = false
                        }

                        c == '\\' && inStr -> {
                            esc = true
                        }

                        c == '"' -> {
                            inStr = !inStr
                        }

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
    fun everyCapturedEventWritesTheSame() {
        var count = 0
        var validIds = 0
        var validIdsJackson = 0
        forEachCapturedEvent { json ->
            val e = JacksonMapper.fromJson(json)
            assertSameAsJackson(e)
            if (EventHasherSerializer.makeJsonForIdHashAndCheck(e.id, e.pubKey, e.createdAt, e.kind, e.tags, e.content)) validIds++
            if (sha.digest(jacksonIdBytes(e.pubKey, e.createdAt, e.kind, e.tags, e.content)).toHexKey() == e.id) validIdsJackson++

            val frame = EventMessage("sub", e)
            assertEquals(JacksonMapper.toJson(frame), NostrJsonWriter.toJsonOrNull(frame))
            val cmd = EventCmd(e)
            assertEquals(JacksonMapper.toJson(cmd), NostrJsonWriter.toJsonOrNull(cmd))
            count++
        }
        println("captured events: $count written the same, $validIds with a matching id")
        assertTrue(count > 200_000, "expected the whole capture, got $count")
        // Some captured events carry an id that does not match their fields; both paths must agree on which.
        assertEquals(validIdsJackson, validIds)
    }

    private val oddStrings =
        listOf(
            "",
            "plain",
            "quote \" and backslash \\ and slash /",
            (0 until 0x20).map { it.toChar() }.joinToString(""),
            "DEL \u007F, NEL \u0085, NBSP \u00A0, LS \u2028, PS \u2029, BOM \uFEFF",
            "accents: \u00E9 \u00FC \u00F1, CJK: \u6F22\u5B57, Arabic: \u0645\u0631\u062D\u0628\u0627",
            "emoji pair: \uD83D\uDE00 and ZWJ \uD83D\uDC69\u200D\uD83D\uDC67",
            "lone high \uD83D then text",
            "lone low \uDE00 then text",
            "high at end \uD83D",
            "\uDE00 low at start",
            "reversed pair \uDE00\uD83D",
            "two highs \uD83D\uD83D\uDE00",
            "max BMP \uFFFF and \uFFFE",
            "U+10FFFF \uDBFF\uDFFF",
            "\u0000",
            "\n\r\t\b\u000C",
            "\\u0041 is not an escape here",
        )

    private fun event(
        content: String,
        tags: TagArray,
        createdAt: Long = 1_700_000_000,
        kind: Int = 1,
    ) = Event("a".repeat(64), "b".repeat(64), createdAt, kind, tags, content, "c".repeat(128))

    @Test
    fun oddStringsWriteTheSame() {
        for (s in oddStrings) {
            assertSameAsJackson(event(s, arrayOf(arrayOf("t", s), arrayOf(s))))
            assertSameAsJackson(event("x", arrayOf(arrayOf(s, s, s))))
        }
        assertSameAsJackson(event("", emptyArray()))
        assertSameAsJackson(event("", arrayOf(emptyArray())))
        assertSameAsJackson(event(oddStrings.joinToString("|"), arrayOf(oddStrings.toTypedArray())))
        for (n in listOf(0L, 1L, 9L, 10L, -1L, -10L, Long.MAX_VALUE, Long.MIN_VALUE, Int.MAX_VALUE.toLong(), 1_000_000_000_000L)) {
            assertSameAsJackson(event("n", emptyArray(), createdAt = n, kind = (n % 70_000).toInt()))
        }
        assertSameAsJackson(event("k", emptyArray(), kind = Int.MIN_VALUE))
    }

    private fun randomString(random: Random): String {
        val pool = "aZ09 \"\\/\u0000\u0001\u001F\u007F\u0080\u07FF\u0800\u2028\uFFFF\uD800\uDBFF\uDC00\uDFFF\uD83D\uDE00\u00E9\u6F22"
        val len = random.nextInt(0, 40)
        return buildString {
            repeat(len) {
                if (random.nextInt(4) == 0) append(random.nextInt(0, 0x10000).toChar()) else append(pool[random.nextInt(pool.length)])
            }
        }
    }

    @Test
    fun randomStringsWriteTheSame() {
        val random = Random(4595)
        repeat(20_000) {
            val tags = Array(random.nextInt(0, 4)) { Array(random.nextInt(0, 4)) { randomString(random) } }
            assertSameAsJackson(event(randomString(random), tags, createdAt = random.nextLong(), kind = random.nextInt()))
        }
    }

    @Test
    fun bigEventsGrowAndReleaseTheBuffers() {
        // Past every buffer the writer keeps, with escapes and multi-byte chars spread through.
        val big = buildString { repeat(30_000) { append(oddStrings[it % oddStrings.size]) } }
        assertSameAsJackson(event(big, arrayOf(arrayOf("t", big))))
        assertSameAsJackson(event("small after big", arrayOf(arrayOf("p", "a".repeat(64)))))
        val tags = Array(5_000) { arrayOf("p", it.toString().padStart(64, '0'), "wss://relay.example.com/", "") }
        assertSameAsJackson(event("contact list", tags, kind = 3))
    }

    private val sampleEvent = event("hello \"world\"\n", arrayOf(arrayOf("e", "d".repeat(64), "wss://r.example/"), arrayOf("t", "nostr")))

    private val filters =
        listOf(
            Filter(),
            Filter(kinds = listOf(1, 6, 7)),
            Filter(ids = listOf("a".repeat(64)), authors = listOf("b".repeat(64), "c".repeat(64)), limit = 10),
            Filter(
                ids = emptyList(),
                authors = emptyList(),
                kinds = emptyList(),
                tags = mapOf("e" to listOf("d".repeat(64)), "t" to listOf("nostr", "odd \" \\ \u0001"), "odd\"key\u0002" to emptyList()),
                tagsAll = mapOf("t" to listOf("a", "b"), "" to listOf("")),
                since = -5,
                until = Long.MAX_VALUE,
                limit = Int.MIN_VALUE,
                search = "search \u2028 \uD83D \"q\"",
            ),
            Filter(search = ""),
        )

    private val messages: List<Message> =
        listOf(
            EventMessage("sub", sampleEvent),
            EventMessage("odd \" sub \u0001", sampleEvent),
            NoticeMessage("notice \\ \n"),
            OkMessage("a".repeat(64), true, ""),
            OkMessage("a".repeat(64), false, "rate-limited: slow down", "5000"),
            OkMessage("a".repeat(64), false, "blocked: \"why\" \uD83D\uDE00"),
            AuthMessage("challenge-\u001F"),
            NotifyMessage("notify"),
            ClosedMessage("sub", "error: closed"),
            ClosedMessage("sub", "auth-required: x", "params \""),
            CountMessage("q", CountResult(0)),
            CountMessage("q", CountResult(42, approximate = true)),
            CountMessage("q", CountResult(Int.MAX_VALUE, approximate = false, hll = ByteArray(256) { it.toByte() })),
            EoseMessage("sub"),
            EoseMessage("sub", emptyList()),
            EoseMessage("sub \"x\"", listOf(EoseMessage.HINT_FINISH, EoseMessage.HINT_MORE, "odd\n")),
            LimitsMessage(),
            LimitsMessage(canWrite = true, maxLimit = 500),
            LimitsMessage(
                canWrite = false,
                canRead = true,
                authForRead = false,
                authForWrite = true,
                acceptedEventKinds = listOf(1, 7, 30023),
                blockedEventKinds = emptyList(),
                minPowDifficulty = 20,
                maxMessageLength = 65536,
                maxSubscriptions = 20,
                maxFilters = 10,
                maxLimit = 5000,
                maxEventTags = 2000,
                maxContentLength = 8196,
                createdAtMsecsAgo = 94_608_000_000,
                createdAtMsecsAhead = 900_000,
                filterRateLimit = 10,
                publishingRateLimit = Long.MIN_VALUE,
                requiredTags = listOf(listOf("t", "nostr"), emptyList(), listOf("\"")),
            ),
            NegMsgMessage("neg", "6100000200"),
            NegMsgMessage("neg \u0003", "6100"),
            NegErrMessage("neg", "blocked: too big"),
            NegErrMessage("neg", "blocked: too big (500 > 100)", 100),
        )

    private val commands: List<Command> =
        listOf(
            ReqCmd("sub", filters),
            ReqCmd("sub", emptyList()),
            ReqCmd("odd \" \\ \u0000", listOf(Filter(kinds = listOf(1)))),
            CountCmd("q", filters),
            EventCmd(sampleEvent),
            EventCmd(sampleEvent, JacksonMapper.toJson(sampleEvent)),
            EventCmd(sampleEvent, "{ \"raw\" : true }"),
            CloseCmd("sub"),
            CloseCmd("\u2028"),
            NegOpenCmd("neg", filters[3], "6100000200"),
            NegMsgCmd("neg", "61"),
            NegCloseCmd("neg"),
        )

    @Test
    fun everyMessageTypeWritesTheSame() {
        for (msg in messages) {
            assertEquals(JacksonMapper.toJson(msg), NostrJsonWriter.toJsonOrNull(msg), msg::class.simpleName)
            // The type's own toJson, fast paths included.
            assertEquals(JacksonMapper.toJson(msg), msg.toJson(), msg::class.simpleName)
        }
    }

    @Test
    fun everyCommandTypeWritesTheSame() {
        for (cmd in commands) {
            assertEquals(JacksonMapper.toJson(cmd), NostrJsonWriter.toJsonOrNull(cmd), cmd::class.simpleName)
            assertEquals(JacksonMapper.toJson(cmd), cmd.toJson(), cmd::class.simpleName)
        }
    }

    @Test
    fun filtersAndRumorsWriteTheSame() {
        for (f in filters) {
            assertEquals(JacksonMapper.toJson(f), NostrJsonWriter.toJson(f))
            assertEquals(JacksonMapper.toJson(f), f.toJson())
        }
        val rumors =
            listOf(
                Rumor(null, null, null, null, null, null),
                Rumor("a".repeat(64), null, 5, null, emptyArray(), null),
                Rumor(null, "b".repeat(64), null, 14, arrayOf(arrayOf("p", "\"")), "dm \uD83D"),
                Rumor.create(sampleEvent),
            )
        for (r in rumors) {
            assertEquals(JacksonMapper.toJson(r), NostrJsonWriter.toJson(r))
            assertEquals(JacksonMapper.toJson(r), Rumor.toJson(r))
        }
    }

    @Test
    fun threadsWritingAtOnceDoNotShareState() {
        val random = Random(7)
        val events = List(500) { event(randomString(random), arrayOf(arrayOf("t", randomString(random)))) }
        val expectedJson = events.map { JacksonMapper.toJson(it) }
        val expectedId = events.map { sha.digest(jacksonIdBytes(it.pubKey, it.createdAt, it.kind, it.tags, it.content)) }
        val failures = AtomicInteger()
        val threads =
            List(4) { t ->
                Thread {
                    repeat(20) { round ->
                        for (k in events.indices) {
                            val idx = (k * 7 + t * 13 + round) % events.size
                            val e = events[idx]
                            if (NostrJsonWriter.toJson(e) != expectedJson[idx]) failures.incrementAndGet()
                            if (!NostrJsonWriter.idHash(e.pubKey, e.createdAt, e.kind, e.tags, e.content).contentEquals(expectedId[idx])) failures.incrementAndGet()
                        }
                    }
                }
            }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(0, failures.get())
    }

    @Test
    fun unknownTypesAreLeftToJackson() {
        val custom =
            object : Message {
                override fun label() = "CUSTOM"
            }
        assertEquals(null, NostrJsonWriter.toJsonOrNull(custom))
        if (NostrJsonWriter.toJsonOrNull(custom as OptimizedSerializable) != null) fail("expected null")
    }

    companion object {
        const val CORPUS = "nostr_vitor_startup_data.json.gz"
    }
}
