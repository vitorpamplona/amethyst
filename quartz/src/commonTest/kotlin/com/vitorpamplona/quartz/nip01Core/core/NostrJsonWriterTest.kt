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

import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.LimitsMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [NostrJsonWriter]'s exact output on every target. The JVM suite also checks it byte for byte
 * against Jackson (NostrJsonWriterJacksonParityTest); these pin the same rules where Jackson
 * does not run.
 */
class NostrJsonWriterTest {
    private val signed =
        """{"id":"0da25a3f43de63544610b0e488d5ead47626d3566ac722fe482d7eeb416f50ef","pubkey":"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c","created_at":1709640151,"kind":1,"tags":[["r","https://github.com/nostr-protocol/nips/pull/997"]],"content":"Git NIP is merged. 🚀 \n\nGood morning. \n\nhttps://github.com/nostr-protocol/nips/pull/997","sig":"e0039c218213e5f7e8312adfda6945f648c410790baf75ee4293bf36b4290c150ed6a3f0f004324481b7b28f5d979c16cfa9964882cb330273245f27d6b72b19"}"""

    private val pk = "b".repeat(64)

    private fun event(
        content: String,
        tags: TagArray = emptyArray(),
    ) = Event("a".repeat(64), pk, 1, 1, tags, content, "c".repeat(128))

    @Test
    fun aSignedEventWritesBackToItsOwnJsonAndId() {
        val e = NostrJsonReader.readEvent(signed)
        assertEquals(signed, NostrJsonWriter.toJson(e))
        assertEquals(e.id, NostrJsonWriter.idHash(e.pubKey, e.createdAt, e.kind, e.tags, e.content).toHexKey())
        assertTrue(EventHasher.hashIdCheck(e.id, e.pubKey, e.createdAt, e.kind, e.tags, e.content))
        assertEquals(e.id, EventHasher.hashId(e.pubKey, e.createdAt, e.kind, e.tags, e.content))
    }

    @Test
    fun escapesLikeJackson() {
        // The short forms, every other control char as uppercase \u00XX, and DEL, U+2028 and `/` as is.
        val e = event("a\u0001\u001F\"\\\n\b\u000C\r\t/\u007F\u2028")
        val expected = "\"a\\u0001\\u001F\\\"\\\\\\n\\b\\f\\r\\t/\u007F\u2028\""
        assertEquals(
            "{\"id\":\"${e.id}\",\"pubkey\":\"$pk\",\"created_at\":1,\"kind\":1,\"tags\":[],\"content\":$expected,\"sig\":\"${e.sig}\"}",
            NostrJsonWriter.toJson(e),
        )
        assertEquals("[[$expected,\"\"],[]]", NostrJsonWriter.toJson(arrayOf(arrayOf(e.content, ""), emptyArray())))
        assertEquals(
            "[0,\"$pk\",1,1,[],$expected]".encodeToByteArray().toList(),
            EventHasherSerializer.fastMakeJsonForId(pk, 1, 1, emptyArray(), e.content).toList(),
        )
    }

    @Test
    fun surrogatesStayAsIsInTextAndAreEscapedAloneInUtf8() {
        val lone = "x\uD83Dy\uDE00"
        assertEquals("[0,\"$pk\",1,1,[],\"$lone\"]", EventHasherSerializer.makeJsonForId(pk, 1, 1, emptyArray(), lone))
        assertEquals(
            "[0,\"$pk\",1,1,[],\"x\\uD83Dy\\uDE00\"]".encodeToByteArray().toList(),
            EventHasherSerializer.fastMakeJsonForId(pk, 1, 1, emptyArray(), lone).toList(),
        )

        val bytes = EventHasherSerializer.fastMakeJsonForId(pk, 1, 1, emptyArray(), "\uD83D\uDE00\u00E9\u6F22")
        val tail = bytes.copyOfRange(bytes.size - 11, bytes.size)
        assertContentEquals(
            byteArrayOf(0x22, 0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte(), 0xC3.toByte(), 0xA9.toByte(), 0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte(), 0x22),
            tail.copyOfRange(0, 11).let { it.copyOfRange(0, 11) }.let { bytes.copyOfRange(bytes.size - 12, bytes.size - 1) },
        )
    }

