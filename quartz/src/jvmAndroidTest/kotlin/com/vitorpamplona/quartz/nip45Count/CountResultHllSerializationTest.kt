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
package com.vitorpamplona.quartz.nip45Count

import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.CountResultKSerializer
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountResultHllSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSerializeWithHll() {
        val hll = ByteArray(256) { (it % 16).toByte() }
        val result = CountResult(count = 42, approximate = true, hll = hll)
        val element = CountResultKSerializer.serializeToElement(result)

        assertEquals(42, element["count"]!!.jsonPrimitive.int)
        assertNotNull(element["hll"])
        assertEquals(512, element["hll"]!!.jsonPrimitive.content.length)
    }

    @Test
    fun testSerializeWithoutHll() {
        val result = CountResult(count = 10, approximate = false)
        val element = CountResultKSerializer.serializeToElement(result)

        assertEquals(10, element["count"]!!.jsonPrimitive.int)
        assertNull(element["hll"])
    }

    @Test
    fun testDeserializeWithHll() {
        val hll = ByteArray(256) { (it % 8).toByte() }
        val hllHex = HyperLogLog.encode(hll)

        val jsonObject: JsonObject =
            buildJsonObject {
                put("count", 100)
                put("approximate", true)
                put("hll", hllHex)
            }

        val result = CountResultKSerializer.deserializeFromElement(jsonObject)

        assertEquals(100, result.count)
        assertTrue(result.approximate)
        assertNotNull(result.hll)
        assertTrue(hll.contentEquals(result.hll!!))
    }

    @Test
    fun testDeserializeWithoutHll() {
        val jsonObject: JsonObject =
            buildJsonObject {
                put("count", 50)
            }

        val result = CountResultKSerializer.deserializeFromElement(jsonObject)

        assertEquals(50, result.count)
        assertNull(result.hll)
    }

    @Test
    fun testRoundTripSerialization() {
        val hll = ByteArray(256) { (it * 3 % 20).toByte() }
        val original = CountResult(count = 75, approximate = true, hll = hll)

        val element = CountResultKSerializer.serializeToElement(original)
        val deserialized = CountResultKSerializer.deserializeFromElement(element)

        assertEquals(original.count, deserialized.count)
        assertNotNull(deserialized.hll)
        assertTrue(hll.contentEquals(deserialized.hll!!))
    }

    @Test
    fun testDeserializeFromRelayJsonWithHll() {
        // Simulate a real relay response: ["COUNT", "sub1", {"count": 42, "hll": "00010203..."}]
        val hll = ByteArray(256) { 5 }
        val hllHex = HyperLogLog.encode(hll)

        val jsonStr = """{"count":42,"hll":"$hllHex"}"""
        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val result = CountResultKSerializer.deserializeFromElement(jsonObject)
        assertEquals(42, result.count)
        assertNotNull(result.hll)
        assertEquals(256, result.hll!!.size)
        assertEquals(5, result.hll!![0].toInt() and 0xFF)
    }
}
