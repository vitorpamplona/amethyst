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
package com.vitorpamplona.quartz.nip92IMeta

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IMetaTagBuilderTest {
    private val sampleUrl = "https://example.com/img.jpg"

    @Test
    fun emptyValueIsSkipped() {
        val tag =
            IMetaTagBuilder(sampleUrl)
                .add("x", "abc")
                .add("alt", "")
                .build()
                .toTagArray()

        assertFalse(tag.any { it.startsWith("alt") }, "empty alt should not appear in tag: ${tag.toList()}")
        assertNoDanglingSpace(tag)
    }

    @Test
    fun whitespaceOnlyValueIsSkipped() {
        val tag =
            IMetaTagBuilder(sampleUrl)
                .add("x", "abc")
                .add("alt", "   ")
                .add("content-warning", "\t\n")
                .build()
                .toTagArray()

        assertFalse(tag.any { it.startsWith("alt") }, "whitespace alt should not appear: ${tag.toList()}")
        assertFalse(tag.any { it.startsWith("content-warning") }, "whitespace content-warning should not appear: ${tag.toList()}")
        assertNoDanglingSpace(tag)
    }

    @Test
    fun leadingAndTrailingWhitespaceIsTrimmed() {
        val tag =
            IMetaTagBuilder(sampleUrl)
                .add("alt", "  hello world  ")
                .build()
                .toTagArray()

        assertTrue(tag.contains("alt hello world"), "alt should be trimmed: ${tag.toList()}")
        assertNoDanglingSpace(tag)
    }

    @Test
    fun internalSpacesArePreserved() {
        val tag =
            IMetaTagBuilder(sampleUrl)
                .add("alt", "a scenic photo of the coast")
                .build()
                .toTagArray()

        assertTrue(tag.contains("alt a scenic photo of the coast"), "internal spaces preserved: ${tag.toList()}")
    }

    @Test
    fun urlAndOneOtherFieldAreEmittedPerNip92() {
        val tag =
            IMetaTagBuilder(sampleUrl)
                .add("m", "image/jpeg")
                .build()
                .toTagArray()

        assertEquals("imeta", tag[0])
        assertEquals("url $sampleUrl", tag[1])
        assertContentEquals(arrayOf("imeta", "url $sampleUrl", "m image/jpeg"), tag)
        assertNoDanglingSpace(tag)
    }

    @Test
    fun emptyValuesProduceMinimalTag() {
        val tag =
            IMetaTagBuilder(sampleUrl)
                .add("alt", "")
                .add("content-warning", "")
                .build()
                .toTagArray()

        assertContentEquals(arrayOf("imeta", "url $sampleUrl"), tag, "only url should remain: ${tag.toList()}")
    }

    @Test
    fun fullImetaProducesNoDanglingSpaces() {
        val tag =
            IMetaTagBuilder("https://blossom.example/abc.jpg")
                .add("x", "a27f4c02678fe5390e34580fa1b74c9e44ef161c62819bc798331599f2846d47")
                .add("size", "168257")
                .add("m", "image/jpeg")
                .add("dim", "1068x1901")
                .add("blurhash", "]LI#u#\$*~Wj[")
                .add("ox", "a27f4c02678fe5390e34580fa1b74c9e44ef161c62819bc798331599f2846d47")
                .add("alt", "Sample alt text")
                .build()
                .toTagArray()

        assertNoDanglingSpace(tag)
        tag.drop(1).forEach { entry ->
            assertTrue(entry.contains(' '), "every entry past index 0 must be 'key SPACE value' per NIP-92: '$entry'")
        }
    }

    private fun assertNoDanglingSpace(tag: Array<String>) {
        tag.forEachIndexed { i, value ->
            assertEquals(value.trim(), value, "tag[$i] has leading or trailing whitespace: '$value'")
        }
    }
}
