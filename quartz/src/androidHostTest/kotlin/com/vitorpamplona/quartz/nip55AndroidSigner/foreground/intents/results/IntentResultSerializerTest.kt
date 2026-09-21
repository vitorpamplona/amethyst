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
package com.vitorpamplona.quartz.nip55AndroidSigner.foreground.intents.results

import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IntentResultSerializerTest {
    val example =
        """
[
  {
    "package": null,
    "signature": "336590c3f90dc6b3e709090f48b63cc82db01f4a53702d3a9802c7647d43b8a946964fd8178a5439a655e6a2a9af1143573c64d5828f526b2bf9b24bfbde61dd",
    "result": "336590c3f90dc6b3e709090f48b63cc82db01f4a53702d3a9802c7647d43b8a946964fd8178a5439a655e6a2a9af1143573c64d5828f526b2bf9b24bfbde61dd",
    "rejected": null,
    "id": "z6AkVNy2jAH4vcUcUaYIZHrOhi6oWROj"
  },
  {
    "package": null,
    "signature": "2560b238cabffd3c4b02b3f9f131fceb96c388f68e4a60382b833b74871ef5474baa409e6278768a47330e8b1be041a3980c242fb05014d451603e86e6240683",
    "result": "2560b238cabffd3c4b02b3f9f131fceb96c388f68e4a60382b833b74871ef5474baa409e6278768a47330e8b1be041a3980c242fb05014d451603e86e6240683",
    "rejected": null,
    "id": "ZQGMSmJbSbqCBRxc7elKskWHEPldIJ2j"
  },
  {
    "package": null,
    "signature": "3577ce8638367ed0569e1953bc8379ec252d66903edf6e607bbc0c081039b3ca8bee82df1956981e598a7ea172d84671d66b052a138ce41c5f43f96aed05f536",
    "result": "3577ce8638367ed0569e1953bc8379ec252d66903edf6e607bbc0c081039b3ca8bee82df1956981e598a7ea172d84671d66b052a138ce41c5f43f96aed05f536",
    "rejected": null,
    "id": "yuIGh0hwUN5vN3mYTJoMAP1kE7EjedEu"
  },
  {
    "package": null,
    "signature": "2c39187a337083f473e0b3b31867efbc5399e5d64e8085295eb6f5cd94149f7a2563be90a38ca26519b2e4943137d08ebd743ae7c65d11715c9ff8b99d676c57",
    "result": "2c39187a337083f473e0b3b31867efbc5399e5d64e8085295eb6f5cd94149f7a2563be90a38ca26519b2e4943137d08ebd743ae7c65d11715c9ff8b99d676c57",
    "rejected": null,
    "id": "iAGHGc9OKEkSze36Zqhtep0cGi26oWZp"
  }
]
        """.trimIndent()

    @Test
    fun testDeserializer() {
        val results = IntentResult.fromJsonArray(example)

        println("${results.get(0).javaClass.simpleName}")
        assertEquals(4, results.size)

        assertEquals("z6AkVNy2jAH4vcUcUaYIZHrOhi6oWROj", results[0].id)
        assertNull(results[0].`package`)
        assertEquals("336590c3f90dc6b3e709090f48b63cc82db01f4a53702d3a9802c7647d43b8a946964fd8178a5439a655e6a2a9af1143573c64d5828f526b2bf9b24bfbde61dd", results[0].result)
        assertNull(results[0].rejected)

        assertEquals("ZQGMSmJbSbqCBRxc7elKskWHEPldIJ2j", results[1].id)
        assertNull(results[1].`package`)
        assertEquals("2560b238cabffd3c4b02b3f9f131fceb96c388f68e4a60382b833b74871ef5474baa409e6278768a47330e8b1be041a3980c242fb05014d451603e86e6240683", results[1].result)
        assertNull(results[1].rejected)

        assertEquals("yuIGh0hwUN5vN3mYTJoMAP1kE7EjedEu", results[2].id)
        assertNull(results[2].`package`)
        assertEquals("3577ce8638367ed0569e1953bc8379ec252d66903edf6e607bbc0c081039b3ca8bee82df1956981e598a7ea172d84671d66b052a138ce41c5f43f96aed05f536", results[2].result)
        assertNull(results[2].rejected)

        assertEquals("iAGHGc9OKEkSze36Zqhtep0cGi26oWZp", results[3].id)
        assertNull(results[3].`package`)
        assertEquals("2c39187a337083f473e0b3b31867efbc5399e5d64e8085295eb6f5cd94149f7a2563be90a38ca26519b2e4943137d08ebd743ae7c65d11715c9ff8b99d676c57", results[3].result)
        assertNull(results[3].rejected)
    }
}
