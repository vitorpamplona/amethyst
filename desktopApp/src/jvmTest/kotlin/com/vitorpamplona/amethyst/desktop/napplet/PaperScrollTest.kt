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

import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaperScrollTest {
    private val extension = "chrome-extension://aaccccgoblfpahcmdninbacclepgppci/${PaperScrollReports.FILE}"

    @Test
    fun readsTheReadersReports() {
        assertEquals(120.0 to 17182.0, PaperScrollReports.parse("amethyst-paper-scroll 120.0 17182.0", extension))
        assertEquals(0.0 to 0.0, PaperScrollReports.parse("amethyst-paper-scroll 0.0 0.0", extension))
    }

    @Test
    fun aReportFromAnywhereElseIsNotOne() {
        // The page's own address, or another script, can't speak for the reader.
        assertNull(PaperScrollReports.parse("amethyst-paper-scroll 1.0 2.0", "https://n1.napplet.local/index.html"))
        assertNull(PaperScrollReports.parse("amethyst-paper-scroll 1.0 2.0", "chrome-extension://aaccccgoblfpahcmdninbacclepgppci/guard.js"))
        assertNull(PaperScrollReports.parse("Refused to load the image 'amethyst-paper-scroll 1 2'", extension))
        assertNull(PaperScrollReports.parse("amethyst-paper-scroll 1.0", extension))
        assertNull(PaperScrollReports.parse("amethyst-paper-scroll x 2.0", extension))
        assertNull(PaperScrollReports.parse("amethyst-paper-scroll 1.0 -2.0", extension))
        assertNull(PaperScrollReports.parse("amethyst-paper-scroll NaN 2.0", extension))
    }

    @Test
    fun theReaderStaysOnlyInAPapersFrame() {
        // Its guard is the first thing it runs: a frame with an opaque origin at an https address.
        assertTrue(PaperScrollReports.SCRIPT.contains("window===top||window.origin!=='null'||location.protocol!=='https:'"))
    }

    private var clock = 0L

    private fun tracker() = PageScrollTracker(settleMs = 400, now = { clock })

    @Test
    fun knowsNothingUntilThePageReports() {
        val t = tracker()
        assertFalse(t.known)
        assertEquals(0.0, t.room(towardEnd = true))
        assertEquals(0.0, t.take(100.0))
        t.report(0.0, 1000.0)
        assertTrue(t.known)
        assertEquals(1000.0, t.room(towardEnd = true))
        assertEquals(0.0, t.room(towardEnd = false))
    }

    @Test
    fun takesWhatThePageHasRoomFor() {
        val t = tracker()
        t.report(900.0, 1000.0)
        assertEquals(100.0, t.take(250.0))
        assertEquals(0.0, t.room(towardEnd = true))
        assertEquals(0.0, t.take(10.0))
        assertEquals(-1000.0, t.take(-5000.0))
        assertEquals(0.0, t.room(towardEnd = false))
    }

    @Test
    fun whileItsOwnTurnsSettleReportsBringOnlyTheRange() {
        val t = tracker()
        t.report(0.0, 1000.0)
        t.take(300.0)
        // Chromium is still animating: a report from halfway doesn't move it back.
        clock = 100
        t.report(120.0, 1200.0)
        assertEquals(900.0, t.room(towardEnd = true))
        // Settled: the page's word is the truth again (a key moved it meanwhile).
        clock = 500
        t.report(640.0, 1200.0)
        assertEquals(560.0, t.room(towardEnd = true))
        assertEquals(640.0, t.room(towardEnd = false))
    }

    @Test
    fun aShrinkingPageClampsWhereItIs() {
        val t = tracker()
        t.report(800.0, 1000.0)
        t.report(800.0, 300.0)
        assertEquals(0.0, t.room(towardEnd = true))
        assertEquals(300.0, t.room(towardEnd = false))
    }

    @Test
    fun underHalfAPixelIsNoRoom() {
        val t = tracker()
        t.report(999.7, 1000.0)
        assertEquals(0.0, t.room(towardEnd = true))
    }

    @Test
    fun navigationKeysGoToJcefByTheirXKeysym() {
        // JCEF on Linux reads these from the character; without one each was Delete (0xFFFF).
        assertEquals(0xff56, linuxKeysym(KeyEvent.VK_PAGE_DOWN))
        assertEquals(0xff55, linuxKeysym(KeyEvent.VK_PAGE_UP))
        assertEquals(0xff50, linuxKeysym(KeyEvent.VK_HOME))
        assertEquals(0xff57, linuxKeysym(KeyEvent.VK_END))
        // JCEF names these itself, and a letter has its character.
        assertNull(linuxKeysym(KeyEvent.VK_DOWN))
        assertNull(linuxKeysym(KeyEvent.VK_A))
    }
}
