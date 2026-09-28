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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toClient

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NIP-67 EOSE completeness hints, through both JSON backends. */
class EoseHintsParsingTest {
    private val parsers: List<Pair<String, (String) -> Message>> =
        listOf(
            "jackson" to { json -> JacksonMapper.fromJsonToMessage(json) },
            "kotlinx" to { json -> KotlinSerializationMapper.fromJsonToMessage(json) },
        )

    private fun eachParser(
        json: String,
        check: (String, EoseMessage) -> Unit,
    ) = parsers.forEach { (name, parse) ->
        val msg = parse(json)
        assertIs<EoseMessage>(msg, name)
        check(name, msg)
    }

    @Test
    fun twoElementEoseHasNoHints() =
        eachParser("""["EOSE","sub1"]""") { name, msg ->
            assertEquals("sub1", msg.subId, name)
            assertNull(msg.hints, name)
            assertFalse(msg.isFinished(), name)
            assertFalse(msg.hasMore(), name)
            assertFalse(msg.needsAuth(), name)
        }

    @Test
    fun finishHint() =
        eachParser("""["EOSE","sub2",["finish"]]""") { name, msg ->
            assertEquals("sub2", msg.subId, name)
            assertEquals(listOf("finish"), msg.hints, name)
            assertTrue(msg.isFinished(), name)
            assertFalse(msg.hasMore(), name)
        }

    @Test
    fun moreHint() =
        eachParser("""["EOSE","sub2b",["more"]]""") { name, msg ->
            assertTrue(msg.hasMore(), name)
            assertFalse(msg.isFinished(), name)
        }

    @Test
    fun multipleAndUnknownHints() =
        eachParser("""["EOSE","sub4",["auth","finish","somethingNew"]]""") { name, msg ->
            assertEquals(listOf("auth", "finish", "somethingNew"), msg.hints, name)
            assertTrue(msg.needsAuth(), name)
            assertTrue(msg.isFinished(), name)
        }

    @Test
    fun emptyHintArray() =
        eachParser("""["EOSE","sub5",[]]""") { name, msg ->
            assertEquals(emptyList(), msg.hints, name)
            assertFalse(msg.isFinished(), name)
        }

    @Test
    fun nonStringHintsAreIgnored() =
        eachParser("""["EOSE","sub6",[1,{"a":[2]},["x"],"finish",null]]""") { name, msg ->
            assertEquals(listOf("finish"), msg.hints, name)
        }

    @Test
    fun nonArrayThirdElementIsIgnored() =
        eachParser("""["EOSE","sub7","finish",{"x":1}]""") { name, msg ->
            assertEquals("sub7", msg.subId, name)
            assertNull(msg.hints, name)
        }

    @Test
    fun trailingElementsAfterHintsAreTolerated() =
        eachParser("""["EOSE","sub8",["more"],"extra",5]""") { name, msg ->
            assertEquals(listOf("more"), msg.hints, name)
        }

    @Test
    fun serializesWithoutHintsAsTwoElements() {
        val msg = EoseMessage("sub1")
        assertEquals("""["EOSE","sub1"]""", msg.toJson())
        assertEquals("""["EOSE","sub1"]""", JacksonMapper.toJson(msg))
        assertEquals("""["EOSE","sub1"]""", KotlinSerializationMapper.toJson(msg))
    }

    @Test
    fun serializesHintsAsThirdElement() {
        val msg = EoseMessage("sub1", listOf("auth", "finish"))
        val expected = """["EOSE","sub1",["auth","finish"]]"""
        assertEquals(expected, msg.toJson())
        assertEquals(expected, JacksonMapper.toJson(msg))
        assertEquals(expected, KotlinSerializationMapper.toJson(msg))
    }

    @Test
    fun roundTripsThroughBothBackends() {
        val json = EoseMessage("s", listOf("more")).toJson()
        parsers.forEach { (name, parse) ->
            val back = parse(json)
            assertIs<EoseMessage>(back, name)
            assertEquals(listOf("more"), back.hints, name)
        }
    }
}
