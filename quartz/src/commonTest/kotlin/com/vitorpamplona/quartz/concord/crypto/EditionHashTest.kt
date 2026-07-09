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
package com.vitorpamplona.quartz.concord.crypto

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EditionHashTest {
    private val eid = ByteArray(32) { 0xAB.toByte() }
    private val content = """{"member":"aa","role_ids":["bb"]}"""

    @Test
    fun hashIsDeterministicAnd32Bytes() {
        val h1 = EditionHash.hash(eid, 4, null, content)
        val h2 = EditionHash.hash(eid, 4, null, content)
        assertEquals(32, h1.size)
        assertContentEquals(h1, h2)
    }

    @Test
    fun genesisAndZeroPrevAreDistinct() {
        // hasPrev flag differentiates "no previous" (0x00) from an explicit zero hash (0x01)
        val genesis = EditionHash.hash(eid, 0, null, content)
        val zeroPrev = EditionHash.hash(eid, 0, ByteArray(32), content)
        assertNotEquals(genesis.toHexKey(), zeroPrev.toHexKey())
    }

    @Test
    fun versionAndContentAndEntityChangeTheHash() {
        val base = EditionHash.hash(eid, 4, null, content).toHexKey()
        assertNotEquals(base, EditionHash.hash(eid, 5, null, content).toHexKey())
        assertNotEquals(base, EditionHash.hash(eid, 4, null, content + " ").toHexKey())
        assertNotEquals(base, EditionHash.hash(ByteArray(32) { 0xCD.toByte() }, 4, null, content).toHexKey())
    }

    @Test
    fun chainLinksThroughPrevHash() {
        val v0 = EditionHash.hash(eid, 0, null, """{"name":"general"}""")
        val v1 = EditionHash.hash(eid, 1, v0, """{"name":"lounge"}""")
        // v1 commits to v0; recomputing v1 with a different prev breaks the link
        assertNotEquals(v1.toHexKey(), EditionHash.hash(eid, 1, ByteArray(32), """{"name":"lounge"}""").toHexKey())
    }

    @Test
    fun contentIsHashedAsExactBytesNotReserialized() {
        // Two byte strings that differ only in whitespace must hash differently,
        // proving we hash the wire bytes verbatim.
        val compact = EditionHash.hash(eid, 1, null, """{"a":1}""").toHexKey()
        val spaced = EditionHash.hash(eid, 1, null, """{ "a": 1 }""").toHexKey()
        assertNotEquals(compact, spaced)
    }
}
