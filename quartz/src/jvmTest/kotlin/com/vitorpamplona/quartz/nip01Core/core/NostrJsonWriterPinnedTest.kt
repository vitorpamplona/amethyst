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

import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
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
import org.junit.Rule
import org.junit.rules.TestName
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [NostrJsonWriter] over every event of the recorded startup capture (as JSON, as its id
 * preimage in text and UTF-8, as tags, as a rumor and inside frames), hand-picked and random
 * strings that need escaping, and every message and command type, each test pinned by a [Pin].
 *
 * The pins were recorded while every output was also compared, byte for byte, with the
 * Jackson serializers the writer replaced.
 */
class NostrJsonWriterPinnedTest {
    @get:Rule val testName = TestName()

    private val pin = Pin("")

    @AfterTest
    fun checkPin() {
        val name = testName.methodName.substringBefore('[')
        pin.name = name
        pin.check(PINS.getValue(name))
    }

    private val sha = MessageDigest.getInstance("SHA-256")

    /** Every way the writer serializes [e], pinned. */
    private fun assertPinned(e: Event) {
        val idBytes = EventHasherSerializer.fastMakeJsonForId(e.pubKey, e.createdAt, e.kind, e.tags, e.content)
        pin.add(NostrJsonWriter.toJson(e))
        pin.add(NostrJsonWriter.toJson(e.tags))
        pin.add(EventHasherSerializer.makeJsonForId(e.pubKey, e.createdAt, e.kind, e.tags, e.content))
        pin.add(idBytes.toHexKey())
        pin.add(NostrJsonWriter.toJson(Rumor.create(e)))
        assertContentEquals(sha.digest(idBytes), NostrJsonWriter.idHash(e.pubKey, e.createdAt, e.kind, e.tags, e.content), "id hash for ${NostrJsonWriter.toJson(e).take(300)}")
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
    fun everyCapturedEventWritesAsPinned() {
        var count = 0
        var validIds = 0
        var validIdsFromBytes = 0
        forEachCapturedEvent { json ->
            val e = NostrJsonReader.readEvent(json)
            assertPinned(e)
            if (EventHasherSerializer.makeJsonForIdHashAndCheck(e.id, e.pubKey, e.createdAt, e.kind, e.tags, e.content)) validIds++
            if (sha.digest(EventHasherSerializer.fastMakeJsonForId(e.pubKey, e.createdAt, e.kind, e.tags, e.content)).toHexKey() == e.id) validIdsFromBytes++

            val frame = EventMessage("sub", e)
            pin.add(NostrJsonWriter.toJsonOrNull(frame))
            assertEquals(frame.toJson(), NostrJsonWriter.toJsonOrNull(frame))
            val cmd = EventCmd(e)
            pin.add(NostrJsonWriter.toJsonOrNull(cmd))
            assertEquals(cmd.toJson(), NostrJsonWriter.toJsonOrNull(cmd))
            count++
        }
        println("captured events: $count written, $validIds with a matching id")
        assertTrue(count > 200_000, "expected the whole capture, got $count")
        // Some captured events carry an id that does not match their fields; both id paths must agree on which.
        assertEquals(validIdsFromBytes, validIds)
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
    fun oddStringsWriteAsPinned() {
        for (s in oddStrings) {
            assertPinned(event(s, arrayOf(arrayOf("t", s), arrayOf(s))))
            assertPinned(event("x", arrayOf(arrayOf(s, s, s))))
        }
        assertPinned(event("", emptyArray()))
        assertPinned(event("", arrayOf(emptyArray())))
        assertPinned(event(oddStrings.joinToString("|"), arrayOf(oddStrings.toTypedArray())))
        for (n in listOf(0L, 1L, 9L, 10L, -1L, -10L, Long.MAX_VALUE, Long.MIN_VALUE, Int.MAX_VALUE.toLong(), 1_000_000_000_000L)) {
            assertPinned(event("n", emptyArray(), createdAt = n, kind = (n % 70_000).toInt()))
        }
        assertPinned(event("k", emptyArray(), kind = Int.MIN_VALUE))
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
    fun randomStringsWriteAsPinned() {
        val random = Random(4595)
        repeat(20_000) {
            val tags = Array(random.nextInt(0, 4)) { Array(random.nextInt(0, 4)) { randomString(random) } }
            assertPinned(event(randomString(random), tags, createdAt = random.nextLong(), kind = random.nextInt()))
        }
    }

    @Test
    fun bigEventsGrowAndReleaseTheBuffers() {
        // Past every buffer the writer keeps, with escapes and multi-byte chars spread through.
        val big = buildString { repeat(30_000) { append(oddStrings[it % oddStrings.size]) } }
        assertPinned(event(big, arrayOf(arrayOf("t", big))))
        assertPinned(event("small after big", arrayOf(arrayOf("p", "a".repeat(64)))))
        val tags = Array(5_000) { arrayOf("p", it.toString().padStart(64, '0'), "wss://relay.example.com/", "") }
        assertPinned(event("contact list", tags, kind = 3))
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
            EventCmd(sampleEvent, NostrJsonWriter.toJson(sampleEvent)),
            EventCmd(sampleEvent, "{ \"raw\" : true }"),
            CloseCmd("sub"),
            CloseCmd("\u2028"),
            NegOpenCmd("neg", filters[3], "6100000200"),
            NegMsgCmd("neg", "61"),
            NegCloseCmd("neg"),
        )

    @Test
    fun everyMessageTypeWritesAsPinned() {
        for (msg in messages) {
            pin.add(NostrJsonWriter.toJsonOrNull(msg))
            // The type's own toJson, fast paths included.
            assertEquals(NostrJsonWriter.toJsonOrNull(msg), msg.toJson(), msg::class.simpleName)
        }
    }

    @Test
    fun everyCommandTypeWritesAsPinned() {
        for (cmd in commands) {
            pin.add(NostrJsonWriter.toJsonOrNull(cmd))
            assertEquals(NostrJsonWriter.toJsonOrNull(cmd), cmd.toJson(), cmd::class.simpleName)
        }
    }

    @Test
    fun filtersAndRumorsWriteAsPinned() {
        for (f in filters) {
            pin.add(NostrJsonWriter.toJson(f))
            assertEquals(NostrJsonWriter.toJson(f), f.toJson())
        }
        val rumors =
            listOf(
                Rumor(null, null, null, null, null, null),
                Rumor("a".repeat(64), null, 5, null, emptyArray(), null),
                Rumor(null, "b".repeat(64), null, 14, arrayOf(arrayOf("p", "\"")), "dm \uD83D"),
                Rumor.create(sampleEvent),
            )
        for (r in rumors) {
            pin.add(NostrJsonWriter.toJson(r))
            assertEquals(NostrJsonWriter.toJson(r), Rumor.toJson(r))
        }
    }

    @Test
    fun threadsWritingAtOnceDoNotShareState() {
        val random = Random(7)
        val events = List(500) { event(randomString(random), arrayOf(arrayOf("t", randomString(random)))) }
        // What one thread alone writes.
        val expectedJson = events.map { NostrJsonWriter.toJson(it) }
        val expectedId = events.map { sha.digest(EventHasherSerializer.fastMakeJsonForId(it.pubKey, it.createdAt, it.kind, it.tags, it.content)) }
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
    fun unknownTypesAreLeftToKotlinx() {
        val custom =
            object : Message {
                override fun label() = "CUSTOM"
            }
        assertEquals(null, NostrJsonWriter.toJsonOrNull(custom))
        if (NostrJsonWriter.toJsonOrNull(custom as OptimizedSerializable) != null) fail("expected null")
    }

    companion object {
        /** See [Pin]: recorded while every output was also compared with Jackson. */
        private val PINS =
            mapOf(
                "everyCapturedEventWritesAsPinned" to "9e5eaceae7542873dc146c65e6d90a835f443a3ab89eb3490b9d3f0ddd01250e",
                "oddStringsWriteAsPinned" to "04e18de89fa95d22a9f73f09ea2843a2d3a5874fcb8173c31fa764bf6d4ac797",
                "randomStringsWriteAsPinned" to "8659a51ab78aa485ffbbf486ebdae79d9eda546133e90b294a45da77a5c4191e",
                "bigEventsGrowAndReleaseTheBuffers" to "c9a492b36f5020abcced842d40ce912c10b056b38b292acc403bf63e7a4c3932",
                "everyMessageTypeWritesAsPinned" to "58f694b89e08afa1372c740b77173796ad04dcfc45e280af661612c82a8f2baa",
                "everyCommandTypeWritesAsPinned" to "ed5ef44394d23eaed7d6877c8454d61bebfe7092cb2db7d11e36e72e8d943bea",
                "filtersAndRumorsWriteAsPinned" to "ac358d4667a389f04205e5a31bd4114464faafc7869377885a70cd45ebf80ece",
                "threadsWritingAtOnceDoNotShareState" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "unknownTypesAreLeftToKotlinx" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            )

        const val CORPUS = "nostr_vitor_startup_data.json.gz"
    }
}
