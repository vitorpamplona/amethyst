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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * How a link reaches the Windows shell: rundll32 by its full path, with the link as one ASCII
 * argument. And `file:` links, which no OS gets to open.
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
    fun nonAsciiGoesOverPercentEncoded() {
        // url.dll's handler is ANSI-only: 日本 would reach the browser as "??".
        assertEquals(
            "https://ja.wikipedia.org/wiki/%E6%97%A5%E6%9C%AC",
            DesktopBrowser.windowsShellCommand("https://ja.wikipedia.org/wiki/日本", "C:\\Windows")?.last(),
        )
    }

    @Test
    fun aFileLinkIsNeverOpened() {
        // Refused before any OS call, so this launches nothing on any machine.
        assertFalse(DesktopBrowser.open("file://attacker.example/share/payload.exe"))
        assertFalse(DesktopBrowser.open("FILE:///C:/Windows/System32/calc.exe"))
    }
}
