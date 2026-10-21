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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/** Tags must be an array of arrays of scalars; anything else fails instead of being read past. */
class TagArrayDeserializerTest {
    private val pk = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"

    private fun event(tags: String) = """{"id":"ab","pubkey":"$pk","created_at":1,"kind":1,"tags":$tags,"content":"x","sig":"cd"}"""

    private fun rejects(
        json: String,
        parse: (String) -> Any = JacksonMapper::fromJson,
    ) {
        try {
            parse(json)
        } catch (e: Exception) {
            return
        }
        fail("accepted $json")
    }

    @Test
    fun malformedTagsAreRejected() {
        for (tags in listOf("{}", """{"a":["b"]}""", "null", "5", "\"t\"", "true", "[{}]", """[{"a":1}]""", "[\"a\"]", "[null]", "[5]", """[["a",{}]]""", """[["a",["b"]]]""", "[[[]]]")) {
            rejects(event(tags))
            rejects("""["EVENT","s",${event(tags)}]""", JacksonMapper::fromJsonToMessage)
        }
        assertNull(Event.fromJsonOrNull(event("{}")))
    }

    @Test
    fun malformedTagsAreRejectedInTemplatesRumorsAndTagArrays() {
        for (tags in listOf("{}", "null", "[{}]", """[["a",{}]]""")) {
            rejects("""{"created_at":1,"kind":1,"tags":$tags,"content":"x"}""", JacksonMapper::fromJsonToEventTemplate)
            rejects("""{"pubkey":"$pk","created_at":1,"kind":1,"tags":$tags,"content":"x"}""", JacksonMapper::fromJsonToRumor)
        }
        for (tags in listOf("{}", "[{}]", """[["a",{}]]""", "[\"a\"]", "[[[]]]")) {
            rejects(tags, JacksonMapper::fromJsonToTagArray)
        }
    }

    @Test
    fun wellFormedTagsReadAsBefore() {
        val e = JacksonMapper.fromJson(event("""[["e","x","","root"],["t"],[],["p",null,1,true,-2.5]]"""))
        assertEquals(4, e.tags.size)
        assertContentEquals(arrayOf("e", "x", "", "root"), e.tags[0])
        assertContentEquals(arrayOf("t"), e.tags[1])
        assertContentEquals(emptyArray(), e.tags[2])
        assertContentEquals(arrayOf("p", "", "1", "true", "-2.5"), e.tags[3])
        assertEquals(0, JacksonMapper.fromJson(event("[]")).tags.size)

        val frame = JacksonMapper.fromJsonToMessage("""["EVENT","s",${event("""[["t","nostr"]]""")}]""") as EventMessage
        assertContentEquals(arrayOf("t", "nostr"), frame.event.tags[0])

        val tags = JacksonMapper.fromJsonToTagArray("""[["a","b"],["c"]]""")
        assertContentEquals(arrayOf("a", "b"), tags[0])
        assertContentEquals(arrayOf("c"), tags[1])
    }
}