    @Test
    fun numbersAtTheEdges() {
        assertEquals(
            "[0,\"$pk\",-9223372036854775808,-2147483648,[],\"\"]",
            EventHasherSerializer.fastMakeJsonForId(pk, Long.MIN_VALUE, Int.MIN_VALUE, emptyArray(), "").decodeToString(),
        )
        assertEquals(
            "[0,\"$pk\",9223372036854775807,0,[],\"\"]",
            EventHasherSerializer.fastMakeJsonForId(pk, Long.MAX_VALUE, 0, emptyArray(), "").decodeToString(),
        )
        assertEquals(
            EventHasherSerializer.makeJsonForId(pk, -10, 10, arrayOf(arrayOf("t", "x")), "y"),
            EventHasherSerializer.fastMakeJsonForId(pk, -10, 10, arrayOf(arrayOf("t", "x")), "y").decodeToString(),
        )
    }

    @Test
    fun framesInTheirWireForm() {
        assertEquals("[\"OK\",\"ab\",true,\"\"]", OkMessage("ab", true, "").toJson())
        assertEquals("[\"OK\",\"ab\",false,\"rate-limited: \\\"x\\\"\",\"500\"]", OkMessage("ab", false, "rate-limited: \"x\"", "500").toJson())
        assertEquals("[\"EOSE\",\"s\",[\"finish\"]]", EoseMessage("s", listOf("finish")).toJson())
        assertEquals("[\"NEG-ERR\",\"s\",\"blocked\",7]", NegErrMessage("s", "blocked", 7).toJson())
        assertEquals("[\"COUNT\",\"q\",{\"count\":3,\"approximate\":true,\"hll\":\"00ff\"}]", CountMessage("q", CountResult(3, true, byteArrayOf(0, -1))).toJson())
        assertEquals("[\"LIMITS\",{\"can_write\":false,\"max_limit\":10,\"required_tags\":[[\"t\"]]}]", LimitsMessage(canWrite = false, maxLimit = 10, requiredTags = listOf(listOf("t"))).toJson())
        assertEquals("[\"LIMITS\",{}]", LimitsMessage().toJson())

        val filter = Filter(kinds = listOf(1), tags = mapOf("e" to listOf("x"), "a\"" to emptyList()), tagsAll = mapOf("t" to listOf("y")), since = 5, limit = 2, search = "q")
        assertEquals("{\"kinds\":[1],\"#e\":[\"x\"],\"#a\\\"\":[],\"&t\":[\"y\"],\"since\":5,\"limit\":2,\"search\":\"q\"}", filter.toJson())
        assertEquals("[\"REQ\",\"s\",{},{\"kinds\":[1,2]}]", ReqCmd("s", listOf(Filter(), Filter(kinds = listOf(1, 2)))).toJson())
        assertEquals("[\"EVENT\",{\"raw\":1}]", EventCmd(event(""), "{\"raw\":1}").toJson())

        assertEquals("{}", Rumor.toJson(Rumor(null, null, null, null, null, null)))
        assertEquals("{\"pubkey\":\"p\",\"kind\":14,\"tags\":[],\"content\":\"\"}", Rumor.toJson(Rumor(null, "p", null, 14, emptyArray(), "")))
    }

    @Test
    fun bigValuesThenSmallOnes() {
        val big = "\u00FC\"".repeat(40_000)
        val e = event(big, arrayOf(arrayOf("t", big)))
        val json = NostrJsonWriter.toJson(e)
        assertEquals(e.content, NostrJsonReader.readEvent(json).content)
        assertEquals(
            NostrJsonWriter.idPreimageJson(e.pubKey, e.createdAt, e.kind, e.tags, e.content),
            NostrJsonWriter.idPreimageBytes(e.pubKey, e.createdAt, e.kind, e.tags, e.content).decodeToString(),
        )
        assertEquals("[[\"a\"]]", NostrJsonWriter.toJson(arrayOf(arrayOf("a"))))
    }
}
