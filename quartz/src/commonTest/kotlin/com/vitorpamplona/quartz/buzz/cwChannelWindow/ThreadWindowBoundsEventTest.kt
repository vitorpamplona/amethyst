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
package com.vitorpamplona.quartz.buzz.cwChannelWindow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ThreadWindowBoundsEventTest {
    private val nilChannel = "00000000-0000-0000-0000-000000000000"
    private val root = "ab".repeat(32)
    private val reader = "ab".repeat(32)
    private val host = "relay.example"

    @Test
    fun bindingMatchesUpstreamKnownAnswer() {
        // `binding_normalizes_defaults_and_binds_every_argument` in buzz-core/src/thread_window.rs.
        val request = ThreadWindowRequest(nilChannel, root, kinds = listOf(9))
        assertEquals("tw:1:5252322dfd797ddb1d5f1150acd4cf914b9fc09e39bcf3048d25aed514b3546d", request.binding(host, reader))

        // Explicit defaults and an uppercase root normalize to the same binding.
        assertEquals(request.binding(host, reader), ThreadWindowRequest(nilChannel, root.uppercase(), listOf(9, 9), limit = 50, depth = 100, includeAux = false).binding(host, reader))

        // Every argument is bound.
        val base = request.binding(host, reader)
        for (changed in listOf(
            request.copy(limit = 49),
            request.copy(depth = 1),
            request.copy(includeAux = true),
            request.copy(kinds = listOf(40002)),
            request.copy(rootId = "cd".repeat(32)),
            request.copy(channelId = "9b353519-f4fe-4757-aef4-bec6cc0ae54c"),
            request.copy(cursor = NextCursor(0, "ab".repeat(32))),
        )) {
            assertNotEquals(base, changed.binding(host, reader), changed.toString())
        }
        assertNotEquals(base, request.binding("other.example", reader))
        assertNotEquals(base, request.binding(host, "cd".repeat(32)))
    }

    @Test
    fun requestRejectsWhatTheRelayRejects() {
        assertFailsWith<IllegalArgumentException> { ThreadWindowRequest(nilChannel, root, kinds = listOf(7)) }
        assertFailsWith<IllegalArgumentException> { ThreadWindowRequest(nilChannel, root, kinds = emptyList()) }
        assertFailsWith<IllegalArgumentException> { ThreadWindowRequest(nilChannel, root, listOf(9), limit = 0) }
        assertFailsWith<IllegalArgumentException> { ThreadWindowRequest(nilChannel, root, listOf(9), limit = 201) }
        assertFailsWith<IllegalArgumentException> { ThreadWindowRequest(nilChannel, root, listOf(9), depth = 101) }
        assertFailsWith<IllegalArgumentException> { ThreadWindowRequest(nilChannel, "bad", listOf(9)) }
    }

    @Test
    fun relayShapedOverlayParsesAndMatches() {
        val request = ThreadWindowRequest(nilChannel, root, kinds = listOf(9))
        val tags = arrayOf(arrayOf("d", request.binding(host, reader)), arrayOf("h", nilChannel), arrayOf("e", root))
        // serde_json's `json!` sorts keys, so the relay's body arrives alphabetized.
        val content = """{"direction":"older","has_more":true,"next_cursor":{"created_at":1751500000,"id":"${"c".repeat(64)}"},"version":1}"""
        val ev = ThreadWindowBoundsEvent("0".repeat(64), "e".repeat(64), 1, tags, content, "sig")

        assertEquals(39007, ev.kind)
        assertEquals(nilChannel, ev.channelId())
        assertEquals(root, ev.rootId())
        assertTrue(ev.bounds().hasMore)
        assertEquals(1751500000L, ev.bounds().nextCursor?.createdAt)
        assertTrue(ev.isWellFormed())
        assertTrue(ev.matches(request, host, reader))
        assertFalse(ev.matches(request.copy(limit = 10), host, reader))
        assertFalse(ev.matches(request, "other.example", reader))
    }

    @Test
    fun exhaustedPageRoundTrips() {
        val request = ThreadWindowRequest(nilChannel, root, kinds = listOf(40002, 9), includeAux = true)
        val tpl = ThreadWindowBoundsEvent.build(request, host, reader, ThreadWindowBoundsContent(hasMore = false))
        assertTrue(tpl.content.contains("\"next_cursor\":null"), tpl.content)
        val ev = ThreadWindowBoundsEvent("0".repeat(64), "e".repeat(64), tpl.createdAt, tpl.tags, tpl.content, "sig")
        assertEquals(listOf("d", "h", "e"), ev.tags.map { it[0] })
        assertTrue(ev.isWellFormed())
        assertTrue(ev.matches(request, host, reader))
    }

    @Test
    fun malformedOverlaysAreRejected() {
        val request = ThreadWindowRequest(nilChannel, root, kinds = listOf(9))
        val d = arrayOf("d", request.binding(host, reader))
        val h = arrayOf("h", nilChannel)
        val e = arrayOf("e", root)
        val good = """{"version":1,"direction":"older","has_more":false,"next_cursor":null}"""

        fun ev(
            tags: Array<Array<String>>,
            content: String = good,
        ) = ThreadWindowBoundsEvent("0".repeat(64), "e".repeat(64), 1, tags, content, "sig")

        assertTrue(ev(arrayOf(d, h, e)).isWellFormed())
        assertFalse(ev(arrayOf(d, h)).isWellFormed())
        assertFalse(ev(arrayOf(d, h, e, arrayOf("p", reader))).isWellFormed())
        assertFalse(ev(arrayOf(d, h, arrayOf("e", root, "wss://relay"))).isWellFormed())
        assertFalse(ev(arrayOf(arrayOf("d", "$nilChannel:head"), h, e)).isWellFormed())
        assertFalse(ev(arrayOf(d, arrayOf("h", nilChannel.uppercase().replace('0', 'A')), e)).isWellFormed())
        assertFalse(ev(arrayOf(d, h, arrayOf("e", root.uppercase()))).isWellFormed())
        // has_more without a cursor, a cursor without has_more, wrong version or direction.
        assertFalse(ev(arrayOf(d, h, e), """{"version":1,"direction":"older","has_more":true,"next_cursor":null}""").isWellFormed())
        assertFalse(ev(arrayOf(d, h, e), """{"version":1,"direction":"older","has_more":false,"next_cursor":{"created_at":1,"id":"$root"}}""").isWellFormed())
        assertFalse(ev(arrayOf(d, h, e), """{"version":2,"direction":"older","has_more":false,"next_cursor":null}""").isWellFormed())
        assertFalse(ev(arrayOf(d, h, e), """{"version":1,"direction":"newer","has_more":false,"next_cursor":null}""").isWellFormed())
    }
}
