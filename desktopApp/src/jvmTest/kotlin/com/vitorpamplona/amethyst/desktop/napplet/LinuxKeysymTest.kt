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
import kotlin.test.assertNull

class LinuxKeysymTest {
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
