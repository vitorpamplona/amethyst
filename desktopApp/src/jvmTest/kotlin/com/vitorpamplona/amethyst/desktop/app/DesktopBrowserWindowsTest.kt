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

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * How a link reaches the Windows shell: rundll32 by its full path, with the link as one argument,
 * and a failed AWT browse logged by Windows' reason, not by the address.
 */
class DesktopBrowserWindowsTest {
    @Test
    fun aWebLinkGoesToRundllByItsFullPath() {
        assertEquals(
            listOf("C:\\Windows\\System32\\rundll32.exe", "url.dll,FileProtocolHandler", "https://example.com/a?b=c&d=e#f"),
            DesktopBrowser.windowsShellCommand("https://example.com/a?b=c&d=e#f", "C:\\Windows"),
        )
    }

    @Test
    fun aTrailingBackslashInTheSystemRootIsNotDoubled() {
        assertEquals(
            "C:\\Windows\\System32\\rundll32.exe",
            DesktopBrowser.windowsShellCommand("https://example.com", "C:\\Windows\\")?.first(),
        )
    }

    @Test
    fun anUnknownSystemRootLeavesTheLinkToAwt() {
        assertNull(DesktopBrowser.windowsShellCommand("https://example.com", null))
        assertNull(DesktopBrowser.windowsShellCommand("https://example.com", " "))
    }

    @Test
    fun anInvalidUriNeverReachesTheCommandLine() {
        // A space or a quote would split the argument or break out of its quoting.
        assertNull(DesktopBrowser.windowsShellCommand("https://example.com/a b", "C:\\Windows"))
        assertNull(DesktopBrowser.windowsShellCommand("https://example.com/\"&calc", "C:\\Windows"))
    }

    @Test
    fun theOsReasonIsKeptAndTheAddressDropped() {
        val e = IOException("Failed to open https://example.com/?secret=5ec2e7. Error message: The system cannot find the file specified.")
        val reason = DesktopBrowser.osError(e)
        assertEquals(" (The system cannot find the file specified.)", reason)
        assertFalse("5ec2e7" in reason)
    }

    @Test
    fun anyOtherMessageAddsNothing() {
        assertEquals("", DesktopBrowser.osError(IOException("Failed to open https://example.com/?secret=5ec2e7")))
        assertEquals("", DesktopBrowser.osError(IOException()))
    }
}
