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
package com.vitorpamplona.amethyst.desktop.ui.settings

import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsMetaTest {
    private val nwc =
        SettingsMeta(
            id = "nwc",
            icon = MaterialSymbols.Bolt,
            title = "Wallet Connect (NWC)",
            subtitle = "Connect a Lightning wallet",
            keywords = persistentListOf("lightning", "zap", "connect wallet", "alby"),
        )

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(nwc.matches(""))
        assertTrue(nwc.matches("   "))
    }

    @Test
    fun matchesByTitleCaseInsensitively() {
        assertTrue(nwc.matches("wallet"))
        assertTrue(nwc.matches("WALLET"))
        assertTrue(nwc.matches("nwc"))
    }

    @Test
    fun matchesBySubtitle() {
        assertTrue(nwc.matches("lightning"))
    }

    @Test
    fun matchesByActionKeyword() {
        // A user typing an action term surfaces the card that hosts it.
        assertTrue(nwc.matches("zap"))
        assertTrue(nwc.matches("Alby"))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertTrue(nwc.matches("  zap  "))
    }

    @Test
    fun nonMatchingQueryReturnsFalse() {
        assertFalse(nwc.matches("relay"))
        assertFalse(nwc.matches("blossom"))
    }
}
