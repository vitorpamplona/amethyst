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
package com.vitorpamplona.amethyst.desktop.napplet

import org.junit.After
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserDownloadsTest {
    private val folder = createTempDirectory("downloads-test").toFile()

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun aSuggestedNameBecomesAPlainFileName() {
        assertEquals("report.pdf", BrowserDownloads.safeName("report.pdf"))
        assertEquals("passwd", BrowserDownloads.safeName("../../etc/passwd"), "no folder in it")
        assertEquals("evil.exe", BrowserDownloads.safeName("C:\\Windows\\evil.exe"))
        assertEquals("bashrc", BrowserDownloads.safeName(".bashrc"), "not hidden")
        assertEquals("ab.txt", BrowserDownloads.safeName("a\u0000b.txt"), "no control characters")
        assertEquals("download", BrowserDownloads.safeName("..."), "never empty")
        assertEquals("download", BrowserDownloads.safeName(""))
        val long = BrowserDownloads.safeName("x".repeat(400) + ".tar.gz")
        assertTrue(long.length <= 180 && long.endsWith(".gz"), "a long name keeps its extension: $long")
    }

    @Test
    fun aTakenNameGetsANumber() {
        File(folder, "report.pdf").writeText("old")
        val first = BrowserDownloads.reserve(folder, "report.pdf")
        assertEquals("report (1).pdf", first.name, "a file already there")
        val second = BrowserDownloads.reserve(folder, "report.pdf")
        assertEquals("report (2).pdf", second.name, "a download still on its way")
        BrowserDownloads.release(first)
        assertEquals("report (1).pdf", BrowserDownloads.reserve(folder, "report.pdf").name, "free again once it ended unsaved")
        assertEquals("README", BrowserDownloads.reserve(folder, "README").name)
        assertEquals("README (1)", BrowserDownloads.reserve(folder, "README").name, "a name without an extension")
    }
}
