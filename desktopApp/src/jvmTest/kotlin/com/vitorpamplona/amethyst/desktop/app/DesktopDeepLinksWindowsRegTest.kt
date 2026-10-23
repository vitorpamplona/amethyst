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
import kotlin.test.assertTrue

/**
 * The .reg file the Windows self-registration imports. The open command must survive with its
 * quotes intact — the reason this goes through a file instead of `reg add` is that the JVM's
 * ProcessBuilder flattening mangles embedded quotes on the command line, and reg.exe then
 * refuses the add with "Invalid syntax".
 */
class DesktopDeepLinksWindowsRegTest {
    @Test
    fun openCommandKeepsQuotedExeAndPercentOne() {
        val content =
            DesktopDeepLinks.windowsUrlSchemeRegFile(
                "C:\\Program Files\\Amethyst\\Amethyst.exe",
                listOf("nostr"),
            )
        assertTrue(
            content.contains(
                "[HKEY_CURRENT_USER\\Software\\Classes\\nostr\\shell\\open\\command]",
            ),
        )
        // .reg string escaping: backslashes doubled, the exe's quotes and %1's quotes backslashed.
        assertTrue(
            content.contains(
                "@=\"\\\"C:\\\\Program Files\\\\Amethyst\\\\Amethyst.exe\\\" \\\"%1\\\"\"",
            ),
            "open command line was:\n" + content.lineSequence().last { it.startsWith("@=") },
        )
    }

    @Test
    fun bothSchemesLandInOneFile() {
        val content =
            DesktopDeepLinks.windowsUrlSchemeRegFile(
                "C:\\Amethyst\\Amethyst.exe",
                listOf("nostr", "amethyst"),
            )
        assertTrue(content.startsWith("Windows Registry Editor Version 5.00\r\n"))
        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\nostr]"))
        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\amethyst]"))
        assertTrue(content.contains("\"URL Protocol\"=\"\""))
        // Two @= per scheme: the scheme description and the shell\open\command default.
        assertEquals(4, content.lineSequence().count { it.startsWith("@=") })
    }
}
