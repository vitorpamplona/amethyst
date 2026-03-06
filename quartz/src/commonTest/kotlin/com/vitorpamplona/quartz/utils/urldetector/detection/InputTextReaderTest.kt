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
package com.vitorpamplona.quartz.utils.urldetector.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputTextReaderTest {
    @Test
    fun testSimpleRead() {
        val reader = InputTextReader(CONTENT)
        for (i in 0..<CONTENT.length) {
            assertEquals(reader.read(), CONTENT[i])
        }
    }

    @Test
    fun testEOF() {
        val reader = InputTextReader(CONTENT)
        for (i in 0..<CONTENT.length - 1) {
            reader.read()
        }

        assertFalse(reader.eof())
        reader.read()
        assertTrue(reader.eof())
    }

    @Test
    fun testGoBack() {
        val reader = InputTextReader(CONTENT)
        assertEquals(reader.read(), CONTENT[0])
        reader.goBack()
        assertEquals(reader.read(), CONTENT[0])
        assertEquals(reader.read(), CONTENT[1])
        assertEquals(reader.read(), CONTENT[2])
        reader.goBack()
        reader.goBack()
        assertEquals(reader.read(), CONTENT[1])
        assertEquals(reader.read(), CONTENT[2])
    }

    @Test
    fun testSeek() {
        val reader = InputTextReader(CONTENT)
        reader.seek(4)
        assertEquals(reader.read(), CONTENT[4])

        reader.seek(1)
        assertEquals(reader.read(), CONTENT[1])
    }

    companion object {
        private val CONTENT = "HELLO WORLD"
    }
}
