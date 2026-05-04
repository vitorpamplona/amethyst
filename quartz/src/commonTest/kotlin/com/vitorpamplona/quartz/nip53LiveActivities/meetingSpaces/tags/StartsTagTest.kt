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
import kotlin.test.assertNull

class StartsTagTest {
    @Test
    fun parsesNumericValue() {
        assertEquals(1_780_000_000L, StartsTag.parse(arrayOf("starts", "1780000000")))
    }

    @Test
    fun rejectsNonNumeric() {
        // Malformed wire data must NOT crash the room-list renderer.
        assertNull(StartsTag.parse(arrayOf("starts", "soon")))
    }

    @Test
    fun rejectsMissingValue() {
        assertNull(StartsTag.parse(arrayOf("starts")))
    }

    @Test
    fun rejectsWrongName() {
        assertNull(StartsTag.parse(arrayOf("ends", "1780000000")))
    }

    @Test
    fun assembleProducesCanonicalShape() {
        assertEquals(arrayOf("starts", "100").toList(), StartsTag.assemble(100L).toList())
    }

    @Test
    fun assembleRejectsNegative() {
        assertFailsWith<IllegalArgumentException> { StartsTag.assemble(-1) }
    }

    @Test
    fun statusEnumIncludesPlanned() {
        assertEquals(StatusTag.STATUS.PLANNED, StatusTag.STATUS.parse("planned"))
    }
}
