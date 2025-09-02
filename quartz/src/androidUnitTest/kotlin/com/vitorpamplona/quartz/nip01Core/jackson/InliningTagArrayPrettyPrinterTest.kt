/**
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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class InliningTagArrayPrettyPrinterTest {
    val mapper =
        jacksonObjectMapper().apply {
            setDefaultPrettyPrinter(InliningTagArrayPrettyPrinter())
        }

    @Test
    fun test() {
        val writer = mapper.writerWithDefaultPrettyPrinter()

        val data =
            mapOf(
                "tags" to arrayOf(intArrayOf(1, 2, 3), intArrayOf(4, 5, 6), intArrayOf(7, 8, 9)),
            )
        val expected =
            """
            {
              "tags": [
                [1, 2, 3],
                [4, 5, 6],
                [7, 8, 9]
              ]
            }
            """.trimIndent()
        val json = writer.writeValueAsString(data)
        assertEquals(expected, json)

        val data2 =
            mapOf(
                "tags" to arrayOf(arrayOf(intArrayOf(1, 2), intArrayOf(3, 4)), arrayOf(intArrayOf(5, 6), intArrayOf(7, 8))),
            )
        val expected2 =
            """
            {
              "tags": [
                [[1, 2], [3, 4]],
                [[5, 6], [7, 8]]
              ]
            }
            """.trimIndent()
        val json2 = writer.writeValueAsString(data2)
        assertEquals(expected2, json2)
    }

    @Test
    fun testEvent() {
        val nostrObject =
            """
            {
              "id": "490d7439e530423f2540d4f2bdb73a0a2935f3df9e1f2a6f699a140c7db311fe",
              "pubkey": "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
              "created_at": 1740669816,
              "kind": 0,
              "tags": [
                ["alt", "User profile for Vitor"],
                ["name", "Vitor"]
              ],
              "content": "{\"name\":\"Vitor\"}",
              "sig": "977a6152199f17d103d8d56736ed1b7767054464cf9423d017c01c8cdd2344698f0a5e13da8dff98d01bb1f798837e3b6271e1fd1cac861bb90686f622ae6ef4"
            }
            """.trimIndent()

        val tree = mapper.readTree(nostrObject)

        val prettified = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree)

        assertEquals(nostrObject, prettified)
    }
}
