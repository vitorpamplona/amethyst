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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrintJob
import com.vitorpamplona.amethyst.commons.frontpage.PrintJobEnd
import com.vitorpamplona.amethyst.commons.frontpage.PrintTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrintQuitRulesTest {
    private val running = FrontPagePrintJob("a".repeat(64), PrintTrigger.MANUAL, startedAt = 0)
    private val scheduled = running.copy(trigger = PrintTrigger.SCHEDULED)
    private val ended = running.copy(end = PrintJobEnd.Ready("2026-11-12", null), endedAt = 10)

    @Test
    fun anyRunningPrintCounts() {
        assertTrue(PrintQuitRules.printing(listOf(running)))
        // The morning's print costs the reader as much as one they started.
        assertTrue(PrintQuitRules.printing(listOf(scheduled)))
        assertFalse(PrintQuitRules.printing(listOf(ended)))
        assertFalse(PrintQuitRules.printing(emptyList()))
    }

    @Test
    fun closingTheWindowAsksOnlyWhileAPrintRuns() {
        assertEquals(CloseAction.QUIT, PrintQuitRules.onClose(printing = false, screenWindowsOpen = false))
        assertEquals(CloseAction.ASK, PrintQuitRules.onClose(printing = true, screenWindowsOpen = false))
        // Screen windows keep the app open anyway: the main window only hides, as before.
        assertEquals(CloseAction.HIDE, PrintQuitRules.onClose(printing = true, screenWindowsOpen = true))
        assertEquals(CloseAction.HIDE, PrintQuitRules.onClose(printing = false, screenWindowsOpen = true))
    }

    @Test
    fun quitAsksOnlyWhileAPrintRuns() {
        assertEquals(CloseAction.ASK, PrintQuitRules.onQuit(printing = true))
        assertEquals(CloseAction.QUIT, PrintQuitRules.onQuit(printing = false))
    }

    @Test
    fun aKeptPrintQuitsOnceItEndsUnlessTheReaderCameBack() {
        assertTrue(PrintQuitRules.quitsAfterPrint(keptForPrint = true, printing = false, anyWindowShowing = false))
        assertFalse(PrintQuitRules.quitsAfterPrint(keptForPrint = true, printing = true, anyWindowShowing = false))
        assertFalse(PrintQuitRules.quitsAfterPrint(keptForPrint = true, printing = false, anyWindowShowing = true))
        // Never for a window the reader didn't close for a print.
        assertFalse(PrintQuitRules.quitsAfterPrint(keptForPrint = false, printing = false, anyWindowShowing = false))
    }
}
