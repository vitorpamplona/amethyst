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
package com.vitorpamplona.quartz.nip5aStaticWebsites.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathTagParseTest {
    private val hash = "f70253e146d7ab35e1d16150e1a24f1df77554e96998ef1f004156d96fa22354"

    @Test
    fun parsesASha256() {
        val tag = PathTag.parse(arrayOf("path", "/index.html", hash))
        assertEquals("/index.html", tag?.path)
        assertEquals(hash, tag?.hash)
    }

    @Test
    fun rejectsATraversalAsTheHash() {
        assertNull(PathTag.parse(arrayOf("path", "/x.txt", "../../shared_prefs/settings.xml")))
    }

    @Test
    fun rejectsAHashOfTheWrongShape() {
        assertNull(PathTag.parse(arrayOf("path", "/x.txt", "abc")))
        assertNull(PathTag.parse(arrayOf("path", "/x.txt", "z".repeat(64))))
        assertNull(PathTag.parse(arrayOf("path", "/x.txt", hash + "00")))
    }
}
