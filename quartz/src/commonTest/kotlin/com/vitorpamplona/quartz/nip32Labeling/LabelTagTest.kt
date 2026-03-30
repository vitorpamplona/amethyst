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
package com.vitorpamplona.quartz.nip32Labeling

import com.vitorpamplona.quartz.nip32Labeling.tags.LabelNamespaceTag
import com.vitorpamplona.quartz.nip32Labeling.tags.LabelTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelTagTest {
    @Test
    fun testParseLabelWithNamespace() {
        val tag = arrayOf("l", "MIT", "license")
        val label = LabelTag.parse(tag)

        assertNotNull(label)
        assertEquals("MIT", label.label)
        assertEquals("license", label.namespace)
    }

    @Test
    fun testParseLabelWithoutNamespaceDefaultsToUgc() {
        val tag = arrayOf("l", "my-label")
        val label = LabelTag.parse(tag)

        assertNotNull(label)
        assertEquals("my-label", label.label)
        assertEquals("ugc", label.namespace)
    }

    @Test
    fun testParseLabelWithEmptyNamespaceDefaultsToUgc() {
        val tag = arrayOf("l", "my-label", "")
        val label = LabelTag.parse(tag)

        assertNotNull(label)
        assertEquals("my-label", label.label)
        assertEquals("ugc", label.namespace)
    }

    @Test
    fun testParseLabelReturnsNullForInvalidTag() {
        assertNull(LabelTag.parse(arrayOf("l")))
        assertNull(LabelTag.parse(arrayOf("l", "")))
        assertNull(LabelTag.parse(arrayOf("e", "something")))
        assertNull(LabelTag.parse(arrayOf()))
    }

    @Test
    fun testParseLabelValue() {
        val tag = arrayOf("l", "en", "ISO-639-1")
        assertEquals("en", LabelTag.parseLabel(tag))
    }

    @Test
    fun testAssembleLabel() {
        val assembled = LabelTag.assemble("MIT", "license")
        assertEquals(3, assembled.size)
        assertEquals("l", assembled[0])
        assertEquals("MIT", assembled[1])
        assertEquals("license", assembled[2])
    }

    @Test
    fun testRoundTrip() {
        val original = LabelTag("approve", "nip28.moderation")
        val assembled = original.toTagArray()
        val parsed = LabelTag.parse(assembled)

        assertNotNull(parsed)
        assertEquals(original, parsed)
    }

    @Test
    fun testIsTagged() {
        assertTrue(LabelTag.isTagged(arrayOf("l", "something")))
        assertTrue(LabelTag.isTagged(arrayOf("l", "MIT", "license")))
    }

    @Test
    fun testIsTaggedWithValue() {
        assertTrue(LabelTag.isTagged(arrayOf("l", "MIT", "license"), "MIT"))
    }

    @Test
    fun testIsTaggedWithNamespace() {
        assertTrue(LabelTag.isTaggedWithNamespace(arrayOf("l", "MIT", "license"), "license"))
    }

    @Test
    fun testParseNamespace() {
        val tag = arrayOf("L", "license")
        val namespace = LabelNamespaceTag.parse(tag)

        assertNotNull(namespace)
        assertEquals("license", namespace.namespace)
    }

    @Test
    fun testParseNamespaceReturnsNullForInvalid() {
        assertNull(LabelNamespaceTag.parse(arrayOf("L")))
        assertNull(LabelNamespaceTag.parse(arrayOf("L", "")))
        assertNull(LabelNamespaceTag.parse(arrayOf("l", "something")))
    }

    @Test
    fun testAssembleNamespace() {
        val assembled = LabelNamespaceTag.assemble("com.example.ontology")
        assertEquals(2, assembled.size)
        assertEquals("L", assembled[0])
        assertEquals("com.example.ontology", assembled[1])
    }

    @Test
    fun testNamespaceRoundTrip() {
        val original = LabelNamespaceTag("ISO-639-1")
        val assembled = original.toTagArray()
        val parsed = LabelNamespaceTag.parse(assembled)

        assertNotNull(parsed)
        assertEquals(original, parsed)
    }

    @Test
    fun testTagAssociationNamespace() {
        val ns = LabelNamespaceTag("#t")
        assertTrue(ns.isTagAssociation())
    }

    @Test
    fun testParseNamespaceValue() {
        assertEquals("ugc", LabelNamespaceTag.parseNamespace(arrayOf("L", "ugc")))
        assertNull(LabelNamespaceTag.parseNamespace(arrayOf("l", "ugc")))
    }
}
