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
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NostrJsonReaderEventTest {
    private val id = "0a5b8e8f2b4b0a6b1f0b64e5d0e9a8f7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a291"
    private val pubkey = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
    private val sig = "908a15e46fb4d8675bab026fc230a0e3542bfade63da02d542fb78b2a8513fcd0092619a2c8c1221e581946e0191f2af505dfdf8657a414dbca329186f009262"

    private fun event(
        content: String = "\"hello\"",
        tags: String = "[[\"e\",\"abc\",\"wss://relay.example\",\"root\"],[\"p\",\"def\"]]",
        createdAt: String = "1700000000",
        kind: String = "1",
        extra: String = "",
    ) = """{"id":"$id","pubkey":"$pubkey","created_at":$createdAt,"kind":$kind,"tags":$tags,"content":$content,"sig":"$sig"$extra}"""

    private fun read(json: String) = NostrJsonReader.readEvent(json)

    private fun contentOf(raw: String) = read(event(content = raw)).content

    @Test
    fun readsEveryField() {
        val e = read(event())
        assertEquals(id, e.id)
        assertEquals(pubkey, e.pubKey)
        assertEquals(1700000000L, e.createdAt)
        assertEquals(1, e.kind)
        assertContentEquals(arrayOf("e", "abc", "wss://relay.example", "root"), e.tags[0])
        assertContentEquals(arrayOf("p", "def"), e.tags[1])
        assertEquals("hello", e.content)
        assertEquals(sig, e.sig)
    }

    @Test
    fun buildsTheKindsClass() {
        assertIs<LnZapEvent>(read(event(kind = "9735", tags = "[]")))
    }

    @Test
    fun readsJsonInsideContent() {
        val json =
            """{"id":"b8e0fdc42eaf0ee0908ce04bacf69838c9d7184b51a2773c5f22a30fe40feeca","pubkey":"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c","created_at":1710026692,"kind":0,"tags":[["alt","User profile for Vitor Pamplona"],["i","twitter:vitorpamplona","1603751686371217409"],["i","github:vitorpamplona","cf19e2d1d7f8dac6348ad37b35ec8421"]],"content":"{\"lud06\":\"LNURL1DP68GURN8GHJ7EM9W3SKCCNE9E3K7MF0D3H82UNVWQHKWUN9V4HXGCTHDC6RZVGR8SW3G\",\"lud16\":\"vitor@vitorpamplona.com\",\"display_name\":\"Vitor Pamplona\",\"name\":\"vitor\",\"banner\":\"https://vitorpamplona.com/images/banner_amethyst.png\",\"website\":\"https://vitorpamplona.com\",\"nip05\":\"_@vitorpamplona.com\",\"about\":\"Developer of Amethyst\",\"picture\":\"https://vitorpamplona.com/images/me_300.jpg\"}","sig":"a1d3f5f2fe6f2fda3c1ddcbbc6d4d3e9a84ea4e01fbb2fa0b5b0e16d0b8bd4e9b8c3d6d2c95cbb7f1ba1bc5a3dc69ba6a07e9b1ea2c42d2e4b2fbde3dbf5e1f1"}"""
        val e = read(json)
        assertEquals(0, e.kind)
        assertTrue(e.content.startsWith("{\"lud06\":\"LNURL1"))
        assertEquals(3, e.tags.size)
    }

    @Test
    fun decodesEveryEscape() {
        assertEquals("a\"b", contentOf("\"a\\\"b\""))
        assertEquals("a\\b", contentOf("\"a\\\\b\""))
        assertEquals("a/b", contentOf("\"a\\/b\""))
        assertEquals("\b\u000C\n\r\t", contentOf("\"\\b\\f\\n\\r\\t\""))
        assertEquals("caf\u00e9", contentOf("\"caf\\u00e9\""))
        assertEquals("\u00e9\u00e9", contentOf("\"\\u00E9\\u00e9\""))
        assertEquals("\uD83D\uDE00 smile", contentOf("\"\\uD83D\\uDE00 smile\""))
        assertEquals("\u0000nul", contentOf("\"\\u0000nul\""))
        assertEquals("a\"b", contentOf("\"a\\u0022b\""))
        assertEquals("\\", contentOf("\"\\u005c\""))
        assertEquals("end\\", contentOf("\"end\\\\\""))
        assertEquals("", contentOf("\"\""))
    }

    @Test
    fun keepsLoneSurrogatesLikeJackson() {
        assertEquals("lone \uD800 high", contentOf("\"lone \\uD800 high\""))
        assertEquals("lone \uDC00 low", contentOf("\"lone \\uDC00 low\""))
    }

    @Test
    fun keepsRawCharacters() {
        assertEquals("raw \u00e9 \u65e5\u672c \uD83D\uDE00", contentOf("\"raw \u00e9 \u65e5\u672c \uD83D\uDE00\""))
        // Raw control characters are allowed (Quartz's Jackson enables ALLOW_UNESCAPED_CONTROL_CHARS).
        assertEquals("raw\ttab", contentOf("\"raw\ttab\""))
        assertEquals("raw\nnewline", contentOf("\"raw\nnewline\""))
        assertEquals("raw\u0001ctl", contentOf("\"raw\u0001ctl\""))
    }

    @Test
    fun decodesEscapesAfterAnEarlierEscapedValue() {
        // The backslash tracker must move on correctly from one value to the next.
        val e =
            read(
                """{"id":"$id","pubkey":"$pubkey","created_at":1,"kind":1,"tags":[["a","x\ny"],["b","plain"],["c","\"q\""]],"content":"z\\z","sig":"$sig"}""",
            )
        assertEquals("x\ny", e.tags[0][1])
        assertEquals("plain", e.tags[1][1])
        assertEquals("\"q\"", e.tags[2][1])
        assertEquals("z\\z", e.content)
        assertEquals(sig, e.sig)
    }

    @Test
    fun readsLongContentFullOfEscapes() {
        val line = "a line of text with a \\\"quote\\\" and \\u00e9\\n"
        val decoded = "a line of text with a \"quote\" and \u00e9\n"
        val e = read(event(content = "\"" + line.repeat(5000) + "\""))
        assertEquals(decoded.repeat(5000), e.content)
        assertEquals(sig, e.sig)
    }

    @Test
    fun skipsEscapedUnknownValuesAndChecksThem() {
        assertEquals(sig, read(event(extra = ",\"x\":\"a\\\"b\\\\\\u00e9\\n\"")).sig)
        assertEquals(sig, read(event(extra = ",\"x\":{\"k\\\"ey\":[\"v\\\\\",\"w\"]}")).sig)
        for (bad in listOf("\"x\\q\"", "\"x\\u12G4\"", "[\"\\\"]", "{\"a\\\":1}")) {
            assertFailsWith<IllegalArgumentException>(bad) { read(event(extra = ",\"x\":$bad")) }
        }
    }

    @Test
    fun rejectsBadEscapes() {
        for (bad in listOf("\"x\\u\"", "\"x\\u12\"", "\"x\\uZZZZ\"", "\"x\\q\"", "\"x\\", "\"unterminated", "\"x\\u00\u2730\u2731\"")) {
            assertFailsWith<IllegalArgumentException>(bad) { contentOf(bad) }
        }
    }

    @Test
    fun readsTagValuesLikeTagArrayDeserializer() {
        assertContentEquals(arrayOf("a", "", "b"), read(event(tags = "[[\"a\",null,\"b\"]]")).tags[0])
        assertContentEquals(arrayOf("1", "2.5", "-3", "1e5"), read(event(tags = "[[1,2.5,-3,1e5]]")).tags[0])
        assertContentEquals(arrayOf("true", "false"), read(event(tags = "[[true,false]]")).tags[0])
        assertEquals(0, read(event(tags = "[]")).tags.size)
        assertEquals(0, read(event(tags = "[[]]")).tags[0].size)
        assertEquals(2, read(event(tags = "[[],[]]")).tags.size)
        val many = (1..40).joinToString(",") { "\"v$it\"" }
        assertEquals(40, read(event(tags = "[[$many]]")).tags[0].size)
        val manyTags = (1..40).joinToString(",") { "[\"t\",\"$it\"]" }
        assertEquals(40, read(event(tags = "[$manyTags]")).tags.size)
    }

    @Test
    fun rejectsMalformedTags() {
        for (bad in listOf("[[\"a\",[\"b\"]]]", "[[\"a\",{\"b\":1}]]", "[\"a\"]", "[null]", "[1]", "{}", "null", "[[\"a\",]]", "[[\"a\"],]", "[[\"a\" \"b\"]]", "[[\"a\"]")) {
            assertFailsWith<IllegalArgumentException>(bad) { read(event(tags = bad)) }
        }
    }

    @Test
    fun readsNumbersLikeJackson() {
        assertEquals(0L, read(event(createdAt = "0")).createdAt)
        assertEquals(-1L, read(event(createdAt = "-1")).createdAt)
        assertEquals(1700000000L, read(event(createdAt = "1.7e9")).createdAt)
        assertEquals(1700000000L, read(event(createdAt = "1700000000.9")).createdAt)
        assertEquals(Long.MAX_VALUE, read(event(createdAt = "9223372036854775807")).createdAt)
        assertEquals(2147483647, read(event(kind = "2147483647")).kind)
        assertEquals(7, read(event(kind = "7.9")).kind)
    }

    @Test
    fun rejectsBadNumbers() {
        for (bad in listOf("01", "+1", "-", "1.", ".5", "1e", "--1", "\"1\"", "null", "true", "9223372036854775808", "1e400", "1E")) {
            assertFailsWith<IllegalArgumentException>("created_at $bad") { read(event(createdAt = bad)) }
        }
        for (bad in listOf("2147483648", "-2147483649", "13e11")) {
            assertFailsWith<IllegalArgumentException>("kind $bad") { read(event(kind = bad)) }
        }
    }

    @Test
    fun rejectsNonStringValuesWhereStringsBelong() {
        for (bad in listOf("null", "1", "true", "[]", "{}")) {
            assertFailsWith<IllegalArgumentException>(bad) { read(event(content = bad)) }
        }
    }

    @Test
    fun skipsUnknownFieldsOfAnyShape() {
        for (u in listOf("1", "\"x\"", "\"a\\\"]}\"", "{\"a\":[1,{\"b\":\"]}\\\"\"}]}", "[[[]]]", "true", "false", "null", "-1.5e3", "{}", "[]", "\"\\u0069d\"")) {
            val e = read(event(extra = ",\"extra\":$u"))
            assertEquals("hello", e.content, u)
            assertEquals(sig, e.sig, u)
        }
        val first = read("{\"extra\":{\"id\":\"no\"}," + event().substring(1))
        assertEquals(id, first.id)
    }

    @Test
    fun rejectsMalformedUnknownFields() {
        for (bad in listOf("[1,]", "{\"a\"}", "{\"a\":}", "tru", "01", "[\"a\" \"b\"]", "{a:1}")) {
            assertFailsWith<IllegalArgumentException>(bad) { read(event(extra = ",\"extra\":$bad")) }
        }
    }

    private fun nested(k: Int) = "[".repeat(k) + "]".repeat(k)

    @Test
    fun nestsAsDeepAsJackson() {
        // Jackson's limit is 1,000 levels, the event object included; an EVENT frame's array adds one.
        assertEquals(sig, read(event(extra = ",\"x\":${nested(999)}")).sig)
        assertFailsWith<IllegalArgumentException> { read(event(extra = ",\"x\":${nested(1000)}")) }
        assertEquals(sig, frame("[\"EVENT\",\"s\",${event(extra = ",\"x\":${nested(998)}")}]").event.sig)
        assertFailsWith<IllegalArgumentException> { NostrJsonReader.readMessage("[\"EVENT\",\"s\",${event(extra = ",\"x\":${nested(999)}")}]") }
        val objects = "{\"a\":".repeat(999) + "1" + "}".repeat(999)
        assertEquals(sig, read(event(extra = ",\"x\":$objects")).sig)
        // Deep nesting is a counter, not recursion: far past the limit it still fails cleanly.
        assertFailsWith<IllegalArgumentException> { read(event(extra = ",\"x\":${nested(100_000)}")) }
    }

    @Test
    fun matchesKeysByTheirText() {
        assertEquals(id, read(event().replace("\"id\":", "\"\\u0069d\":")).id)
        // "jE" has the same String.hashCode as "id"; it is not the id.
        assertEquals("", read(event().replace("\"id\":", "\"jE\":")).id)
        assertEquals("", read(event().replace("\"sig\":", "\"sih\":")).sig)
        assertFailsWith<IllegalArgumentException> { read(event().replace("\"pubkey\":", "\"pubKey\":")) }
    }

    @Test
    fun lastDuplicateFieldWins() {
        assertEquals(7, read(event(kind = "1,\"kind\":7")).kind)
    }

    @Test
    fun allowsWhitespaceEverywhere() {
        val spaced =
            event()
                .replace(":", " :\n\t ")
                .replace(",", " \r\n, ")
                .replace("{", "{ ")
                .replace("}", " }")
                .replace("[", "[ ")
                .replace("]", " ]")
        val e = read("  \n$spaced\n  ")
        assertEquals(id, e.id)
        assertContentEquals(arrayOf("p", "def"), e.tags[1])
    }

    @Test
    fun ignoresTrailingData() {
        for (t in listOf("x", "}", "]", ",{}", " garbage")) assertEquals(id, read(event() + t).id)
    }

    @Test
    fun failsWithoutAPubkey() {
        assertFailsWith<IllegalArgumentException> { read("{}") }
        assertFailsWith<IllegalArgumentException> { read("{\"id\":\"$id\"}") }
    }

    @Test
    fun rejectsEveryTruncation() {
        val json = event(content = "\"a\\nb \\uD83D\\uDE00\"")
        for (k in 0 until json.length) {
            assertFailsWith<IllegalArgumentException>("prefix $k") { read(json.substring(0, k)) }
        }
    }

    private fun frame(json: String) = NostrJsonReader.readMessage(json) as EventMessage

    @Test
    fun readsEventFrames() {
        val msg = frame("[\"EVENT\",\"sub1\",${event()}]")
        assertEquals("sub1", msg.subId)
        assertEquals(id, msg.event.id)

        val spaced = frame(" [ \"EVENT\" , \"sub\\\"2\" , ${event()} ] ")
        assertEquals("sub\"2", spaced.subId)

        // Extra elements are skipped, like MessageDeserializer; trailing data after the frame is ignored.
        assertEquals("s", frame("[\"EVENT\",\"s\",${event()},{\"x\":[1]},\"y\"]").subId)
        assertEquals("s", frame("[\"EVENT\",\"s\",${event()}] trailing").subId)
    }

    @Test
    fun readsAnEscapedEventLabel() {
        assertEquals("s", frame("[\"EV\\u0045NT\",\"s\",${event()}]").subId)
        assertFailsWith<IllegalArgumentException> { NostrJsonReader.readMessage("[\"EV\\u0045NTS\",\"s\",${event()}]") }
    }

    @Test
    fun rejectsMalformedEventFrames() {
        // A frame cut before its closing bracket fails, as in Jackson.
        for (bad in listOf("{}", "", "[\"EVENT\"]", "[\"EVENT\",1,{}]", "[\"EVENT\",\"s\"]", "[\"EVENT\",\"s\",{}]", "[\"EVENT\",\"s\",${event()},]", "[\"EVENT\",\"s\",${event()} x]", "[\"EVENT\",\"s\",${event()}", "[\"EVENT\",\"s\",${event()},")) {
            assertFailsWith<IllegalArgumentException>(bad) { NostrJsonReader.readMessage(bad) }
        }
    }

    @Test
    fun goesThroughOptimizedJsonMapper() {
        assertEquals(id, OptimizedJsonMapper.fromJson(event()).id)
        assertEquals(id, Event.fromJson(event()).id)
    }
}
