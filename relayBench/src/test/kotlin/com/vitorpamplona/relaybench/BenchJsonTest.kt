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
package com.vitorpamplona.relaybench

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BenchJsonTest {
    @Test
    fun splitsTopLevelObjectsOfAnArray() {
        val input = """ [ {"a":"}{","b":{"c":[1,{"d":2}]}}, {"e":"quote \" and \\ end }"} ,{} ] """
        val seen = ArrayList<String>()
        BenchJson.forEachArrayObject(StringReader(input)) {
            seen += it
            true
        }
        assertEquals(listOf("""{"a":"}{","b":{"c":[1,{"d":2}]}}""", """{"e":"quote \" and \\ end }"}""", "{}"), seen)
    }

    @Test
    fun stopsWhenAsked() {
        var count = 0
        BenchJson.forEachArrayObject(StringReader("""[{"a":1},{"a":2},{"a":3}]""")) { ++count < 2 }
        assertEquals(2, count)
    }

    @Test
    fun readsFramesLikeJacksonNodes() {
        val ok = BenchJson.parse("""["OK","abc",true,""]""")
        assertEquals("OK", ok[0]?.asText())
        assertEquals("abc", ok[1]?.asText())
        assertEquals(true, ok[2]?.asBoolean())
        assertNull(ok[9])
        assertNull(ok["id"])

        val event = BenchJson.parse("""["EVENT","s",{"id":"x","kind":"7","created_at":12.9}]""")[2]
        assertEquals("x", event["id"]?.asText())
        assertEquals(7, event["kind"]?.asInt())
        assertEquals(12L, event["created_at"]?.asLong())
        assertEquals("null", BenchJson.parse("[null]")[0]?.asText())
    }
}
