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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A `kind 3330` shard reads in either encoding, and the rules of §1 still apply
 * to the one the deck specifies.
 */
class SnoShardEventTest {
    private fun shard(
        content: String = "",
        tags: Array<Array<String>> = emptyArray(),
    ) = SnoShardEvent("aa".repeat(32), "11".repeat(32), 0L, tags, content, "22".repeat(64))

    private val payloadJson =
        """{"v":2,"name":"hidden","unit":0,"mode":"solid","vertices":[[0,0,0],[2,0,0],[1,0,2],[1,2,1]],"colors":[238,235,239,225],"faces":[[0,1,2]]}"""

    @Test
    fun aShardCarriesThePayloadInContent() {
        val payload = shard(content = payloadJson).shardOrNull()
        assertNotNull(payload)
        assertEquals("hidden", payload.name)
        assertEquals(2, payload.version)
        assertEquals(4, payload.vertexCount)
        assertEquals(1, payload.faceCount)
        assertEquals(0xFFFF0000.toInt(), payload.colors[0])
    }

    @Test
    fun aShardIsHeldToTheSameRulesAsAnObject() {
        // The container changed; §1.9 did not. This is one format in two
        // containers, not two ways of writing the format.
        val result = shard(content = """{"v":2,"name":"x","unit":0,"mode":"wireframe","vertices":[],"colors":[],"faces":[]}""").shard()
        assertTrue(result is SnoResult.Invalid)
        assertEquals("4", result.rule)
    }

    @Test
    fun anEmptyShardIsSealedRatherThanBroken() {
        // §7.6: a reader that cannot open a bag has learned nothing about the
        // bag, so an item without its payload is not an error to report. A
        // client draws nothing for one instead of complaining at its finder.
        assertTrue(shard().isSealed())
        assertTrue(!shard(content = payloadJson).isSealed())

        // It still has no shape to hand back, which is a separate question.
        assertTrue(shard().shard() is SnoResult.Invalid)
    }

    @Test
    fun theCoordinateIsReadButIsOnlyAClaim() {
        val coordinate = "3d5ffa91f5c8c13d0bc82ecfe2e546020b3d51763333589829c9c3fdc24fe74a"
        val withC = shard(content = payloadJson, tags = arrayOf(arrayOf("C", coordinate)))
        assertEquals(coordinate, withC.coordinate())
        assertNull(shard(content = payloadJson).coordinate())
    }
}
