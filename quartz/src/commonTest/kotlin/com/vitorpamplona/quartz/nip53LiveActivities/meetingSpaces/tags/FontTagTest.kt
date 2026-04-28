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
package com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FontTagTest {
    @Test
    fun parsesFamilyOnly() {
        val parsed = FontTag.parse(arrayOf("f", "Inter"))
        assertNotNull(parsed)
        assertEquals("Inter", parsed.family)
        assertNull(parsed.url)
    }

    @Test
    fun parsesFamilyWithUrl() {
        val parsed = FontTag.parse(arrayOf("f", "Inter", "https://fonts.example/inter.woff2"))
        assertNotNull(parsed)
        assertEquals("Inter", parsed.family)
        assertEquals("https://fonts.example/inter.woff2", parsed.url)
    }

    @Test
    fun rejectsBlankFamily() {
        // A blank family is not actionable for the renderer — match
        // ColorTag's strictness rather than letting an empty value
        // through and surprising the typography fallback.
        assertNull(FontTag.parse(arrayOf("f", "")))
        assertNull(FontTag.parse(arrayOf("f", "  ")))
    }

    @Test
    fun rejectsMissingFamily() {
        assertNull(FontTag.parse(arrayOf("f")))
    }

    @Test
    fun rejectsWrongName() {
        assertNull(FontTag.parse(arrayOf("font", "Inter")))
    }

    @Test
    fun blankUrlBecomesNull() {
        // A blank third element is treated as "no URL" rather than
        // an empty URL — otherwise the renderer would attempt an
        // HTTP fetch for "" and fail at the URL parser.
        val parsed = FontTag.parse(arrayOf("f", "Inter", ""))
        assertNotNull(parsed)
        assertNull(parsed.url)
    }

    @Test
    fun assembleFamilyOnly() {
        assertEquals(
            arrayOf("f", "Inter").toList(),
            FontTag.assemble("Inter").toList(),
        )
    }

    @Test
    fun assembleWithUrl() {
        assertEquals(
            arrayOf("f", "Inter", "https://fonts.example/inter.woff2").toList(),
            FontTag.assemble("Inter", "https://fonts.example/inter.woff2").toList(),
        )
    }

    @Test
    fun assembleRejectsBlankFamily() {
        assertFailsWith<IllegalArgumentException> { FontTag.assemble("") }
    }
}
