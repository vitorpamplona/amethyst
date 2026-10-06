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

import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** Notes are attacker-controlled: URL detection must stay bounded on any content. */
class UrlDetectorAdversarialTest {
    @Test
    fun manyAtSignsDoNotOverflowTheStack() {
        // readUserPass and readDomainName call each other once per '@'.
        UrlDetector("a@".repeat(50_000)).detect()
    }

    @Test
    fun colonHeavyContentIsNotQuadratic() {
        // Each ':' scanned to the end of the token looking for '@': 160 KB took ~24 s.
        val elapsed = measureTime { UrlDetector("a:".repeat(80_000)).detect() }
        assertTrue(elapsed.inWholeSeconds < 3, "took $elapsed")
    }

    @Test
    fun userPassUrlsStillParse() {
        assertEquals(listOf("http://user:pass@example.com/path"), UrlDetector("see http://user:pass@example.com/path now").detect().map { it.originalUrl })
        assertEquals(listOf("user@example.com"), UrlDetector("mail user@example.com").detect().map { it.originalUrl })
    }

    @Test
    fun anEmptyTagIsNotAContentWarning() {
        assertNull(ContentWarningTag.parse(arrayOf()))
    }
}
